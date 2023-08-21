# 使用

## 编译 bootimgtools

bootimgtools 用于对 boot.img 进行解包和重打包，这里使用了 Magisk 的代码。

```shell
cd imgtools
mkdir build; cd build
cmake ..
make
```

## 下载 kpimg, kptools

[KernelPatch Release](<https://github.com/bmax121/KernelPatch/releases/tag/latest>)

## 编译 manager (当前只有测试功能)

将 [manager](/manager) 导入到 AndroidStudio 编译即可

## 安装

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
