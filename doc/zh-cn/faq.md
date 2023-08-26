# 常见问题

为了方便，下文中  
[AndroidKernelPatch](/) 简称 **AKP**  
[KernelPatch](https://github.com/bmax121/KernelPatch) 简称 **KP**  

## AKP 的原理

AKP 依赖 KP，KP 修补内核镜像，向内核空间注入代码，能够完全控制内核。  
如果你想了解更多，可以到 [KernelPatch](https://github.com/bmax121/KernelPatch) 查看。

## AKP 是如何 root 的

Root 是 KP 提供的功能。  
KP 修改 cred 将 uid 全都置 0，capability 全置为 1，selinux 通过 hook 绕过。  
KP Hook 系统调用来将此能力提供给用户空间，这个系统调用被称作 SuperCall。   
SuperCall 的调用需要传入一个凭证，此凭证被称作 SuperKey，SuperCall 只有 SuperKey 正确的情况下才能调用成功，SuperKey 错误调用者无感。  

## 如何调用 su 命令

暂未提供 su 命令，会在开发完 KPM 后再做，包括 root 授权管理。  
现在可以使用 `kpatch your_skey --su` 代替 `su` 命令。  

## AKP 会被检测到吗

KP 的所有功能都在内核实现，并且只会通过系统调用导出，不会在用户空间做任何修改，不会修改 Selinux，也不会新建进程，文件，占用端口等，并不会被检测到。  

## 是否可以与 KernelSU， Magisk 共存

可以的

## 模块支持

还未开发，AKP将支持两种模块

### KPM

Kernel-Patch-Module，这是 KP 提供的能力。  
KPM 是一种类似内核模块的机制，会被加载到内核执行。
KPM 会导出 Kernel symbol lookup，System-call Hook，Kernel-Inline-Hook 等功能。  

### Magisk Module

可能会以 KPM 的形式来实现。
