#!/sbin/sh
#
TMPDIR=/dev/tmp
rm -rf $TMPDIR
mkdir -p $TMPDIR 2>/dev/null

ui_print() {
  echo -n -e "ui_print $1\n" > /proc/self/fd/$FD
  echo -n -e "ui_print\n" > /proc/self/fd/$FD
}

ui_print "- Welecom to apatch twrp tool"
ui_print "- Installing from Apatch TWRP"
ui_print "- Powered by Apatch"
solt=$(getprop ro.boot.slot_suffix)
if [ -d solt ]; then
	ui_print "*********************************************************"
    ui_print "! Your solt is not set,maybe your device is not support A/B"
    ui_print "! Please backup your devices"
	ui_print "*********************************************************"
fi

export BBBIN=$TMPDIR/busybox
for arch in "x86_64" "x86" "arm64-v8a" "armeabi-v7a"; do
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
$BBBIN unzip -o "$3" "assets/*" "lib/*" "META-INF/com/google/*" -x "lib/*/libbusybox.so" -d $INSTALLER >&2

export ASH_STANDALONE=1
if echo "$3" | $BBBIN grep -q "uninstall"; then
  exec $BBBIN sh "$INSTALLER/assets/uninstaller.sh" "$@"
else
  exec $BBBIN sh "$INSTALLER/META-INF/com/google/android/updater-script" "$@"
fi
