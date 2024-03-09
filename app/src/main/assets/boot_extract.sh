#!/system/bin/sh

ARCH=$(getprop ro.product.cpu.abi)

# Load utility functions
. ./util_functions.sh

mount_partitions

[ -z "$SLOT" ] && [ -n "$( find_block boot_a )" ] && { >&2 echo "- can't determined current boot slot!"; exit 1; }

find_boot_image

[ -e "$BOOTIMAGE" ] || { >&2 echo "- can't find boot.img!"; exit 1; }

true
