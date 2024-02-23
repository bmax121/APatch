#!/system/bin/sh

ARCH=$(getprop ro.product.cpu.abi)

# Load utility functions
. ./util_functions.sh

mount_partitions

[ -z $SLOT ] && { echo "- can't determined current boot slot!"; exit 1; }

find_boot_image

[ -e "$BOOTIMAGE" ] || { echo "- can't find boot.img!"; exit 1; }

true
