import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart'
    show Brightness, Color, TargetPlatform, TextTheme, ThemeData, VisualDensity;

import '../../components/app_bars/styles/m3e_app_bar_theme.dart';
import '../../components/badges/styles/m3e_badge_theme.dart';
import '../../components/bottom_sheets/styles/m3e_bottom_sheet_theme.dart';
import '../../components/button_group/styles/m3e_button_group_theme.dart';
import '../../components/buttons/styles/m3e_button_theme.dart';
import '../../components/cards/styles/m3e_card_theme.dart';
import '../../components/carousel/styles/m3e_carousel_theme.dart';
import '../../components/checkbox/styles/m3e_checkbox_theme.dart';
import '../../components/chips/styles/m3e_chip_theme.dart';
import '../../components/date_pickers/styles/m3e_date_picker_theme.dart';
import '../../components/dialogs/styles/m3e_dialog_theme.dart';
import '../../components/divider/styles/m3e_divider_theme.dart';
import '../../components/dropdown_menus/styles/m3e_dropdown_menu_theme.dart';
import '../../components/fab_menu/styles/m3e_fab_menu_theme.dart';
import '../../components/floating_action_buttons/styles/m3e_fab_theme.dart';
import '../../components/icon_buttons/styles/m3e_icon_button_theme.dart';
import '../../components/lists/styles/m3e_list_theme.dart';
import '../../components/loading_indicator/styles/m3e_loading_indicator_theme.dart';
import '../../components/menus/styles/m3e_menu_theme.dart';
import '../../components/navigation_bar/styles/m3e_navigation_bar_theme.dart';
import '../../components/navigation_drawer/styles/m3e_navigation_drawer_theme.dart';
import '../../components/navigation_rail/styles/m3e_navigation_rail_theme.dart';
import '../../components/progress_indicators/styles/m3e_progress_indicator_theme.dart';
import '../../components/radio_button/styles/m3e_radio_theme.dart';
import '../../components/refresh_indicator/styles/m3e_refresh_indicator_theme.dart';
import '../../components/search/styles/m3e_search_bar_theme.dart';
import '../../components/search/styles/m3e_search_view_theme.dart';
import '../../components/segmented_buttons/styles/m3e_segmented_button_theme.dart';
import '../../components/selection/styles/m3e_selection_theme.dart';
import '../../components/side_sheets/styles/m3e_side_sheet_theme.dart';
import '../../components/sliders/styles/m3e_slider_theme.dart';
import '../../components/snackbar/styles/m3e_snackbar_theme.dart';
import '../../components/split_buttons/styles/m3e_split_button_theme.dart';
import '../../components/switch_control/styles/m3e_switch_theme.dart';
import '../../components/tabs/styles/m3e_tab_theme.dart';
import '../../components/text_fields/styles/m3e_text_field_theme.dart';
import '../../components/time_pickers/styles/m3e_time_picker_theme.dart';
import '../../components/toolbars/styles/m3e_toolbar_theme.dart';
import '../../components/tooltips/styles/m3e_tooltip_theme.dart';
import '../color/m3e_color_scheme.dart';
import '../interaction/m3e_focus_ring_theme.dart';
import '../tokens/m3e_spacing.dart';
import '../type/m3e_typography.dart';
import '../type/m3e_variable_font_config.dart';

part 'm3e_theme_data_copy_with.dart';

/// Immutable bundle of expressive design tokens and per-component themes.
@immutable
class M3EThemeData {
  /// Creates an expressive theme from tokens and component themes.
  M3EThemeData({
    M3EColorScheme? colorScheme,
    M3ETypography? typography,
    M3ETypeScale? typeScale,
    this.iconTheme = const IconThemeData(size: 24),
    this.spacing = const M3ESpacing.regular(),
    this.visualDensity = 0,
    this.platform,
    this.useMaterial3 = true,
    this.splashColor,
    this.highlightColor,
    this.keyboardFocusIndicators = true,
    this.focusRingTheme = M3EFocusRingTheme.defaults,
    this.appBarTheme = M3EAppBarTheme.defaults,
    this.badgeTheme = M3EBadgeTheme.defaults,
    this.bottomSheetTheme = M3EBottomSheetTheme.defaults,
    this.buttonTheme = M3EButtonTheme.defaults,
    this.cardTheme = M3ECardTheme.defaults,
    this.carouselTheme = M3ECarouselTheme.defaults,
    this.checkboxTheme = M3ECheckboxTheme.defaults,
    this.chipTheme = M3EChipTheme.defaults,
    this.datePickerTheme = M3EDatePickerTheme.defaults,
    this.dialogTheme = M3EDialogTheme.defaults,
    this.dividerTheme = M3EDividerTheme.defaults,
    this.dropdownMenuTheme = M3EDropdownMenuTheme.defaults,
    this.fabTheme = M3EFabTheme.defaults,
    this.fabMenuTheme = M3EFabMenuTheme.defaults,
    this.iconButtonTheme = M3EIconButtonTheme.defaults,
    this.listTheme = M3EListTheme.defaults,
    this.loadingIndicatorTheme = M3ELoadingIndicatorTheme.defaults,
    this.menuTheme = M3EMenuTheme.defaults,
    this.navigationBarTheme = M3ENavigationBarTheme.defaults,
    this.navigationDrawerTheme = M3ENavigationDrawerTheme.defaults,
    this.navigationRailTheme = M3ENavigationRailTheme.defaults,
    this.progressIndicatorTheme = M3EProgressIndicatorTheme.defaults,
    this.radioTheme = M3ERadioTheme.defaults,
    this.refreshIndicatorTheme = M3ERefreshIndicatorTheme.defaults,
    this.searchBarTheme = M3ESearchBarTheme.defaults,
    this.searchViewTheme = M3ESearchViewTheme.defaults,
    this.segmentedButtonTheme = M3ESegmentedButtonTheme.defaults,
    this.selectionTheme = M3ESelectionTheme.defaults,
    this.sideSheetTheme = M3ESideSheetTheme.defaults,
    this.sliderTheme = M3ESliderTheme.defaults,
    this.snackBarTheme = M3ESnackbarTheme.defaults,
    this.splitButtonTheme = M3ESplitButtonTheme.defaults,
    this.switchTheme = M3ESwitchTheme.defaults,
    this.tabTheme = M3ETabTheme.defaults,
    this.textFieldTheme = M3ETextFieldTheme.defaults,
    this.timePickerTheme = M3ETimePickerTheme.defaults,
    this.buttonGroupTheme = M3EButtonGroupTheme.defaults,
    this.toolbarTheme = M3EToolbarTheme.defaults,
    this.tooltipTheme = M3ETooltipTheme.defaults,
  }) : colorScheme = colorScheme ?? M3EColorScheme.light(),
       typography = _resolveTypography(typography, typeScale),
       brightness = (colorScheme ?? M3EColorScheme.light()).brightness;

  static M3ETypography _resolveTypography(
    M3ETypography? typography,
    M3ETypeScale? typeScale,
  ) {
    if (typography != null) {
      return typography;
    }
    if (typeScale != null) {
      return M3ETypography(
        baseline: typeScale,
        emphasized: M3ETypeScale.emphasized(),
      );
    }
    return M3ETypography.material3();
  }

  /// Light theme, optionally seeded from [seedColor].
  factory M3EThemeData.light({Color? seedColor}) {
    return M3EThemeData(
      colorScheme: seedColor == null
          ? M3EColorScheme.light()
          : M3EColorScheme.fromSeed(seedColor),
    );
  }

  /// Dark theme, optionally seeded from [seedColor].
  factory M3EThemeData.dark({Color? seedColor}) {
    return M3EThemeData(
      colorScheme: seedColor == null
          ? M3EColorScheme.dark()
          : M3EColorScheme.fromSeed(seedColor, brightness: Brightness.dark),
    );
  }

  /// Adapts a Material [ThemeData] into an [M3EThemeData].
  factory M3EThemeData.fromMaterial(ThemeData theme) {
    final M3EThemeData? cached = _materialCache[theme];
    if (cached != null) {
      return cached;
    }
    final data = M3EThemeData(
      colorScheme: M3EColorScheme.fromColorScheme(theme.colorScheme),
      typography: M3ETypography(
        baseline: M3ETypeScale.fromTextTheme(theme.textTheme),
        emphasized: M3ETypeScale.emphasized(),
      ),
      iconTheme: theme.iconTheme,
      visualDensity: theme.visualDensity.vertical,
      platform: theme.platform,
      useMaterial3: theme.useMaterial3,
      splashColor: theme.splashColor,
      highlightColor: theme.highlightColor,
    );
    _materialCache[theme] = data;
    return data;
  }

  /// Color roles for this theme.
  final M3EColorScheme colorScheme;

  /// Baseline and emphasized type scales for this theme.
  final M3ETypography typography;

  /// Baseline type scale for this theme (alias for `typography.baseline`).
  M3ETypeScale get typeScale => typography.baseline;

  /// Default icon size/opacity/etc. When color is null, icons use
  /// [M3EColorScheme.onSurface] (including after dynamic color updates).
  final IconThemeData iconTheme;

  /// Spacing scale for this theme.
  final M3ESpacing spacing;

  /// Visual density offset applied to Material [VisualDensity].
  final double visualDensity;

  /// Target platform overrides, if any.
  final TargetPlatform? platform;

  /// Whether Material 3 behavior is enabled in [toThemeData].
  final bool useMaterial3;

  /// Optional splash color override for ink.
  final Color? splashColor;

  /// Optional highlight color override for ink.
  final Color? highlightColor;

  /// Whether keyboard Tab focus rings and focus-state chrome are shown.
  ///
  /// When false, Tab traversal and activation still work, but no focus ring or
  /// focus state-layer indicators are painted. Defaults to true.
  final bool keyboardFocusIndicators;

  /// Shared keyboard focus ring tokens for actionable controls.
  final M3EFocusRingTheme focusRingTheme;

  /// Brightness derived from [colorScheme].
  final Brightness brightness;

  /// [typeScale] as a Material [TextTheme], colored with
  /// [M3EColorScheme.onSurface] so it tracks the active scheme.
  TextTheme get textTheme =>
      typeScale.withColor(colorScheme.onSurface).toTextTheme();

  /// [iconTheme] with [M3EColorScheme.onSurface] when no explicit color is set.
  IconThemeData get resolvedIconTheme =>
      iconTheme.copyWith(color: iconTheme.color ?? colorScheme.onSurface);

  /// App bar component theme.
  final M3EAppBarTheme appBarTheme;

  /// Badge component theme.
  final M3EBadgeTheme badgeTheme;

  /// Bottom sheet component theme.
  final M3EBottomSheetTheme bottomSheetTheme;

  /// Button component theme.
  final M3EButtonTheme buttonTheme;

  /// Card component theme.
  final M3ECardTheme cardTheme;

  /// Carousel component theme.
  final M3ECarouselTheme carouselTheme;

  /// Checkbox component theme.
  final M3ECheckboxTheme checkboxTheme;

  /// Chip component theme.
  final M3EChipTheme chipTheme;

  /// Date picker component theme.
  final M3EDatePickerTheme datePickerTheme;

  /// Dialog component theme.
  final M3EDialogTheme dialogTheme;

  /// Divider component theme.
  final M3EDividerTheme dividerTheme;

  /// Dropdown menu component theme.
  final M3EDropdownMenuTheme dropdownMenuTheme;

  /// FAB component theme.
  final M3EFabTheme fabTheme;

  /// FAB menu component theme.
  final M3EFabMenuTheme fabMenuTheme;

  /// Icon button component theme.
  final M3EIconButtonTheme iconButtonTheme;

  /// List component theme.
  final M3EListTheme listTheme;

  /// Loading indicator component theme.
  final M3ELoadingIndicatorTheme loadingIndicatorTheme;

  /// Menu component theme.
  final M3EMenuTheme menuTheme;

  /// Navigation bar component theme.
  final M3ENavigationBarTheme navigationBarTheme;

  /// Navigation drawer component theme.
  final M3ENavigationDrawerTheme navigationDrawerTheme;

  /// Navigation rail component theme.
  final M3ENavigationRailTheme navigationRailTheme;

  /// Progress indicator component theme.
  final M3EProgressIndicatorTheme progressIndicatorTheme;

  /// Radio button component theme.
  final M3ERadioTheme radioTheme;

  /// Refresh indicator component theme.
  final M3ERefreshIndicatorTheme refreshIndicatorTheme;

  /// Search bar component theme.
  final M3ESearchBarTheme searchBarTheme;

  /// Search view component theme.
  final M3ESearchViewTheme searchViewTheme;

  /// Segmented button component theme.
  final M3ESegmentedButtonTheme segmentedButtonTheme;

  /// Selection list / app bar component theme.
  final M3ESelectionTheme selectionTheme;

  /// Side sheet component theme.
  final M3ESideSheetTheme sideSheetTheme;

  /// Slider component theme.
  final M3ESliderTheme sliderTheme;

  /// Snackbar component theme.
  final M3ESnackbarTheme snackBarTheme;

  /// Split button component theme.
  final M3ESplitButtonTheme splitButtonTheme;

  /// Switch component theme.
  final M3ESwitchTheme switchTheme;

  /// Tab component theme.
  final M3ETabTheme tabTheme;

  /// Text field component theme.
  final M3ETextFieldTheme textFieldTheme;

  /// Time picker component theme.
  final M3ETimePickerTheme timePickerTheme;

  /// Button group component theme.
  final M3EButtonGroupTheme buttonGroupTheme;

  /// Toolbar component theme.
  final M3EToolbarTheme toolbarTheme;

  /// Tooltip component theme.
  final M3ETooltipTheme tooltipTheme;

  /// Returns a copy with [colorScheme] swapped and all component themes kept.
  M3EThemeData withColorScheme(M3EColorScheme colorScheme) {
    return copyWith(colorScheme: colorScheme);
  }

  /// Builds a dark template from this light-oriented [M3EThemeData].
  ///
  /// Preserves non-color tokens (type scale, spacing, component themes) while
  /// swapping in a dark `M3EColorScheme` seeded from `colorScheme.primary`.
  M3EThemeData deriveDarkTemplate() => _m3eDeriveDarkTemplate(this);

  /// Returns a copy with the given fields replaced.
  M3EThemeData copyWith({
    M3EColorScheme? colorScheme,
    M3ETypography? typography,
    M3ETypeScale? typeScale,
    M3ETypefaceConfig? typeface,
    M3EVariableFontConfig? variableFont,
    String? fontFamily,
    List<String>? fontFamilyFallback,
    String? package,
    List<FontVariation>? fontVariations,
    IconThemeData? iconTheme,
    M3ESpacing? spacing,
    double? visualDensity,
    TargetPlatform? platform,
    bool? useMaterial3,
    Color? splashColor,
    Color? highlightColor,
    bool? keyboardFocusIndicators,
    M3EFocusRingTheme? focusRingTheme,
    M3EAppBarTheme? appBarTheme,
    M3EBadgeTheme? badgeTheme,
    M3EBottomSheetTheme? bottomSheetTheme,
    M3EButtonTheme? buttonTheme,
    M3ECardTheme? cardTheme,
    M3ECarouselTheme? carouselTheme,
    M3ECheckboxTheme? checkboxTheme,
    M3EChipTheme? chipTheme,
    M3EDatePickerTheme? datePickerTheme,
    M3EDialogTheme? dialogTheme,
    M3EDividerTheme? dividerTheme,
    M3EDropdownMenuTheme? dropdownMenuTheme,
    M3EFabTheme? fabTheme,
    M3EFabMenuTheme? fabMenuTheme,
    M3EIconButtonTheme? iconButtonTheme,
    M3EListTheme? listTheme,
    M3ELoadingIndicatorTheme? loadingIndicatorTheme,
    M3EMenuTheme? menuTheme,
    M3ENavigationBarTheme? navigationBarTheme,
    M3ENavigationDrawerTheme? navigationDrawerTheme,
    M3ENavigationRailTheme? navigationRailTheme,
    M3EProgressIndicatorTheme? progressIndicatorTheme,
    M3ERadioTheme? radioTheme,
    M3ERefreshIndicatorTheme? refreshIndicatorTheme,
    M3ESearchBarTheme? searchBarTheme,
    M3ESearchViewTheme? searchViewTheme,
    M3ESegmentedButtonTheme? segmentedButtonTheme,
    M3ESelectionTheme? selectionTheme,
    M3ESideSheetTheme? sideSheetTheme,
    M3ESliderTheme? sliderTheme,
    M3ESnackbarTheme? snackBarTheme,
    M3ESplitButtonTheme? splitButtonTheme,
    M3ESwitchTheme? switchTheme,
    M3ETabTheme? tabTheme,
    M3ETextFieldTheme? textFieldTheme,
    M3ETimePickerTheme? timePickerTheme,
    M3EButtonGroupTheme? buttonGroupTheme,
    M3EToolbarTheme? toolbarTheme,
    M3ETooltipTheme? tooltipTheme,
  }) => _m3eCopyThemeData(
    this,
    colorScheme: colorScheme,
    typography: typography,
    typeScale: typeScale,
    typeface: typeface,
    variableFont: variableFont,
    fontFamily: fontFamily,
    fontFamilyFallback: fontFamilyFallback,
    package: package,
    fontVariations: fontVariations,
    iconTheme: iconTheme,
    spacing: spacing,
    visualDensity: visualDensity,
    platform: platform,
    useMaterial3: useMaterial3,
    splashColor: splashColor,
    highlightColor: highlightColor,
    keyboardFocusIndicators: keyboardFocusIndicators,
    focusRingTheme: focusRingTheme,
    appBarTheme: appBarTheme,
    badgeTheme: badgeTheme,
    bottomSheetTheme: bottomSheetTheme,
    buttonTheme: buttonTheme,
    cardTheme: cardTheme,
    carouselTheme: carouselTheme,
    checkboxTheme: checkboxTheme,
    chipTheme: chipTheme,
    datePickerTheme: datePickerTheme,
    dialogTheme: dialogTheme,
    dividerTheme: dividerTheme,
    dropdownMenuTheme: dropdownMenuTheme,
    fabTheme: fabTheme,
    fabMenuTheme: fabMenuTheme,
    iconButtonTheme: iconButtonTheme,
    listTheme: listTheme,
    loadingIndicatorTheme: loadingIndicatorTheme,
    menuTheme: menuTheme,
    navigationBarTheme: navigationBarTheme,
    navigationDrawerTheme: navigationDrawerTheme,
    navigationRailTheme: navigationRailTheme,
    progressIndicatorTheme: progressIndicatorTheme,
    radioTheme: radioTheme,
    refreshIndicatorTheme: refreshIndicatorTheme,
    searchBarTheme: searchBarTheme,
    searchViewTheme: searchViewTheme,
    segmentedButtonTheme: segmentedButtonTheme,
    selectionTheme: selectionTheme,
    sideSheetTheme: sideSheetTheme,
    sliderTheme: sliderTheme,
    snackBarTheme: snackBarTheme,
    splitButtonTheme: splitButtonTheme,
    switchTheme: switchTheme,
    tabTheme: tabTheme,
    textFieldTheme: textFieldTheme,
    timePickerTheme: timePickerTheme,
    buttonGroupTheme: buttonGroupTheme,
    toolbarTheme: toolbarTheme,
    tooltipTheme: tooltipTheme,
  );

  /// Projects this theme onto a Material [ThemeData].
  ThemeData toThemeData() {
    final IconThemeData icons = resolvedIconTheme;
    return ThemeData(
      useMaterial3: useMaterial3,
      colorScheme: colorScheme.toColorScheme(),
      textTheme: textTheme,
      iconTheme: icons,
      primaryIconTheme: icons.copyWith(color: colorScheme.onPrimary),
      visualDensity: VisualDensity(
        horizontal: visualDensity,
        vertical: visualDensity,
      ),
      platform: platform,
      splashColor: splashColor,
      highlightColor: highlightColor,
    );
  }
}

final Expando<M3EThemeData> _materialCache = Expando<M3EThemeData>();
