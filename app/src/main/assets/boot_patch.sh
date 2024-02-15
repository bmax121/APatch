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

echo "APatch Boot Image Patcher"

# Check if 64-bit
if [ $(uname -m) = "aarch64" ]; then
  echo "System is arm64"
else
  echo "System is not arm64"
  exit 1
fi

SUPERKEY=$1
BOOTIMAGE=$2

if [ -z "$BOOTIMAGE" ]; then
  find_boot_image
fi

[ -z "$SUPERKEY" ] && { echo "SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { echo "$BOOTIMAGE does not exist!"; exit 1; }

echo "- Target image: $BOOTIMAGE"

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "kptools not found!"; exit 1; }

echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "Unpack error: $?"
  exit $?
fi

mv kernel kernel.ori

echo "- Patching kernel"
./kptools -p --image kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel

if [ $? -ne 0 ]; then
  echo "Patch error: $?"
  exit $?
fi

echo "- Repacking boot image"
./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1 || exit $?

# Reset any error code
true
