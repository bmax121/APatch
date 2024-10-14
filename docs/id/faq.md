# FAQ

## Apa itu APatch?
APatch adalah solusi root yang mirip dengan Magisk atau KernelSU yang menyatukan yang terbaik dari keduanya.
Ia menggabungkan metode instalasi Magisk yang mudah dan praktis melalui `boot.img` dengan kemampuan patching kernel KernelSU yang hebat.

## Apa perbedaan antara APatch dan Magisk?

- Magisk memodifikasi sistem init dengan patch di ramdisk boot image Anda, sementara APatch menambal kernel secara langsung.

## APatch vs KernelSU
- KernelSU memerlukan kode sumber untuk kernel perangkat Anda yang tidak selalu disediakan oleh OEM. APatch hanya berfungsi dengan `boot.img` bawaan Anda.

## APatch vs Magisk, KernelSU
- APatch memungkinkan Anda untuk secara opsional tidak memodifikasi SELinux, ini berarti bahwa utas APP dapat di-root, libsu dan IPC tidak diperlukan.

- **Modul Patch Kernel** disediakan.

## Apa itu Modul Patch Kernel? Beberapa kode berjalan di Kernel Space, mirip dengan Loadable Kernel Modules (LKM).

Selain itu, KPM menyediakan kemampuan untuk melakukan inline-hook, syscall-table-hook di kernel space.

Untuk informasi selengkapnya, lihat [Cara menulis KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## Hubungan antara APatch dan KernelPatch

APatch bergantung pada KernelPatch, mewarisi semua kemampuannya, dan telah diperluas.

Anda hanya dapat menginstal KernelPatch, tetapi ini tidak akan memungkinkan Anda untuk menggunakan modul Magisk

[Pelajari selengkapnya tentang KernelPatch](https://github.com/bmax121/KernelPatch)

## Apa itu SuperKey?
KernelPatch menambahkan panggilan sistem baru (syscall) untuk menyediakan semua kemampuan bagi aplikasi dan program di userspace, syscall ini disebut sebagai **SuperCall**. Saat aplikasi/program mencoba memanggil **SuperCall**, aplikasi/program tersebut perlu menyediakan kredensial akses, yang dikenal sebagai **SuperKey**.
**SuperCall** hanya dapat dipanggil dengan sukses jika **SuperKey** benar dan jika tidak, pemanggil tidak akan terpengaruh.

## Bagaimana dengan SELinux?
- KernelPatch tidak mengubah konteks SELinux dan melewati SELinux melalui hook.

Ini memungkinkan Anda untuk melakukan root pada thread Android dalam konteks aplikasi tanpa perlu menggunakan libsu untuk memulai proses baru dan kemudian melakukan IPC.

Ini sangat praktis.

- Selain itu, APatch secara langsung menggunakan magiskpolicy untuk menyediakan dukungan SELinux tambahan.