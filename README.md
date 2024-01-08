# APatch

The patching of Android kernel and Android system.

``` shell
    _    ____       _       _     
   / \  |  _ \ __ _| |_ ___| |__  
  / _ \ | |_) / _` | __/ __| '_ \ 
 / ___ \|  __/ (_| | || (__| | | |
/_/   \_\_|   \__,_|\__\___|_| |_|
```

- Root
- APM: Magisk module like support
- KPM: Kernel Patch Module support. (Allow you to inject any code into the kernel, Kernel function inline-hook and syscall-table-hook is available)

APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)

## Supported Versions

Only arm64.  
Android Kernel Version 3.18 - 6.1

## Requirement

CONFIG_KALLSYMS=y  
CONFIG_KALLSYMS_ALL=y (CONFIG_KALLSYMS_ALL=n, Planned support)

## Translation
To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.
[![Translation status](https://hosted.weblate.org/widget/APatch/apatch/multi-auto.svg)](https://hosted.weblate.org/engage/APatch/)


## Get Help

### Discussions

- Telegram Group: <https://t.me/APatchGroup>
- 中文: <https://t.me/APatch_CN_Group>

### More Information

- [FAQ](docs/en/faq.md)
- [常见问题解答](docs/cn/faq_cn.md)
- [常見問題解答](docs/cn_tw/faq_cn_tw.md)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 (GPL-3) (<http://www.gnu.org/copyleft/gpl.html>).  
