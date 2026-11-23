#!/usr/bin/env python3
"""Compare selinux_hide timing probes.

Reads one or more probe CSVs and compares two labels per (uid, op) using
Mann-Whitney U (tie-corrected, normal approximation) and Cliff's delta.

Cliff's delta magnitude (Romano et al.):
    |d| < 0.147  negligible
    |d| < 0.330  small
    |d| < 0.474  medium
    else         large

Usage:
    analyze.py --baseline ksu-hide-off --candidate ksu-hide-on /tmp/*.csv
    analyze.py --baseline ksu-hide-on  --candidate yusu-hide-on /tmp/*.csv
    analyze.py --dump /tmp/*.csv
"""

import argparse
import csv
import math
import sys
from collections import defaultdict


def median(xs):
    xs = sorted(xs)
    n = len(xs)
    if n == 0:
        return float("nan")
    mid = n // 2
    return xs[mid] if n % 2 else 0.5 * (xs[mid - 1] + xs[mid])


def quantile(xs, q):
    xs = sorted(xs)
    if not xs:
        return float("nan")
    pos = (len(xs) - 1) * q
    lo, hi = int(math.floor(pos)), int(math.ceil(pos))
    if lo == hi:
        return xs[lo]
    return xs[lo] + (xs[hi] - xs[lo]) * (pos - lo)


def mann_whitney(a, b):
    """Returns (U for a, two-sided p). Handles ties."""
    n1, n2 = len(a), len(b)
    if n1 == 0 or n2 == 0:
        return float("nan"), float("nan")

    combined = sorted([(v, 0) for v in a] + [(v, 1) for v in b])
    n = n1 + n2
    ranks = [0.0] * n
    tie_term = 0.0
    i = 0
    while i < n:
        j = i
        while j + 1 < n and combined[j + 1][0] == combined[i][0]:
            j += 1
        avg = (i + j) / 2.0 + 1.0
        for k in range(i, j + 1):
            ranks[k] = avg
        t = j - i + 1
        if t > 1:
            tie_term += t ** 3 - t
        i = j + 1

    r1 = sum(ranks[k] for k in range(n) if combined[k][1] == 0)
    u1 = r1 - n1 * (n1 + 1) / 2.0

    mu = n1 * n2 / 2.0
    var = n1 * n2 / 12.0 * ((n + 1) - tie_term / (n * (n - 1)))
    if var <= 0:
        return u1, 1.0
    z = (u1 - mu) / math.sqrt(var)
    p = 2.0 * (1.0 - 0.5 * (1.0 + math.erf(abs(z) / math.sqrt(2.0))))
    return u1, min(1.0, p)


def cliffs_delta(u1, n1, n2):
    """P(candidate > baseline) - P(candidate < baseline).

    Positive means the candidate is slower.
    """
    return 1.0 - 2.0 * u1 / (n1 * n2)


def magnitude(d):
    d = abs(d)
    if d < 0.147:
        return "negligible"
    if d < 0.330:
        return "small"
    if d < 0.474:
        return "medium"
    return "large"


def load(paths):
    data = defaultdict(list)
    for path in paths:
        with open(path, newline="") as fp:
            for row in csv.DictReader(fp):
                key = (row["label"], int(row["uid"]), row["op"])
                data[key].append(int(row["total_ns"]))
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="+")
    ap.add_argument("--baseline")
    ap.add_argument("--candidate")
    ap.add_argument("--dump", action="store_true")
    ap.add_argument("--batch", type=int, default=0, help="calls per sample, for per-call column")
    ap.add_argument("--ratio", help="discriminant as NUM:DEN, e.g. newfstatat:statx")
    args = ap.parse_args()

    data = load(args.csv)
    if not data:
        print("no samples", file=sys.stderr)
        return 1

    if args.dump or not args.baseline:
        print(f"{'label':<22}{'uid':>8}  {'op':<20}{'n':>6}{'median_us':>11}{'p10_us':>10}{'p90_us':>10}")
        for (label, uid, op) in sorted(data):
            xs = data[(label, uid, op)]
            per = (lambda v: v / args.batch / 1000.0) if args.batch else (lambda v: v / 1000.0)
            print(
                f"{label:<22}{uid:>8}  {op:<20}{len(xs):>6}"
                f"{per(median(xs)):>11.2f}{per(quantile(xs, .1)):>10.2f}{per(quantile(xs, .9)):>10.2f}"
            )

        if args.ratio:
            num, _, den = args.ratio.partition(":")
            print()
            print(f"detector discriminant: median({num}) / median({den})")
            print(f"{'label':<22}{'uid':>8}{'ratio':>10}   verdict")
            for (label, uid, op) in sorted(data):
                if op != num:
                    continue
                ref = data.get((label, uid, den))
                if not ref:
                    continue
                r = median(data[(label, uid, op)]) / median(ref)
                print(f"{label:<22}{uid:>8}{r:>10.3f}   {'> 1.2  KSU-like' if r > 1.2 else '< 1.2  clean'}")
        return 0

    print(f"baseline  = {args.baseline}")
    print(f"candidate = {args.candidate}")
    print()
    hdr = f"{'uid':>8}  {'op':<20}{'base_med':>10}{'cand_med':>10}{'ratio':>8}{'p':>12}{'delta':>8}  verdict"
    print(hdr)
    print("-" * len(hdr))

    for (label, uid, op) in sorted(data):
        if label != args.baseline:
            continue
        a = data[(label, uid, op)]
        b = data.get((args.candidate, uid, op))
        if not b:
            continue
        ma, mb = median(a), median(b)
        u, p = mann_whitney(a, b)
        d = cliffs_delta(u, len(a), len(b))
        ratio = mb / ma if ma else float("nan")
        verdict = "SAME" if magnitude(d) == "negligible" else "DIFFERENT"
        print(f"{uid:>8}  {op:<20}{ma/1000:>10.1f}{mb/1000:>10.1f}{ratio:>8.3f}{p:>12.3g}{d:>8.3f}  "
              f"{verdict} ({magnitude(d)})")

    print()
    print("ratio/base_med/cand_med are per-sample totals in microseconds;")
    print("use --batch to convert to per-call cost.")
    print("delta > 0 means the candidate (second label) is slower;")
    print("SAME below |delta| < 0.147, i.e. the two kernels are indistinguishable")
    print("at the resolution this probe can achieve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
