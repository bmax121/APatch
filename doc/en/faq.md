# FAQ

For convenience, in the following text:  
[AndroidKernelPatch](/) is abbreviated as AKP  
[KernelPatch](https://github.com/bmax121/KernelPatch) is abbreviated as KP

## Principle of AKP

AKP relies on KP, which patches the kernel image and injects code into the kernel space to gain full control over the kernel.  
If you want to learn more, you can visit [KernelPatch](https://github.com/bmax121/KernelPatch).

## How does AKP root?

Root access is provided by KP. KP achieves root access by hooking system calls, which are called SuperCall. SuperCall requires a credential called SuperKey, and SuperCall can only be successfully invoked when the SuperKey is correct. If the SuperKey is incorrect, the caller remains unaware.

By invoking SuperCall, you can grant root access to yourself, a running process, or even a thread of an Android application without affecting others.

The granularity of permissions in KP is a Task in the kernel, which is the finest granularity. The specific permissions for a Task have not been further divided at this time, but it is not difficult to achieve the finest granularity if needed.

## How to invoke the su command?

AKP achieves root access through system calls, but the su command is not provided at the moment. It will be added later.  
You can use `kpatch -k your_skey --su` as a substitute. By compiling the manager, you can obtain `kpatch`.

## Will AKP be detected?

All the functions of KP are implemented in the kernel and only exported through system calls. KP does not make any modifications in the user space, does not modify Selinux, create processes or files, or occupy ports. Therefore, it cannot be detected.

To my knowledge, the only trace of KP is that it allocates some memory in the kernel space, resulting in an additional line in `/sys/kernel/debug/memblock`. However, this cannot be used for detection. If necessary, I can remove this as well.

## Can it coexist with KernelSU and Magisk?

Yes, it can.

## Module Support

Modules have not been developed yet, but AKP will support two types of modules:

### KPM (Kernel Patch Module)

KPM is a capability provided by KP. It is a mechanism similar to Loadable Kernel Modules (LKM) and provides functionalities such as exporting kernel symbol lookup, system call hooking, and kernel inline hooking.

Module code is directly added to the kernel, allowing direct modification of the kernel. It is powerful but also dangerous! With KPM, you can have complete control over the kernel. Let your imagination run wild!

### Magisk Module

It may be implemented in t
he form of KPM.

## UID-level Permission Management

Existing root solutions are based on UID granularity for root authorization, and compatibility will be considered in the future.