# 常见问题解答

## 什么是APatch？

APatch是一种类似于Magisk或KernelSU的根解决方案，但APatch提供更多功能。

## APatch与Magisk的区别？

- Magisk修改init，APatch则对Linux内核进行补丁。

## APatch与KernelSU的区别？

- KernelSU需要源代码。而APatch则仅需要boot.img。

## APatch与Magisk、KernelSU的区别？

- 可选择不修改SELinux。在Android应用程序上下文中进行root，无需libsu和IPC。
- 提供**Kernel Patch Module**。

## 什么是Kernel Patch Module？

一些代码在内核空间运行，类似于可加载内核模块（LKM）。

此外，KPM提供在内核空间进行内联挂钩、系统调用表挂钩的能力。

[如何编写KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## APatch与KernelPatch的关系

APatch依赖于KernelPatch，继承了其所有功能并进行了扩展。

您可以仅安装KernelPatch，但这将不允许您使用Magisk模块，  
要使用超级用户管理，您需要安装AndroidPatch，然后卸载它。

[了解更多关于KernelPatch的信息](https://github.com/bmax121/KernelPatch)

## 什么是SuperKey？

KernelPatch通过挂钩系统调用提供所有功能给用户空间，这个系统调用被称为**SuperCall**。  
调用SuperCall需要传递一种凭据，称为**SuperKey**。  
只有在SuperKey正确的情况下，SuperCall才能成功调用；如果SuperKey不正确，调用者将不受影响。

## 关于SELinux如何处理？

- KernelPatch不修改SELinux上下文，通过挂钩绕过SELinux，  
  这允许您在应用程序上下文中root Android线程，无需使用libsu启动新进程，然后执行IPC。这非常方便。
- 此外，APatch直接利用magiskpolicy提供额外的SELinux支持。  
  但是，仅这样会被检测为Magisk。有兴趣的人可以尝试绕过，问题已经很明确。
