#!/system/bin/sh
#######################################################################################
# APatch Boot Image Patcher
#######################################################################################
#
# Usage: boot_patch.sh <superkey> <bootimage> [true|false] [ARGS_PASS_TO_KPTOOLS]
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
#
#######################################################################################

ARCH=$(getprop ro.product.cpu.abi)

# Load utility functions
. ./util_functions.sh

echo "****************************"
echo " APatch Boot Image Patcher"
echo "****************************"

fail_patch() {
  echo "- $1" >&2
  exit "${2:-1}"
}

[ "$#" -ge 2 ] || fail_patch "Usage: boot_patch.sh <superkey> <bootimage> [true|false] [kptools options]"
SUPERKEY="$1"
BOOTIMAGE="$2"
shift 2
FLASH_TO_DEVICE=false
case "${1:-}" in
  true|false) FLASH_TO_DEVICE="$1"; shift ;;
esac

[ -n "$SUPERKEY" ] || fail_patch "SuperKey empty!"
[ -e "$BOOTIMAGE" ] || fail_patch "$BOOTIMAGE does not exist!"
[ -x ./kptools ] || fail_patch "Command kptools not found!"
[ -s kpimg ] || fail_patch "KernelPatch image not found!"

# Always unpack the selected image: the manager can select another image or
# retry a failed patch in this directory. Never reuse its previous kernel/output.
# Refuse an input that cleanup would overwrite.
case "$(readlink -f "$BOOTIMAGE")" in
  "$PWD/kernel"|"$PWD/kernel.ori"|"$PWD/new-boot.img"|"$PWD/ori.img")
    fail_patch "Copy the input boot image outside the patcher's working files first." ;;
esac
rm -f kernel kernel.ori new-boot.img || fail_patch "Cannot clear previous patch output."
trap 'patch_rc=$?; if [ "$patch_rc" -ne 0 ]; then rm -f new-boot.img; fi' EXIT

echo "- Unpacking boot image"
./kptools unpack "$BOOTIMAGE"
patch_rc=$?
[ "$patch_rc" -eq 0 ] || fail_patch "Unpack error: $patch_rc" "$patch_rc"
[ -s kernel ] || fail_patch "Boot image has no kernel; select boot.img, not init_boot.img."

KERNEL_CONFIG=$(./kptools -i kernel -f)
patch_rc=$?
[ "$patch_rc" -eq 0 ] || fail_patch "Cannot read kernel config: $patch_rc" "$patch_rc"
echo "$KERNEL_CONFIG" | grep -q '^CONFIG_KALLSYMS=y$' ||
  fail_patch "APatch requires CONFIG_KALLSYMS=y in the selected kernel."

KERNEL_INFO=$(./kptools -i kernel -l)
patch_rc=$?
[ "$patch_rc" -eq 0 ] || fail_patch "Cannot read kernel information: $patch_rc" "$patch_rc"
if echo "$KERNEL_INFO" | grep -q '^patched=false$'; then
  echo "- Backing up boot.img"
  cp "$BOOTIMAGE" ori.img || fail_patch "Cannot back up boot.img."
elif ! echo "$KERNEL_INFO" | grep -q '^patched=true$'; then
  fail_patch "Cannot determine whether the selected kernel is patched."
fi

mv kernel kernel.ori || fail_patch "Cannot stage the original kernel."

echo "- Patching kernel"
# Keep the key as one argument, and never expose it through shell tracing.
[ "$SUPERKEY" = "su" ] || set -- -S "$SUPERKEY" "$@"
./kptools -p -i kernel.ori -k kpimg -o kernel "$@"
patch_rc=$?
[ "$patch_rc" -eq 0 ] || fail_patch "Patch kernel error: $patch_rc" "$patch_rc"
[ -s kernel ] || fail_patch "Patcher produced an empty kernel."

echo "- Repacking boot image"
./kptools repack "$BOOTIMAGE"
patch_rc=$?
[ "$patch_rc" -eq 0 ] || fail_patch "Repack error: $patch_rc" "$patch_rc"
[ -s new-boot.img ] || fail_patch "Repacker produced an empty boot image."
repair_boot_avb_footer "$BOOTIMAGE" new-boot.img ||
  fail_patch "Cannot preserve the original boot image's AVB metadata."

if ! echo "$KERNEL_CONFIG" | grep -q '^CONFIG_KALLSYMS_ALL=y$'; then
  echo "- Detected CONFIG_KALLSYMS_ALL is not set!"
  echo "- APatch has patched but maybe your device won't boot."
  echo "- Make sure you have original boot image backup."
fi

if [ "$FLASH_TO_DEVICE" = "true" ]; then
  [ -b "$BOOTIMAGE" ] || [ -c "$BOOTIMAGE" ] || fail_patch "Flash target is not a block or character device."
  echo "- Flashing new boot image"
  flash_image new-boot.img "$BOOTIMAGE"
  patch_rc=$?
  [ "$patch_rc" -eq 0 ] || fail_patch "Flash error: $patch_rc" "$patch_rc"
  echo "- Successfully Flashed!"
else
  echo "- Successfully Patched!"
fi
