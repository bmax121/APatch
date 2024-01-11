# SSS

## APatch nedir?

APatch, Magisk veya KernelSU'ya benzer bir root çözümdür ancak APatch daha fazlasını sunar.

## APatch vs Magisk

- Magisk, init'i modifiye eder, APatch linux kernelini yamalar.

## APatch vs KernelSU

- KernelSU kaynak kodu gerektirir. APatch için sadece boot.img yeterlidir.

## APatch vs Magisk, KernelSU

- İsteğe bağlı olarak SELinux'u değiştirmez. Android uygulaması bağlamında root, libsu ve IPC'ye gerek yoktur.
- **Kernel Patch Modülü** sağlanır

## Kernel Patch Modülü nedir?

Bazı kodlar, Yüklenebilir Çekirdek Modüllerinde (LKM) olduğu gibi Kernel Seviyesinde çalışır.

Ek olarak KPM, çekirdek alanında satır içi kanca, sistem çağrısı-tablo kancası yapma yeteneği sağlar.

[KPM nasıl yazılır?](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## APatch and KernelPatch arasındaki ilişki

APatch, KernelPatch'e bağımlıdır, onun tüm yeteneklerini devralır ve genişletilmiştir.

Yalnızca KernelPatch'i kurabilirsiniz ancak bu, magisk modülünü kullanmanıza izin vermez,
Superuser yönetimini kullanmak için AndroidPatch'i yüklemeniz ve ardından kaldırmanız gerekir.

[KernelPatch hakkında daha fazlasını öğrenin](https://github.com/bmax121/KernelPatch)

## Super Anahtar nedir?

KernelPatch, tüm yetenekleri kullanıcı alanına sağlamak için sistem çağrılarını kancalar ve bu sistem çağrısına **SuperCall** denir.  
SuperCall'ı çağırmak, **Super Anahtar** olarak bilinen bir kimlik bilgisinin iletilmesini gerektirir.
SuperCall yalnızca Super Anahtar doğru olduğunda başarılı bir şekilde çağrılabilir; Super Anahtar hatalıysa çağıran bundan etkilenmez.

## SELinux'a ne dersiniz?

- KernelPatch, selinux içeriğini değiştirmez ve selinux'u kanca yoluyla atlamaz,
  Bu, yeni bir işlem başlatmak ve ardından IPC gerçekleştirmek için libsu kullanmanıza gerek kalmadan, uygulama bağlamında bir Android iş parçacığını rootlamanıza olanak tanır.
  Bu çok kullanışlıdır.
- Ayrıca APatch, ek SELinux desteği sağlamak için doğrudan magiskpolicy'yi kullanır.
  Ancak yalnızca bu Magisk olarak algılanacaktır. İlgilenen herkes bunu atlamayı deneyebilir, sorun zaten oldukça açık.
