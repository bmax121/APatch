## Foire aux questions (FAQ)

## Qu'est-ce qu'APatch ?

APatch est une méthode de root, similaire à Magisk ou KernelSU, offrant encore plus de fonctionnalités.

## Quelle est la différence entre APatch et Magisk ?

- Magisk modifie init, tandis qu'APatch patche le noyau Linux.

## Quelle est la différence entre APatch et KernelSU ?

- KernelSU nécessite le code source. APatch n'a besoin que du fichier boot.img.

## Quelle est la différence entre APatch, Magisk et KernelSU ?

- Optionnellement, ne modifie pas SELinux. Root dans le contexte d'application Android, libsu et d'IPC non nécessaires
- Fournit **Kernel Patch Module**

## Qu'est-ce que Kernel Patch Module ?

Certains codes s'exécutent dans l'espace du noyau, à l'instar des modules noyau chargeables (LKM, Loadable Kernel Modules).

De plus, KPM offre la possibilité d'effectuer des inline-hook, syscall-table-hook dans l'espace noyau.

[Comment écrire un module KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/fr/module.md)

## Relation entre APatch et KernelPatch

APatch dépend de KernelPatch, héritant de toutes ses fonctionnalités, et l'étendant.

Vous pouvez installer KernelPatch seul, mais cela ne vous permettra pas d'utiliser de module Magisk.
Pour utiliser la gestion super utilisateur, vous devez installer AndroidPatch puis le désinstaller.

[En savoir plus sur KernelPatch](https://github.com/bmax121/KernelPatch)

## Qu'est-ce que la clé (SuperKey) ?

KernelPatch fournit toutes les fonctionnalités à l'espace utilisateur en effectuant un appel système appelé **SuperCall**.  
L'appel du SuperCall nécessite le passage d'un type d'informations d'identification appelé **SuperKey**.  
Un SuperCall ne peut être effectué avec succès que si la clé est correcte. Si la clé est incorrecte, l'appelant ne sera pas affecté.

## Qu'en est-il de SELinux ?

- KernelPatch ne modifie pas le contexte SELinux et contourne SELinux via des hooks.  
  Cela vous permet de rooter un processus Android dans le contexte de applicatif, sans avoir à démarrer un nouveau processus avec libsu et ensuite exécuter l'IPC.
- De plus, APatch fournit un support SELinux supplémentaire directement via magiskpolicy.  
  Cependant, seul ce dernier sera détecté en tant que Magisk. Toute personne intéressée peut essayer de le contourner, le problème est déjà très clair.
