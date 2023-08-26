# FAQ

For convenience, in the following text:  
[AndroidKernelPatch](/) is abbreviated as AKP  
[KernelPatch](https://github.com/bmax121/KernelPatch) is abbreviated as KP

## Principle of AKP

AKP relies on KP, which patches the kernel image and injects code into the kernel space to gain full control over the kernel.  
If you want to learn more, you can visit [KernelPatch](https://github.com/bmax121/KernelPatch).

## How does AKP root?

Root access is a feature provided by KP.  
KP modifies the "cred" structure to set the UID to 0 (root user) and all capabilities to 1.   
Additionally, it utilizes SELinux hooks to grant tasks access to all permissions.  
KP hooks into system calls to provide this capability to user-space, and this system call is referred to as SuperCall.   
To invoke SuperCall, a credential is required, known as the SuperKey. Successful invocation of SuperCall depends on the correctness of the SuperKey. If the SuperKey is incorrect, the caller remains unaffected.

## How to invoke the su command?

The `su` command is not currently available and will be added after the development of KPM is complete, including root authorization management.  
For now, you can use the command `kpatch your_skey --su` as a substitute for the `su` command.

## Will AKP be detected?

All the functions of KP are implemented in the kernel and only exported through system calls. 
KP does not make any modifications in the user space, does not modify Selinux, create processes or files, or occupy ports. Therefore, it cannot be detected.

## Can it coexist with KernelSU and Magisk?

Yes, it can.

## Module Support

Modules have not been developed yet, but AKP will support two types of modules:

### KPM (Kernel Patch Module)

KPM is a mechanism similar to kernel modules, which will be loaded into the kernel for execution.
KPM exports functionalities such as Kernel symbol lookup, System-call Hook, Kernel-Inline-Hook, and more.

### Magisk Module

It may be implemented in the form of KPM.
