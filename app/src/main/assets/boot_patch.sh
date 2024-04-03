#!/system/bin/sh
#######################################################################################
# APatch Boot Image Patcher
#######################################################################################
#
# Usage: boot_patch.sh <superkey> <bootimage> [ARGS_PASS_TO_KPTOOLS]
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

# Load utility functions
. ./util_functions.sh

echo "****************************"
echo " APatch Boot Image Patcher"
echo "****************************"

SUPERKEY=$1
BOOTIMAGE=$2
FLASH_TO_DEVICE=$3
shift 2

[ -z "$SUPERKEY" ] && { >&2 echo "- SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { >&2 echo "- $BOOTIMAGE does not exist!"; exit 1; }

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { >&2 echo "- Command magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { >&2 echo "- Command kptools not found!"; exit 1; }

if [ ! -f kernel ]; then
echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    >&2 echo "- Unpack error: $?"
    exit $?
  fi
fi

mv kernel kernel.ori

echo "- Patching kernel"

set -x
./kptools -p -i kernel.ori -S "$SUPERKEY" -k kpimg -o kernel "$@"
patch_rc=$?
set +x

if [ $patch_rc -ne 0 ]; then
  >&2 echo "- Patch kernel error: $patch_rc"
  exit $?
fi

echo "- Repacking boot image"
./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1

if [ $? -ne 0 ]; then
  >&2 echo "- Repack error: $?"
  exit $?
fi

# shellcheck disable=SC2039
if [[ $FLASH_TO_DEVICE == *"true"* ]]; then
  # flash
  if [ -b "$BOOTIMAGE" ] || [ -c "$BOOTIMAGE" ] && [ -f "new-boot.img" ]; then
    echo "- Flashing new boot image"
    flash_image new-boot.img "$BOOTIMAGE"
    if [ $? -ne 0 ]; then
      >&2 echo "- Flash error: $?"
      exit $?
    fi
  fi

  echo "- Successfully Flashed!"
else
  echo "- Successfully Patched!"
fi

