# AndroidKernelPatch

Patching and hooking and rooting the Android kernel with only stripped linux kernel image.

**English** | [简体中文](README_zh-CN.md)

AndroidKernelPatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)  and is the Android version of KernelPatch.

Not limited to rooting, AndroidKernelPatch can patch the kernel and inject arbitrary code into it even without source code or symbol information.

Want to learn more? Visit [KernelPatch](https://github.com/bmax121/KernelPatch/) .

## Supported Versions

Same as KernelPatch.

Currently only supports arm64.

Linux 3.8 - 6.2 (theoretically)  
Linux 6.3+ (not yet adapted)  

Pixel2xl-Android10, Linux 4.4.210 (tested)  
Pixel3xl-Android12, Linux 4.9.270 (tested)  
Pixel4xl-Android13, Linux 4.14.276 (tested)  
Oneplus8T-Android13, Linux 4.19.157 (tested)  
Pixel6-Android13, Linux 5.10.157 (tested)  

## Development Status

It is still in the early stages, and many features have not been implemented yet. Development is happening sporadically and slowly. **Welcome those who are interested to join in**.

## Get Help

## Discussions

## More Information

[FAQ](./doc/en/faq.md)  
[Documentation](./doc/en/)  

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.  
- [Magisk](https://github.com/topjohnwu/Magisk): Used their code for unpacking and repacking boot.img.

## License

AndroidKernelPatch is licensed under the GNU General Public License v3 (GPL-3) (http://www.gnu.org/copyleft/gpl.html).
