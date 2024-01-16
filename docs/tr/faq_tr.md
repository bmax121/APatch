# SSS

## APatch nedir?

APatch, Magisk veya KernelSU'ya benzeyen ve her ikisinin de en iyi yönlerini birleştiren bir root çözümdür. Magisk'in boot.img aracılığıyla kullanışlı ve kolay kurulum yöntemini ve KernelSU'nun güçlü kernel yamalama yeteneklerini birleştirir.

## APatch vs Magisk

- Magisk başlangıç sistemini kernel'in ramdisk'indeki bir yama ile değiştirirken; APatch kernel'i doğrudan yamalar.

## APatch vs KernelSU

- KernelSU, cihazınızın kernel'i için her zaman OEM tarafından sağlanmayan kaynak kodunu gerektirir. APatch yalnızca stok boot.img dosyanızla çalışır.

## APatch vs Magisk, KernelSU

- İsteğe bağlı olarak SELinux'u değiştirmez. Bu şu anlama gelir; android uygulaması bağlamında root, libsu ve IPC'ye gerek yoktur.
- **Kernel Patch Modülü** sağlanır.

## Kernel Patch Modülü nedir?

Bazı kodlar, Yüklenebilir Kernel Modüllerinde (LKM) olduğu gibi Kernel Seviyesinde çalışır.

Ek olarak KPM, kernel sviyesinde satır içi kanca, sistem çağrısı-tablo kancası yapma yeteneği sağlar.

[KPM nasıl yazılır?](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## APatch and KernelPatch arasındaki ilişki

APatch, KernelPatch'e dayalıdır; onun tüm yeteneklerini devralır ve onu daha da geliştirmiştir.

Yalnızca KernelPatch'i kurabilirsiniz ancak bu magisk modüllerini kullanmanıza izin vermez,
Superuser yönetimini kullanmak için de AndroidPatch'i yüklemeniz ve ardından kaldırmanız gerekir.

[KernelPatch hakkında daha fazlasını öğrenin](https://github.com/bmax121/KernelPatch)

## Super Anahtar nedir?

KernelPatch, tüm yetenekleri kullanıcı alanına (userspace) sağlamak için sistem çağrılarını kancalar ve bu sistem çağrısına **SuperCall** denir.  
SuperCall'ı çağırmak, **Super Anahtar** olarak bilinen bir kimlik bilgisinin iletilmesini gerektirir.
SuperCall yalnızca Super Anahtar doğru olduğunda başarılı bir şekilde çağrılabilir; Super Anahtar hatalıysa çağıran bundan etkilenmez.

## SELinux'a ne dersiniz?

- KernelPatch, SELinux içeriğini değiştirmez ve SELinux'u kanca yoluyla atlamaz. Bu, yeni bir işlem başlatmak ve ardından IPC gerçekleştirmek için libsu kullanmanıza gerek kalmadan, uygulama bağlamında bir Android iş parçacığını rootlamanıza olanak tanır. Bu çok kullanışlıdır.
- Ayrıca APatch, ek SELinux desteği sağlamak için doğrudan magiskpolicy'yi kullanır.
