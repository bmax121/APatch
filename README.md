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
emulator, Linux 5.15.41 (tested)

## Development Status

It is still in the early stages, and many features have not been implemented yet.

## Get Help

 [https://t.me/bmax121](https://t.me/bmax121) 

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
