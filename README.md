# AndroidKernelPatch

Patching, hooking, and rooting the Android kernel using only a stripped Linux kernel image.

# su has been implemented and can be downloaded from KernelPatch release. The module is under development and this app is also under development
# su已实现，可到 KernelPatch release 中下载。模块开发中，此app也在开发中


**English** | [简体中文](README_zh-CN.md)

AndroidKernelPatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)  and is the Android version of KernelPatch.

Not limited to rooting, AndroidKernelPatch can patch the kernel and inject arbitrary code into it even without source code or symbol information.

Want to learn more? Visit [KernelPatch](https://github.com/bmax121/KernelPatch/) .

## Supported Versions

Only arm64.  
Android Kernel Version 3.18 - 6.1 

## Development Status

** It is still in the early stages, and many features have not been implemented yet. currently unavailable. **

## Get Help

## Discussions

- Telegram Group: https://t.me/+B5aYwCw4tY9hOWI9

## More Information

[FAQ](./doc/en/faq.md)  
[Documentation](./doc/en/)  

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.  
- [Magisk](https://github.com/topjohnwu/Magisk): Unpacking and repacking boot.img, and SELinux support. (todo)
- [KernelSU](https://github.com/tiann/KernelSU): The Android UI, and The module support. (In developing ...)

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
