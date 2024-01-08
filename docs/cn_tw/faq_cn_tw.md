# 常見問題解答

## 什麼是APatch？

APatch是一種類似於Magisk或KernelSU的根解決方案，但APatch提供更多功能。

## APatch與Magisk的區別？

- Magisk修改init，APatch則對Linux內核進行補丁。

## APatch與KernelSU的區別？

- KernelSU需要原始碼，而APatch僅需要boot.img。

## APatch與Magisk、KernelSU的區別？

- 可選擇不修改SELinux。在Android應用程式上下文中進行root，無需libsu和IPC。
- 提供**Kernel Patch Module**。

## 什麼是Kernel Patch Module？

一些代碼在內核空間運行，類似於可加載內核模組（LKM）。

此外，KPM提供在內核空間進行內聯挂鉤、系統調用表挂鉤的能力。

[如何編寫KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## APatch與KernelPatch的關係

APatch依賴於KernelPatch，繼承了其所有功能並進行了擴展。

您可以僅安裝KernelPatch，但這將不允許您使用Magisk模組，  
要使用超級用戶管理，您需要安裝AndroidPatch，然後卸載它。

[了解更多關於KernelPatch的資訊](https://github.com/bmax121/KernelPatch)

## 什麼是SuperKey？

KernelPatch透過挂鉤系統調用提供所有功能給用戶空間，這個系統調用被稱為**SuperCall**。  
調用SuperCall需要傳遞一種憑證，稱為**SuperKey**。  
只有在SuperKey正確的情況下，SuperCall才能成功調用；如果SuperKey不正確，調用者將不受影響。

## 關於SELinux如何處理？

- KernelPatch不修改SELinux上下文，透過挂鉤繞過SELinux，  
  這允許您在應用程式上下文中root Android線程，無需使用libsu啟動新進程，然後執行IPC。這非常方便。
- 此外，APatch直接利用magiskpolicy提供額外的SELinux支援。  
  但是，僅這樣會被檢測為Magisk。有興趣的人可以嘗試繞過，問題已經很明確。
