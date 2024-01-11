# APatch

<img src="https://github.com/bmax121/APatch/assets/92950980/70a840b5-eaff-46da-b8f3-2b6a21b85e1b" style="width: 100px;" alt="logo">

</br>

[![latest release badge](https://img.shields.io/github/v/release/bmax121/APatch?label=Release&logo=github)](https://github.com/bmax121/APatch/releases/latest)
[![weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/APatch)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/APatchGroup)
[![GitHub License](https://img.shields.io/github/license/bmax121/APatch?logo=gnu)](/LICENSE)

The patching of Android kernel and Android system.

- A new Kernel-based root solution for Android devices.
- APM: Magisk module like support
- KPM: Kernel Patch Module support. (Allow you to inject any code into the kernel, Kernel function inline-hook and syscall-table-hook is available)
- APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/)

The source code for both APatch UI and APM has been copied and modified from [KernelSU](https://github.com/tiann/KernelSU)

## Supported Versions

- ARM64 only
- Android Kernel Version 3.18 - 6.1

## Requirement

Kernel configs

- `CONFIG_KALLSYMS=y`  
- `CONFIG_KALLSYMS_ALL=y` 
- `CONFIG_KALLSYMS_ALL=n` (Next version of KernelPatch will support)

## Translation
To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.

<div align="center">

[![Translation status](https://hosted.weblate.org/widget/APatch/apatch/horizontal-auto.svg)](https://hosted.weblate.org/engage/APatch/)

[![Translation status](https://hosted.weblate.org/widget/APatch/apatch/287x66-black.png)](https://hosted.weblate.org/engage/APatch/)

</div>

## Get Help

### Usage

Installation guide (coming soon)

### Discussions

- Telegram Group: [@APatchGroup](https://t.me/APatchGroup)
- 中文: [@APatch_CN_Group](https://t.me/APatch_CN_Group)


### More Information

- [FAQ](docs/en/faq.md)
- [Preguntas frecuentes](docs/es/faq_es.md)
- [常见问题解答](docs/cn/faq_cn.md)
- [常見問題解答](docs/cn_tw/faq_cn_tw.md)
- [Perguntas frequentes](docs/pt_br/faq_pt_br.md)
- [Domande frequenti](docs/it/faq_it.md)
- [Sık sorulan sorular](docs/tr/faq_tr.md)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 (GPL-3) (<http://www.gnu.org/copyleft/gpl.html>).  
