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

还比较初期，很多功能还没做，不定期缓慢开发中，**期待有兴趣的一起**。

## 获取帮助

## 讨论

## 更多信息

[常见问题](./doc/zh-cn/faq.md)  
[文档](./doc/zh-cn/)  

## 鸣谢

- [KernelPatch](https://github.com/bmax121/KernelPatch/): 核心
- [Magisk](https://github.com/topjohnwu/Magisk): 使用其代码用于解包，重打包 boot.img

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
