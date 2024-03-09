#!/system/bin/sh

ARCH=$(getprop ro.product.cpu.abi)

# Load utility functions
. ./util_functions.sh

mount_partitions

find_boot_image

[ -e "$BOOTIMAGE" ] || { >&2 echo "- can't find boot.img!"; exit 1; }

true
