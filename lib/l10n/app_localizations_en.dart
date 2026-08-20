// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get home => 'Home';

  @override
  String get settings => 'Settings';

  @override
  String get resolution => 'Resolution';

  @override
  String get theme => 'Theme';

  @override
  String get system => 'System';

  @override
  String get light => 'Light';

  @override
  String get dark => 'Dark';

  @override
  String get display => 'Display';

  @override
  String get secureDisplay => 'Secure display';

  @override
  String get secureDisplayDescription =>
      'Allow protected content to be displayed';

  @override
  String get mirrorBackend => 'Display mirroring method';

  @override
  String get mirrorBackendAuto => 'Automatic (compatibility)';

  @override
  String get mirrorBackendAutoDescription =>
      'Use the best available method for this device';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get castMode => 'Google Cast method';

  @override
  String get castModeSimple => 'Compatibility';

  @override
  String get castModeSimpleDescription =>
      'Prioritizes connection stability and compatibility with more displays';

  @override
  String get castModeReceiver => 'Low latency';

  @override
  String get castModeReceiverDescription =>
      'Prioritizes responsive controls while streaming the display';

  @override
  String get updateAvailable => 'Update available';

  @override
  String get updateAvailableTitle => 'A new release is available on GitHub!';

  @override
  String get playUpdateAvailableTitle =>
      'An update is available on Google Play';

  @override
  String get playUpdateAvailableDescription =>
      'You can update to the latest version through Google Play.';

  @override
  String get updateNow => 'Update now';

  @override
  String get checkForUpdates => 'Check for updates';

  @override
  String get checkingForUpdates => 'Retrieving update information';

  @override
  String get updateNotChecked =>
      'Update information has not been retrieved yet';

  @override
  String get upToDate => 'You are up to date';

  @override
  String get updateCheckFailed => 'Could not retrieve update information';

  @override
  String get currentVersion => 'Current';

  @override
  String get latestVersion => 'Latest';

  @override
  String get openOnGitHub => 'Open on GitHub';

  @override
  String get close => 'Close';

  @override
  String get deviceInfo => 'Device information';

  @override
  String get desktopMode => 'Desktop mode in use';

  @override
  String get accessibilitySettings => 'Accessibility settings';

  @override
  String get accessibilityDescription => 'Open Dextop service settings';

  @override
  String get appInfo => 'App information';

  @override
  String get licenses => 'Open-source licenses';

  @override
  String get licensesDescription => 'View Flutter and dependency licenses';

  @override
  String get landscape => 'Landscape';

  @override
  String get portrait => 'Portrait';

  @override
  String get stopped => 'Stopped';

  @override
  String get running => 'Running';

  @override
  String get start => 'Start';

  @override
  String get stop => 'Stop';

  @override
  String get customAdd => 'Add custom resolution';

  @override
  String get editResolution => 'Edit resolution';

  @override
  String get add => 'Add';

  @override
  String get save => 'Save';

  @override
  String get deleteResolution => 'Delete this resolution';

  @override
  String get width => 'Width';

  @override
  String get height => 'Height';

  @override
  String get protectedContent => 'Allow protected content to be displayed';

  @override
  String get version => 'Version 1.0.0';

  @override
  String get setupWelcome => 'Welcome to Dextop.';

  @override
  String get setupTagline =>
      'A perfect desktop environment on your smartphone.';

  @override
  String get setupBegin => 'Start';

  @override
  String get setupPhaseTerms => 'Terms of use';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => 'Check your device';

  @override
  String get setupPhaseDemo => 'Try the controls';

  @override
  String get back => 'Back';

  @override
  String get continueLabel => 'Continue';

  @override
  String get done => 'Done';

  @override
  String get incomplete => 'Incomplete';

  @override
  String get setupSystemTitle => 'Uses system-level features';

  @override
  String get setupSystemDescription =>
      'Dextop uses Shizuku and ADB to control behavior such as virtual displays, screen orientation, input, and system UI.';

  @override
  String get setupDisclaimer =>
      'The developer is not responsible for any defects, data loss, or impact on device functionality caused by differences in device or OS implementation, system updates, conflicts with other apps, etc. Please understand the contents before use.';

  @override
  String get setupShizukuTitle => 'Prepare Shizuku';

  @override
  String get setupShizukuDescription =>
      'Dextop uses Shizuku to securely access system functions.';

  @override
  String get setupInstallShizuku => 'Install Shizuku';

  @override
  String get setupConfigureShizuku => 'Set up Shizuku';

  @override
  String get setupShizukuHint =>
      'Open Shizuku, follow the steps under \"Pairing\" in order, then start Shizuku.';

  @override
  String get setupOpenShizuku => 'Open Shizuku';

  @override
  String get setupValidate => 'Finished setup? Verify now';

  @override
  String get setupDextopPermission => 'Dextop permission';

  @override
  String get setupInstallPlay => 'Install from Google Play';

  @override
  String get setupAllowPermission => 'Grant permission';

  @override
  String get setupProviderChoiceTitle => 'Choose a privilege service';

  @override
  String get setupProviderChoiceDescription =>
      'Stellar and Shizuku are both installed. Choose which service Dextop should use.';

  @override
  String get setupUseStellar => 'Stellar (recommended)';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => 'The service is running as root';

  @override
  String get setupRootVerified =>
      'Shizuku was verified as running with root privileges. Grant Dextop permission next.';

  @override
  String get setupRootNotRunning =>
      'Shizuku could not be verified as running with root privileges. Start it with root and try again.';

  @override
  String get setupQuestionOpen => 'Have you opened Shizuku?';

  @override
  String get setupQuestionPair =>
      'Have you completed all the steps listed under \"Pairing\"?';

  @override
  String get setupQuestionStart =>
      'Did you tap \"Start\" in Shizuku and confirm that it says \"Shizuku is running\"?';

  @override
  String get yes => 'Yes';

  @override
  String get no => 'No';

  @override
  String get setupVerified => 'Shizuku setup verified';

  @override
  String get setupVerificationFailed =>
      'Could not verify that Shizuku is configured and running. Complete the steps in Shizuku, then try again.';

  @override
  String get setupPermissionCheckFailed =>
      'Could not check permissions for Shizuku';

  @override
  String get setupDeviceTitle => 'Configuration on this device';

  @override
  String get model => 'Model';

  @override
  String get vendor => 'Vendor';

  @override
  String get desktopUi => 'Desktop UI';

  @override
  String get detectedResolution => 'Automatically detected resolution';

  @override
  String get automaticResolution => 'Automatic';

  @override
  String get loadingLabel => 'Loading…';

  @override
  String get setupDeviceDescription =>
      'This information is used to set the initial resolution and device-specific desktop controls.';

  @override
  String get setupGestureTitle => 'Open the control panel with a gesture';

  @override
  String get setupGestureDescription =>
      'Place three fingers on the three circles below at the same time.';

  @override
  String get setupInstallGitHub => 'Download from GitHub';

  @override
  String get setupGestureReviewed => 'The new gestures have been reviewed.';

  @override
  String get setupGestureReview => 'Review demo again';

  @override
  String get setupGestureStart => 'Start demo';

  @override
  String get setupGestureLandscape =>
      'Landscape\nSwipe right with three fingers from the left edge';

  @override
  String get setupGesturePortrait =>
      'Portrait\nSwipe down with three fingers from the top edge';

  @override
  String get setupGestureNext => 'Next';

  @override
  String get uiTwoFingerTap => 'Two-finger tap';

  @override
  String get ui3FingerTap => 'Three-finger tap';

  @override
  String get ui4Divisions => 'Four-way split';

  @override
  String get uiDextopIsReady => 'Dextop is ready';

  @override
  String get uiDextopStopping => 'Finishing Dextop shutdown';

  @override
  String get uiStopDextop => 'Stop Dextop';

  @override
  String get uiDextopCanBeRestarted => 'Dextop can be resumed';

  @override
  String get uiOpenDextop => 'Open Dextop';

  @override
  String get uiCreateADextopSession => 'Create a Dextop session';

  @override
  String get uiDextopWorkspaceJson => 'Dextop workspace JSON';

  @override
  String get uiPerformanceDisplayOnDextop =>
      'Show performance overlay in Dextop';

  @override
  String get uiDoNotSleepWhileRunningDextop =>
      'Keep screen awake while Dextop is running';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower =>
      'Show FPS, memory, power usage, and battery in real time';

  @override
  String get uiCouldNotLoadJson => 'Could not load JSON';

  @override
  String get uiSecureSettingsPermission => 'Secure Settings permission';

  @override
  String get uiAllowShizukuPermissions => 'Allow Shizuku permissions';

  @override
  String get uiInstallShizuku => 'Install Shizuku';

  @override
  String get uiCheckingShizukuConnection => 'Checking Shizuku connection';

  @override
  String get uiShizukuConnection => 'Shizuku connection';

  @override
  String get uiCopy => ' copy';

  @override
  String get uiOthers => 'Other';

  @override
  String get uiAccessibilityOverlay => 'Accessibility overlay';

  @override
  String get uiAccessibilityServices => 'Accessibility service';

  @override
  String get uiAppNotFound => 'App not found';

  @override
  String get uiAppsAndWorkspace => 'Apps and workspace';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace =>
      'Launch apps and configure workspaces';

  @override
  String get uiRestartTheApp => 'Restart app';

  @override
  String get uiSearchApp => 'Search apps';

  @override
  String get uiAppMemory => 'App memory';

  @override
  String get uiAppLauncher => 'App launcher';

  @override
  String get uiAppLauncherSettings => 'App launcher settings';

  @override
  String get uiAppLaunchFunction => 'App launch function';

  @override
  String get uiImport => 'Import';

  @override
  String get uiExport => 'Export';

  @override
  String get uiCursor => 'Cursor';

  @override
  String get uiCancel => 'Cancel';

  @override
  String get uiQuickSettingsTile => 'Quick Settings tile';

  @override
  String get uiGesture => 'Gesture';

  @override
  String get uiSecondaryIme => 'Secondary IME';

  @override
  String get uiSecureDisplayFoldable =>
      'Secure display, mirroring method, Foldable';

  @override
  String get uiSecurity => 'Security';

  @override
  String get topologyTitle => 'Display arrangement';

  @override
  String get topologyArrangeDisplays => 'Display arrangement';

  @override
  String get topologySummary =>
      'Optimize the layout for your physical monitor arrangement';

  @override
  String get topologyDescription =>
      'Drag displays to rearrange them. Place each screen so pointer movement between displays matches their physical setup.';

  @override
  String get topologyApply => 'Apply';

  @override
  String get topologyApplied => 'Display arrangement applied';

  @override
  String get topologyIdentify => 'Identify';

  @override
  String get topologyRefresh => 'Refresh';

  @override
  String get topologyReset => 'Reset';

  @override
  String get topologyBuiltInScreen => 'Built-in screen';

  @override
  String get displayIncludePhoneSummary =>
      'Enable moving apps and the mouse pointer across displays';

  @override
  String get displayAutoHideTaskbarSummary =>
      'Automatically hide the desktop taskbar when it is not in use';

  @override
  String get displayForceInternal120Hz => 'Run built-in display at 120 Hz';

  @override
  String get displayForceInternal120HzSummary =>
      'Pin a supported built-in screen to 120 Hz while Dextop is running';

  @override
  String get displaySoftwareCursorFallback => 'Use software cursor';

  @override
  String get displaySoftwareCursorFallbackSummary =>
      'Use the previous software cursor when enabled';

  @override
  String get mouseSettingsTitle => 'Mouse';

  @override
  String get mouseSettingsDescription =>
      'Configure virtual mouse input and cursor behavior';

  @override
  String get virtualPointerProfile => 'Input device';

  @override
  String get virtualTouchpad => 'Touchpad';

  @override
  String get virtualTouchpadDescription => 'Register as a virtual touchpad';

  @override
  String get virtualPointerMouse => 'Virtual mouse';

  @override
  String get virtualPointerMouseDescription =>
      'Register as a relative mouse device';

  @override
  String get virtualPointerSoftware => 'Software cursor';

  @override
  String get virtualPointerSoftwareDescription =>
      'Use Dextop\'s original software cursor';

  @override
  String get virtualMouseScrollDirection => 'Scroll direction';

  @override
  String get virtualMouseNaturalScroll => 'Natural (Mac)';

  @override
  String get virtualMouseStandardScroll => 'Standard (Windows)';

  @override
  String get uiConvenience => 'Convenience';

  @override
  String get uiDisplayCategory => 'Display';

  @override
  String get foldableLaptopMode => 'Automatically detect laptop mode';

  @override
  String get foldableLaptopModeDescription =>
      'Automatically enable laptop mode when a supported fold angle is detected';

  @override
  String get topologyNoDisplays => 'No displays are available to arrange';

  @override
  String get topologyUnavailable =>
      'Display topology is unavailable on this device';

  @override
  String get uiTap => 'Tap';

  @override
  String get uiTapPressAndHoldMultiFingerOperation =>
      'Tap, long press, and multi-finger controls';

  @override
  String get uiOpenAppOnDesktop => 'Open app on desktop';

  @override
  String get uiDesktopMode => 'Desktop mode';

  @override
  String get uiDesktopFeatures => 'Desktop features';

  @override
  String get uiTrackpad => 'Trackpad';

  @override
  String get uiDrag => 'Drag';

  @override
  String get uiBattery => 'Battery';

  @override
  String get uiPerformance => 'Performance';

  @override
  String get uiPerformanceCompatibility => 'Performance and compatibility';

  @override
  String get uiItSupportsMultiTouchAndTheThree =>
      'Enables multi-touch and changes the three-finger gesture to a swipe from the left edge.';

  @override
  String get uiMainLarge2Sub => 'Large main + two secondary';

  @override
  String get uiMainLeft => 'Main (left)';

  @override
  String get uiLayout => 'Layout';

  @override
  String get uiWorkSpace => 'Workspace';

  @override
  String get uiCopiedWorkspaceJsonToClipboard =>
      'Copied workspace JSON to clipboard';

  @override
  String get uiImportWorkspace => 'Import workspace';

  @override
  String get uiSaveWorkspace => 'Save workspace';

  @override
  String get uiDeleteWorkspace => 'Delete workspace';

  @override
  String get uiEditWorkspace => 'Edit workspace';

  @override
  String get uiUp => 'Move up';

  @override
  String get uiDividedIntoUpperAndLowerParts => 'Top/bottom split';

  @override
  String get uiUpperHalf => 'Top half';

  @override
  String get uiMoveDown => 'Move down';

  @override
  String get uiLowerHalf => 'Bottom half';

  @override
  String get uiCenter => 'Center';

  @override
  String get uiCompatibilityDiagnosis => 'Compatibility diagnostics';

  @override
  String get uiVirtualDisplayCreation => 'Virtual display creation';

  @override
  String get uiOpenASavedAppConfiguration => 'Open a saved app configuration';

  @override
  String get uiNoSavedWorkspaces => 'No saved workspaces';

  @override
  String get uiInputAndGestures => 'Input and gestures';

  @override
  String get uiInputMode => 'Input mode';

  @override
  String get uiCancelFullScreen => 'Exit full screen';

  @override
  String get uiReDiagnosis => 'Run diagnostics again';

  @override
  String get uiRestart => 'Resume';

  @override
  String get uiAvailableMemory => 'Available memory';

  @override
  String get uiDelete => 'Delete';

  @override
  String get uiYouCanRestoreYourPreviousSession =>
      'You can restore your previous session';

  @override
  String get uiRight => 'Right';

  @override
  String get uiRight13 => 'Right 1/3';

  @override
  String get uiRight23 => 'Right 2/3';

  @override
  String get uiRightClick => 'Right click';

  @override
  String get uiUpperRight => 'Top right';

  @override
  String get uiLowerRight => 'Bottom right';

  @override
  String get uiRightHalf => 'Right half';

  @override
  String get uiName => 'Name';

  @override
  String get uiLargeScreenFoldable => 'Large screen/Foldable';

  @override
  String get uiActualFps => 'Actual FPS';

  @override
  String get uiExperimentalMultiTouch => 'Experimental multi-touch';

  @override
  String get uiExperimentalFeatures => 'Experimental features';

  @override
  String get uiLeft => 'Left';

  @override
  String get uiLeft13 => 'Left 1/3';

  @override
  String get uiLeft13Right23 => 'Left 1/3 + right 2/3';

  @override
  String get uiLeft23 => 'Left 2/3';

  @override
  String get uiLeft23Right13 => 'Left 2/3 + right 1/3';

  @override
  String get uiLeftCenterRight => 'Left / center / right';

  @override
  String get uiUpperLeft => 'Top left';

  @override
  String get uiUpperLeftUpperRightLowerHalf =>
      'Upper left, upper right, lower half';

  @override
  String get uiLowerLeft => 'Bottom left';

  @override
  String get uiLeftHalf => 'Left half';

  @override
  String get uiDividedIntoLeftAndRight => 'Left/right split';

  @override
  String get uiSwipeRightWithThreeFingersFromThe =>
      'Swipe right with three fingers from the left edge';

  @override
  String get uiRecoverySession => 'Session recovery';

  @override
  String get uiEstimatedPowerConsumption => 'Estimated power consumption';

  @override
  String get uiOperationOverlay => 'Control overlay';

  @override
  String get uiShowActionOverlay => 'Show control overlay';

  @override
  String get uiOperationMenu => 'Control menu';

  @override
  String get uiThereIsAnExistingSession => 'There is an existing session';

  @override
  String get uiSaveConfiguration => 'Save configuration';

  @override
  String get uiRestorePrivileges => 'Restore permissions';

  @override
  String get uiChangeToHorizontalHold => 'Switch to landscape';

  @override
  String get uiPreparationIsRequired => 'Setup required';

  @override
  String get uiPhysicalKeyboard => 'Physical keyboard';

  @override
  String get uiPhysicalMouse => 'Physical mouse';

  @override
  String get uiConditionAndDiagnosis => 'Status and diagnostics';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically =>
      'Prevents the screen from turning off automatically';

  @override
  String get uiDestruction => 'Discard';

  @override
  String get uiTerminalAndPermissions => 'Device and permissions';

  @override
  String get uiDeviceInformationDesktopModeAccessibility =>
      'Device information, desktop mode, accessibility';

  @override
  String get uiTerminalResolution => 'Device resolution';

  @override
  String get uiEnd => 'End';

  @override
  String get uiTerminationProcessingCompletedSuccessfully =>
      'Session ended successfully.';

  @override
  String get uiEdit => 'Edit';

  @override
  String get uiChangeToPortraitOrientation => 'Switch to portrait';

  @override
  String get uiVerticalHorizontalSwitching => 'Portrait / landscape';

  @override
  String get uiDisplayOptimization => 'Display optimization';

  @override
  String get uiDisplayRefreshRate => 'Display refresh rate';

  @override
  String get uiReproduction => 'Duplicate';

  @override
  String get uiManageLaunchedAppsAndConfigurations =>
      'Manage apps to launch and their layouts';

  @override
  String get uiCouldNotStart => 'Could not start';

  @override
  String get uiLongPress => 'Long press';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      'Automatically uses measured resolution for open and closed states';

  @override
  String get uiStart => 'Start';

  @override
  String get uiRunningAuto => 'Running (Auto)';

  @override
  String get uiRunningAutoPlus => 'Running (Auto+)';

  @override
  String get uiStartPhoneDextop => 'Start Dextop on this phone';

  @override
  String get uiStopAndroidAuto => 'Stop (Android Auto)';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState =>
      'Automatic switching according to open/closed state';

  @override
  String get uiOpeningQuote => '“';

  @override
  String get uiDeleteWorkspaceQuestionSuffix => '” — delete this workspace?';

  @override
  String get uiAbnormalSessionWarning =>
      'The session ended in an invalid state.\nSome Android system functions may still be disabled.';

  @override
  String get uiChecking => 'Checking';

  @override
  String get uiIdle => 'Idle';

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
  String get diagnosticLog => 'Operation log and device diagnostics';

  @override
  String get diagnosticLogDescription =>
      'View app logs, capability detection, and detailed device specifications';

  @override
  String get loadDiagnosticLog => 'Load diagnostic report';

  @override
  String get copyDiagnosticLog => 'Copy';

  @override
  String get shareDiagnosticLog => 'Share';

  @override
  String get clearDiagnosticLog => 'Clear log';

  @override
  String get deviceReport => 'Device report';

  @override
  String get uiCpuTemperature => 'CPU temperature';

  @override
  String get deviceReportDescription =>
      'Report device and feature compatibility by email';

  @override
  String get deviceReportIntro =>
      'Device details are collected automatically. Select the result for each feature.';

  @override
  String get reportWorking => 'Working';

  @override
  String get reportNotWorking => 'Not working';

  @override
  String get reportUntested => 'Not tested';

  @override
  String get reportOverall => 'Overall status';

  @override
  String get reportNotes => 'Other notes';

  @override
  String get sendDeviceReport => 'Send report by email';

  @override
  String get reportEmailUnavailable => 'Could not open an email app';

  @override
  String get reportTemplateTitle => 'Dextop device report';

  @override
  String get reportNoNotes => 'None';

  @override
  String get reportNoSessionLog =>
      'No completed Dextop session has been recorded yet.';

  @override
  String get reportFeatureStartup => 'App startup and device detection';

  @override
  String get reportFeatureSession => 'Dextop session startup';

  @override
  String get reportFeatureVirtualDisplay => 'VirtualDisplay mirroring';

  @override
  String get reportFeatureWindowManager => 'WindowManager mirroring';

  @override
  String get reportFeatureSurfaceControl => 'SurfaceControl mirroring';

  @override
  String get reportFeatureLandscape => 'Landscape mode';

  @override
  String get reportFeaturePortrait => 'Portrait mode';

  @override
  String get reportFeatureSecureDisplay => 'Secure display';

  @override
  String get reportFeatureLauncher => 'App launcher and freeform windows';

  @override
  String get reportFeatureWorkspace => 'Workspace save and restore';

  @override
  String get reportFeatureCursor => 'Cursor and touchpad input';

  @override
  String get reportFeatureDirectTouch => 'Direct-touch input';

  @override
  String get reportFeatureMultiTouch => 'Multi-touch scrolling and pinch zoom';

  @override
  String get reportFeatureGesture => 'Three-finger overlay gesture';

  @override
  String get reportFeatureMouse => 'Physical mouse';

  @override
  String get reportFeatureKeyboard => 'Physical keyboard';

  @override
  String get reportFeatureRouting =>
      'Physical mouse and keyboard display routing';

  @override
  String get reportFeatureFoldable => 'Foldable automatic resolution';

  @override
  String get reportFeaturePerformance => 'Performance overlay';

  @override
  String get reportFeatureCleanup => 'Session cleanup and Android restoration';

  @override
  String get samsungExperimentalTitle =>
      'Experimental Samsung desktop settings';

  @override
  String get samsungUnavailable => 'Available on Samsung devices only';

  @override
  String get samsungExperimentalDescription =>
      'Change settings hidden by the native DeX settings screen';

  @override
  String get samsungSettingsTitle => 'Samsung desktop settings';

  @override
  String get samsungSettingsSummary => 'Display, input and taskbar settings';

  @override
  String get samsungRestoreSuccess => 'Samsung settings restored';

  @override
  String get samsungConfirmTitle => 'Confirm settings access';

  @override
  String get samsungPermanentWarning =>
      'These settings may permanently affect Dextop and the desktop environment used normally until they are reset.';

  @override
  String get samsungAcceptEnable => 'Accept and enable';

  @override
  String get samsungAboutSetting => 'About this setting';

  @override
  String get samsungRestoreEnvironment => 'Restore environment';

  @override
  String get samsungSettingsIntro =>
      'Directly changes DeX values hidden when Samsung Settings reports no external display. Changes affect Samsung DeX and corresponding Dextop features.';

  @override
  String get samsungResolution => 'External resolution';

  @override
  String get samsungScreenZoom => 'Screen zoom (DPI)';

  @override
  String get samsungFontScale => 'Font size';

  @override
  String get samsungScreenTimeout => 'Screen timeout';

  @override
  String get samsungAudioOutput => 'Play audio on external display';

  @override
  String get samsungDisplayOrientation => 'External display rotation';

  @override
  String get samsungDisplayArrangement => 'Display arrangement';

  @override
  String get samsungSectionInput => 'Input';

  @override
  String get samsungSectionDesktop => 'Desktop';

  @override
  String get samsungInputLockedWhileRunning =>
      'Conflicting Samsung input settings cannot be changed while Dextop is running.';

  @override
  String get samsungAutorunTouchpad => 'Start touchpad automatically';

  @override
  String get samsungTouchpadScrollDirection => 'Reverse scrolling direction';

  @override
  String get samsungTouchKeyboard => 'Show on-screen keyboard when connected';

  @override
  String get samsungKeyboardDex => 'Show keyboard with physical keyboard';

  @override
  String get samsungSpenInputMode => 'Use S Pen as a mouse';

  @override
  String get samsungThreeFingerGesture => 'Three-finger gesture';

  @override
  String get samsungFourFingerGesture => 'Four-finger gesture';

  @override
  String get samsungAutoHideTaskbar => 'Automatically hide taskbar';

  @override
  String get samsungDexCommandArrow => 'Show command arrow';

  @override
  String get samsungIncludePhoneDisplay => 'Include Dextop in display topology';

  @override
  String get samsungMirrorPhoneDisplay => 'Mirror built-in display';

  @override
  String get samsungReviewEnable => 'Review warning and enable changes';

  @override
  String get samsungSeconds15 => '15 seconds';

  @override
  String get samsungSeconds30 => '30 seconds';

  @override
  String get samsungMinute1 => '1 minute';

  @override
  String get samsungMinutes2 => '2 minutes';

  @override
  String get samsungMinutes5 => '5 minutes';

  @override
  String get samsungMinutes10 => '10 minutes';

  @override
  String get samsungMinutes20 => '20 minutes';

  @override
  String get samsungMinutes30 => '30 minutes';

  @override
  String get samsungHour1 => '1 hour';

  @override
  String get samsungLeft => 'Left';

  @override
  String get samsungRight => 'Right';

  @override
  String get samsungAutomatic => 'Automatic';

  @override
  String get samsungGestureNone => 'None';

  @override
  String get samsungGestureApps => 'Apps';

  @override
  String get samsungGestureRecents => 'Recents';

  @override
  String get samsungGestureNotifications => 'Notifications';

  @override
  String get samsungGestureQuickSettings => 'Quick settings';

  @override
  String get samsungHelp_resolution =>
      'Sets the workspace Samsung Desktop uses to draw apps and windows. Higher resolutions fit more content but make controls smaller and increase rendering load. Lower resolutions favor readability and performance. This is stored separately from Dextop\'s resolution.';

  @override
  String get samsungHelp_screenZoom =>
      'Scales text, icons, and controls across Samsung Desktop. A higher DPI makes everything larger and easier to read; a lower DPI fits more content on screen. It does not change the actual resolution.';

  @override
  String get samsungHelp_fontScale =>
      'Changes text size without substantially resizing icons or windows. Use it to improve readability while preserving the workspace. Very large values can cause text to wrap or overflow in some apps.';

  @override
  String get samsungHelp_screenTimeout =>
      'Controls how long Samsung Desktop stays lit without input. A longer timeout is useful for documents or video, but may increase power use and heat.';

  @override
  String get samsungHelp_audioOutput =>
      'When enabled, media and notification audio is routed to the HDMI display or dock. When disabled, the phone or currently selected audio device is normally used. Enabling this with a display that has no speakers may result in no audible sound.';

  @override
  String get samsungHelp_displayOrientation =>
      'Rotates Samsung Desktop to the selected angle. Use this for a portrait-mounted or rotatable monitor. A value that does not match the physical screen can make the picture and pointer direction feel misaligned.';

  @override
  String get samsungHelp_displayArrangement =>
      'Tells Samsung whether the phone is positioned to the left or right of the external display. This changes which screen edge the pointer crosses, making movement between screens match the physical setup.';

  @override
  String get samsungHelp_autorunTouchpad =>
      'When enabled, Samsung\'s touchpad opens automatically on the phone after connecting, allowing the phone to work like a laptop touchpad. It duplicates Dextop input handling, so this option is hidden while Dextop is running.';

  @override
  String get samsungHelp_touchpadScrollDirection =>
      'Reverses the relationship between two-finger movement and page movement on Samsung\'s touchpad. Use it to choose between mouse-wheel-style and direct-touch-style scrolling.';

  @override
  String get samsungHelp_touchKeyboard =>
      'When enabled, the on-screen keyboard can appear after selecting a text field in desktop mode. It is useful without a physical keyboard, but overlaps Dextop keyboard handling, so it is hidden while Dextop is running.';

  @override
  String get samsungHelp_keyboardDex =>
      'When enabled, the on-screen keyboard remains available even with a physical keyboard connected. This helps with emoji, handwriting, and voice input, but reduces workspace and conflicts with Dextop IME handling.';

  @override
  String get samsungHelp_spenInputMode =>
      'When enabled, S Pen acts as a pointer, including hover before touching the screen. This improves precise desktop selection. Check drawing apps if you rely on their normal pressure-sensitive pen behavior.';

  @override
  String get samsungHelp_threeFingerGesture =>
      'Runs the selected action—such as Apps, Home, Recents, or Back—when Samsung detects a three-finger gesture. Dextop also uses three fingers for its controls, so this option is hidden while Dextop is running.';

  @override
  String get samsungHelp_fourFingerGesture =>
      'Runs the selected system action from a supported four-finger touchpad gesture. It can speed up navigation, but conflicts with Dextop multi-touch detection and is therefore hidden during a Dextop session.';

  @override
  String get samsungHelp_autoHideTaskbar =>
      'When enabled, Samsung Desktop hides the taskbar while it is not in use, giving apps more vertical space. Move the pointer to the bottom edge to reveal it. Disable this if you prefer app switching to remain visible.';

  @override
  String get samsungHelp_dexCommandArrow =>
      'When enabled, Samsung Desktop shows an arrow for opening Samsung command controls. It provides faster access to Samsung actions, but may overlap Dextop overlays or edge gestures.';

  @override
  String get samsungHelp_includePhoneDisplay =>
      'When enabled, the built-in phone screen becomes part of the same desktop topology as the external display, allowing apps and the pointer to move between them. Leave it disabled to keep the phone as an independent Android control screen.';

  @override
  String get samsungHelp_mirrorPhoneDisplay =>
      'When enabled, the external desktop shows the same content as the built-in phone screen. This is useful for demonstrations, but it duplicates rather than expands the workspace, so the two screens cannot show independent apps.';

  @override
  String get keyboardThemesTitle => 'Keyboard themes';

  @override
  String get keyboardThemesChoose => 'Choose a theme';

  @override
  String get keyboardThemesNew => 'New keyboard theme';

  @override
  String get keyboardThemesName => 'Theme name';

  @override
  String get keyboardThemesCreate => 'Create';

  @override
  String get keyboardThemesEdit => 'Edit';

  @override
  String get keyboardThemesDone => 'Done';

  @override
  String get keyboardThemesDeleteTitle => 'Delete theme?';

  @override
  String keyboardThemesDeleteBody(String name) {
    return 'Delete “$name”? This cannot be undone.';
  }

  @override
  String get keyboardThemesBuiltIn => 'Built-in themes cannot be deleted';

  @override
  String get keyboardThemesSelectFirst =>
      'Select this theme first to preview it.';

  @override
  String get keyboardThemesStartFirst =>
      'Start Dextop first to show the real laptop overlay.';

  @override
  String get keyboardThemesAdd => 'Add custom theme';

  @override
  String get keyboardThemesPreview => 'Preview keyboard demo';

  @override
  String get keyboardThemesEditTip => 'Edit';

  @override
  String get keyboardThemesImage => 'Choose background image';

  @override
  String get keyboardThemesExport => 'Export theme';

  @override
  String get keyboardThemesExportDialog => 'Export keyboard theme';

  @override
  String get keyboardThemesOpacity => 'Opacity';

  @override
  String get keyboardThemesBlur => 'Blur';

  @override
  String get keyboardThemesRadius => 'Corner radius';

  @override
  String get keyboardThemesBackground => 'Background';

  @override
  String get keyboardThemesKey => 'Key';

  @override
  String get keyboardThemesBorder => 'Border';

  @override
  String get keyboardThemesText => 'Text';

  @override
  String get keyboardThemesTrackpad => 'Trackpad';

  @override
  String get keyboardThemesKeyOpacity => 'Key opacity';

  @override
  String get keyboardThemesTrackpadOpacity => 'Trackpad opacity';

  @override
  String get keyboardThemesShowTrackpadLabel => 'Show TRACKPAD label';

  @override
  String get keyboardThemesDescription =>
      'Laptop keyboard themes and custom appearance';

  @override
  String get autoSettingsTitle => 'Auto';

  @override
  String get autoSettingsDescription =>
      'Auto display and phone-side mirroring options';

  @override
  String get autoMatchPhoneOrientation =>
      'Match phone mirror orientation to Auto';

  @override
  String get autoMatchPhoneOrientationDescription =>
      'When Dextop is shown in Auto from the phone-side mirror, adjust its orientation to the head-unit aspect ratio.';
}
