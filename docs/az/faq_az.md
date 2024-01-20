# TSS


## APatch nədir?
APatch, hər ikisinin ən yaxşısını birləşdirən Magisk və ya KernelSU-ya bənzər kök həllidir.
O, Magisk-in `boot.img` vasitəsilə rahat və asan quraşdırma metodunu KernelSU-nun güclü nüvə yamaqlamaq qabiliyyətləri ilə birləşdirir.


## APatch və Magisk arasındakı fərq nədir?
- Magisk başlanğıc sistemini nüvə imajınızın ramdiskindəki yamaq ilə dəyişdirir, APatch isə nüvəni birbaşa yamaqlayır.


## APatch vs KernelSU
- KernelSU cihazınızın nüvəsi üçün həmişə OEM tərəfindən təmin edilməyən mənbə kodunu tələb edir. APAtch yalnız sizin stok `boot.img` ilə işləyir.


## APatch vs Magisk, KernelSU
- APatch istəyə bağlı olaraq SELinux-u dəyişdirməməyə imkan verir, bu o deməkdir ki, Android proqramı köklənə bilər, libsu və IPC zəruri deyil.
- **Nüvə Yamaq Modulu** təmin edilmişdir.


## Nüvə Yamaq Modulu nədir?
Bəzi kodlar Yüklənəbilən Nüvə Modulları (LKM) kimi Nüvə Məkanında işləyir.

Əlavə olaraq, KPM nüvə məkanında daxili-çəngəl, sistem-zəngi-cədvəli-çəngəlləri etmək imkanı verir.

Ətraflı məlumat üçün [KPM necə yazılır](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md) bölməsinə baxın.


## APatch və NüvəYamağı arasındakı əlaqə

APatch NüvəYamağından asılıdır, onun bütün imkanlarını miras alır və genişləndirilib.

Siz yalnızca NüvəYamağı quraşdıra bilərsiniz, lakin bu, Magisk modullarından istifadə etməyə imkan verməyəcək və superistifadəçi idarəçiliyindən istifadə etmək üçün AndroidYamağını quraşdırmalı və sonra onu silməlisiniz.

[NüvəYamağı haqqında ətraflı öyrənin](https://github.com/bmax121/KernelPatch)


## SuperAçar nədir?
NüvəYamağı istifadəçi məkanındakı tətbiq və proqramlara bütün imkanları təmin etmək üçün yeni sistem zəngi (syscall) əlavə edir, bu sistem zəngi **SuperZəng** adlanır.
Tətbiq/proqram **SuperZəngi** işə salmağa çalışdıqda, o, **SuperAçar** kimi tanınan giriş etimadnaməsini təmin etməlidir.
**SuperZəng** yalnız **SuperAçar** düzgün olduqda uğurla işə salına bilər və bu deyilsə, zəng edən şəxs təsirsiz qalacaq.


## SELinux bəs?
- NüvəYamağı SELinux kontekstini dəyişdirmir və çəngəl vasitəsilə SELinux-dan yan keçir.
  Bu, yeni prosesə başlamaq və sonra IPC yerinə yetirmək üçün libsu-dan istifadə etmədən proqram kontekstində Android mövzusunu kökləməyə imkan verir.
  Bu çox rahatdır.
- Bundan əlavə, APatch əlavə SELinux dəstəyi təmin etmək üçün birbaşa magiskpolicy-dən istifadə edir.
