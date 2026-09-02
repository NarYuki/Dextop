// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get home => 'Главная';

  @override
  String get settings => 'Настройки';

  @override
  String get resolution => 'Разрешение';

  @override
  String get displayMagnification => 'Масштаб отображения';

  @override
  String get displayMagnificationDescription =>
      'Увеличивает весь рабочий стол для удобного просмотра. При большем масштабе рабочая область уменьшается.';

  @override
  String get theme => 'Тема';

  @override
  String get system => 'Системная';

  @override
  String get light => 'Светлая';

  @override
  String get dark => 'Тёмная';

  @override
  String get display => 'Экран';

  @override
  String get secureDisplay => 'Защищённый экран';

  @override
  String get secureDisplayDescription =>
      'Разрешить показ защищённого содержимого';

  @override
  String get mirrorBackend => 'Способ зеркалирования дисплея';

  @override
  String get mirrorBackendAuto => 'Автоматически (совместимость)';

  @override
  String get mirrorBackendAutoDescription =>
      'Использовать лучший доступный способ для устройства';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get castMode => 'Способ Google Cast';

  @override
  String get castModeSimple => 'Совместимость';

  @override
  String get castModeSimpleDescription =>
      'Приоритет стабильности подключения и совместимости с дисплеями';

  @override
  String get castModeReceiver => 'Низкая задержка';

  @override
  String get castModeReceiverDescription =>
      'Приоритет быстрой реакции управления при передаче экрана';

  @override
  String get updateAvailable => 'Доступно обновление';

  @override
  String get updateAvailableTitle => 'На GitHub опубликована новая версия!';

  @override
  String get playUpdateAvailableTitle => 'В Google Play доступно обновление';

  @override
  String get playUpdateAvailableDescription =>
      'Вы можете обновить приложение до последней версии через Google Play.';

  @override
  String get updateNow => 'Обновить сейчас';

  @override
  String get checkForUpdates => 'Проверить обновления';

  @override
  String get checkingForUpdates => 'Получение сведений об обновлении';

  @override
  String get updateNotChecked => 'Сведения об обновлении ещё не получены';

  @override
  String get upToDate => 'Установлена последняя версия';

  @override
  String get updateCheckFailed => 'Не удалось получить сведения об обновлении';

  @override
  String get currentVersion => 'Текущая версия';

  @override
  String get latestVersion => 'Последняя версия';

  @override
  String get openOnGitHub => 'Открыть на GitHub';

  @override
  String get close => 'Закрыть';

  @override
  String get deviceInfo => 'Информация об устройстве';

  @override
  String get desktopMode => 'Режим рабочего стола';

  @override
  String get accessibilitySettings => 'Специальные возможности';

  @override
  String get accessibilityDescription => 'Открыть настройки службы Dextop';

  @override
  String get appInfo => 'О приложении';

  @override
  String get appInfoEmbeddedBinder => 'Встроенный Binder';

  @override
  String get appInfoEmbeddedBinderIncluded => 'Включён в эту сборку';

  @override
  String get appInfoEmbeddedBinderNotIncluded => 'Не включён в эту сборку';

  @override
  String get appInfoEmbeddedBinderProvider => 'Состояние использования';

  @override
  String get appInfoEmbeddedBinderSelected => 'Используется';

  @override
  String get appInfoEmbeddedBinderStandby => 'Используется внешний поставщик';

  @override
  String get appInfoEmbeddedBinderConnection => 'Подключение Binder';

  @override
  String get appInfoEmbeddedBinderPermission => 'Разрешение Dextop';

  @override
  String get appInfoEmbeddedBinderNotifications => 'Разрешение на уведомления';

  @override
  String get appInfoStatusConnected => 'Подключено';

  @override
  String get appInfoStatusDisconnected => 'Не подключено';

  @override
  String get appInfoStatusGranted => 'Разрешено';

  @override
  String get appInfoStatusNotGranted => 'Не разрешено';

  @override
  String get licenses => 'Лицензии открытого ПО';

  @override
  String get licensesDescription => 'Лицензии Flutter и используемых библиотек';

  @override
  String get landscape => 'Альбомная';

  @override
  String get portrait => 'Портретная';

  @override
  String get stopped => 'Остановлено';

  @override
  String get running => 'Работает';

  @override
  String get start => 'Запустить';

  @override
  String get stop => 'Остановить';

  @override
  String get customAdd => 'Добавить разрешение';

  @override
  String get editResolution => 'Изменить разрешение';

  @override
  String get add => 'Добавить';

  @override
  String get save => 'Сохранить';

  @override
  String get deleteResolution => 'Удалить разрешение';

  @override
  String get width => 'Ширина';

  @override
  String get height => 'Высота';

  @override
  String get protectedContent => 'Разрешить показ защищённого содержимого';

  @override
  String get version => 'Версия 1.0.0';

  @override
  String get setupWelcome => 'Добро пожаловать в Dextop.';

  @override
  String get setupTagline =>
      'Идеальная среда рабочего стола на вашем смартфоне.';

  @override
  String get setupBegin => 'Начать';

  @override
  String get setupPhaseTerms => 'Перед началом работы';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => 'Проверка устройства';

  @override
  String get setupPhaseDemo => 'Знакомство с управлением';

  @override
  String get back => 'Назад';

  @override
  String get review => 'Повторить';

  @override
  String get reviewThreeFingerGesture => 'Повторить жест тремя пальцами';

  @override
  String get continueLabel => 'Продолжить';

  @override
  String get done => 'Готово';

  @override
  String get incomplete => 'Не завершено';

  @override
  String get setupSystemTitle => 'Использование системных функций';

  @override
  String get setupSystemDescription =>
      'Dextop использует Shizuku и ADB для управления виртуальными дисплеями, ориентацией экрана, вводом и системным интерфейсом. API AccessibilityService используется для отображения и управления оверлеем рабочего стола, передачи запрошенных пользователем сенсорных, указательных и навигационных действий и восстановления интерфейса устройства после завершения сеанса. Данные специальных возможностей не собираются, не сохраняются и не передаются.';

  @override
  String get setupDisclaimer =>
      'Разработчик не несет ответственности за любые дефекты, потерю данных или влияние на функциональность устройства, вызванное различиями в реализации устройства или ОС, обновлениями системы, конфликтами с другими приложениями и т. д. Перед использованием ознакомьтесь с содержанием.';

  @override
  String get setupShizukuTitle => 'Подготовка Shizuku';

  @override
  String get setupShizukuDescription =>
      'Dextop использует Shizuku для безопасного доступа к функциям системы.';

  @override
  String get setupInstallShizuku => 'Установить Shizuku';

  @override
  String get setupConfigureShizuku => 'Настроить Shizuku';

  @override
  String get setupShizukuHint =>
      'Откройте Shizuku, установите его в порядке, указанном в разделе «Сопряжение», и запустите Shizuku.';

  @override
  String get setupOpenShizuku => 'Открыть Shizuku';

  @override
  String get setupValidate => 'Настройка завершена? Проверить';

  @override
  String get setupDextopPermission => 'Разрешения для Dextop';

  @override
  String get setupInstallPlay => 'Установить из Google Play';

  @override
  String get setupAllowPermission => 'Предоставить разрешение';

  @override
  String get setupProviderChoiceTitle => 'Выберите привилегированную службу';

  @override
  String get setupProviderChoiceDescription =>
      'Установлено несколько служб, совместимых с Shizuku. Выберите службу для Dextop.';

  @override
  String get setupUseStellar => 'Stellar (рекомендуется)';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => 'Служба запущена с правами root';

  @override
  String get setupRootVerified =>
      'Подтверждено, что Shizuku работает с правами root. Теперь предоставьте разрешение Dextop.';

  @override
  String get setupRootNotRunning =>
      'Не удалось подтвердить, что Shizuku работает с правами root. Запустите его с правами root и повторите попытку.';

  @override
  String get setupQuestionOpen => 'Вы открыли Shizuku?';

  @override
  String get setupQuestionPair =>
      'Вы выполнили все шаги, перечисленные в разделе «Сопряжение»?';

  @override
  String get setupQuestionStart =>
      'Вы нажали «Запустить» в Shizuku и убедились, что отображается сообщение «Shizuku работает»?';

  @override
  String get yes => 'Да';

  @override
  String get no => 'Нет';

  @override
  String get setupVerified => 'Настройка Shizuku проверена';

  @override
  String get setupAccessVerified => 'Настройка разрешения доступа проверена';

  @override
  String get setupVerificationFailed =>
      'Невозможно подтвердить конфигурацию или запуск Shizuku. Пожалуйста, выполните действия в Shizuku, а затем проверьте еще раз.';

  @override
  String get setupPermissionCheckFailed =>
      'Не удалось проверить разрешения для Shizuku.';

  @override
  String get setupDeviceTitle => 'Конфигурация на этом устройстве';

  @override
  String get model => 'Модель';

  @override
  String get vendor => 'Производитель';

  @override
  String get desktopUi => 'Интерфейс рабочего стола';

  @override
  String get detectedResolution => 'Автоматически определённое разрешение';

  @override
  String get automaticResolution => 'Автоматически';

  @override
  String get loadingLabel => 'Загрузка…';

  @override
  String get setupDeviceDescription =>
      'Эта информация используется для установки исходного разрешения и элементов управления рабочим столом для конкретного устройства.';

  @override
  String get setupGestureTitle => 'Вызов панели управления жестами';

  @override
  String get setupGestureDescription =>
      'Поместите три пальца одновременно на три круга внизу.';

  @override
  String get setupInstallGitHub => 'Скачать с GitHub';

  @override
  String get setupGestureReviewed => 'Проверка новых жестов завершена.';

  @override
  String get setupGestureReview => 'Повторить демонстрацию';

  @override
  String get setupGestureStart => 'Начать демонстрацию';

  @override
  String get setupGestureLandscape =>
      'Альбомная ориентация\nСмахните тремя пальцами слева направо';

  @override
  String get setupGesturePortrait =>
      'Портретная ориентация\nСмахните тремя пальцами сверху вниз';

  @override
  String get setupGestureNext => 'Далее';

  @override
  String get uiTwoFingerTap => 'Касание двумя пальцами';

  @override
  String get ui3FingerTap => 'Касание тремя пальцами';

  @override
  String get ui4Divisions => 'Разделение на четыре области';

  @override
  String get uiDextopIsReady => 'Dextop готов.';

  @override
  String get uiDextopStopping => 'Завершение работы Dextop';

  @override
  String get uiStopDextop => 'Остановить Dextop';

  @override
  String get uiDextopCanBeRestarted => 'Сеанс Dextop можно возобновить';

  @override
  String get uiOpenDextop => 'Открыть Dextop';

  @override
  String get uiCreateADextopSession => 'Создать сеанс Dextop';

  @override
  String get uiDextopWorkspaceJson => 'Рабочая область Dextop в формате JSON';

  @override
  String get uiPerformanceDisplayOnDextop =>
      'Показывать панель производительности в Dextop';

  @override
  String get uiDoNotSleepWhileRunningDextop =>
      'Не выключать экран во время работы Dextop';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower =>
      'Отображение в реальном времени FPS, памяти, энергопотребления и заряда батареи.';

  @override
  String get uiCouldNotLoadJson => 'Не удалось загрузить JSON';

  @override
  String get uiSecureSettingsPermission =>
      'Разрешение на настройки безопасности';

  @override
  String get uiAllowShizukuPermissions => 'Предоставить разрешение Shizuku';

  @override
  String get uiInstallShizuku => 'Установить Shizuku';

  @override
  String get uiCheckingShizukuConnection => 'Проверка подключения к Shizuku';

  @override
  String get uiShizukuConnection => 'Подключение к Shizuku';

  @override
  String get uiCopy => ' — копия';

  @override
  String get uiOthers => 'Другое';

  @override
  String get uiAccessibilityOverlay => 'Оверлей специальных возможностей';

  @override
  String get uiAccessibilityServices => 'Служба специальных возможностей';

  @override
  String get uiAppNotFound => 'Приложение не найдено';

  @override
  String get uiAppsAndWorkspace => 'Приложения и рабочее пространство';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace =>
      'Запуск приложений и настройка рабочих областей';

  @override
  String get uiRestartTheApp => 'Перезапустить приложение';

  @override
  String get uiSearchApp => 'Поиск приложений';

  @override
  String get uiAppMemory => 'Память приложения';

  @override
  String get uiAppLauncher => 'Панель приложений';

  @override
  String get uiAppLauncherSettings => 'Настройки запуска приложений';

  @override
  String get uiAppLaunchFunction => 'Функция запуска приложения';

  @override
  String get uiImport => 'Импортировать';

  @override
  String get uiExport => 'Экспортировать';

  @override
  String get uiCursor => 'Курсор';

  @override
  String get uiCancel => 'Отмена';

  @override
  String get uiQuickSettingsTile => 'Плитка быстрых настроек';

  @override
  String get uiGesture => 'Жест';

  @override
  String get uiSecondaryIme => 'Вторичный IME';

  @override
  String get uiSecureDisplayFoldable =>
      'Защищённый экран, способ зеркалирования, складные устройства';

  @override
  String get uiSecurity => 'Безопасность';

  @override
  String get topologyTitle => 'Расположение дисплеев';

  @override
  String get topologyArrangeDisplays => 'Расположение дисплеев';

  @override
  String get topologySummary =>
      'Можно оптимизировать схему под физическое расположение мониторов';

  @override
  String get topologyDescription =>
      'Перетаскивайте дисплеи, чтобы изменить их расположение. Разместите их так, чтобы движение указателя соответствовало физической установке.';

  @override
  String get topologyApply => 'Применить';

  @override
  String get topologyApplied => 'Расположение дисплеев применено';

  @override
  String get topologyIdentify => 'Определить';

  @override
  String get topologyRefresh => 'Обновить';

  @override
  String get topologyReset => 'Сбросить';

  @override
  String get topologyBuiltInScreen => 'Встроенный экран';

  @override
  String get displayIncludePhoneSummary =>
      'Позволяет перемещать приложения и указатель мыши между дисплеями';

  @override
  String get displayAutoHideTaskbarSummary =>
      'Автоматически скрывает панель задач рабочего стола, когда она не используется';

  @override
  String get displayForceInternal120Hz => 'Частота встроенного экрана 120 Гц';

  @override
  String get displayForceInternal120HzSummary =>
      'Фиксирует частоту поддерживаемого встроенного экрана на 120 Гц во время работы Dextop';

  @override
  String get displaySoftwareCursorFallback => 'Использовать программный курсор';

  @override
  String get displaySoftwareCursorFallbackSummary =>
      'При включении используется прежний программный курсор';

  @override
  String get mouseSettingsTitle => 'Мышь';

  @override
  String get mouseSettingsDescription =>
      'Настройка виртуального ввода и курсора';

  @override
  String get virtualPointerProfile => 'Устройство ввода';

  @override
  String get virtualTouchpad => 'Сенсорная панель';

  @override
  String get virtualTouchpadDescription =>
      'Зарегистрировать как виртуальную сенсорную панель';

  @override
  String get virtualPointerMouse => 'Виртуальная мышь';

  @override
  String get virtualPointerMouseDescription =>
      'Зарегистрировать как относительную мышь';

  @override
  String get virtualPointerSoftware => 'Программный курсор';

  @override
  String get virtualPointerSoftwareDescription =>
      'Использовать исходный курсор Dextop';

  @override
  String get virtualMouseScrollDirection => 'Направление прокрутки';

  @override
  String get virtualMouseNaturalScroll => 'Естественное (Mac)';

  @override
  String get virtualMouseStandardScroll => 'Стандартное (Windows)';

  @override
  String get uiConvenience => 'Удобные функции';

  @override
  String get uiDisplayCategory => 'Дисплей';

  @override
  String get foldableLaptopMode => 'Автоопределение режима ноутбука';

  @override
  String get foldableLaptopModeDescription =>
      'Автоматически включать режим ноутбука при поддерживаемом угле сгиба';

  @override
  String get topologyNoDisplays => 'Нет дисплеев для размещения';

  @override
  String get topologyUnavailable =>
      'Топология дисплеев недоступна на этом устройстве';

  @override
  String get displaySettingsTitle => 'Настройки дисплея';

  @override
  String get displaySettingsSummary =>
      'Управление расположением и разрешением дисплеев';

  @override
  String get displayResolutionListTitle => 'Разрешение дисплея';

  @override
  String get displayResolutionListSummary =>
      'Просмотр подключённых дисплеев и поддерживаемых режимов';

  @override
  String get displayDetailsTitle => 'Сведения о дисплее';

  @override
  String get displayNoLongerAvailable => 'Выбранный дисплей больше недоступен';

  @override
  String get displaySupportedResolutions => 'Поддерживаемые разрешения';

  @override
  String get displayDextopResolutionTitle => 'Разрешение сеанса Dextop';

  @override
  String get displayDextopResolutionSummary =>
      'Изменяется в разделе разрешения на главном экране';

  @override
  String get displayCurrentMode => 'Используется сейчас';

  @override
  String get displayModeApplied => 'Разрешение дисплея изменено';

  @override
  String get uiTap => 'Касание';

  @override
  String get uiTapPressAndHoldMultiFingerOperation =>
      'Касание, долгое нажатие и управление несколькими пальцами';

  @override
  String get uiOpenAppOnDesktop => 'Открыть приложение на рабочем столе';

  @override
  String get uiDesktopMode => 'Режим рабочего стола';

  @override
  String get uiDesktopFeatures => 'Функции рабочего стола';

  @override
  String get uiTrackpad => 'Трекпад';

  @override
  String get uiDrag => 'Перетаскивание';

  @override
  String get uiBattery => 'Батарея';

  @override
  String get uiPerformance => 'Производительность';

  @override
  String get uiPerformanceCompatibility => 'Производительность и совместимость';

  @override
  String get uiItSupportsMultiTouchAndTheThree =>
      'Включает мультитач и заменяет жест тремя пальцами на свайп от левого края экрана.';

  @override
  String get uiMainLarge2Sub => 'Большое основное окно + два дополнительных';

  @override
  String get uiMainLeft => 'Главный (слева)';

  @override
  String get uiLayout => 'Расположение';

  @override
  String get uiWorkSpace => 'Рабочая область';

  @override
  String get uiCopiedWorkspaceJsonToClipboard =>
      'JSON рабочей области скопирован в буфер обмена.';

  @override
  String get uiImportWorkspace => 'Импорт рабочей области';

  @override
  String get uiSaveWorkspace => 'Сохранить рабочее пространство';

  @override
  String get uiDeleteWorkspace => 'Удалить рабочую область';

  @override
  String get uiEditWorkspace => 'Изменить рабочую область';

  @override
  String get uiUp => 'Переместить вверх';

  @override
  String get uiDividedIntoUpperAndLowerParts => 'Разделение сверху и снизу';

  @override
  String get uiUpperHalf => 'Верхняя половина';

  @override
  String get uiMoveDown => 'Переместить вниз';

  @override
  String get uiLowerHalf => 'Нижняя половина';

  @override
  String get uiCenter => 'По центру';

  @override
  String get uiCompatibilityDiagnosis => 'Диагностика совместимости';

  @override
  String get uiVirtualDisplayCreation => 'Создание виртуального дисплея';

  @override
  String get uiOpenASavedAppConfiguration =>
      'Откройте сохраненную конфигурацию приложения';

  @override
  String get uiNoSavedWorkspaces => 'Нет сохраненных рабочих пространств';

  @override
  String get uiInputAndGestures => 'Ввод и жесты';

  @override
  String get uiInputMode => 'Режим ввода';

  @override
  String get uiCancelFullScreen => 'Выйти из полноэкранного режима';

  @override
  String get uiReDiagnosis => 'Повторить диагностику';

  @override
  String get uiRestart => 'Возобновить';

  @override
  String get uiAvailableMemory => 'Доступная память';

  @override
  String get uiDelete => 'Удалить';

  @override
  String get uiYouCanRestoreYourPreviousSession =>
      'Вы можете восстановить предыдущий сеанс';

  @override
  String get uiRight => 'Справа';

  @override
  String get uiRight13 => 'Правая 1/3';

  @override
  String get uiRight23 => 'Справа 2/3';

  @override
  String get uiRightClick => 'Щелчок правой кнопкой';

  @override
  String get uiUpperRight => 'Справа сверху';

  @override
  String get uiLowerRight => 'Справа снизу';

  @override
  String get uiRightHalf => 'Правая половина';

  @override
  String get uiName => 'Название';

  @override
  String get uiLargeScreenFoldable => 'Большой экран/складной';

  @override
  String get uiActualFps => 'Фактический FPS';

  @override
  String get uiExperimentalMultiTouch => 'Экспериментальный мультитач';

  @override
  String get uiExperimentalFeatures => 'Экспериментальные функции';

  @override
  String get uiLeft => 'Слева';

  @override
  String get uiLeft13 => 'Слева 1/3';

  @override
  String get uiLeft13Right23 => 'Слева 1/3 + справа 2/3';

  @override
  String get uiLeft23 => 'Слева 2/3';

  @override
  String get uiLeft23Right13 => 'Слева 2/3 + справа 1/3';

  @override
  String get uiLeftCenterRight => 'Слева / по центру / справа';

  @override
  String get uiUpperLeft => 'Слева сверху';

  @override
  String get uiUpperLeftUpperRightLowerHalf =>
      'Верхний левый, верхний правый, нижняя половина';

  @override
  String get uiLowerLeft => 'Слева снизу';

  @override
  String get uiLeftHalf => 'Левая половина';

  @override
  String get uiDividedIntoLeftAndRight => 'Разделение слева и справа';

  @override
  String get uiSwipeRightWithThreeFingersFromThe =>
      'Проведите тремя пальцами вправо от левого края';

  @override
  String get uiRecoverySession => 'Восстановление сеанса';

  @override
  String get uiEstimatedPowerConsumption => 'Расчетное энергопотребление';

  @override
  String get uiOperationOverlay => 'Панель управления';

  @override
  String get uiShowActionOverlay => 'Показать панель управления';

  @override
  String get uiOperationMenu => 'Меню управления';

  @override
  String get uiThereIsAnExistingSession => 'Обнаружен существующий сеанс';

  @override
  String get uiSaveConfiguration => 'Сохранить конфигурацию';

  @override
  String get uiRestorePrivileges => 'Восстановить разрешения';

  @override
  String get uiChangeToHorizontalHold => 'Переключить в альбомную ориентацию';

  @override
  String get uiPreparationIsRequired => 'Требуется настройка';

  @override
  String get uiPhysicalKeyboard => 'Физическая клавиатура';

  @override
  String get uiPhysicalMouse => 'Физическая мышь';

  @override
  String get uiConditionAndDiagnosis => 'Состояние и диагностика';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically =>
      'Предотвращает автоматическое выключение экрана';

  @override
  String get uiDestruction => 'Удалить';

  @override
  String get uiTerminalAndPermissions => 'Устройство и разрешения';

  @override
  String get uiDeviceInformationDesktopModeAccessibility =>
      'Информация об устройстве, режим рабочего стола, специальные возможности';

  @override
  String get uiTerminalResolution => 'Разрешение устройства';

  @override
  String get uiEnd => 'Завершить';

  @override
  String get uiTerminationProcessingCompletedSuccessfully =>
      'Сеанс успешно завершён.';

  @override
  String get uiEdit => 'Изменить';

  @override
  String get uiChangeToPortraitOrientation =>
      'Переключиться на портретную ориентацию';

  @override
  String get uiVerticalHorizontalSwitching =>
      'Портретная / альбомная ориентация';

  @override
  String get uiDisplayOptimization => 'Оптимизация дисплея';

  @override
  String get uiDisplayRefreshRate => 'Частота обновления дисплея';

  @override
  String get uiReproduction => 'Создать копию';

  @override
  String get uiManageLaunchedAppsAndConfigurations =>
      'Управление запускаемыми приложениями и их расположением';

  @override
  String get uiCouldNotStart => 'Не удалось запустить';

  @override
  String get uiLongPress => 'Долгое нажатие';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      'Автоматически использует измеренное разрешение для открытых и закрытых состояний.';

  @override
  String get uiStart => 'Начать';

  @override
  String get uiRunningAuto => 'Работает (Auto)';

  @override
  String get uiRunningAutoPlus => 'Работает (Auto+)';

  @override
  String get uiStartPhoneDextop => 'Запустить Dextop на этом телефоне';

  @override
  String get uiStopAndroidAuto => 'Остановить (Android Auto)';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState =>
      'Автоматическое переключение в зависимости от открытого/закрытого состояния.';

  @override
  String get uiOpeningQuote => '«';

  @override
  String get uiDeleteWorkspaceQuestionSuffix =>
      '» — удалить это рабочее пространство?';

  @override
  String get uiAbnormalSessionWarning =>
      'Сеанс завершился в некорректном состоянии.\nНекоторые системные функции Android могут оставаться отключёнными.';

  @override
  String get uiChecking => 'Проверка';

  @override
  String get uiIdle => 'Ожидание';

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
  String get diagnosticLog => 'Журнал работы и диагностика устройства';

  @override
  String get diagnosticLogDescription =>
      'Журналы приложения, проверка возможностей и характеристики устройства';

  @override
  String get loadDiagnosticLog => 'Загрузить отчёт диагностики';

  @override
  String get copyDiagnosticLog => 'Копировать';

  @override
  String get shareDiagnosticLog => 'Поделиться';

  @override
  String get clearDiagnosticLog => 'Очистить журнал';

  @override
  String get deviceReport => 'Отчёт о работе устройства';

  @override
  String get uiCpuTemperature => 'Температура ЦП';

  @override
  String get deviceReportDescription =>
      'Отправить совместимость устройства и функций по электронной почте';

  @override
  String get deviceReportIntro =>
      'Данные устройства собираются автоматически. Выберите результат для каждой функции.';

  @override
  String get reportWorking => 'Работает';

  @override
  String get reportNotWorking => 'Не работает';

  @override
  String get reportUntested => 'Не проверено';

  @override
  String get reportOverall => 'Общий статус';

  @override
  String get reportNotes => 'Другие примечания';

  @override
  String get sendDeviceReport => 'Отправить отчёт по почте';

  @override
  String get reportEmailUnavailable => 'Не удалось открыть почтовое приложение';

  @override
  String get reportTemplateTitle => 'Отчёт о работе Dextop';

  @override
  String get reportNoNotes => 'Нет';

  @override
  String get reportNoSessionLog =>
      'Журнал завершённого сеанса Dextop пока отсутствует.';

  @override
  String get reportFeatureStartup =>
      'Запуск приложения и обнаружение устройства';

  @override
  String get reportFeatureSession => 'Запуск сеанса Dextop';

  @override
  String get reportFeatureVirtualDisplay => 'Зеркалирование VirtualDisplay';

  @override
  String get reportFeatureWindowManager => 'Зеркалирование WindowManager';

  @override
  String get reportFeatureSurfaceControl => 'Зеркалирование SurfaceControl';

  @override
  String get reportFeatureLandscape => 'Альбомный режим';

  @override
  String get reportFeaturePortrait => 'Портретный режим';

  @override
  String get reportFeatureSecureDisplay => 'Защищённый дисплей';

  @override
  String get reportFeatureLauncher => 'Панель приложений и свободные окна';

  @override
  String get reportFeatureWorkspace =>
      'Сохранение и восстановление рабочих областей';

  @override
  String get reportFeatureCursor => 'Ввод курсором и сенсорной панелью';

  @override
  String get reportFeatureDirectTouch => 'Прямой сенсорный ввод';

  @override
  String get reportFeatureMultiTouch => 'Мультитач-прокрутка и масштабирование';

  @override
  String get reportFeatureGesture => 'Жест оверлея тремя пальцами';

  @override
  String get reportFeatureMouse => 'Физическая мышь';

  @override
  String get reportFeatureKeyboard => 'Физическая клавиатура';

  @override
  String get reportFeatureRouting =>
      'Маршрутизация мыши и клавиатуры между дисплеями';

  @override
  String get reportFeatureFoldable => 'Авторазрешение для складных устройств';

  @override
  String get reportFeaturePerformance => 'Оверлей производительности';

  @override
  String get reportFeatureCleanup =>
      'Завершение сеанса и восстановление Android';

  @override
  String get samsungExperimentalTitle =>
      'Экспериментальные настройки рабочего стола Samsung';

  @override
  String get samsungUnavailable => 'Доступно только на устройствах Samsung';

  @override
  String get samsungExperimentalDescription =>
      'Изменение скрытых параметров DeX из Dextop';

  @override
  String get samsungSettingsTitle => 'Настройки рабочего стола Samsung';

  @override
  String get samsungSettingsSummary => 'Экран, ввод и панель задач';

  @override
  String get samsungRestoreSuccess => 'Настройки Samsung восстановлены';

  @override
  String get samsungConfirmTitle => 'Подтверждение изменения настроек';

  @override
  String get samsungPermanentWarning =>
      'Эти параметры могут постоянно влиять на Dextop и обычную среду рабочего стола до их сброса.';

  @override
  String get samsungAcceptEnable => 'Принять и включить';

  @override
  String get samsungAboutSetting => 'Об этом параметре';

  @override
  String get samsungRestoreEnvironment => 'Восстановить среду';

  @override
  String get samsungSettingsIntro =>
      'Напрямую изменяет значения DeX, скрытые настройками Samsung при отсутствии внешнего дисплея. Изменения влияют на Samsung DeX и соответствующие функции Dextop.';

  @override
  String get samsungResolution => 'Разрешение внешнего экрана';

  @override
  String get samsungScreenZoom => 'Масштаб экрана (DPI)';

  @override
  String get samsungFontScale => 'Размер шрифта';

  @override
  String get samsungScreenTimeout => 'Тайм-аут экрана';

  @override
  String get samsungAudioOutput => 'Вывод звука на внешний экран';

  @override
  String get samsungDisplayOrientation => 'Поворот внешнего экрана';

  @override
  String get samsungDisplayArrangement => 'Расположение экранов';

  @override
  String get samsungSectionInput => 'Ввод';

  @override
  String get samsungSectionDesktop => 'Рабочий стол';

  @override
  String get samsungInputLockedWhileRunning =>
      'Конфликтующие параметры ввода Samsung нельзя менять во время работы Dextop.';

  @override
  String get samsungAutorunTouchpad =>
      'Автоматически запускать сенсорную панель';

  @override
  String get samsungTouchpadScrollDirection => 'Обратное направление прокрутки';

  @override
  String get samsungTouchKeyboard =>
      'Показывать экранную клавиатуру при подключении';

  @override
  String get samsungKeyboardDex =>
      'Показывать клавиатуру при физической клавиатуре';

  @override
  String get samsungSpenInputMode => 'Использовать S Pen как мышь';

  @override
  String get samsungThreeFingerGesture => 'Жест тремя пальцами';

  @override
  String get samsungFourFingerGesture => 'Жест четырьмя пальцами';

  @override
  String get samsungAutoHideTaskbar => 'Автоматически скрывать панель задач';

  @override
  String get samsungDexCommandArrow => 'Показывать стрелку команд';

  @override
  String get samsungIncludePhoneDisplay =>
      'Включить Dextop в топологию дисплеев';

  @override
  String get samsungMirrorPhoneDisplay => 'Дублировать встроенный экран';

  @override
  String get samsungReviewEnable =>
      'Прочитать предупреждение и разрешить изменения';

  @override
  String get samsungSeconds15 => '15 секунд';

  @override
  String get samsungSeconds30 => '30 секунд';

  @override
  String get samsungMinute1 => '1 минута';

  @override
  String get samsungMinutes2 => '2 минуты';

  @override
  String get samsungMinutes5 => '5 минут';

  @override
  String get samsungMinutes10 => '10 минут';

  @override
  String get samsungMinutes20 => '20 минут';

  @override
  String get samsungMinutes30 => '30 минут';

  @override
  String get samsungHour1 => '1 час';

  @override
  String get samsungLeft => 'Слева';

  @override
  String get samsungRight => 'Справа';

  @override
  String get samsungAutomatic => 'Автоматически';

  @override
  String get samsungGestureNone => 'Нет';

  @override
  String get samsungGestureApps => 'Приложения';

  @override
  String get samsungGestureRecents => 'Недавние';

  @override
  String get samsungGestureNotifications => 'Уведомления';

  @override
  String get samsungGestureQuickSettings => 'Быстрые настройки';

  @override
  String get samsungHelp_resolution =>
      'Определяет рабочую область, в которой Samsung Desktop размещает приложения и окна. Высокое разрешение вмещает больше содержимого, но уменьшает элементы и повышает нагрузку. Низкое улучшает читаемость и производительность. Настройка хранится отдельно от разрешения Dextop.';

  @override
  String get samsungHelp_screenZoom =>
      'Масштабирует текст, значки и элементы управления Samsung Desktop. Более высокий DPI делает их крупнее и удобнее для чтения, а низкий позволяет видеть больше содержимого. Фактическое разрешение не меняется.';

  @override
  String get samsungHelp_fontScale =>
      'Изменяет только размер текста, почти не затрагивая значки и окна. Полезно для читаемости без потери рабочей области. Слишком большой масштаб может вызвать перенос или обрезку текста.';

  @override
  String get samsungHelp_screenTimeout =>
      'Задаёт время работы экрана Samsung Desktop без ввода. Долгий интервал удобен для документов и видео, но может увеличить расход энергии и нагрев.';

  @override
  String get samsungHelp_audioOutput =>
      'При включении звук мультимедиа и уведомлений направляется на HDMI-монитор или док-станцию. При выключении обычно используется телефон или текущее аудиоустройство. Если у монитора нет динамиков, звук может исчезнуть.';

  @override
  String get samsungHelp_displayOrientation =>
      'Поворачивает Samsung Desktop на выбранный угол. Используйте для вертикально установленного монитора. Несоответствие физической ориентации может привести к расхождению изображения и направления указателя.';

  @override
  String get samsungHelp_displayArrangement =>
      'Указывает, расположен ли телефон слева или справа от внешнего экрана, и меняет край перехода указателя между экранами. Совпадение с реальным расположением делает переход естественным.';

  @override
  String get samsungHelp_autorunTouchpad =>
      'При включении панель Samsung автоматически открывается на телефоне после подключения и работает как тачпад ноутбука. Она дублирует ввод Dextop, поэтому параметр скрыт во время сеанса Dextop.';

  @override
  String get samsungHelp_touchpadScrollDirection =>
      'Меняет соответствие движения двумя пальцами и прокрутки страницы на панели Samsung. Позволяет выбрать направление в стиле колеса мыши или прямого сенсорного управления.';

  @override
  String get samsungHelp_touchKeyboard =>
      'При включении экранная клавиатура появляется при выборе поля ввода в режиме рабочего стола. Это удобно без физической клавиатуры, но пересекается с управлением Dextop и скрывается во время сеанса.';

  @override
  String get samsungHelp_keyboardDex =>
      'При включении экранная клавиатура доступна даже при подключённой физической клавиатуре — для эмодзи, рукописного и голосового ввода. Она занимает рабочую область и может конфликтовать с IME Dextop.';

  @override
  String get samsungHelp_spenInputMode =>
      'При включении S Pen работает как указатель, включая наведение до касания экрана. Это помогает точно выбирать элементы. Проверьте поведение приложений для рисования, если используете чувствительность к нажатию.';

  @override
  String get samsungHelp_threeFingerGesture =>
      'Выполняет выбранное действие Samsung — приложения, Домой, недавние или Назад — по жесту тремя пальцами. Dextop тоже использует три пальца, поэтому параметр скрыт во время его работы.';

  @override
  String get samsungHelp_fourFingerGesture =>
      'Выполняет выбранное системное действие жестом четырьмя пальцами на поддерживаемой панели. Ускоряет навигацию, но конфликтует с мультитачем Dextop и скрывается во время сеанса.';

  @override
  String get samsungHelp_autoHideTaskbar =>
      'При включении панель задач Samsung Desktop скрывается, когда не используется, освобождая вертикальное место. Для показа переместите указатель к нижнему краю. Отключите, если переключатель приложений должен быть виден постоянно.';

  @override
  String get samsungHelp_dexCommandArrow =>
      'При включении отображается стрелка для вызова команд Samsung. Она ускоряет доступ к вспомогательным действиям, но может перекрывать оверлей или краевые жесты Dextop.';

  @override
  String get samsungHelp_includePhoneDisplay =>
      'При включении встроенный экран телефона становится частью общей топологии рабочего стола, позволяя перемещать приложения и указатель между экранами. Отключите, чтобы оставить телефон независимым экраном управления Android.';

  @override
  String get samsungHelp_mirrorPhoneDisplay =>
      'При включении внешний экран показывает то же содержимое, что и телефон. Это удобно для демонстраций, но дублирует, а не расширяет рабочую область, поэтому разные приложения на двух экранах недоступны.';

  @override
  String get keyboardSettingsTitle => 'Клавиатура';

  @override
  String get keyboardSettingsDescription =>
      'Темы клавиатуры и языки свайп-ввода';

  @override
  String get keyboardSwipeLanguages => 'Языки свайп-ввода';

  @override
  String get keyboardSwipeLanguagesDescription =>
      'Выберите языки, отображаемые при удержании MENU.';

  @override
  String get keyboardSwipeAddLanguage => 'Добавить язык';

  @override
  String get keyboardSwipeDefaultLanguage => 'По умолчанию';

  @override
  String get keyboardSwipeInput => 'Ввод свайпом';

  @override
  String get keyboardSwipeInputDescription =>
      'Вводите слова, проводя по клавиатуре. По умолчанию выключено.';

  @override
  String get keyboardSwipeCandidates => 'Показывать варианты';

  @override
  String get keyboardSwipeCandidatesDescription =>
      'Показывать список вариантов после ввода свайпом.';

  @override
  String get keyboardThemesTitle => 'Темы клавиатуры';

  @override
  String get keyboardThemesChoose => 'Выберите тему';

  @override
  String get keyboardThemesNew => 'Новая тема клавиатуры';

  @override
  String get keyboardThemesName => 'Название темы';

  @override
  String get keyboardThemesCreate => 'Создать';

  @override
  String get keyboardThemesEdit => 'Изменить';

  @override
  String get keyboardThemesDone => 'Готово';

  @override
  String get keyboardThemesDeleteTitle => 'Удалить тему?';

  @override
  String keyboardThemesDeleteBody(String name) {
    return 'Удалить «$name»? Это действие нельзя отменить.';
  }

  @override
  String get keyboardThemesBuiltIn => 'Встроенные темы нельзя удалить';

  @override
  String get keyboardThemesSelectFirst => 'Сначала выберите эту тему.';

  @override
  String get keyboardThemesStartFirst =>
      'Сначала запустите Dextop для показа демонстрации.';

  @override
  String get keyboardThemesAdd => 'Добавить свою тему';

  @override
  String get keyboardThemesPreview => 'Предпросмотр клавиатуры';

  @override
  String get keyboardThemesEditTip => 'Изменить';

  @override
  String get keyboardThemesImage => 'Выбрать фоновое изображение';

  @override
  String get keyboardThemesExport => 'Экспорт темы';

  @override
  String get keyboardThemesExportDialog => 'Экспорт темы клавиатуры';

  @override
  String get keyboardThemesOpacity => 'Непрозрачность';

  @override
  String get keyboardThemesBlur => 'Размытие';

  @override
  String get keyboardThemesRadius => 'Радиус углов';

  @override
  String get keyboardThemesBackground => 'Фон';

  @override
  String get keyboardThemesKey => 'Клавиша';

  @override
  String get keyboardThemesBorder => 'Граница';

  @override
  String get keyboardThemesText => 'Текст';

  @override
  String get keyboardThemesTrackpad => 'Трекпад';

  @override
  String get keyboardThemesKeyOpacity => 'Непрозрачность клавиш';

  @override
  String get keyboardThemesTrackpadOpacity => 'Непрозрачность тачпада';

  @override
  String get keyboardThemesShowTrackpadLabel => 'Показывать надпись TRACKPAD';

  @override
  String get keyboardThemesDescription =>
      'Темы и настройка внешнего вида клавиатуры ноутбука';

  @override
  String get autoSettingsTitle => 'Auto';

  @override
  String get autoSettingsDescription =>
      'Параметры отображения Auto и зеркалирования телефона';

  @override
  String get autoSettingsOptions => 'Android Auto';

  @override
  String get autoMatchPhoneOrientation =>
      'Согласовать ориентацию зеркала телефона с Auto';

  @override
  String get autoMatchPhoneOrientationDescription =>
      'При показе Dextop в Auto через зеркало телефона менять ориентацию по соотношению сторон головного устройства.';

  @override
  String get autoExperimentalFeatures => 'Экспериментальные функции';

  @override
  String get autoHiddenDisplay =>
      'Скрывать виртуальный дисплей Auto на телефоне';

  @override
  String get autoHiddenDisplayDescription =>
      'Передаёт рабочий стол Auto без отображения его виртуального дисплея на телефоне. Доступность зависит от устройства.';

  @override
  String get autoDisplayModeDescription =>
      'Android Auto использует отдельный рабочий стол, размер которого соответствует подключённому головному устройству.';

  @override
  String get setupEmbeddedTitle => 'Настройка доступа Dextop';

  @override
  String get setupEmbeddedDescription =>
      'Подключите встроенную службу доступа Dextop.\n\n1. Разрешите уведомления.\n2. Откройте и включите беспроводную отладку.\n3. Нажмите «Сопряжение устройства с помощью кода».\n4. Введите шестизначный код Android в поле уведомления.';

  @override
  String get setupEmbeddedSetupDescription =>
      'Откройте «Сопряжение устройства с помощью кода» в разделе беспроводной отладки. Когда служба сопряжения будет обнаружена, автоматически появится уведомление для ввода шестизначного кода.';

  @override
  String get setupEmbeddedWirelessDebuggingDescription =>
      'Откройте настройки беспроводной отладки Android и включите беспроводную отладку.';

  @override
  String get setupEmbeddedOpenWirelessDebugging =>
      'Открыть беспроводную отладку';

  @override
  String get setupEmbeddedEnableWirelessDebugging =>
      'Включить беспроводную отладку';

  @override
  String get setupEmbeddedPairingCode => 'Код сопряжения';

  @override
  String get setupEmbeddedPairingCodeHint =>
      'Шестизначный код, показанный Android';

  @override
  String get setupEmbeddedInvalidCode => 'Введите правильный шестизначный код';

  @override
  String get setupEmbeddedPair => 'Сопрячь и запустить';

  @override
  String get setupEmbeddedPairAndStart => 'Настроить доступ Dextop';

  @override
  String get setupEmbeddedIncluded => 'Служба доступа Dextop включена';

  @override
  String get setupEmbeddedConnectedDescription =>
      'Разрешение доступа Dextop готово к использованию.';

  @override
  String get setupEmbeddedConfigure => 'Сопряжение беспроводной отладки';

  @override
  String get setupEmbeddedPairingFailed => 'Не удалось выполнить сопряжение';

  @override
  String get setupEmbeddedStartFailed =>
      'Не удалось запустить службу доступа Dextop';

  @override
  String get setupEmbeddedNotificationPermission => 'Разрешение на уведомления';

  @override
  String get setupEmbeddedAllowNotifications => 'Разрешить уведомления';

  @override
  String get setupEmbeddedSearchingPairing => 'Поиск службы сопряжения';

  @override
  String get setupEmbeddedPairingServiceFound => 'Служба сопряжения найдена';

  @override
  String get setupEmbeddedPairingInProgress => 'Выполняется сопряжение';

  @override
  String get setupEmbeddedPairingServiceNotFound =>
      'Служба сопряжения не найдена';

  @override
  String get setupEmbeddedRetryPairing => 'Повторить сопряжение';

  @override
  String get setupEmbeddedPairingNotificationReady =>
      'Введите шестизначный код в поле уведомления.';

  @override
  String get experimentalCoverDisplay => 'Сеанс на внешнем экране';

  @override
  String get experimentalCoverDisplayDescription =>
      'Используйте Android или отдельный сеанс Dextop на внешнем экране складного устройства';

  @override
  String get experimentalCoverDisplayUnavailable =>
      'Доступно только на складных устройствах';

  @override
  String get experimentalForceLaptopMode =>
      'Принудительно разрешить режим ноутбука';

  @override
  String get experimentalForceLaptopModeDescription =>
      'Разрешить ручной запуск режима ноутбука из панели на нескладных устройствах';

  @override
  String get experimentalBlackBerryMode => 'Режим BlackBerry';

  @override
  String get experimentalBlackBerryModeDescription =>
      'Компактная раскладка в стиле физической клавиатуры для телефона в панели Dextop';

  @override
  String get blackBerryAutoStart => 'Автоматически открывать режим BlackBerry';

  @override
  String get blackBerryAutoStartDescription =>
      'Автоматически показывать клавиатуру BlackBerry при запуске сеанса Dextop в портретном режиме';

  @override
  String get keyboardHaptics => 'Виброотклик клавиатуры';

  @override
  String get keyboardHapticsDescription =>
      'Вибрация при нажатии клавиш и использовании сенсорной панели';

  @override
  String get nativeUnknownPermissionResult =>
      'Shizuku вернул неизвестный результат разрешения';

  @override
  String get nativeDevice => 'Устройство';

  @override
  String get nativeCoverDisplay => 'Внешний экран';

  @override
  String get nativeCoverDisplayDescription =>
      'Открывает Android или создаёт независимый сеанс Dextop на внешнем экране';

  @override
  String get nativeCoverAndroidDescription =>
      'Использует внешний экран для обычного Android, не завершая сеанс Dextop на основном экране.';

  @override
  String get nativeCoverDextopDescription =>
      'Создаёт на внешнем экране независимый от основного сеанс Dextop.';

  @override
  String get nativeOpenCoverAndroid => 'Открыть Android на внешнем экране';

  @override
  String get nativeStopCoverAndroid => 'Завершить сеанс Android';

  @override
  String get nativeStartCoverDextop => 'Создать сеанс Dextop на внешнем экране';

  @override
  String get nativeStopCoverDextop => 'Завершить сеанс Dextop';

  @override
  String get nativeCoverDisplayFailed => 'Не удалось изменить внешний экран';

  @override
  String get nativeSwitchToResolutionSuffix =>
      'Переключиться на это разрешение и DPI';

  @override
  String get nativeShizukuUnavailable => 'Shizuku недоступен';

  @override
  String get nativeSelectedAppCannotLaunch =>
      'Не удалось запустить выбранное приложение';

  @override
  String get nativeShizukuBinderUnavailable =>
      'Подключение к Shizuku недоступно';

  @override
  String get nativeMirrorSurfaceUnavailable =>
      'Область зеркального отображения недоступна';

  @override
  String get nativeRotationReleaseUnsupported =>
      'Снятие блокировки поворота не поддерживается';

  @override
  String get nativePerformanceHudFormat =>
      '%1\$.1f FPS  |  %2\$d MB\nБатарея: %3\$d%%  |  %4\$s\nCPU: %6\$s  |  Ввод: %5\$s';

  @override
  String get nativeInputMouse => 'Мышь';

  @override
  String get nativeInputTouch => 'Касание';

  @override
  String get nativeInputTrackpad => 'Трекпад';

  @override
  String get nativeInputIdle => 'Ожидание';

  @override
  String get nativePhysicalMouse => 'Переключить назначение физической мыши';

  @override
  String get nativePhysicalKeyboard =>
      'Переключить назначение физической клавиатуры';

  @override
  String get nativePhysicalMouseDemo =>
      'Переключает физическую мышь между Android и Dextop. Доступно на поддерживаемых устройствах при подключённом внешнем дисплее.';

  @override
  String get nativePhysicalKeyboardDemo =>
      'Переключает физическую клавиатуру между Android и Dextop. Доступно на поддерживаемых устройствах при подключённом внешнем дисплее.';

  @override
  String get nativeTheThreeFingerGestureIsAnEssential =>
      'Жест тремя пальцами необходим при работе с Dextop.\\nНажмите кнопку, чтобы увидеть описание её функции.';

  @override
  String get nativeAsusZenuiRogUiDesktop => 'Рабочий стол ASUS ZenUI/ROG UI';

  @override
  String get nativeAndroidDesktopFreeform =>
      'Рабочий стол Android (свободная форма)';

  @override
  String get nativeColorosDesktop => 'Рабочий стол ColorOS';

  @override
  String get nativeAdjustTheVolumeOfPlaybackOnDextop =>
      'Отрегулируйте громкость воспроизведения на Dextop';

  @override
  String get nativeSwitchBetweenPortraitAndLandscapeOrientationOf =>
      'Переключение между книжной и альбомной ориентацией Dextop';

  @override
  String get nativeSwitchDextopResolutionAndDpi =>
      'Переключение разрешения Dextop и DPI';

  @override
  String get nativePauseDextopAndReturnYourAndroidTo =>
      'Приостановить Dextop и вернуть Android в состояние обычного управления.';

  @override
  String get nativeTerminateYourDextopSession => 'Завершите сеанс Dextop';

  @override
  String get nativeAdjustTheBrightnessOfTheDesktopDisplay =>
      'Настройка яркости дисплея рабочего стола';

  @override
  String get nativeHonorMagicosDesktop => 'Рабочий стол HONOR MagicOS';

  @override
  String get nativeHuaweiEmuiDesktop => 'Рабочий стол Huawei EMUI';

  @override
  String get nativeHyperosMiuiDesktop => 'Рабочий стол HyperOS / MIUI';

  @override
  String get nativeMotorolaLenovoDesktop => 'Рабочий стол Motorola / Lenovo';

  @override
  String get nativeNothingOsDesktop => 'Рабочий стол Nothing OS';

  @override
  String get nativeOriginosFuntouchOsDesktop =>
      'Рабочий стол OriginOS / Funtouch OS';

  @override
  String get nativePixelAndroidDesktop => 'Рабочий стол Pixel Android';

  @override
  String get nativeShizukuPermissionCheckTimedOut =>
      'Истекло время проверки разрешения Shizuku';

  @override
  String get nativeRequiresConnectionToShizukuAndPermissions =>
      'Требуется подключение к Shizuku и разрешения.';

  @override
  String get nativePermissionDeniedToShizuku => 'Разрешение Shizuku отклонено';

  @override
  String get nativePleaseInstallShizuku => 'Установите Shizuku';

  @override
  String get nativePleaseStartShizuku => 'Запустите Shizuku';

  @override
  String get nativeRemoveThisResolution => 'Удалить это разрешение';

  @override
  String get nativeNoAppSelected => 'Приложение не выбрано';

  @override
  String get nativeCouldNotStartApp => 'Не удалось запустить приложение';

  @override
  String get nativeAddCustomResolution => 'Добавить собственное разрешение';

  @override
  String get nativeRotate180 => 'Повернуть на 180°';

  @override
  String get nativeCast => 'Трансляция';

  @override
  String get nativeCastDescription => 'Выбрать устройство трансляции';

  @override
  String get nativeNoCastDevices => 'Нет доступных устройств Google Cast';

  @override
  String get nativeCastUnavailable => 'Не удалось инициализировать Google Cast';

  @override
  String get nativeCasting => 'Трансляция';

  @override
  String get nativeStopCasting => 'Остановить трансляцию';

  @override
  String get nativeScanAgain => 'Сканировать снова';

  @override
  String get nativeScanning => 'Сканирование…';

  @override
  String get nativeCursor => 'Курсор';

  @override
  String get nativeTap => 'Касание';

  @override
  String get nativeTapToExit => 'Нажмите, чтобы выйти';

  @override
  String get nativeTapToOpen => 'Нажмите, чтобы открыть';

  @override
  String get nativeYouCanRearrangeTheLayoutInThe =>
      'Вы можете изменить расположение макета на панели управления, удерживая кнопку.';

  @override
  String get nativeWorkSpace => 'Рабочая область';

  @override
  String get nativeExpandWorkspace => 'Расширить рабочую область';

  @override
  String get nativeTemporarilyReturnToAndroid => 'Временно вернуться в Android';

  @override
  String get nativeLaptopMode => 'Режим ноутбука';

  @override
  String get nativeLaptopModeDescription =>
      'Показать клавиатуру и тачпад на нижней половине складного устройства.';

  @override
  String get nativeKeyboardStyle => 'Стиль';

  @override
  String get nativeBlackBerryMode => 'Режим BlackBerry';

  @override
  String get nativeBlackBerryLayout => 'Раскладка BlackBerry';

  @override
  String get nativeAutomaticLayout => 'Автоматическая раскладка';

  @override
  String get nativeManualHeight => 'Ручная настройка высоты';

  @override
  String get nativeKeyboardHeight => 'Высота клавиатуры';

  @override
  String get nativeBlackBerryModeDescription =>
      'Показывает компактную клавиатуру телефона без трекпада.';

  @override
  String get nativeKeyboardSettings => 'Настройки клавиатуры';

  @override
  String get nativeKeyboardSettingsDescription =>
      'Настройте внешний вид клавиатуры ноутбука';

  @override
  String get nativeTheme => 'Тема';

  @override
  String get nativeSwipeLanguage => 'Язык ввода свайпом';

  @override
  String get nativeSwipeLanguageDescription =>
      'Обычные нажатия продолжают использовать текущую IME';

  @override
  String get nativeKeyboardThemeStandard => 'Стандартная';

  @override
  String get nativeKeyboardThemeCrimson => 'Малиновая';

  @override
  String get nativeKeyboardThemeCloud => 'Cloud Pop';

  @override
  String get nativeKeyboardThemeAmoled => 'AMOLED';

  @override
  String get nativeKeyboardThemeStandardDescription =>
      'Нейтральная тёмная клавиатура';

  @override
  String get nativeKeyboardThemeCrimsonDescription =>
      'Тёплый малиновый цвет в стиле складных ПК';

  @override
  String get nativeKeyboardThemeCloudDescription =>
      'Мягкая облачно-синяя клавиатура';

  @override
  String get nativeKeyboardThemeAmoledDescription =>
      'Энергосберегающая клавиатура на чистом чёрном фоне';

  @override
  String get nativeKeyboardThemeCustomDescription =>
      'Пользовательская тема клавиатуры';

  @override
  String get nativeOpenDextopWithYourSavedAppPlacement =>
      'Откройте Dextop с сохраненным местом размещения приложения.';

  @override
  String get nativeSaveAndApply => 'Сохраните и примените';

  @override
  String get nativeFailedToSaveUnableToRetrieveRunning =>
      'Не удалось сохранить: невозможно получить запущенные приложения на Dextop.';

  @override
  String get nativeFailedToSaveFailedToWriteTo =>
      'Не удалось сохранить: не удалось записать в память устройства.';

  @override
  String get nativeViewSavedWorkspacesAndSaveCurrentArrangement =>
      'Просмотр сохраненных рабочих пространств и сохранение текущего расположения';

  @override
  String get nativeNoSavedWorkspaces => 'Нет сохраненных рабочих пространств';

  @override
  String get nativePleaseStartDextopFirst =>
      'Пожалуйста, сначала запустите Dextop';

  @override
  String get nativeReconnect => 'Переподключиться';

  @override
  String get nativeWidth => 'Ширина';

  @override
  String get nativeReturn => 'Назад';

  @override
  String get nativePermissionRequestInProgress =>
      'Запрос разрешения обрабатывается';

  @override
  String get nativeHorizontalHolding => 'Альбомная ориентация';

  @override
  String get nativeSaveTheCurrentAppArrangementAsA =>
      'Сохраните текущее расположение приложения как рабочую область.';

  @override
  String get nativeAddCurrentAppPlacement =>
      'Добавить текущее место размещения приложения';

  @override
  String get nativeScreenBrightness => 'Яркость экрана';

  @override
  String get nativeUseTheScreenAsATrackpadTo =>
      'Используйте экран как трекпад для управления курсором';

  @override
  String get nativeEnd => 'Завершить';

  @override
  String get nativeEdit => 'Изменить';

  @override
  String get nativeVerticalHolding => 'Портретная ориентация';

  @override
  String get nativeReconnectIfYouHaveDisplayOrConnection =>
      'Переподключитесь, если у вас возникли проблемы с отображением или подключением.';

  @override
  String get nativeDisplayProfileIsOutOfRange =>
      'Профиль дисплея находится вне диапазона';

  @override
  String get nativeResolution => 'Разрешение';

  @override
  String get nativeEditResolution => 'Изменить разрешение';

  @override
  String get nativeAddResolution => 'Добавить разрешение';

  @override
  String get nativeSendsTheTouchedPositionDirectlyToDextop =>
      'Отправляет положение касания непосредственно в Dextop как касание';

  @override
  String get nativeAddAndApply => 'Добавить и применить';

  @override
  String get nativeEditPlacement => 'Изменить место размещения';

  @override
  String get nativeCompletePlacementEdit =>
      'Завершить редактирование места размещения';

  @override
  String get nativeVolume => 'Громкость';

  @override
  String get nativeHeight => 'Высота';

  @override
  String get multiTouchUpgradeTitle => 'Приложение обновлено';

  @override
  String get multiTouchUpgradeBody =>
      'Добавлена поддержка мультитача, поэтому жесты также были обновлены.';

  @override
  String get multiTouchUpgradeLandscape =>
      'Альбомная ориентация\\nПроведите тремя пальцами вправо от левого края';

  @override
  String get multiTouchUpgradePortrait =>
      'Портретная ориентация\\nПроведите тремя пальцами вниз от верхнего края';

  @override
  String get multiTouchUpgradeClose => 'Понятно';

  @override
  String appUpdatedTitle(String version) {
    return 'Обновлено до версии $version!';
  }

  @override
  String get appUpdatedMessage => 'Dextop обновлён до последней версии.';

  @override
  String get androidSwipeImeLabel => 'Ввод жестами Dextop';

  @override
  String get androidAccessibilityServiceDescription =>
      'Управляет виртуальным дисплеем, вводом и панелью управления Dextop';

  @override
  String get androidEmbeddedPairingChannel => 'Настройка встроенного доступа';

  @override
  String get androidEmbeddedPairingTitle => 'Сопряжение Dextop';

  @override
  String get androidEmbeddedPairingNotificationText =>
      'Введите шестизначный код из меню беспроводной отладки';

  @override
  String get androidEmbeddedPairingCode => 'Шестизначный код сопряжения';

  @override
  String get androidEmbeddedPairingEnterCode => 'Ввести код сопряжения';

  @override
  String get androidEmbeddedPairingSuccess => 'Dextop подключён';

  @override
  String get androidEmbeddedPairingSuccessMessage =>
      'Сопряжение завершено. Вернитесь в Dextop.';

  @override
  String get androidEmbeddedPairingFailed => 'Не удалось подключить Dextop';

  @override
  String get androidEmbeddedPairingInvalidCode => 'Введите шестизначный код';

  @override
  String get androidEmbeddedPairingSearching => 'Поиск службы сопряжения';

  @override
  String get androidEmbeddedPairingServiceFound => 'Служба сопряжения найдена';

  @override
  String get androidEmbeddedPairingServiceNotFound =>
      'Служба сопряжения не найдена';

  @override
  String get androidEmbeddedPairingRetry => 'Повторить';

  @override
  String get androidEmbeddedPairingInProgress => 'Выполняется сопряжение';
}
