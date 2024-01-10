# Preguntas frecuentes

## ¿Qué es APatch?
APatch es una solución de root similar a Magisk o KernelSU que une lo mejor de ambos.
Combina el método de instalación conveniente y fácil de Magisk a través de un patche en `boot.img` y las potentes capacidades de KernelSU para parchear el kernel.


## Diferencias entre APatch y Magisk
- Magisk modifica el sistema de init (el sistema que inicializa todos los subsistemas del dispositivo) con un parche en la ramdisk de tu imagen `boot.img`, mientras que APatch parchea el kernel directamente.


## Diferencias entre APatch y KernelSU
- KernelSU requiere el código fuente del kernel del dispositivo, que no siempre es brindado por el fabricante del mismo. APatch solo requiere la imagen `boot.img`


## Diferencias entre APatch y ambas soluciones de root
- APatch permite opcionalmente no modificar el contexto de SELinux. También da acceso root a las apps en su propio contexto, lo cual significa que no hay necesidad de usar libsu ni IPC.
- Se añaden los **KPM** (Kernel Patch Modules), explicados abajo


## ¿Qué es un Kernel Patch Module (KPM, Módulo de Parche del Kernel)?
Permite implementar código personalizado que corra en kernelspace, es decir, en el mismo entorno que el kernel. Funciona similar a los módulos de kernel (los LKM, Loadable Kernel Modules) que se cargan con insmod/rmmod/modprobe/etc.

Además, KPM permite hacer inline-hooks ("engancharse" a una función en el mismo lugar de memoria, he de ahí el "inline") y syscall-table-hook (modificar la tabla de syscalls)

Para más información, lee [Como escribir un KPM](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md) (en Inglés)


## Relación entre APatch y KernelPatch
APatch depende de KernelPatch, hereda todas sus capacidades y las expande.

Puedes instalar solo KernelPatch, pero esto no va a permitir que uses módulos de Magisk, y para poder administrar el acceso root vas a tener que instalar AndroidPatch y desinstalarlo.

[Aprende más de KernelPatch](https://github.com/bmax121/KernelPatch) (en Inglés)


## ¿Qué es SuperKey?
KernelPatch agrega una nueva syscall (llamada al sistema) para dar todas las capacidades a apps y programas en userspace (el espacio del usuario fuera del kernel), y esta syscall se llama **SuperCall**.
Cuando una app o programa intenta llamar **SuperCall**, necesita proveer una credencial de acceso llamada **SuperKey**.
**SuperCall** solo se puede llamar si se provee una **SuperKey** correcta, caso contrario la app/programa que lo llame quedará intacta.


## ¿Y qué pasa con SELinux?
- KernelPatch no modifica el contexto de SELinux y simplemente se lo salta a través de un hook, lo cual permite que des acceso root a un hilo de Android dentro del contexto de la aplicación sin necesidad de usar libsu para iniciar un nuevo proceso y luego comunicarte con él a través de IPC (Inter-Process Communication)
- Además, APatch usa magiskpolicy directamente para obtener mayor soporte de SELinux. Solo esto va a ser detectado como Magisk, y cualquiera que lo desee puede intentar evitar esto ya que el problema ya está bastante claro.
