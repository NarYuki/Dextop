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
  String get updateAvailable => 'Update available';

  @override
  String get updateAvailableTitle => 'A new release is available on GitHub!';

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
  String get uiTwoFingerTap => 'Two-finger tap';

  @override
  String get ui3FingerTap => 'Three-finger tap';

  @override
  String get ui4Divisions => 'Four-way split';

  @override
  String get uiDextopIsReady => 'Dextop is ready';

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
}
