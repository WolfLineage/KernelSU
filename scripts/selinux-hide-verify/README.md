# SELinux hide 时序验证套件

回答一个问题：**YuSU 的 selinux_hide 时序特征修好了吗？是不是换成了另一种形态？怎么检验？**

## 0. 先给结论

`1c6d0e37`（`kernel: unify selinux hide UID gate for timing hardening`）**没有修复任何时序特征，也没有把它换成别的形态**。原因是这次改动对检测器在**语义上不可观测**：

- `ksu_selinux_hide_applies()` 是 `current_uid().val >= FIRST_APPLICATION_UID`，而 `FIRST_APPLICATION_UID == 10000`。
- 检测器是普通 app，uid 恒 ≥ 10000，谓词对它**恒为 true**。
- 改动前后它走的都是同一个分支、同一段代码。变的只是"怎么算出这个 true"（多一次取反），编译后连这一次都没有。

机器码层的实测（NDK clang 21 / aarch64 / -O2）：

```
IDENTICAL  ksu_ctx_write    ==  yusu_ctx_write    (22 instrs)
IDENTICAL  ksu_status_open  ==  yusu_status_open  (23 instrs)
```

所以正确的表述是：**"分支形状统一了，检测器可观测的时序面一个字节都没变。"** 不是"修好了"，也不是"变成另一种特征"，而是**根本没碰到检测器能看到的那一层**。

---

## 0.5 先分清：能测到 KSU 的那条时序，大概率不是 selinux_hide

KSU 家族里真正能被普通 app 用时序判定的，是**系统调用表替换**，不是 SELinux 那套：

```c
/* kernel/hook/syscall_hook_manager.c:139 */
ksu_register_syscall_hook(__NR_setresuid,  ksu_hook_setresuid);
ksu_register_syscall_hook(__NR_execve,     ksu_hook_execve);
ksu_register_syscall_hook(__NR_execveat,   ksu_hook_execveat);
ksu_register_syscall_hook(__NR_newfstatat, ksu_hook_newfstatat);
ksu_register_syscall_hook(__NR_faccessat,  ksu_hook_faccessat);
```

`newfstatat` / `faccessat` 每次调用都要多走：dispatcher 跳转 → `ksu_su_compat_enabled` 判断
→ `ksu_is_allow_uid_for_current()` 查 allowlist；命中 allowlist 之后还要
`strncpy_from_user_nofault()` + `memcmp(path, su_path)`。而 **`statx` 不在替换表里**。

于是检测器只需要 `median(newfstatat) / median(statx) > 1.2` 就能判定 KSU 存在 ——
这正是春秋文档里写的那条检测原理。

**这条和 selinux_hide 完全无关**：

- `git log 44f9f28d..HEAD -- kernel/hook/syscall_hook_manager.c kernel/feature/sucompat.c`
  输出为空 —— YuSU 一个字节没动；
- 它也不受 `feature set selinux_hide 0/1` 影响，由 `su_compat` 开关控制。

所以如果你的检测结论来自"时序"，先确认它测的是不是 `newfstatat`/`faccessat` 相对 `statx`
的比值。如果是，那这条特征 100% 还在，而且**本次改动从来没打算处理它**。

---

## 1. 先把"时序特征"拆开，否则没法验证

"selinux hide 有时序特征"这句话，取决于**谁能测**。逐条列出来：

### 1.1 app（uid ≥ 10000，域 untrusted_app）能触达的面

| 路径 | 权限 | app 能否进入 hook | 相对 stock 的差 |
|---|---|---|---|
| `open`/`read` `/sys/fs/selinux/status` | 0444 | 能，`my_sel_open_handle_status` | 跳过一次判空，约 1–2 条指令 |
| `read` `/sys/fs/selinux/enforce` | 0644 | 未 hook | 0 |
| `open` `/sys/fs/selinux/context` | **0600** | 不能，VFS 直接 EACCES | 0 |
| `open` `/sys/fs/selinux/access` | **0600** | 不能，VFS 直接 EACCES | 0 |
| `write` `/proc/self/attr/current` | 自身可写 | 能，`my_setprocattr` → `PROCESS__SETCURRENT` 先拒 | stock 在同一位置同一结果，≈0 |
| `fsetxattr(fd,"security.selinux",…)` | 自家文件 | 能，`my_inode_setxattr` → 无 `CAP_MAC_ADMIN` 即走 orig | 多一次 `strcmp` + `cap_capable`，~10ns |
| `statx` | — | 未 hook | 0 |
| `newfstatat` / `faccessat` | — | **被 sucompat 替换**（§0.5） | 每次多一次 allowlist 查询；命中 allowlist 后还多一次 `strncpy_from_user_nofault` |

**结论：app 侧没有可用的时序指纹。** 最"重"的两条路径（`context` / `access` 的双 policy 解析
+ sidtab 同步，微秒级差异）都锁在 0600 后面，普通 app 连 `open()` 都过不去。

### 1.2 只有 root / shell 才测得到的面

| 路径 | 差异来源 | 量级 |
|---|---|---|
| `write` `/sys/fs/selinux/context` | `security_context_to_sid_with_policy(backup)` + `sid_to_context(backup)`，比 stock 多解析一份 policy | 微秒级 |
| `write` `/sys/fs/selinux/access` | 两次 `context_to_sid(backup)` + `compute_av_user(backup)` | 微秒级 |
| `write` `/proc/self/attr/current`（合法域迁移） | `-EAGAIN` 时双解析；#3981 之后正常路径单解析 | 亚微秒 |
| 跨 uid 分支差（uid<10000 走 orig / ≥10000 走 hook） | 分支本身 | 存在，但需要同一进程能切换 uid |

**这些是真实的、无法消除的代价**（隐藏要求解析"影子策略"，这是功能本身的开销），
但它们的前置条件是 **root**。root 已经能直接读 `/data/adb`、`lsmod`、`dmesg`，
用时序去推断 KSU 没有意义。

### 1.3 所以"时序被特征了"这句话成立吗

- 如果指的是 `newfstatat` / `faccessat` 相对 `statx` 的比值：**成立**，这是 app 可测、
  确实存在的指纹，但它属于 sucompat 的 syscall hook，见 §0.5，与 selinux_hide 无关。
- 如果指的是 §1.2：成立，但只有 root 能测 → 不构成对普通 app 的检测能力。
- 如果指的是 §1.1：不成立，因为能触达的路径上差异在纳秒级，落不进噪声。

**如果某个检测器声称能在 app 侧用时序判定 selinux_hide，它必须落在 §1.1 的某一行。
本套件的作用就是把这张表逐行量化，让"号称能测"变成"实测 delta 多少"。**

---

## 2. 三层验证

### L1 源码层：改动是否可能影响 app

```bash
git show 1c6d0e37 -- kernel/feature/selinux_hide.c
grep -n "current_uid()" kernel/feature/selinux_hide.c    # 应只剩 predicate 内部一处
```

判据有两条，第二条更硬：

1. 5 个 hook（`write_context` / `write_access` / `setprocattr` / `inode_setxattr` /
   `sel_open_handle_status`）全部经过 `ksu_selinux_hide_applies()`，文件里不再有裸 `10000`。
2. **谓词对所有 uid ≥ 10000 恒为 true**，所以任何 uid ≥ 10000 的调用者在改动前后
   都走同一分支 —— 不需要看机器码就能断定检测器观测不到变化。

### L2 机器码层：确认没有意外的 codegen 漂移

```bash
./codegen-diff.sh          # 自动找 NDK，也可用 $1 指定
```

`IDENTICAL` 说明 `likely(uid < 10000)` 与 `!likely(uid >= 10000)` 编出同一段指令
（编译器把两个分支提示都归一成"原路径优先"）。这一层是**否证**用的：排除
"改完反而多出一条分支"的可能。

对真实函数（不是 fixture）做同样的比对，在 Linux 上：

```bash
llvm-objdump -d --no-show-raw-insn old.o | sed 's/^ *[0-9a-f]*://' > old.s
llvm-objdump -d --no-show-raw-insn new.o | sed 's/^ *[0-9a-f]*://' > new.s
diff old.s new.s
```

### L3 真机层：唯一能回答"时序到底差多少"的方法

在**同一个内核**上把 feature 关掉再打开，差值就是这个内核的 hide 时序指纹：

```
hide=off ──┐
           ├── Δ = on − off     ← 该内核的 hide 指纹
hide=on  ──┘
```

`ksud feature set selinux_hide 0/1` 会真正 patch / unpatch `selinux_write_op[]` 和
LSM hook，不用重新刷机。在 stock / 原版 KSU / YuSU 上各测一次 Δ，然后比 Δ：

| 比较 | 结果 | 结论 |
|---|---|---|
| YuSU vs 原版 KSU | Δ 一致 | 时序特征没被修改（本次改动的必然结果） |
| YuSU vs 原版 KSU | Δ 显著不同 | 确实换了形态，回 L1 找漏改的 hook |
| YuSU vs stock | Δ 显著 | hide 的固有代价仍在（双 policy 解析） |
| YuSU vs stock | Δ ≈ 0 | 说明该路径没进 hook（很可能测错了面，见 §1.1） |

---

## 3. 跑起来

### 3.1 方案 A：真 app 身份（推荐，唯一能证伪"检测器能测"的方案）

`su -c` 之后 `setuid(10123)` **只换 uid 不换 SELinux 域**，进程仍是 `u:r:su:s0`，
`avc_has_perm` 会放行，于是探针能走到 `security_context_to_sid_live()` 的完整解析 ——
这比真正的 untrusted_app 走得深。用它测出来的差值只是 **hook 体的成本上限**，不是 app
能观测到的值。

要得到 app 真实可观测值，必须让进程真的跑在 `untrusted_app` 域。最省事的办法是借
Termux（app uid 10xxx）：

```bash
# 1. 编译并推到设备
adb push out/probe /data/local/tmp/sel_probe

# 2. 拷进 Termux 私有目录（untrusted_app 对 /data/local/tmp 无 execute 权限）
TERMUX_UID=$(adb shell dumpsys package com.termux | awk -F= '/userId=/{print $2; exit}' | tr -d ' ')
adb shell su -c "cp /data/local/tmp/sel_probe /data/data/com.termux/files/home/sel_probe && \
                 chown ${TERMUX_UID}:${TERMUX_UID} /data/data/com.termux/files/home/sel_probe && \
                 chmod 700 /data/data/com.termux/files/home/sel_probe"

# 3. 在 Termux 里跑（这才是 untrusted_app 域 + app uid）
~/sel_probe --label yusu-hide1 --samples 400 --batch 32 --out ~/yusu-hide1.csv

# 4. 取回
adb shell su -c 'cat /data/data/com.termux/files/home/yusu-hide1.csv' > out/yusu-hide1.csv
```

### 3.2 方案 B：hook 体成本上限（su + setuid）

```bash
./run.sh yusu                                   # hide 关/开 × uid 0/app 四轮
./analyze.py --baseline yusu-hide0 --candidate yusu-hide1 out/yusu/*.csv
./analyze.py --baseline ksu-hide1  --candidate yusu-hide1  out/*/*.csv
```

### 3.3 判读

`analyze.py` 对每个 (uid, op) 做 Mann-Whitney U（带 tie 修正）并给出 Cliff's delta。
判据用 delta 不用 p 值：样本量一大，任何微小差异都会"显著"，delta 才反映差异有多大。
`|delta| < 0.147` 记为 `SAME`。

**如果一个检测器声称能在 app 侧判定 selinux_hide，它必然在 §1.1 的某一行上产生
`DIFFERENT`（large）。跑完这张表，这个声称就被量化了。**

想直接复现春秋那条判据：

```bash
./analyze.py --dump --ratio newfstatat:statx out/*/*.csv
./analyze.py --dump --ratio faccessat:statx  out/*/*.csv
```

输出里 `> 1.2 KSU-like` / `< 1.2 clean` 就是检测器的结论。stock 内核应当全 `< 1.2`；
KSU 内核在 app 身份下若 `> 1.2`，说明这条指纹仍然存在，且与 selinux_hide 无关。

---

## 4. 测量陷阱

1. **域 vs uid**：`setuid` 不换域，方案 B 的 `attr_write` / `setxattr` 测的是 hook 体
   成本上限。只有方案 A 是 app 真实视角。
2. **必须 round-robin**：`probe.c` 外层是样本、内层是 op，每个样本是 `batch` 次调用的
   总时长。不要改成"一个 op 跑完再跑下一个"，否则 CPU 升频 / 热节流会被误判成 op 间差异。
3. **同工具链**：LTO、CFI、`-O2` vs `-Oz` 都会改变 codegen，跨版本比机器码没有意义。
4. **别用平均**：延迟是长尾分布，中位数 + p90 才稳定；显著性用 rank-based 检验正是
   为了不被长尾带偏。
5. **温度**：两轮之间至少隔 30s，或把两轮交叉跑三遍取中位。

---

## 5. 文件

| 文件 | 作用 |
|---|---|
| `probe.c` | 设备端探针，12 条路径 × batch 计时，round-robin 采样，输出 CSV |
| `run.sh` | 方案 B：编译 + push + hide 关/开 × uid 0/app 四轮采集 |
| `analyze.py` | Mann-Whitney U + Cliff's delta，输出 SAME / DIFFERENT |
| `codegen-diff.sh` | L2：两种分支形状的 aarch64 汇编对比 |
| `codegen_fixture.c` | L2 的输入，原版 / YuSU 四段等价代码 |
