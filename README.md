<div align="center">
<a href="https://github.com/bmax121/APatch/releases/latest"><img src="https://images.weserv.nl/?url=https://raw.githubusercontent.com/bmax121/APatch/main/app/src/main/ic_launcher-playstore.png&mask=circle" style="width: 128px;" alt="logo"></a>

<h1 align="center">APatch</h1>

[![Latest release](https://img.shields.io/github/v/release/bmax121/APatch?label=Release&logo=github)](https://github.com/bmax121/APatch/releases/latest)
[![Weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/APatch)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/APatchGroup)
[![GitHub License](https://img.shields.io/github/license/bmax121/APatch?logo=gnu)](/LICENSE)

</div>

The patching of Android kernel and Android system.

- A new Kernel-based root solution for Android devices.
- APM: Magisk module like support.
- KPM: Kernel Patch Module support. (Allow you to inject any code into the kernel, Kernel function inline-hook and syscall-table-hook is available)
- APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/).
- The source code for both APatch UI and APM has been copied and modified from [KernelSU](https://github.com/tiann/KernelSU).

## Supported Versions

- ARM64 only
- Android Kernel Version 3.18 - 6.1

Support for Samsung devices with security protection: planned

## Requirement

Kernel configs

- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`
- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=n`: Initial support

## Security Alert

The **SUPERKEY** has higher privileges than root access.  
Weak or compromised keys can result in unauthorized control of your device.  
It is critical to use robust keys and safeguard them from exposure to maintain the security of your device.

## Translation
To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.

<div align="center">

[![Translation status](https://hosted.weblate.org/widget/APatch/open-graph.png)](https://hosted.weblate.org/engage/APatch/)

</div>

## Get Help

### Usage

Installation guide (coming soon)
<hr>

### Updates
- Telegram Channel: [@APatchChannel](https://t.me/APatchChannel)

### Discussions
- Telegram Group: [@APatchGroup(EN/CN)](https://t.me/Apatch_discuss)
- Telegram Group: [中文](https://t.me/APatch_CN_Group)



### More Information

- [Documents](docs/)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 [GPL-3](http://www.gnu.org/copyleft/gpl.html).
