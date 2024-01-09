# FAQ

## What is APatch

APatch is a root solution similar to Magisk or KernelSU, but APatch offers even more.

## APatch vs Magisk

- Magisk modify init, APatch patch linux kernel.

## APatch vs KernelSU

- KernelSU require source. Only boot.img for APatch is enough.

## APatch vs Magisk, KernelSU

- Optionally don't modify SELinux. Root in android app context, no libsu and IPC needed
- **Kernel Patch Module** provided

## What is Kernel Patch Module

Some code runs in Kernel Space, similar to Loadable Kernel Modules (LKM).

Additionally, KPM provides the ability to do inline-hook, syscall-table-hook in kernel space.

[How to write a KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## Relationship between APatch and KernelPatch

APatch depends on KernelPatch, inherits all its capabilities, and has been expanded.  

You can install KernelPatch only, but this will not allow you to use the magisk module,  
and to use superuser management, you needs to install AndroidPatch and then uninstall it.

[Learn more about KernelPatch](https://github.com/bmax121/KernelPatch)

## What is SuperKey

KernelPatch hooks system calls to provide all capability to user space, and this system call is referred to as **SuperCall**.  
Invoking SuperCall requires passing a credential, known as the **SuperKey**.  
SuperCall can only be successfully invoked when the SuperKey is correct; if the SuperKey is incorrect, the caller remains unaffected.

## How about SELinux

- KernelPatch don't modify selinux context and bypass selinux via hook,  
  This allows you to root an Android thread within the app context without the need to use libsu to start a new process and then perform IPC.
  This is very convenient.
- In addition, APatch directly utilizes magiskpolicy to provide additional SELinux support.  
  However, only this will be detected as Magisk. Anyone interested can try to bypass it, the issue is already quite clear.
  
