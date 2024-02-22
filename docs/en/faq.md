# FAQ


## What is APatch?
APatch is a root solution similar to Magisk or KernelSU that unites the best of both.
It combines Magisk's convenient and easy install method through `boot.img` with KernelSU's powerful kernel patching abilities.


## What's the difference between APatch and Magisk?
- Magisk modifies the init system with a patch in your boot image's ramdisk, while APatch patches the kernel directly.


## APatch vs KernelSU
- KernelSU requires the source code for your device's kernel which is not always provided by the OEM. APatch works with just your stock `boot.img`.


## APatch vs Magisk, KernelSU
- APatch allows you to optionally not modify SELinux, this means that the APP thread can be rooted, libsu and IPC are not necessary.
- **Kernel Patch Module** provided.


## What is Kernel Patch Module?
Some code runs in Kernel Space, similar to Loadable Kernel Modules (LKM).

Additionally, KPM provides the ability to do inline-hook, syscall-table-hook in kernel space.

For more information, see [How to write a KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)


## Relationship between APatch and KernelPatch

APatch depends on KernelPatch, inherits all its capabilities, and has been expanded.

You can install KernelPatch only, but this will not allow you to use Magisk modules

[Learn more about KernelPatch](https://github.com/bmax121/KernelPatch)


## What is SuperKey?
KernelPatch adds a new system call (syscall) to provide all capabilities to apps and programs in userspace, this syscall is referred to as **SuperCall**.
When an app/program tries to invoke **SuperCall**, it needs to provide an access credential, known as the **SuperKey**.
**SuperCall** can only be successfully invoked if the **SuperKey** is correct and if it's not the caller will remain unaffected.


## How about SELinux?
- KernelPatch doesn't modify the SELinux context and bypasses SELinux via a hook.
  This allows you to root an Android thread within the app context without the need to use libsu to start a new process and then perform IPC.
  This is very convenient.
- In addition, APatch directly utilizes magiskpolicy to provide additional SELinux support.
