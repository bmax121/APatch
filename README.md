# APatch

<div align="center">
<a href="https://github.com/bmax121/APatch/releases/latest"><img src="https://images.weserv.nl/?url=https://raw.githubusercontent.com/bmax121/APatch/main/app/src/main/ic_launcher-playstore.png&mask=circle" style="width: 128px;" alt="logo"></a>
	
</br>

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

### Updates
- Telegram Channel: [@APatchChannel](https://t.me/APatchChannel)

### Discussions
- Telegram Group: [@APatchGroup](https://t.me/APatchGroup)
- 中文: [@APatch_CN_Group](https://t.me/APatch_CN_Group)


### More Information

- [FAQ (EN)](docs/en/faq.md)
- [常见问题解答 (CN)](docs/cn/faq_cn.md)
- [常見問題解答 (CN_TW)](docs/cn_tw/faq_cn_tw.md)
- [Preguntas frecuentes (ES)](docs/es/faq_es.md)
- [Foire aux questions (FR)](docs/fr/faq_fr.md)
- [Domande frequenti (IT)](docs/it/faq_it.md)
- [Perguntas frequentes (PT_BR)](docs/pt_br/faq_pt_br.md)
- [Часто задаваемые вопросы (RU)](docs/ru/faq_ru.md)
- [Sık sorulan sorular (TR)](docs/tr/faq_tr.md)
- [Tez-tez soruşulan suallar (AZ)](docs/az/faq_az.md)


## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 (GPL-3) (<http://www.gnu.org/copyleft/gpl.html>).  
