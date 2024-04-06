#Apatch
############################################
# Apatch Flash Script (updater-script)
############################################

##############
# Preparation
##############

# Default permissions
umask 022

OUTFD=$2
COMMONDIR=$INSTALLER/assets
CHROMEDIR=$INSTALLER/assets/chromeos

if [ ! -f $COMMONDIR/util_functions.sh ]; then
  echo "! Unable to extract zip file!"
  exit 1
fi

# Load utility functions
. $COMMONDIR/util_functions.sh

setup_flashable

############
# Detection
############

if echo $APATCH_VER | grep -q '\.'; then
  PRETTY_VER=$APATCH_VER
else
  PRETTY_VER="$APATCH_VER($APATCH_VER_CODE)"
fi
print_title "APATCH $PRETTY_VER Installer"

is_mounted /data || mount /data || is_mounted /cache || mount /cache
mount_partitions
check_data
get_flags
find_boot_image

[ -z $BOOTIMAGE ] && abort "! Unable to detect target image"
ui_print "- Target image: $BOOTIMAGE"

# Detect version and architecture
api_level_arch_detect

[ $API -lt 23 ] && abort "! APATCH only support Android 6.0 and above"

ui_print "- Device platform: $ABI"

BINDIR=$INSTALLER/lib/$ABI
cd $BINDIR
for file in lib*.so; do mv "$file" "${file:3:${#file}-6}"; done
cd /

#cp -af $INSTALLER/lib/$ABI32/libmagisk32.so $BINDIR/magisk32 2>/dev/null

# Check if system root is installed and remove
$BOOTMODE || remove_system_su

##############
# Environment
##############
chmod 755 libmagiskboot.so
chmod 755 libkptools.so
solt=$(getprop ro.boot.slot_suffix)
dd if=/dev/block/by-name/boot$solt of=boot.img
mv kernel kernel-b
libmagiskboot.so unpack boot.img
set -x
libkptools.so -p -i kernel-b -K libkpatch.so -s a1234567 -k kpimg -o kernel
patch_rc=$?
set +x
if [ $patch_rc -ne 0 ]; then
  ui_print "- Patch kernel error: $patch_rc"
  exit $?
fi
libmagiskboot.so repack boot.img
dd if=boot.img of=/dev/block/by-name/boot$solt

exit 1

ui_print "- Constructing environment"

# Copy required files
rm -rf $MAGISKBIN/* 2>/dev/null
mkdir -p $MAGISKBIN 2>/dev/null
cp -af $BINDIR/. $COMMONDIR/. $BBBIN $MAGISKBIN

# Remove files only used by the Magisk app
rm -f $MAGISKBIN/bootctl $MAGISKBIN/main.jar \
  $MAGISKBIN/module_installer.sh $MAGISKBIN/uninstaller.sh

chmod -R 755 $MAGISKBIN

# addon.d
if [ -d /system/addon.d ]; then
  ui_print "- Adding addon.d survival script"
  blockdev --setrw /dev/block/mapper/system$SLOT 2>/dev/null
  mount -o rw,remount /system || mount -o rw,remount /
  ADDOND=/system/addon.d/99-magisk.sh
  cp -af $COMMONDIR/addon.d.sh $ADDOND
  chmod 755 $ADDOND
fi

##################
# Image Patching
##################

install_apatch

# Cleanups
$BOOTMODE || recovery_cleanup
rm -rf $TMPDIR

ui_print "- Done"
exit 0
