#!/bin/sh

TMPDIR=/dev/tmp
rm -rf $TMPDIR
mkdir -p $TMPDIR 2>/dev/null

export BBBIN=$TMPDIR/busybox
unzip -o "$3" "busybox" -d $TMPDIR >&2

for arch in  "arm64-v8a" ; do
  unzip -o "$3" "lib/$arch/libbusybox.so" -d $TMPDIR >&2
  libpath="$TMPDIR/lib/$arch/libbusybox.so"
  chmod 755 $libpath
  if [ -x $libpath ] && $libpath >/dev/null 2>&1; then
    mv -f $libpath $BBBIN
    break
  fi
done
$BBBIN rm -rf $TMPDIR/lib

export INSTALLER=$TMPDIR/install
$BBBIN mkdir -p $INSTALLER
$BBBIN unzip -o "$3" "assets/*" "addon/*" "META-INF/com/google/*" "lib/*" "META-INF/com/google/*" -x "lib/*/libbusybox.so" -d $INSTALLER >&2
export ASH_STANDALONE=1
if echo "$3" | $BBBIN grep -q "uninstall"; then
  exec $BBBIN sh "$INSTALLER/addon/UninstallAP.sh" "$@"
elif echo "$3" | $BBBIN grep -q "uninstaller"; then
  exec $BBBIN sh "$INSTALLER/addon/UninstallAP.sh" "$@"
else
  exec $BBBIN sh "$INSTALLER/addon/InstallAP.sh" "$@"
fi