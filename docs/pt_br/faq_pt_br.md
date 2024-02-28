# Perguntas frequentes

## O que é APatch?

APatch é uma solução root semelhante ao Magisk ou KernelSU que une o melhor de ambos. Ele combina o método de instalação fácil e conveniente do Magisk por meio do `boot.img` com as poderosas habilidades de patch de kernel do KernelSU.

## Qual é a diferença entre APatch e Magisk?

- Magisk modifica o sistema init com um patch no ramdisk da sua imagem de inicialização, enquanto o APatch corrige o kernel diretamente.

## APatch vs KernelSU

- KernelSU requer o código-fonte do kernel de seu dispositivo, que nem sempre é fornecido pelo OEM. APatch funciona apenas com seu `boot.img` stock.

## APatch vs Magisk e KernelSU

- APatch permite opcionalmente não modificar o SELinux, isso significa que o thread do app pode ser rooteado, libsu e IPC não são necessários.
- **Módulo KernelPatch** fornecido.

## O que é Módulo KernelPatch?

Alguns códigos são executados no Kernel Space, semelhante ao Loadable Kernel Modules (LKM).

Além disso, o KPM fornece a capacidade de executar inline-hook e syscall-table-hook no Kernel Space.

Para mais informações, veja [como escrever um KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md).

## Relacionamento entre APatch e KernelPatch

APatch depende do KernelPatch. Ele herda todas as suas capacidades e foi expandido.

Você pode instalar apenas o KernelPatch, mas isso não permitirá o uso de módulos Magisk.

[Saiba mais sobre o KernelPatch](https://github.com/bmax121/KernelPatch).

## O que é SuperKey?

KernelPatch conecta chamadas do sistema para fornecer todos os recursos ao espaço do usuário, e essa chamada do sistema é chamada de **SuperCall**. Invocar o SuperCall requer a passagem de uma credencial, conhecida como **SuperKey**. SuperCall só pode ser invocado com sucesso quando a SuperKey estiver correta. Se a SuperKey estiver incorreta, o chamador não será afetado.

## E o SELinux?

- KernelPatch não modifica o contexto do SELinux e ignora o SELinux via hook. Isso permite que você faça root em um thread do Android dentro do contexto do app sem a necessidade de usar libsu para iniciar um novo processo e então executar o IPC.
- Além disso, o APatch utiliza diretamente o magiskpolicy para fornecer suporte adicional ao SELinux.
