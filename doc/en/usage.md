# Compilation and Usage

## Using AKPatch

Import this project into AndroidStudio for compilation and installation. It provides functions for patching boot.img and exporting kpatch.

## Alternatively, Step-by-step Manual Operation

### Compiling bootimgtools

bootimgtools is used for unpacking and repacking boot.img, and it employs the code from Magisk.

```shell
cd bootimgtools
mkdir build; cd build
cmake ..
make
```

### kpimg, kptools, and kpuser

[KernelPatch Release](<https://github.com/bmax121/KernelPatch/releases/tag/latest>)

kpimg is a precompiled binary.  
Compile kptool using cmake or makefile.  
kpuser is used for compiling kpatch.  

### Patching the boot.img

Refer to the content in [Patch.kt](/app/src/main/java/me/bmax/akpatch/ui/util/Patch.kt).

```shell
# 1. First, obtain the `boot.img` for your device.

# 2. Unpack the `boot.img`.
cd imgtools/build
./bootimgtools unpack where_your_boot_img
cp kernel kernel.ori

# 3. Use `kptools` to patch the kernel image.
# Note: `skey` is the unique credential for invoking SuperCall. Remember your `skey` and do not share it!
./kptools -p kernel.ori --kpimg where_your_kpimg --skey your_skey --out kernel

# 4. Repack the `boot.img`.
./bootimgtools repack boot.img

# 5. Finally, flash the new `boot.img`.
# It is recommended to first use
fastboot boot new-boot.img
# If you are sure that there are no issues, then flash
fastboot flash boot new-boot.img


```


