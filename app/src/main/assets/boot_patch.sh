#!/system/bin/sh
#######################################################################################
# APatch Boot Image Patcher
#######################################################################################
#
# Usage: boot_patch.sh <superkey> <bootimage>
#
# This script should be placed in a directory with the following files:
#
# File name          Type          Description
#
# boot_patch.sh      script        A script to patch boot image for APatch.
#                  (this file)      The script will use files in its same
#                                  directory to complete the patching process.
# bootimg            binary        The target boot image
# kpimg              binary        KernelPatch core Image
# kptools            executable    The KernelPatch tools binary to inject kpimg to kernel Image
# magiskboot         executable    Magisk tool to unpack boot.img.
#
#######################################################################################

ARCH=$(getprop ro.product.cpu.abi)

getdir() {
  case "$1" in
    */*)
      dir=${1%/*}
      if [ ! -d $dir ]; then
        echo "/"
      else
        echo $dir
      fi
    ;;
    *) echo "." ;;
  esac
}

# Switch to the location of the script file
cd "$(getdir "${BASH_SOURCE:-$0}")"

# Load utility functions
. ./util_functions.sh

echo "****************************"
echo " APatch Boot Image Patcher"
echo "****************************"

SUPERKEY=$1
BOOTIMAGE=$2
LEGACYSAR=false
PATCHEDKERNEL=false
BACKUPIMAGE="/data/adb/apatch_backup_boot.img"
TMPBACKUPIMAGE="/data/data/me.bmax.apatch/backup/boot.img"

mount_partitions

if [ -z "$BOOTIMAGE" ]; then
  ISDIRECTINSTALL=true
  find_boot_image
fi

[ -z "$SUPERKEY" ] && { echo "- SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { echo "- $BOOTIMAGE does not exist!"; exit 1; }

echo "- Target image: $BOOTIMAGE"

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "- Command magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "- Command kptools not found!"; exit 1; }

echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "- Unpack error: $?"
  exit $?
fi

if [ ! "$ISDIRECTINSTALL" ]; then
  patched=$(./kptools -l --image kernel | grep "patched=" | cut -d'=' -f2)
  if [[ "$patched" == "false" ]]; then
    echo "- Stock boot image detected"
    backup_boot_image
  else
    echo "- Patched boot image detected"
  fi
else
  mv "$TMPBACKUPIMAGE" "$BACKUPIMAGE"
  rmdir $(dirname "$TMPBACKUPIMAGE") 2>/dev/null
fi

cp kernel kernel.ori

# Remove Samsung RKP
./magiskboot hexpatch kernel \
49010054011440B93FA00F71E9000054010840B93FA00F7189000054001840B91FA00F7188010054 \
A1020054011440B93FA00F7140020054010840B93FA00F71E0010054001840B91FA00F7181010054 \
&& PATCHEDKERNEL=true

# Remove Samsung defex
# Before: [mov w2, #-221]   (-__NR_execve)
# After:  [mov w2, #-32768]
./magiskboot hexpatch kernel 821B8012 E2FF8F12 && PATCHEDKERNEL=true

# Force kernel to load rootfs for legacy SAR devices
# skip_initramfs -> want_initramfs
$LEGACYSAR && ./magiskboot hexpatch kernel \
736B69705F696E697472616D667300 \
77616E745F696E697472616D667300 \
&& PATCHEDKERNEL=true

# If the kernel doesn't need to be patched at all,
# keep raw kernel to avoid bootloops on some weird devices
$PATCHEDKERNEL || mv kernel.ori kernel

mv kernel kernel.ori

echo "- Patching kernel"
./kptools -p --image kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel

if [ $? -ne 0 ]; then
  echo "- Patch error: $?"
  exit $?
fi

echo "- Repacking boot image"
./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "- Repack error: $?"
  exit $?
fi

echo "- Cleaning up"
./magiskboot cleanup >/dev/null 2>&1
rm -f kernel.ori

if [ "$ISDIRECTINSTALL" ] && [ -f "new-boot.img" ]; then
  echo "- Flashing new boot image"
  flash_image new-boot.img "$BOOTIMAGE"

  if [ $? -ne 0 ]; then
    echo "- Flash error: $?"
    rm -f new-boot.img
    exit $?
  fi
fi

# Reset any error code
true
