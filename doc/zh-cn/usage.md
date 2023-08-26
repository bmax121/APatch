# 编译和使用

## 使用 AKPatch

导入此项目到 AndroidStudio 编译安装，里面提供了修补 boot.img 和导出 kpatch 的功能

## 或者一步步自己操作

### 编译 bootimgtools

bootimgtools 用于对 boot.img 进行解包和重打包，这里使用了 Magisk 的代码。

```shell
cd bootimgtools
mkdir build; cd build
cmake ..
make
```

### kpimg, kptools, kpuser

[KernelPatch Release](<https://github.com/bmax121/KernelPatch/releases/tag/latest>)

kpimg 是一个二进制文件，是编译好了的  
kptool 用 cmake 或者 makefile 编译  
kpuser 用于编译 kpatch  

### 修补 boot.img

参照 [Patch.kt](/app/src/main/java/me/bmax/akpatch/ui/util/Patch.kt) 的内容

```shell
# 1. 首先获取你的设备的 boot.img

# 2. 解包 boot.img
cd imgtools/build
./bootimgtools unpack where_your_boot_img
cp kernel kernel.ori

# 3. 使用 kptools 来修补内核镜像
# 注意：skey 是你调用 SuperCall 的唯一凭证，请记住你的 skey，并不要泄漏出去！
./kptools -p kernel.ori --kpimg where_your_kpimg --skey your_skey --out kernel

# 4. 重打包 boot.img
./bootimgtools repack boot.img

# 5. 最后刷入 boot.img
# 推荐先使用
fastboot boot new-boot.img
# 如果确定没有问题了，再刷入
fastboot flash boot new-boot.img
```
