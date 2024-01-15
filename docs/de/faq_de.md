# Häufig gestellte Fragen


## Was ist APatch?
APatch ist eine Root-Lösung, ähnlich wie Magisk oder KernelSU, welche das Beste beider vereint.
Es kombiniert Magisks bequeme und leichte Installationsmethode über `boot.img` mit KernelSUs starken Kernel-Korrektur-Fähigkeiten.


## Was ist der Unterschied zwischen APatch und Magisk?
- Magisk modifiziert das Init-System mit einer Korrektur in Ihrem Startbild ramdisk, während APatch den Kernel direkt korrigiert.


## APatch gegen KernelSU
- KernelSU requires the source code for your device's kernel which is not always provided by the OEM. APatch works with just your stock `boot.img`.
- KernelSU benötigt den Quellcode für dein Geräte-Kernel, welcher nicht immer von der OEM gegeben wird. APatch funktioniert allein mit Ihrem Standard-`boot.img`.

## APatch gegen Magisk, KernelSU
- APatch erlaubt Ihnen, optional, nicht SELinux zu modifizieren. Es erlaubt Ihnen auch, einen App-Thread zu rooten, ohne einen neuen au erstellen, sodass libsu und IPC nicht benötigt werden.
- **Kernel Korrigtur Modul** gegeben.


## Was ist ein Kernel Korrigtur Modul?
Etwas Code läuft im Kernelbereich, ähnlich zu Ladbaren Kernel-Modulen (LKM).

Dazu stellt KPM die Fähigkeit bereit, "inline-hook", "syscall-table-hook" im Kernelbereich zu machen.

Für mehr Informationen, siehe [Wie schreibt man ein KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)


## Beziehung zwischen APatch und KernelPatch

APatch benötigt KernelPatch, übernimmt all seine Fähigkeiten, und wurde erweitert.

Sie können nur KernelPatch installieren, aber dies wird Ihnen nicht erlauben, Magisk-Module zu installieren, und um Superuserverwaltung zu nutzen, müssen Sie APatch installieren und es dann deinstallieren.
[Lerne mehr über KernelPatch](https://github.com/bmax121/KernelPatch)


## Was ist SuperKey?
KernelPatch fügt einen neuen System-Abruf (syscall) hinzu, um alle Möglichkeiten, Apps und anderen Programmen im Benutzerbereich, bereitzustellen, dieser System-Abruf ist bekannt als **SuperCall**.
Wenn eine App / ein Programm versucht\, **SuperCall** abzurufen, muss es ein Berechtigungsnachweis vorlegen, bekannt als **SuperKey**.
**SuperCall** kann nur erfolgreich abgerufen werden, wenn der **SuperKey** korrekt ist und, wenn nicht, bleibt der Rufer unbetroffen.


## Was ist mit SELinux?
- KernelPatch bearbeitet den SELinux-Kontext nicht und umgeht den SELinux nicht über einen Haken.
  Dies erlaubt Ihnen, einen Android-Thread in dem App-Kontext, ohne dem Gebrauch, libsu zu nutzen, um einen neuen Prozess zu starten und darruf IPC auszuführen, zu rooten.
  Dies ist bequem.
- Dazu verwendet APatch direkt das Magiskkonzept, um weiteren SELinux-Unterstützung bereitzustellen.
  Jedoch wird nur dies als Magisk erkannt. Jeder Interessierter kann versuchen, dies zu umgehen, das Problem ist bereits recht klar.
