# FAQ


## APatch가 뭔가요?
APatch는 Magisk와 KernelSU와 유사한 루팅툴로, 두 가지의 장점을 결합하였습니다.
`boot.img`을 통해 설치하는 Magisk의 편리하고 간편한 방법과 강력한 커널 패치 기능을 제공하는 KernelSU를 결합하였습니다.


## APatch와 Magisk의 차이점은 무엇인가요?
- Magisk는 부트 이미지의 램디스크에 패치를 적용하여 초기 시스템을 수정하는 반면, APatch는 커널을 직접 패치합니다.


## APatch vs KernelSU
- KernelSU는 기기의 커널 소스 코드가 필요하지만 OEM에서 항상 제공하지는 않습니다. 반면, APatch는 단지 여러분의 기본 `boot.img`만으로 작동합니다.


## APatch vs Magisk, KernelSU
- APatch는 선택적으로 SELinux를 수정하지 않을 수 있어, APP 스레드를 루팅할 수 있으며, libsu와 IPC가 필요하지 않습니다.
- **커널 패치 모듈**을 제공합니다..


## 커널 패치 모듈이 뭔가요?
커널 패치 모듈은 Loadable Kernel Modules (LKM)과 유사하게 커널 공간에서 실행되는 코드입니다.

또한, KPM은 커널에서 인라인 훅과 시스템 콜 테이블 훅을 수행할 수 있습니다.

자세한 정보는 [KPM 작성 방법](https://github.com/bmax121/KernelPatch/blob/main/doc/module.md)에서 확인하세요.


## APatch와 KernelPatch의 관계

APatch는 KernelPatch에 의존하며, 모든 기능을 상속받고 확장되었습니다.

KernelPatch만 설치할 수도 있지만, 이 경우 Magisk 모듈을 사용할 수 없습니다.

[KernelPatch에 대해 더 알아보기](https://github.com/bmax121/KernelPatch)


## SuperKey가 뭔가요?
KernelPatch는 사용자 공간의 앱 및 프로그램에 모든 기능을 제공하는 새로운 시스템 콜 (syscall)을 추가하며, 이것을 **SuperCall**이라고 합니다.
앱이나 프로그램이 **SuperCall**를 호출하려고 할 때, 접근 자격증명인 **SuperKey**를 제공해야 합니다.
**SuperCall**은 **SuperKey**가 정확하고 호출자에게 영향을 미치지 않을 경우에만 성공적으로 수행됩니다.


## SELinux는 어떻게 다루나요?
- KernelPatch는 SELinux 컨텍스트를 수정하지 않고 SELinux를 우회하는 훅을 사용합니다.
  이를 통해 앱 컨텍스트 내에서 안드로이드 스레드를 루팅할 수 있으며, 새 프로세스를 시작하고 IPC를 수행하기 위해 libsu를 사용할 필요가 없습니다.
  이 방법은 매우 편리합니다.
- 또한, APatch는 추가적인 SELinux 지원을 제공하기 위해 직접 magiskpolicy를 활용합니다.
