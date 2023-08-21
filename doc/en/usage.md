# Usage

## Build bootimgtools

bootimgtools is a tool used for unpacking and repacking boot.img files. It utilizes code from Magisk for this purpose.

```shell
cd imgtools
mkdir build; cd build
cmake ..
make
```

## Download kpimg and kptools

[KernelPatch Release](<https://github.com/bmax121/KernelPatch/releases/tag/latest>)

## Build manager (Only for test now)

Import [manager](/manager) to AndroidStudio.

## Install

```shell
# 1. First, obtain the boot.img file for your device

# 2. Unpack the boot.img
cd imgtools/build
./bootimgtools unpack where_your_boot_img
cp kernel kernel.ori

# 3. Use kptools to patch the kernel image
# Note: skey is the unique credential for invoking SuperCall. Remember to keep your skey confidential and not disclose it to others!
./kptools -p kernel.ori --kpimg where_your_kpimg --skey your_skey --out kernel

# 4. Repack boot.img
./bootimgtools repack boot.img

# 5. Finally, flash the boot.img
# It is recommended to use first.
fastboot boot new-boot.img
# If everything is working fine, then you can proceed to flash it.
fastboot flash boot new-boot.img
```
