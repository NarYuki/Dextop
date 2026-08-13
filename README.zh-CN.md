<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a> | <a href="README.zh-CN.md">简体中文</a>
</p>

Dextop 是一款开源 Android 应用，可在手机上创建虚拟显示器，并仅使用智能手机提供类似桌面的工作空间。它通过 Shizuku 和 Android 系统服务控制应用启动、窗口位置、触摸输入、屏幕方向及相关桌面行为。

## 截图与演示

<table>
  <tr>
    <td width="20%" align="center"><img src="docs/media/home.jpg" alt="Dextop 主屏幕"><br><sub>主页与工作区</sub></td>
    <td width="20%" align="center"><img src="docs/media/desktop.jpg" alt="Dextop 桌面"><br><sub>桌面</sub></td>
    <td width="20%" align="center"><img src="docs/media/control-overlay.jpg" alt="Dextop 控制浮层"><br><sub>控制浮层</sub></td>
    <td width="20%" align="center"><img src="docs/media/multi-window.jpg" alt="Dextop 多窗口工作区"><br><sub>多窗口工作区</sub></td>
    <td width="20%" align="center"><a href="docs/media/dextop-demo.mp4"><img src="docs/media/demo-poster.jpg" alt="播放 Dextop 演示视频"></a><br><sub>▶ 演示视频</sub></td>
  </tr>
</table>

## 功能

- [x] 可配置分辨率、DPI、横屏或竖屏方向的虚拟显示器
- [x] 安全显示和 Android 系统装饰控制
- [x] 桌面应用启动器
- [x] 保存并恢复多个应用位置的工作区
- [x] 双栏、三栏、四栏及其他窗口布局
- [x] 以 JSON 导入和导出工作区
- [x] 光标与直接触摸输入模式
- [x] 点击、长按、拖动、右键、双指及三指手势
- [x] 根据折叠设备的展开和折叠状态自动切换分辨率
- [x] 显示 FPS、刷新率、内存、电池和估算功耗的性能浮层
- [x] 从快速设置磁贴启动
- [x] 恢复中断的会话和临时 Android 设置
- [x] 包含应用日志、能力探测、回退结果和设备规格的详细诊断报告
- [x] 日语、英语、中文、韩语和俄语界面
- [ ] 完整的物理鼠标支持（目前仅支持移动、基本点击、滚动等输入）
- [ ] 完整的物理键盘支持（快捷键、输入法和外接显示器输入路由仍取决于设备）

## 兼容性

| 环境 | 状态 | 备注 |
| --- | --- | --- |
| Samsung DeX | 基本支持 | 目前功能最完整的环境。由 DeX 管理的功能使用 Samsung 平台实现。 |
| Google Pixel | 有限且不完整 | 取决于 Android 的自由窗口／桌面实现和隐藏 API 可用性，部分功能可能无法工作。 |
| 其他 Android 设备 | 实验性 | 虚拟显示、镜像和自由窗口支持因厂商、型号和系统更新而异。 |

Dextop 会在运行时探测设备能力，并依次尝试兼容的后端。但由于仍依赖 Android 隐藏 API 和 OEM 行为，即使是同一厂商的不同型号或系统版本，结果也可能不同。

<details>
<summary><strong>支持的设备</strong></summary>

以下状态仅适用于实际测试过的固件版本。展开厂商可查看设备。构建信息和逐项功能结果请参阅[设备兼容性 Wiki](https://github.com/NarYuki/Dextop/wiki/Device-Compatibility)。

<details>
<summary><strong>Samsung</strong></summary>

| 设备 | 型号 | 已测试软件 | 状态 |
| --- | --- | --- | --- |
| Galaxy S26 | SM-S942Z (`m1q`) | Android 16 / One UI 8.5 / `S942ZSCS1AZF2` | ✅ 已确认可用 |

</details>

<details>
<summary><strong>Google</strong></summary>

尚未确认任何具体型号与固件组合。Pixel 支持仍然有限且不完整。

</details>

<details>
<summary><strong>其他厂商</strong></summary>

尚未确认任何具体型号与固件组合。

</details>
</details>

## 运行要求

- Android 10 或更高版本
- [Shizuku](https://shizuku.rikka.app/)
- 通过无线调试或 ADB 启动 Shizuku
- 授予 Dextop Shizuku 权限

如果不清楚 Shizuku 的设置步骤，请参阅 [Shizuku 官方设置指南](https://shizuku.rikka.app/guide/setup/)中的 **Start via wireless debugging**。

## 安装

Google Play 版本目前正在审核中。

从 [GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest) 下载最新 APK 并安装。

## 开发

```sh
git clone https://github.com/NarYuki/Dextop.git
cd Dextop
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

如需贡献新设备支持，请阅读[添加设备支持](docs/ADDING_DEVICE_SUPPORT.zh-CN.md)。[英文版](docs/ADDING_DEVICE_SUPPORT.en.md)和[日文版](docs/ADDING_DEVICE_SUPPORT.md)也可供参考。

## 诊断

打开**设置 → 应用信息 → 运行日志与设备诊断**，即可查看、复制或分享设备规格、能力探测、回退结果和 Dextop 运行日志。在将报告附加到 Issue 前，请删除不希望公开的个人信息。

本项目正在积极开发中。可用功能和行为可能随设备固件及 Android 更新而变化。

## 许可证

本项目采用 GPL-3.0-or-later 许可证。详情请参阅 [LICENSE](LICENSE)。
