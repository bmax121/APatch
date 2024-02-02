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
# extra files        kpm/exec/shell/bin
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
shift 2

[ -z "$SUPERKEY" ] && { echo "- SuperKey empty!"; exit 1; }
[ -e "$BOOTIMAGE" ] || { echo "- $BOOTIMAGE does not exist!"; exit 1; }

# Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "- Command magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "- Command kptools not found!"; exit 1; }

if [ ! -f kernel ]; then
echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "- Unpack error: $?"
    exit $?
  fi
fi

#cp kernel kernel.ori
#
## TODO: copied from magisk, figure out why and then ...
#IS_SAMSUNG_PATCH=false
## Remove Samsung RKP
#./magiskboot hexpatch kernel \
#49010054011440B93FA00F71E9000054010840B93FA00F7189000054001840B91FA00F7188010054 \
#A1020054011440B93FA00F7140020054010840B93FA00F71E0010054001840B91FA00F7181010054 \
#&& IS_SAMSUNG_PATCH=true
#
## Remove Samsung defex
## Before: [mov w2, #-221]   (-__NR_execve)
## After:  [mov w2, #-32768]
#./magiskboot hexpatch kernel 821B8012 E2FF8F12 && IS_SAMSUNG_PATCH=true
#
## If the kernel doesn't need to be patched at all,
## keep raw kernel to avoid bootloops on some weird devices
#$IS_SAMSUNG_PATCHL || mv kernel.ori kernel
#
#echo "$IS_SAMSUNG_PATCHL"
#
#if [[ $IS_SAMSUNG_PATCH == true ]]; then
#  ADDITION_ARGS="-a samsung=true "
#  echo "======= Warning ======="
#  echo "Hex patching kernel of Samsung, this behavior is performed as Magisk, If your devices is not Samsung, Please report this issue."
#  echo "======= Warning ======="
#fi
#

mv kernel kernel.ori

echo "- Patching kernel"

set -x
./kptools -p --image kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel "$@"
set +x

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
rm kernel.ori

# Reset any error code
true
