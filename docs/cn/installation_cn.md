# APatch 安装指南

## 安装前提

确保你的内核开启了以下依赖:

```
CONFIG_KALLSYMS=y
CONFIG_KALLSYMS_ALL=y
DEBUG_KERNEL=y
```

你可以使用下面的命令来检查:

```bash
su -c "zcat /proc/config.gz" | grep KALLSYMS
su -c "zcat /proc/config.gz" | grep DEBUG_KERNEL
```

确保它们的值都为y

## 安装

### 修补

- 点击app主页的`Patch/修补`按钮，并给你之后将要刷入的内核创建一个 密码，即`superkey`。

> 关于superkey是什么，请前往APatch EN群组内使用Rose Bot的notes功能自行查看。

- 接下来选择要修补的boot文件。

> 修补后，如果无误，修补过的boot镜像将会保存在`/storage/emulated/0/Download/apatch-{VERSIONCODE}-{随机字母}-boot.img`

### 刷入

- 使用任意的分区刷写软件将修补过的boot镜像刷入boot分区。

这里使用fastboot做例子:
先将修补过的boot复制到用来执行fastboot操作的设备上，随后使用下面的命令刷入boot镜像:

```bash
fastboot flash boot {修补过的boot镜像路径}
```

随后重启你的设备。

> 请确保设备已正确连接到用来执行fastboot的设备！

> 你也可以使用其他的分区刷写软件，甚至可以在开机状态下使用分区刷写app刷写noot分区。

### 激活

还记得之前的`superkey`吗？重启后点击`Superkey/超级密钥`并输入你先 前在修补内核时输入的superkey。
确认后你就可以正式的开始使用APatch了。

### 升级

从GitHub下载更新的release，在app内重新修补并刷入。

> **请确保使用没有使用APatch修补过的boot镜像**
