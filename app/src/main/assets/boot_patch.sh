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
# Load utility functions
. ./util_functions.sh
# Check if 64-bit
api_level_arch_detect

print_title "APatch Boot Image Patcher"

SUPERKEY=$1
BOOTIMAGE=$2

[ -e "$BOOTIMAGE" ] || abort "$BOOTIMAGE does not exist!"
[ -z "$SUPERKEY" ] && abort "SuperKey empty!"

ui_print "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE"

mv kernel kernel.ori

ui_print "- Patching kernel"
./kptools -p kernel.ori --skey "$SUPERKEY" --kpimg kpimg --out kernel

if [ $? -ne 0 ]; then
  ui_print "Patch error: $?"
  exit $?
fi

ui_print "- Repacking boot image"
./magiskboot repack "$BOOTIMAGE" || abort "! Unable to repack boot image"

ls -l "new-boot.img" | ui_print

# Reset any error code
true
