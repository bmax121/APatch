#!/system/bin/sh
#######################################################################################
# APatch Boot Image Patcher
#######################################################################################
#
# Usage: boot_patch.sh <bootimage> <superkey> <outimage>
#
# This script should be placed in a directory with the following files:
#
# File name          Type          Description
#
# boot_patch.sh      script        A script to patch boot image for APatch.
#                  (this file)     The script will use files in its same
#                                  directory to complete the patching process.
# util_functions.sh  script        A script which hosts all functions required
#                                  for this script to work properly.
# bootimg            binary        The target boot image
# kpimg              binary        KernelPatch core Image
# kptools            executable    The KernelPatch tools binary to inject kpimg to kernel Image
# magiskboot         executable    Magisk tool to unpack boot.img.
#
#######################################################################################
ARCH=$(getprop ro.product.cpu.abi)

if [ "$ARCH" != "arm64-v8a" ]; then
  echo "Not is arm64"
  exit 1
fi

echo "system is arm64"
# Pure bash dirname implementation
getdir() {
  case "$1" in
    */*)
      dir=${1%/*}
      if [! -d $dir ]; then
        echo "/"
      else
        echo $dir
      fi
    ;;
    *) echo "." ;;
  esac
}

# Switch to the location of the script file
cd "$(getdir "${BASH_SOURCE:-$0}")" || exit $?

echo "APatch Boot Image Patcher"

SUPERKEY=$1
BOOTIMAGE=$2

[ -z "$SUPERKEY" ] && { echo "SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { echo "$BOOTIMAGE does not exist!"; exit 1; }

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "kptools not found!"; exit 1; }

echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE"

mv kernel kernel.ori

echo "- Patching kernel"
./kptools -p kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel

if [ $? -ne 0 ]; then
  echo "Patch error: $?"
  exit $?
fi

echo "- Repacking boot image"
./magiskboot repack "$BOOTIMAGE" || exit $?

ls -l "new-boot.img" | echo

# Reset any error code
true