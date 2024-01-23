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

# Check if 64-bit
if [ $(uname -m) = "aarch64" ]; then
  echo "- System arch: arm64"
else
  echo "- System arch: not arm64"
  exit 1
fi

SUPERKEY=$1
BOOTIMAGE=$2
BACKUPDIR="../backup"
BACKUPIMAGE="$BACKUPDIR/boot.img"
LEGACYSAR=false
PATCHEDKERNEL=false

mount_partitions

if [ -z "$BOOTIMAGE" ]; then
  ISDIRECTINSTALL=true
  find_boot_image
  if [ ! -f "$BACKUPIMAGE" ]; then
    echo "Direct flash is not possible!"
    exit 1
  fi
elif [ ! -f "$BACKUPIMAGE" ]; then
  mkdir -p "$BACKUPDIR"
  cp "$BOOTIMAGE" "$BACKUPIMAGE"
fi

[ -z "$SUPERKEY" ] && { echo "- SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { echo "- $BOOTIMAGE does not exist!"; exit 1; }

echo "- Target image: $BOOTIMAGE"

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "- Command magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "- Command kptools not found!"; exit 1; }

echo "- Unpacking boot image"
if [ "$ISDIRECTINSTALL" ]; then
  ./magiskboot unpack "$BACKUPIMAGE" >/dev/null 2>&1
else
  ./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1
fi

if [ $? -ne 0 ]; then
  echo "- Unpack error: $?"
  exit $?
fi

mv kernel kernel.ori

echo "- Patching kernel"
./kptools -p kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel

if [ $? -ne 0 ]; then
  echo "- Patch error: $?"
  exit $?
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

echo "- Repacking boot image"
if [ "$ISDIRECTINSTALL" ]; then
  ./magiskboot repack "$BACKUPIMAGE" >/dev/null 2>&1
else
  ./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1
fi

if [ $? -ne 0 ]; then
  echo "- Repack error: $?"
  exit $?
fi

echo "- Cleaning up"
./magiskboot cleanup >/dev/null 2>&1
rm -f kernel.ori

if [ "$ISDIRECTINSTALL" ] && [ -f "new-boot.img" ]; then
  echo "- Flashing patched boot image"
  flash_image new-boot.img "$BOOTIMAGE"

  if [ $? -ne 0 ]; then
    echo "- Flash error: $?"
    exit $?
  fi
fi

# Reset any error code
true
