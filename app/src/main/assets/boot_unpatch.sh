#!/system/bin/sh
#######################################################################################
# APatch Boot Image Unpatcher
#######################################################################################

ARCH=$(getprop ro.product.cpu.abi)

# Load utility functions
. ./util_functions.sh

echo "****************************"
echo " APatch Boot Image Unpatcher"
echo "****************************"

BOOTIMAGE=$1

[ -e "$BOOTIMAGE" ] || { echo "- $BOOTIMAGE does not exist!"; exit 1; }

echo "- Target image: $BOOTIMAGE"

  # Check for dependencies
command -v ./magiskboot >/dev/null 2>&1 || { echo "- Command magiskboot not found!"; exit 1; }
command -v ./kptools >/dev/null 2>&1 || { echo "- Command kptools not found!"; exit 1; }

if [ ! -f kernel ]; then
echo "- Unpacking boot image"
./magiskboot unpack "$BOOTIMAGE" >/dev/null 2>&1
if [ $? -ne 0 ]; then
    >&2 echo "- Unpack error: $?"
    exit $?
  fi
fi

if [ ! $(./kptools -i kernel -l | grep patched=false) ]; then
	echo "- kernel has been patched "
  if [ -f "new-boot.img" ]; then
    echo "- found backup boot.img ,use it for recovery"
  else
    mv kernel kernel.ori
    echo "- Unpatching kernel"
    ./kptools -u --image kernel.ori --out kernel
    if [ $? -ne 0 ]; then
      >&2 echo "- Unpatch error: $?"
      exit $?
    fi
    echo "- Repacking boot image"
    ./magiskboot repack "$BOOTIMAGE" >/dev/null 2>&1
    if [ $? -ne 0 ]; then
      >&2 echo "- Repack error: $?"
      exit $?
    fi
  fi

else
  echo "- no need unpatch"
  exit 0
fi



if [ -f "new-boot.img" ]; then
  echo "- Flashing boot image"
  flash_image new-boot.img "$BOOTIMAGE"

  if [ $? -ne 0 ]; then
    >&2 echo "- Flash error: $?"
    exit $?
  fi
fi

echo "- Flash successful"

# Reset any error code
true
