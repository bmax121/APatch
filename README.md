# AndroidKernelPatch

Patching, hooking, and rooting the Android kernel using only a stripped Linux kernel image.

**English** | [简体中文](README_zh-CN.md)

AndroidKernelPatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)  and is the Android version of KernelPatch.

Not limited to rooting, AndroidKernelPatch can patch the kernel and inject arbitrary code into it even without source code or symbol information.

Want to learn more? Visit [KernelPatch](https://github.com/bmax121/KernelPatch/) .

## Supported Versions

Same as KernelPatch.

Currently only supports arm64.

Linux 3.8 - 6.2 (theoretically)  
Linux 6.3+ (not yet adapted)  

Pixel2xl-Android10, Linux 4.4.210 (tested before)  
Pixel3xl-Android12, Linux 4.9.270 (tested)  
Pixel4xl-Android13, Linux 4.14.276 (tested)  
Oneplus8T-Android13, Linux 4.19.157 (tested before)  
Pixel6-Android13, Linux 5.10.157 (tested)  

## Development Status

It is still in the early stages, and many features have not been implemented yet.

## Get Help

Due to the limited number of testing devices at the current stage, compatibility issues or certain bugs may inevitably arise.   
However, there's no need to be worried, as such occurrences will become less frequent over time.  

If you encounter **issues with booting up** during usage, you can follow the steps below for assistance.  
While these steps might require some time investment, but we don't have a better way to handle matters during the kernel startup phase.  

1.  Root your device, whether using Magisk or KernelSU, to be able to access kernel logs.
2.  Visit [https://t.me/bmax121](https://t.me/bmax121) and seek help from bmax121.
3.  Send your rooted boot.img, whether it's a magisk_patched or kernelsu boot.img, to bmax121.
4.  Clearly describe the symptoms of the phone, and if you have kernel logs, send them as well.
5.  bmax121 will modify the code based on the symptoms, then re-patch your rooted boot.img and send you the new-boot.img.
6.  Upon receiving the new-boot.img, you can use **fastboot boot new-boot.img**(recommand) or **fastboot flash boot new-boot.img** to test if it boots properly. If it does, you can switch to root and use **dmesg** to retrieve the full logs, or use **dmesg | grep KP** to get the KernelPatch logs.
7.  Repeat steps 4-6 until the issue is resolved.

## Discussions

- Telegram Group: https://t.me/+B5aYwCw4tY9hOWI9

## More Information

[FAQ](./doc/en/faq.md)  
[Documentation](./doc/en/)  

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.  
- [Magisk](https://github.com/topjohnwu/Magisk): Used their code for unpacking and repacking boot.img.

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
