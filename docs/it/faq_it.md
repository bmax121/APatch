# Domande frequenti

## Cos'è APatch

APatch è una soluzione per il root simile a Magisk o KernelSU, ma offre funzionalità aggiuntive.

## APatch vs Magisk

- Magisk modifica l'init, mentre APatch patcha il kernel Linux.

## APatch vs KernelSU

- KernelSU richiede il codice sorgente del kernel, mentre per APatch è sufficiente solo il file boot.img.

## APatch vs Magisk, KerenlSU

- Opzionalmente, non modifica SELinux.
- Consente di ottenere i permessi di root nel contesto dell'app Android, senza la necessità di libsu e IPC.
- Possibilità di utilizzare **Kernel Patch Module**

## Cos'è un Kernel Patch Module

È del codice eseguito nello spazio del Kernel, simile ai Loadable Kernel Modules (LKM).

Inoltre, KPM fornisce la possibilità di effettuare inline-hook e syscall-table-hook nello spazio del kernel.

[Come scrivere un KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## Relazione tra APatch e KernelPatch

APatch dipende da KernelPatch, eredita tutte le sue capacità ed è stato ampliato.

Puoi installare solo KernelPatch, ma ciò non consentirà l'uso dei moduli Magisk.
Per gestire i permessi di root, è necessario installare AndroidPatch e successivamente disinstallarlo.

[Scopri di più su KernelPatch](https://github.com/bmax121/KernelPatch)

## Cos'è la SuperKey

KernelPatch aggancia le chiamate di sistema per fornire tutte le capacità allo spazio utente, e questa chiamata di sistema è chiamata **SuperCall**.
Invocare SuperCall richiede il passaggio di una credenziale, nota come **SuperKey**.
SuperCall può essere invocato con successo solo quando la SuperKey è corretta; se la SuperKey è errata, chi effettua la chiamata rimane inalterato.

## Riguardo a SELinux

- KernelPatch non modifica il contesto SELinux e bypassa SELinux tramite hook,  
  consentendo di ottenere i privilegi di root in un thread Android all'interno del contesto dell'app senza la necessità di utilizzare libsu per avviare un nuovo processo e quindi eseguire IPC.
  Questo è molto conveniente.
- Inoltre, APatch utilizza direttamente magiskpolicy per fornire ulteriore supporto SELinux.  
  Tuttavia, solo questo sarà rilevato come Magisk. Chiunque sia interessato può cercare di bypassarlo; il problema è già abbastanza chiaro.