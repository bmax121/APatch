#!/system/bin/sh
#######################################################################################
# APatch Boot Image Unpatcher
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
echo " APatch Boot Image Unpatcher"
echo "****************************"

# Check if 64-bit
if [ $(uname -m) = "aarch64" ]; then
  echo "- System arch: arm64"
else
  echo "- System arch: not arm64"
  exit 1
fi

LEGACYSAR=false
PATCHEDKERNEL=false
BACKUPIMAGE="/data/adb/apatch_backup_boot.img"

mount_partitions
find_boot_image

[ -e "$BOOTIMAGE" ] || { echo "- $BOOTIMAGE does not exist!"; exit 1; }

echo "- Target image: $BOOTIMAGE"

if [ -f "$BACKUPIMAGE" ]; then
  echo "- Stock boot image detected"
  cp "$BACKUPIMAGE" "new-boot.img"
else
  # Check for dependencies
  command -v ./magiskboot >/dev/null 2>&1 || { echo "- Command magiskboot not found!"; exit 1; }
  command -v ./kptools >/dev/null 2>&1 || { echo "- Command kptools not found!"; exit 1; }

  echo "- Unpacking boot image"
  ./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1

  if [ $? -ne 0 ]; then
    echo "- Unpack error: $?"
    exit $?
  fi

  mv kernel kernel.ori

  echo "- Unpatching kernel"
  ./kptools -u --image kernel.ori --out kernel

  if [ $? -ne 0 ]; then
    echo "- Unpatch error: $?"
    exit $?
  fi

  cp kernel kernel.ori

  # Reapply Samsung RKP
  ./magiskboot hexpatch kernel \
  A1020054011440B93FA00F7140020054010840B93FA00F71E0010054001840B91FA00F7181010054 \
  49010054011440B93FA00F71E9000054010840B93FA00F7189000054001840B91FA00F7188010054 \
  && PATCHEDKERNEL=true

  # Reapply Samsung defex
  # Before:  [mov w2, #-32768]
  # After: [mov w2, #-221]   (-__NR_execve)
  ./magiskboot hexpatch kernel E2FF8F12 821B8012 && PATCHEDKERNEL=true

  # Force kernel to skip rootfs for legacy SAR devices
  # want_initramfs -> skip_initramfs
  $LEGACYSAR && ./magiskboot hexpatch kernel \
  77616E745F696E697472616D667300 \
  736B69705F696E697472616D667300 \
  && PATCHEDKERNEL=true

  # If the kernel doesn't need to be patched at all,
  # keep raw kernel to avoid bootloops on some weird devices
  $PATCHEDKERNEL || mv kernel.ori kernel

  echo "- Repacking boot image"
  ./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1

  if [ $? -ne 0 ]; then
    echo "- Repack error: $?"
    exit $?
  fi

  echo "- Cleaning up"
  ./magiskboot cleanup >/dev/null 2>&1
  rm -f kernel.ori
fi

if [ -f "new-boot.img" ]; then
  echo "- Restoring stock boot image"
  flash_image new-boot.img "$BOOTIMAGE"

  if [ $? -ne 0 ]; then
    echo "- Flash error: $?"
    exit $?
  fi
fi

# Reset any error code
true
