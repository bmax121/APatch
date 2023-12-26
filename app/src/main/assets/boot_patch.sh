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
#                  (this file)     The script will use files in its same
#                                  directory to complete the patching process.
# bootimg            binary        The target boot image
# kpimg              binary        KernelPatch core Image
# kptools            executable    The KernelPatch tools binary to inject kpimg to kernel Image
# magiskboot         executable    Magisk tool to unpack boot.img.
#
#######################################################################################


# Pure bash dirname implementation
getdir() {
  case "$1" in
    */*)
      dir=${1%/*}
      if [ -z $dir ]; then
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

# Check if 64-bit
if [ $(uname -m) = "aarch64" ]; then
  echo "system is arm64"
else
  echo "Not is arm64"
  exit
fi

echo "APatch Boot Image Patcher"

SUPERKEY=$1
BOOTIMAGE=$2

[ -e "$BOOTIMAGE" ] || abort "$BOOTIMAGE does not exist!"
[ -z "$SUPERKEY" ] && abort "SuperKey empty!"

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
./magiskboot repack "$BOOTIMAGE" || abort "! Unable to repack boot image"

ls -l "new-boot.img" | echo

# Reset any error code
true
