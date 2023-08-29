# AndroidKernelPatch

Patching, hooking, and rooting the Android kernel using only a stripped Linux kernel image.

AndroidKernelPatch 依赖 [KernelPatch](https://github.com/bmax121/KernelPatch/) ，是 KernelPatch 的 Android 版本。

不仅仅是 Root，AndroidKernelPatch 可以在无源码无符号的情况下修补内核，并向内核注入任意代码。

想要了解更多？请到 [KernelPatch](https://github.com/bmax121/KernelPatch/)

## 支持情况

同 KernelPatch

当前只支持 arm64

Linux 3.8 - 6.2 (理论上)  
Linux 6.3+ (暂未适配)  

Pixel2xl-Android10, Linux 4.4.210 (以前测试过)  
Pixel3xl-Android12, Linux 4.9.270 (测试过)  
Pixel4xl-Android13, Linux 4.14.276 (测试过)  
Oneplus8T-Android13, Linux 4.19.157 (以前测试过)  
Pixel6-Android12, Linux 5.10.81 (测试过)  

## 开发状态

还比较初期，很多功能还没做

## 获取帮助

因为现阶段测试的设备较少，所以难免会存在兼容性问题或者某些bug, 不过也不用特别担心，这种情况肯定会越来越少。  
如果你在使用的过程中发现**无法正常开机**，可以参照以下的步骤寻求帮助，这会花费你一定的时间成本，但对于内核启动阶段的事，我们暂时还没有更好的办法处理  

1.  将你的设备 root, 无论是 Magisk 还是 KernelSU 都可以，这么做是为了能够查看内核日志
2. 打开 [https://t.me/bmax121](https://t.me/bmax121) 向 bmax121 寻求帮助
3. 将你完成 root 的 boot.img, 无论是 magisk_patched 还是 kernelsu boot.img 发送给 bmax121
4. 描述清楚你所遇到的现象，如果有内核日志，发送内核日志
5. bmax121 将会根据现象修改代码，然后重新 patch 你的 rooted boot.img 并将 new-boot.img 发送给你
6. 当你收到 new-boot.img 后，你可以使用 **fastboot boot new-boot.img**(推荐) 或者 **fastboot flash boot new-boot.img**  来测试是否能够正常启动。如果能够正常启动，你可以切换到 root，然后用 **dmesg** 获取全部日志，或者 **dmesg | grep KP** 获取 KernelPatch 的日志。
7. 重复 4-6 的步骤，直到问题解决

## 讨论

## 更多信息

[常见问题](./doc/zh-cn/faq.md)  
[文档](./doc/zh-cn/)  

## 鸣谢

- [KernelPatch](https://github.com/bmax121/KernelPatch/): 核心
- [Magisk](https://github.com/topjohnwu/Magisk): 使用其代码用于解包，重打包 boot.img

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
