# 从 KernelSU 迁移 APModule

APatch 的 APModule（APM）机制源自 KernelSU，但两者并非完全相同。目前没有通用的“一键迁移”可以安全地复制所有模块及其运行状态。

## 推荐迁移流程

1. 在 KernelSU 中记录已安装模块的名称、版本和来源，并保留原始模块 ZIP。
2. 保留当前可启动镜像和恢复手段，确认设备能够回滚后再切换 root 方案。
3. 安装 APatch 并先验证基础 root 功能，不要立即恢复全部模块。
4. 通过 APatch 管理器逐个安装原始模块 ZIP；每安装一个模块就重启并验证一次。
5. 模块全部验证后，再删除旧环境中的备份。

不要直接复制 `/data/adb/ksu` 下的状态目录到 APatch。运行状态、内部文件和工具路径可能不同，直接复制也无法验证模块安装脚本是否正确执行。

## 兼容性检查

迁移前应检查模块脚本是否存在以下情况：

- 硬编码 KernelSU 路径。APatch 的 BusyBox 位于 `/data/adb/ap/bin/busybox`；模块自己的路径应通过 `MODDIR=${0%/*}` 获取。
- 依赖 KernelSU 专属 API、环境变量或管理器行为。
- 依赖内置 Zygisk。APatch 不内置 Zygisk 支持，需要按照模块作者提供的 APatch 或 ZygiskNext 方案单独确认。
- 包含设备、内核版本或 SELinux 策略相关逻辑。APatch 使用 `magiskpolicy` 提供额外 SELinux 支持，不能假设旧策略无需验证即可工作。

模块格式、脚本环境和 APatch 与 KernelSU 的差异，请参阅 [APM 开发指南](ap_module.md)。

## 出现启动问题时

停止批量尝试，回滚到迁移前的可启动状态。确认是哪个模块导致问题后，再向模块作者提供设备型号、Android/内核版本、APatch 版本和模块版本；不要在公开 Issue 中上传密钥、设备凭据或包含个人数据的完整日志。
