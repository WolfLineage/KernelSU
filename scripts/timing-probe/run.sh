#!/system/bin/sh
# Minimal launcher. No shift, no redirection, no glob loops -- compatible
# with MT Manager's restricted shell.
#
# Usage in MT root terminal:
#   sh run.sh arm64
#
# The binary is placed in /data/local/tmp (executable under root/shell), then
# the probe itself drops to an app uid and prints the measurement identity.

ARCH="$1"
if [ -z "$ARCH" ]; then
  ARCH=arm64
fi

HERE=$(pwd)
if [ "$ARCH" = "arm64" ]; then
  SRC="$HERE/timing-probe-arm64"
else
  SRC="$HERE/timing-probe-arm32"
fi

if [ ! -f "$SRC" ]; then
  echo "binary not found: $SRC"
  echo "put timing-probe-$ARCH next to run.sh and cd to that directory"
  exit 1
fi

cp "$SRC" /data/local/tmp/timing-probe
chmod 755 /data/local/tmp/timing-probe
echo "launching /data/local/tmp/timing-probe (probe will drop to app uid)"
/data/local/tmp/timing-probe
