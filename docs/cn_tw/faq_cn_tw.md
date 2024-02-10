# 〈常見問題集〉


## 什麼是 APatch？
APatch 為一套汲取 Magisk、KernelSU 優勢於一身的 Root 解決方案。
不僅保留 Magisk 自身便捷、修補 `boot.img` 即用的特性，也有 KernelSU 強大的核心修補功能。


## APatch 與 Magisk 的差別為何？
- Magisk 會先調用 boot 修補鏡像的 ramdisk，再修改 init 系統；APatch 則經由修補鏡像，直接修改核心本身。


## APatch vs KernelSU
- KernelSU 依託 OEM 廠商提供的裝置核心原始碼，但並非每間廠商都會提供；而 APatch 只需修補原廠 `boot.img`。


## APatch vs Magisk、KernelSU
- APatch 可選擇不修改 SELinux，但仍讓應用程式執行緒調用 Root 權限。過程不需要 libsu 以及 IPC。
- 支援**核心修補模組**。


## 什麼是核心修補模組？
**核心修補模組**為一套執行在核心空間的程式片段——類似於裝載式核心模組（LKM）——。

包括 inline-hook、syscall-table-hook，核心修補模組也能做到。

更多詳情，請參閱[〈如何編寫核心修補模組？〉](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)（英語）。


## APatch 和 KernelPatch 的關係

APatch 基於 KernelPatch。繼承 KernelPatch 特性的基礎之上，新增更多功能。

你也能選擇只安裝 KernelPatch，但這樣就沒辦法使用 Magisk 模組，且包括管理超級使用者權限在內的功能，你都必須解除安裝 KernelPatch 後再安裝 AndroidPatch。

[深入了解 KernelPatch](https://github.com/bmax121/KernelPatch)


## 什麼是超級密鑰？
**超級密鑰**為 KernelPatch 上的系統呼叫（syscall）服務，旨在讓使用者空間內的程式也能套用修補變更；也稱作**超級呼叫**。當程式請求**超級呼叫**，需提供一份稱作**超級密鑰**的存取憑證。
**超級呼叫**僅在**超級密鑰**正確無虞之下生效，反之則失效。


## 那 SELinux 怎麼辦？
- KernelPatch 不修改 SELinux 內容，而是使用 Hook 忽略 SELinux 層；
  換句話說，應用程式內的 Android 執行緒可直接調用 Root 權限，而不用經過讓 libsu 新增運算排程、安排 IPC 等步驟。
  可謂事半功倍。
- 此外，由於 APatch 使用了 magiskpolicy，使你能有額外的 SELinux 支援。
