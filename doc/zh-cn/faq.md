# 常见问题

为了方便，下文中  
[AndroidKernelPatch](/) 简称 **AKP**  
[KernelPatch](https://github.com/bmax121/KernelPatch) 简称 **KP**  

## AKP 的原理

AKP 依赖 KP，KP 修补内核镜像，向内核空间注入代码，能够完全控制内核。  
如果你想了解更多，可以到 [KernelPatch](https://github.com/bmax121/KernelPatch) 查看。

## AKP 是如何 root 的

Root 是 KP 提供的功能。KP 通过 Hook 系统调用来实现 root，这个系统调用被称作 SuperCall。  SuperCall 的调用需要传入一个凭证，此凭证被称作 SuperKey，SuperCall 只有 SuperKey 正确的情况下才能调用成功，SuperKey 错误调用者无感。  

通过调用 SuperCall 可以直接给自己 root 或者给一个正在运行的进程 root 权限。甚至可以直接给一个安卓应用的一个线程 root 权限，而不影响其他。  

KP 权限主体的粒度是内核中的一个 Task，这是最细的粒度。
对于一个 Task 具体有哪些权限，现在还没细分，如果需要的话也可以做到最细，这并不困难。  

## 如何调用 su 命令

AKP 通过系统调用 root，但是还未提供 su 命令，后面再做。  
可以使用 `kpatch -k your_skey --su` 代替，`kpatch` 通过编译 manager 就可以获取到。

## AKP 会被检测到吗

KP 的所有功能都在内核实现，并且只会通过系统调用导出，不会在用户空间做任何修改，不会修改 Selinux，也不会新建进程，文件，占用端口等，并不会被检测到。  

就我现在所知 KP 的唯一痕迹就是在内核空间申请了一些内存，`/sys/kernel/debug/memblock` 中会多一行，但这并不能被用于检测。如果有必要，我可以把这个也去掉。

## 是否可以与 KernelSU， Magisk 共存

可以的

## 模块支持

还未开发，AKP将支持两种模块

### KPM

Kernel-Patch-Module，这是 KP 提供的能力。  
KPM 是一种类似 [LKM](https://en.wikipedia.org/wiki/Loadable_kernel_module) 的机制，并且会导出内核符号查找功能，系统调用 Hook 功能， Kernel-Inline-Hook 等基础功能。  

模块代码会被直接加在到内核，可以直接修改内核。强大，但也危险！  
利用 KPM 可以完全得控制内核，发挥想象吧。

### Magisk Module

可能会以 KPM 的形式来实现。

## UID 粒度的权限管理

当前存在的一些 root 方案都是以 UID 粒度进行 root 授权，后面会去兼容，
