# Perguntas frequentes

## O que é APatch?

APatch é uma solução root semelhante ao Magisk ou KernelSU, mas o APatch oferece ainda mais.

## APatch vs Magisk

- Magisk modifica init, APatch corrige o kernel Linux.

## APatch vs KernelSU

- KernelSU requer a fonte. Apenas o boot.img para APatch é suficiente.

## APatch vs Magisk e KernelSU

- É recomendado não modificar o SELinux. Root no contexto de app Android, sem necessidade de libsu e IPC.
- **Módulo KernelPatch** fornecido.

## O que é Módulo KernelPatch?

Alguns códigos são executados no Kernel Space, semelhante aos Loadable Kernel Modules (LKM).

Além disso, o KPM fornece a capacidade de executar inline-hook e syscall-table-hook no Kernel Space.

[Como escrever um KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)

## Relacionamento entre APatch e KernelPatch

APatch depende do KernelPatch. Ele herda todas as suas capacidades e foi expandido.

Você pode instalar apenas o KernelPatch, mas isso não permitirá que você use módulos do Magisk, e para usar o gerenciamento de SuperUsuário, você precisa instalar o AndroidPatch e depois desinstalá-lo.

[Saiba mais sobre KernelPatch](https://github.com/bmax121/KernelPatch)

## O que é SuperKey?

KernelPatch conecta chamadas do sistema para fornecer todos os recursos ao espaço do usuário, e essa chamada do sistema é chamada de **SuperCall**.
Invocar o SuperCall requer a passagem de uma credencial, conhecida como **SuperKey**.
SuperCall só pode ser invocado com sucesso quando a SuperKey estiver correta. Se a SuperKey estiver incorreta, o chamador não será afetado.

## E o SELinux?

- KernelPatch não modifica o contexto do SELinux e ignora o SELinux via hook.
  Isso permite que você faça root em um thread do Android dentro do contexto do app sem a necessidade de usar libsu para iniciar um novo processo e então executar o IPC. Isto é muito conveniente.
- Além disso, o APatch utiliza diretamente o magiskpolicy para fornecer suporte adicional ao SELinux. No entanto, apenas este será detectado como Magisk. Qualquer pessoa interessada pode tentar contornar, a questão já está bastante clara.