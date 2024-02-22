<div align="center">
<a href="https://github.com/bmax121/APatch/releases/latest"><img src="https://images.weserv.nl/?url=https://raw.githubusercontent.com/bmax121/APatch/main/app/src/main/ic_launcher-playstore.png&mask=circle" style="width: 128px;" alt="logo"></a>

<h1 align="center">APatch</h1>

[![latest release badge](https://img.shields.io/github/v/release/bmax121/APatch?label=Release&logo=github)](https://github.com/bmax121/APatch/releases/latest)
[![weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/APatch)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/APatchGroup)
[![GitHub License](https://img.shields.io/github/license/bmax121/APatch?logo=gnu)](/LICENSE)

</div>

The patching of Android kernel and Android system.

- A new Kernel-based root solution for Android devices.
- APM: Magisk module like support
- KPM: Kernel Patch Module support. (Allow you to inject any code into the kernel, Kernel function inline-hook and syscall-table-hook is available)
- APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)
- The source code for both APatch UI and APM has been copied and modified from [KernelSU](https://github.com/tiann/KernelSU)

## Supported Versions

- ARM64 only
- Android Kernel Version 3.18 - 6.1

## Requirement

Kernel configs

- `CONFIG_KALLSYMS=y`  

## Translation
To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.

<div align="center">

[![Translation status](https://hosted.weblate.org/widget/APatch/apatch/horizontal-auto.svg)](https://hosted.weblate.org/engage/APatch/)

[![Translation status](https://hosted.weblate.org/widget/APatch/apatch/287x66-black.png)](https://hosted.weblate.org/engage/APatch/)

</div>

## Get Help

### Usage

Installation guide (coming soon)
<hr>

### Updates
- Telegram Channel: [@APatchChannel](https://t.me/APatchChannel)

### Discussions
- Telegram Group: [@APatchGroup](https://t.me/APatchGroup)
- 中文: [@APatch_CN_Group](https://t.me/APatch_CN_Group)


### More Information

- [Documents](docs/)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 (GPL-3) (<http://www.gnu.org/copyleft/gpl.html>).  
