# AndroidKernelPatch

Patching, hooking, and rooting the Android kernel using only a stripped Linux kernel image.

AndroidKernelPatch 依赖 [KernelPatch](https://github.com/bmax121/KernelPatch/) ，是 KernelPatch 的 Android 版本。

不仅仅是 Root，AndroidKernelPatch 可以在无源码无符号的情况下修补内核，并向内核注入任意代码。

想要了解更多？请到 [KernelPatch](https://github.com/bmax121/KernelPatch/)

## 支持情况

当前只支持 arm64  
Android内核版本 3.18 - 6.1 

## 开发状态

** 正在开发中，暂不可用 **

## 获取帮助

## 讨论

## 更多信息

[常见问题](./doc/zh-cn/faq.md)  
[文档](./doc/zh-cn/)  

## 鸣谢

- [KernelPatch](https://github.com/bmax121/KernelPatch/): 核心
- [Magisk](https://github.com/topjohnwu/Magisk): 解包重打包boot.img。SELinux 支持。模块支持。（待开发）
- [KernelSU](https://github.com/tiann/KernelSU): Android App 界面 (正在开发中)

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
