// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get home => '主页';

  @override
  String get settings => '设置';

  @override
  String get resolution => '分辨率';

  @override
  String get theme => '主题';

  @override
  String get system => '跟随系统';

  @override
  String get light => '浅色';

  @override
  String get dark => '深色';

  @override
  String get display => '显示';

  @override
  String get secureDisplay => '安全显示';

  @override
  String get secureDisplayDescription => '允许显示受保护的内容';

  @override
  String get mirrorBackend => '显示镜像方式';

  @override
  String get mirrorBackendAuto => '自动（兼容性优先）';

  @override
  String get mirrorBackendAutoDescription => '使用此设备可用的最佳方式';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get updateAvailable => '有可用更新';

  @override
  String get updateAvailableTitle => 'GitHub 上已发布新版本！';

  @override
  String get playUpdateAvailableTitle => 'Google Play 上有可用更新';

  @override
  String get playUpdateAvailableDescription => '您可以通过 Google Play 更新到最新版本。';

  @override
  String get updateNow => '立即更新';

  @override
  String get checkForUpdates => '检查更新';

  @override
  String get checkingForUpdates => '正在获取更新信息';

  @override
  String get updateNotChecked => '尚未获取更新信息';

  @override
  String get upToDate => '已是最新版本';

  @override
  String get updateCheckFailed => '无法获取更新信息';

  @override
  String get currentVersion => '当前版本';

  @override
  String get latestVersion => '最新版本';

  @override
  String get openOnGitHub => '在 GitHub 中打开';

  @override
  String get close => '关闭';

  @override
  String get deviceInfo => '设备信息';

  @override
  String get desktopMode => '使用的桌面模式';

  @override
  String get accessibilitySettings => '无障碍设置';

  @override
  String get accessibilityDescription => '打开 Dextop 服务设置';

  @override
  String get appInfo => '应用信息';

  @override
  String get licenses => '开源许可证';

  @override
  String get licensesDescription => '查看 Flutter 和依赖库许可证';

  @override
  String get landscape => '横向';

  @override
  String get portrait => '纵向';

  @override
  String get stopped => '已停止';

  @override
  String get running => '运行中';

  @override
  String get start => '启动';

  @override
  String get stop => '停止';

  @override
  String get customAdd => '添加自定义分辨率';

  @override
  String get editResolution => '编辑分辨率';

  @override
  String get add => '添加';

  @override
  String get save => '保存';

  @override
  String get deleteResolution => '删除此分辨率';

  @override
  String get width => '宽度';

  @override
  String get height => '高度';

  @override
  String get protectedContent => '允许显示受保护的内容';

  @override
  String get version => '版本 1.0.0';

  @override
  String get setupWelcome => '欢迎使用 Dextop。';

  @override
  String get setupTagline => '智能手机上的完美桌面环境。';

  @override
  String get setupBegin => '开始';

  @override
  String get setupPhaseTerms => '使用条款';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => '检查您的设备';

  @override
  String get setupPhaseDemo => '体验操作';

  @override
  String get back => '返回';

  @override
  String get continueLabel => '继续';

  @override
  String get done => '完成';

  @override
  String get incomplete => '未完成';

  @override
  String get setupSystemTitle => '使用系统级功能';

  @override
  String get setupSystemDescription =>
      'Dextop 使用 Shizuku 和 ADB 来控制虚拟显示、屏幕方向、输入和系统 UI 等行为。';

  @override
  String get setupDisclaimer =>
      '对于因设备或操作系统实现差异、系统更新、与其他应用程序冲突等而导致的任何缺陷、数据丢失或对设备功能的影响，开发者不承担任何责任。请在使用前了解内容。';

  @override
  String get setupShizukuTitle => '准备 Shizuku';

  @override
  String get setupShizukuDescription => 'Dextop 使用 Shizuku 安全地访问系统功能。';

  @override
  String get setupInstallShizuku => '安装 Shizuku';

  @override
  String get setupConfigureShizuku => '设置 Shizuku';

  @override
  String get setupShizukuHint => '打开 Shizuku，按照“配对”中显示的顺序进行设置，然后启动 Shizuku。';

  @override
  String get setupOpenShizuku => '打开 Shizuku';

  @override
  String get setupValidate => '设置完成了吗？立即验证';

  @override
  String get setupDextopPermission => '授予 Dextop 权限';

  @override
  String get setupInstallPlay => '从 Google Play 安装';

  @override
  String get setupAllowPermission => '授予权限';

  @override
  String get setupProviderChoiceTitle => '选择特权服务';

  @override
  String get setupProviderChoiceDescription =>
      '已同时安装 Stellar 和 Shizuku。请选择 Dextop 要使用的服务。';

  @override
  String get setupUseStellar => 'Stellar（推荐）';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => '服务正在以 root 运行';

  @override
  String get setupRootVerified => '已确认 Shizuku 正在以 root 权限运行。接下来请授予 Dextop 权限。';

  @override
  String get setupRootNotRunning =>
      '无法确认 Shizuku 正在以 root 权限运行。请使用 root 启动后重试。';

  @override
  String get setupQuestionOpen => '您打开 Shizuku 了吗？';

  @override
  String get setupQuestionPair => '您是否已完成“配对”下列出的所有步骤？';

  @override
  String get setupQuestionStart => '您是否已在 Shizuku 中点击“启动”，并确认显示“Shizuku 正在运行”？';

  @override
  String get yes => '是';

  @override
  String get no => '否';

  @override
  String get setupVerified => '已检查 Shizuku 设置';

  @override
  String get setupVerificationFailed =>
      '无法确认 Shizuku 的配置或启动。请完成 Shizuku 中的步骤，然后再次检查。';

  @override
  String get setupPermissionCheckFailed => '无法检查 Shizuku 的权限';

  @override
  String get setupDeviceTitle => '该设备上的配置';

  @override
  String get model => '型号';

  @override
  String get vendor => '厂商';

  @override
  String get desktopUi => '桌面界面';

  @override
  String get detectedResolution => '自动检测分辨率';

  @override
  String get loadingLabel => '加载中…';

  @override
  String get setupDeviceDescription => '此信息用于设置初始分辨率和设备特定的桌面控件。';

  @override
  String get setupGestureTitle => '通过手势调出控制面板';

  @override
  String get setupGestureDescription => '将三个手指同时放在下面的三个圆圈上。';

  @override
  String get uiTwoFingerTap => '两根手指点击';

  @override
  String get ui3FingerTap => '3 指点击';

  @override
  String get ui4Divisions => '四分屏';

  @override
  String get uiDextopIsReady => 'Dextop 已准备就绪';

  @override
  String get uiStopDextop => '停止 Dextop';

  @override
  String get uiDextopCanBeRestarted => '可以恢复 Dextop';

  @override
  String get uiOpenDextop => '打开 Dextop';

  @override
  String get uiCreateADextopSession => '创建 Dextop 会话';

  @override
  String get uiDextopWorkspaceJson => 'Dextop 工作区 JSON';

  @override
  String get uiPerformanceDisplayOnDextop => '在 Dextop 中显示性能浮层';

  @override
  String get uiDoNotSleepWhileRunningDextop => 'Dextop 运行时保持屏幕常亮';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower => '实时显示 FPS、内存、功耗和电量';

  @override
  String get uiCouldNotLoadJson => '无法加载 JSON';

  @override
  String get uiSecureSettingsPermission => '安全设置权限';

  @override
  String get uiAllowShizukuPermissions => '授予 Shizuku 权限';

  @override
  String get uiInstallShizuku => '安装 Shizuku';

  @override
  String get uiCheckingShizukuConnection => '检查 Shizuku 连接';

  @override
  String get uiShizukuConnection => 'Shizuku 连接';

  @override
  String get uiCopy => '的副本';

  @override
  String get uiOthers => '其他';

  @override
  String get uiAccessibilityOverlay => '无障碍浮层';

  @override
  String get uiAccessibilityServices => '无障碍服务';

  @override
  String get uiAppNotFound => '找不到应用程序';

  @override
  String get uiAppsAndWorkspace => '应用程序和工作区';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace => '启动应用并配置工作区';

  @override
  String get uiRestartTheApp => '重启应用';

  @override
  String get uiSearchApp => '搜索应用';

  @override
  String get uiAppMemory => '应用内存';

  @override
  String get uiAppLauncher => '应用程序启动器';

  @override
  String get uiAppLauncherSettings => '应用程序启动器设置';

  @override
  String get uiAppLaunchFunction => '应用程序启动功能';

  @override
  String get uiImport => '导入';

  @override
  String get uiExport => '导出';

  @override
  String get uiCursor => '光标';

  @override
  String get uiCancel => '取消';

  @override
  String get uiQuickSettingsTile => '快速设置图块';

  @override
  String get uiGesture => '手势';

  @override
  String get uiSecondaryIme => '辅助输入法';

  @override
  String get uiSecureDisplayFoldable => '安全显示、镜像方式、可折叠设备';

  @override
  String get uiSecurity => '安全';

  @override
  String get topologyTitle => '显示器排列';

  @override
  String get topologyArrangeDisplays => '显示器排列';

  @override
  String get topologySummary => '可根据实际显示器摆放优化布局';

  @override
  String get topologyDescription => '拖动显示器进行重新排列，使指针在屏幕之间的移动方向与实际摆放位置一致。';

  @override
  String get topologyApply => '应用';

  @override
  String get topologyApplied => '已应用显示器排列';

  @override
  String get topologyIdentify => '识别';

  @override
  String get topologyRefresh => '刷新';

  @override
  String get topologyReset => '重置';

  @override
  String get topologyBuiltInScreen => '内置屏幕';

  @override
  String get displayIncludePhoneSummary => '启用后，可在显示器之间移动应用和鼠标指针';

  @override
  String get displayAutoHideTaskbarSummary => '不使用时自动隐藏桌面任务栏';

  @override
  String get displayForceInternal120Hz => '内置屏幕以 120 Hz 运行';

  @override
  String get displayForceInternal120HzSummary => 'Dextop 运行时将支持的内置屏幕固定为 120 Hz';

  @override
  String get uiConvenience => '便捷功能';

  @override
  String get uiDisplayCategory => '显示器';

  @override
  String get topologyNoDisplays => '没有可排列的显示器';

  @override
  String get topologyUnavailable => '此设备不支持显示器拓扑';

  @override
  String get uiTap => '点击';

  @override
  String get uiTapPressAndHoldMultiFingerOperation => '点击、长按和多指操作';

  @override
  String get uiOpenAppOnDesktop => '在桌面上打开应用程序';

  @override
  String get uiDesktopMode => '桌面模式';

  @override
  String get uiDesktopFeatures => '桌面功能';

  @override
  String get uiTrackpad => '触控板';

  @override
  String get uiDrag => '拖动';

  @override
  String get uiBattery => '电池';

  @override
  String get uiPerformance => '性能';

  @override
  String get uiPerformanceCompatibility => '性能、兼容性';

  @override
  String get uiItSupportsMultiTouchAndTheThree => '启用多点触控后，三指手势将改为从屏幕左边缘滑动。';

  @override
  String get uiMainLarge2Sub => '主窗口大 + 两个副窗口';

  @override
  String get uiMainLeft => '主窗口（左）';

  @override
  String get uiLayout => '布局';

  @override
  String get uiWorkSpace => '工作空间';

  @override
  String get uiCopiedWorkspaceJsonToClipboard => '将工作区 JSON 复制到剪贴板';

  @override
  String get uiImportWorkspace => '导入工作区';

  @override
  String get uiSaveWorkspace => '保存工作区';

  @override
  String get uiDeleteWorkspace => '删除工作区';

  @override
  String get uiEditWorkspace => '编辑工作区';

  @override
  String get uiUp => '上移';

  @override
  String get uiDividedIntoUpperAndLowerParts => '上下分屏';

  @override
  String get uiUpperHalf => '上半部分';

  @override
  String get uiMoveDown => '向下移动';

  @override
  String get uiLowerHalf => '下半部分';

  @override
  String get uiCenter => '居中';

  @override
  String get uiCompatibilityDiagnosis => '兼容性诊断';

  @override
  String get uiVirtualDisplayCreation => '虚拟显示创建';

  @override
  String get uiOpenASavedAppConfiguration => '打开保存的应用程序配置';

  @override
  String get uiNoSavedWorkspaces => '没有保存的工作区';

  @override
  String get uiInputAndGestures => '输入和手势';

  @override
  String get uiInputMode => '输入方式';

  @override
  String get uiCancelFullScreen => '退出全屏';

  @override
  String get uiReDiagnosis => '重新运行诊断';

  @override
  String get uiRestart => '恢复';

  @override
  String get uiAvailableMemory => '可用内存';

  @override
  String get uiDelete => '删除';

  @override
  String get uiYouCanRestoreYourPreviousSession => '您可以恢复之前的会话';

  @override
  String get uiRight => '右侧';

  @override
  String get uiRight13 => '右1/3';

  @override
  String get uiRight23 => '右2/3';

  @override
  String get uiRightClick => '右键单击';

  @override
  String get uiUpperRight => '右上';

  @override
  String get uiLowerRight => '右下';

  @override
  String get uiRightHalf => '右半屏';

  @override
  String get uiName => '名称';

  @override
  String get uiLargeScreenFoldable => '大屏/可折叠';

  @override
  String get uiActualFps => '实际帧率';

  @override
  String get uiExperimentalMultiTouch => '实验性多点触控';

  @override
  String get uiExperimentalFeatures => '实验性功能';

  @override
  String get uiLeft => '左侧';

  @override
  String get uiLeft13 => '左1/3';

  @override
  String get uiLeft13Right23 => '左1/3・右2/3';

  @override
  String get uiLeft23 => '左 2/3';

  @override
  String get uiLeft23Right13 => '左2/3・右1/3';

  @override
  String get uiLeftCenterRight => '左/中/右';

  @override
  String get uiUpperLeft => '左上';

  @override
  String get uiUpperLeftUpperRightLowerHalf => '左上、右上、下半部';

  @override
  String get uiLowerLeft => '左下';

  @override
  String get uiLeftHalf => '左半屏';

  @override
  String get uiDividedIntoLeftAndRight => '左右分屏';

  @override
  String get uiSwipeRightWithThreeFingersFromThe => '用三个手指从左边缘向右滑动';

  @override
  String get uiRecoverySession => '恢复会话';

  @override
  String get uiEstimatedPowerConsumption => '预计功耗';

  @override
  String get uiOperationOverlay => '操作浮层';

  @override
  String get uiShowActionOverlay => '显示操作浮层';

  @override
  String get uiOperationMenu => '控制菜单';

  @override
  String get uiThereIsAnExistingSession => '已有会话';

  @override
  String get uiSaveConfiguration => '保存配置';

  @override
  String get uiRestorePrivileges => '恢复权限';

  @override
  String get uiChangeToHorizontalHold => '切换为横向';

  @override
  String get uiPreparationIsRequired => '需要完成设置';

  @override
  String get uiPhysicalKeyboard => '物理键盘';

  @override
  String get uiPhysicalMouse => '物理鼠标';

  @override
  String get uiConditionAndDiagnosis => '状态与诊断';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically => '防止屏幕自动关闭';

  @override
  String get uiDestruction => '丢弃';

  @override
  String get uiTerminalAndPermissions => '设备与权限';

  @override
  String get uiDeviceInformationDesktopModeAccessibility => '设备信息、桌面模式、辅助功能';

  @override
  String get uiTerminalResolution => '设备分辨率';

  @override
  String get uiEnd => '结束';

  @override
  String get uiTerminationProcessingCompletedSuccessfully => '会话已正常结束。';

  @override
  String get uiEdit => '编辑';

  @override
  String get uiChangeToPortraitOrientation => '切换为纵向';

  @override
  String get uiVerticalHorizontalSwitching => '横向/纵向切换';

  @override
  String get uiDisplayOptimization => '显示优化';

  @override
  String get uiDisplayRefreshRate => '显示刷新率';

  @override
  String get uiReproduction => '复制';

  @override
  String get uiManageLaunchedAppsAndConfigurations => '管理要启动的应用及其布局';

  @override
  String get uiCouldNotStart => '无法启动';

  @override
  String get uiLongPress => '长按';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      '自动使用测量的打开和关闭状态分辨率';

  @override
  String get uiStart => '开始';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState => '根据开/关状态自动切换';

  @override
  String get uiOpeningQuote => '“';

  @override
  String get uiDeleteWorkspaceQuestionSuffix => '”？';

  @override
  String get uiAbnormalSessionWarning =>
      '会话在异常状态下结束。\n部分 Android 系统功能可能仍处于禁用状态。';

  @override
  String get uiChecking => '正在检查';

  @override
  String get uiIdle => '空闲';

  @override
  String get uiAvailable => 'Available';

  @override
  String get uiUnavailable => 'Unavailable';

  @override
  String get appName => 'Dextop';

  @override
  String get uiAndroid => 'Android';

  @override
  String get uiGitHub => 'GitHub';

  @override
  String get uiGitHubRepository => 'NarYuki/Dextop';

  @override
  String get diagnosticLog => '运行日志和设备诊断';

  @override
  String get diagnosticLogDescription => '查看应用日志、功能检测和详细设备规格';

  @override
  String get loadDiagnosticLog => '加载诊断报告';

  @override
  String get copyDiagnosticLog => '复制';

  @override
  String get shareDiagnosticLog => '分享';

  @override
  String get clearDiagnosticLog => '清除日志';

  @override
  String get deviceReport => '设备运行报告';

  @override
  String get uiCpuTemperature => 'CPU 温度';

  @override
  String get deviceReportDescription => '通过电子邮件报告设备和功能兼容性';

  @override
  String get deviceReportIntro => '设备信息将自动收集。请选择各项功能的运行结果。';

  @override
  String get reportWorking => '可用';

  @override
  String get reportNotWorking => '不可用';

  @override
  String get reportUntested => '未测试';

  @override
  String get reportOverall => '总体状态';

  @override
  String get reportNotes => '其他备注';

  @override
  String get sendDeviceReport => '通过邮件发送报告';

  @override
  String get reportEmailUnavailable => '无法打开邮件应用';

  @override
  String get reportTemplateTitle => 'Dextop 设备运行报告';

  @override
  String get reportNoNotes => '无';

  @override
  String get reportNoSessionLog => '尚未记录已完成的Dextop会话日志。';

  @override
  String get reportFeatureStartup => '应用启动与设备检测';

  @override
  String get reportFeatureSession => 'Dextop 会话启动';

  @override
  String get reportFeatureVirtualDisplay => 'VirtualDisplay 镜像';

  @override
  String get reportFeatureWindowManager => 'WindowManager 镜像';

  @override
  String get reportFeatureSurfaceControl => 'SurfaceControl 镜像';

  @override
  String get reportFeatureLandscape => '横屏模式';

  @override
  String get reportFeaturePortrait => '竖屏模式';

  @override
  String get reportFeatureSecureDisplay => '安全显示';

  @override
  String get reportFeatureLauncher => '应用启动器与自由窗口';

  @override
  String get reportFeatureWorkspace => '工作区保存与恢复';

  @override
  String get reportFeatureCursor => '光标与触控板输入';

  @override
  String get reportFeatureDirectTouch => '直接触控输入';

  @override
  String get reportFeatureMultiTouch => '多点触控滚动与双指缩放';

  @override
  String get reportFeatureGesture => '三指浮层手势';

  @override
  String get reportFeatureMouse => '物理鼠标';

  @override
  String get reportFeatureKeyboard => '物理键盘';

  @override
  String get reportFeatureRouting => '物理鼠标与键盘显示路由';

  @override
  String get reportFeatureFoldable => '折叠设备自动分辨率';

  @override
  String get reportFeaturePerformance => '性能浮层';

  @override
  String get reportFeatureCleanup => '会话清理与 Android 状态恢复';

  @override
  String get samsungExperimentalTitle => '实验性 Samsung 桌面设置';

  @override
  String get samsungUnavailable => '仅可在 Samsung 设备上使用';

  @override
  String get samsungExperimentalDescription => '从 Dextop 更改原生 DeX 设置中隐藏的项目';

  @override
  String get samsungSettingsTitle => 'Samsung 桌面设置';

  @override
  String get samsungSettingsSummary => '显示、输入和任务栏设置';

  @override
  String get samsungRestoreSuccess => '已恢复 Samsung 设置';

  @override
  String get samsungConfirmTitle => '确认更改设置';

  @override
  String get samsungPermanentWarning => '这些项目可能会永久影响 Dextop 和日常使用的桌面环境，直到重置为止。';

  @override
  String get samsungAcceptEnable => '同意并启用';

  @override
  String get samsungAboutSetting => '关于此设置';

  @override
  String get samsungRestoreEnvironment => '恢复环境';

  @override
  String get samsungSettingsIntro =>
      '直接更改 Samsung 设置因未检测到外接显示器而隐藏的 DeX 值。更改会影响 Samsung DeX 及对应的 Dextop 功能。';

  @override
  String get samsungResolution => '外接屏幕分辨率';

  @override
  String get samsungScreenZoom => '屏幕缩放 (DPI)';

  @override
  String get samsungFontScale => '字体大小';

  @override
  String get samsungScreenTimeout => '屏幕超时';

  @override
  String get samsungAudioOutput => '从外接屏幕输出音频';

  @override
  String get samsungDisplayOrientation => '外接屏幕旋转';

  @override
  String get samsungDisplayArrangement => '屏幕排列';

  @override
  String get samsungSectionInput => '输入';

  @override
  String get samsungSectionDesktop => '桌面';

  @override
  String get samsungInputLockedWhileRunning =>
      'Dextop 运行时无法更改有冲突的 Samsung 输入设置。';

  @override
  String get samsungAutorunTouchpad => '自动启动触控板';

  @override
  String get samsungTouchpadScrollDirection => '反转滚动方向';

  @override
  String get samsungTouchKeyboard => '连接时显示屏幕键盘';

  @override
  String get samsungKeyboardDex => '使用物理键盘时仍显示键盘';

  @override
  String get samsungSpenInputMode => '将 S Pen 用作鼠标';

  @override
  String get samsungThreeFingerGesture => '三指手势';

  @override
  String get samsungFourFingerGesture => '四指手势';

  @override
  String get samsungAutoHideTaskbar => '自动隐藏任务栏';

  @override
  String get samsungDexCommandArrow => '显示命令箭头';

  @override
  String get samsungIncludePhoneDisplay => '将手机屏幕加入显示拓扑';

  @override
  String get samsungMirrorPhoneDisplay => '镜像内置屏幕';

  @override
  String get samsungReviewEnable => '查看警告并启用更改';

  @override
  String get samsungSeconds15 => '15 秒';

  @override
  String get samsungSeconds30 => '30 秒';

  @override
  String get samsungMinute1 => '1 分钟';

  @override
  String get samsungMinutes2 => '2 分钟';

  @override
  String get samsungMinutes5 => '5 分钟';

  @override
  String get samsungMinutes10 => '10 分钟';

  @override
  String get samsungMinutes20 => '20 分钟';

  @override
  String get samsungMinutes30 => '30 分钟';

  @override
  String get samsungHour1 => '1 小时';

  @override
  String get samsungLeft => '左';

  @override
  String get samsungRight => '右';

  @override
  String get samsungAutomatic => '自动';

  @override
  String get samsungGestureNone => '无';

  @override
  String get samsungGestureApps => '应用列表';

  @override
  String get samsungGestureRecents => '最近任务';

  @override
  String get samsungGestureNotifications => '通知';

  @override
  String get samsungGestureQuickSettings => '快捷设置';

  @override
  String get samsungHelp_resolution =>
      '决定 Samsung 桌面绘制应用和窗口的工作区大小。较高分辨率可同时显示更多内容，但文字和按钮会更小，也会增加渲染负载；较低分辨率更易阅读且运行更轻。此设置与 Dextop 分辨率分开保存。';

  @override
  String get samsungHelp_screenZoom =>
      '统一缩放 Samsung 桌面的文字、图标和按钮。提高 DPI 会让界面更大、更易阅读；降低 DPI 可在同一屏幕显示更多内容，但不会改变实际分辨率。';

  @override
  String get samsungHelp_fontScale =>
      '只调整文字大小，不明显改变图标和窗口尺寸。适合在保留工作区的同时提高可读性；过大的值可能导致部分应用文字换行或溢出。';

  @override
  String get samsungHelp_screenTimeout =>
      '设置无操作后 Samsung 桌面保持亮屏的时间。较长时间适合查看文档或视频，但可能增加耗电和发热。';

  @override
  String get samsungHelp_audioOutput =>
      '启用后，媒体和通知声音会输出到 HDMI 显示器或扩展坞；关闭后通常使用手机或当前音频设备。如果外接屏幕没有扬声器，启用后可能听不到声音。';

  @override
  String get samsungHelp_displayOrientation =>
      '将 Samsung 桌面旋转到所选角度，适用于竖放或可旋转显示器。若与屏幕实际方向不符，画面和指针移动方向可能错位。';

  @override
  String get samsungHelp_displayArrangement =>
      '指定手机位于外接屏幕左侧还是右侧，从而决定指针通过哪一侧边缘跨屏。与实际摆放位置一致时，跨屏移动会更自然。';

  @override
  String get samsungHelp_autorunTouchpad =>
      '启用后，连接桌面时会在手机上自动打开 Samsung 触控板，可像笔记本触控板一样操作。它会与 Dextop 输入重复，因此 Dextop 运行时隐藏此项。';

  @override
  String get samsungHelp_touchpadScrollDirection =>
      '反转 Samsung 触控板双指移动与页面滚动的对应方向，可在鼠标滚轮式和手机直接触控式滚动之间选择。';

  @override
  String get samsungHelp_touchKeyboard =>
      '启用后，在桌面模式选择输入框时可显示屏幕键盘，没有物理键盘时很方便。它与 Dextop 键盘控制重叠，因此 Dextop 运行时隐藏此项。';

  @override
  String get samsungHelp_keyboardDex =>
      '启用后，即使连接物理键盘也可使用屏幕键盘，便于输入表情、手写或语音；但会占用工作区并与 Dextop IME 控制冲突。';

  @override
  String get samsungHelp_spenInputMode =>
      '启用后，S Pen 可作为指针使用，包括笔尖未接触屏幕时的悬停位置，便于精确选择。使用压感绘图应用时请确认其笔输入行为。';

  @override
  String get samsungHelp_threeFingerGesture =>
      'Samsung 检测到三指手势时执行所选的应用列表、主页、最近任务或返回等操作。Dextop 也使用三指控制，因此运行时隐藏此项以避免误操作。';

  @override
  String get samsungHelp_fourFingerGesture =>
      '在支持的触控板上用四指执行所选系统操作，可加快导航，但会与 Dextop 多点触控识别冲突，因此 Dextop 运行时隐藏此项。';

  @override
  String get samsungHelp_autoHideTaskbar =>
      '启用后，Samsung 桌面会在不使用时隐藏任务栏，为应用提供更多纵向空间；将指针移到屏幕底边可再次显示。需要始终看到应用切换时请关闭。';

  @override
  String get samsungHelp_dexCommandArrow =>
      '启用后显示用于打开 Samsung 命令控制的箭头，可快速访问 Samsung 操作，但可能与 Dextop 浮层或边缘手势重叠。';

  @override
  String get samsungHelp_includePhoneDisplay =>
      '启用后，手机内置屏幕会加入外接屏幕的同一桌面拓扑，可在两屏之间移动应用和指针。若要将手机保留为独立 Android 控制屏，请关闭。';

  @override
  String get samsungHelp_mirrorPhoneDisplay =>
      '启用后，外接屏幕显示与手机内置屏幕相同的内容，适合演示；它是复制而非扩展工作区，因此两块屏幕不能显示不同应用。';
}
