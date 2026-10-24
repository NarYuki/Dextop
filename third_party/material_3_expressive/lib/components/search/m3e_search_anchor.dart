import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import '../icon_buttons/m3e_icon_buttons.dart';
import 'components/m3e_search_anchor_scope.dart';
import 'components/m3e_search_view.dart';
import 'controllers/m3e_search_controller.dart';
import 'enums/m3e_search_enums.dart';
import 'm3e_search_bar.dart';
import 'models/m3e_search_anchor_surface.dart';
import 'res/m3e_search_constants.dart';
import 'utils/m3e_search_spring.dart';

/// Manages a search view route opened from a search bar or custom anchor.
class M3ESearchAnchor extends StatefulWidget {
  /// M3ESearchAnchor.
  const M3ESearchAnchor({
    required this.builder,
    required this.suggestionsBuilder,
    super.key,
    this.isFullScreen,
    this.searchController,
    this.viewBuilder,
    this.viewLeading,
    this.viewTrailing,
    this.viewHintText,
    this.viewBackgroundColor,
    this.viewElevation,
    this.viewSurfaceTintColor,
    this.viewSide,
    this.viewShape,
    this.viewBarPadding,
    this.headerHeight,
    this.headerTextStyle,
    this.headerHintStyle,
    this.dividerColor,
    this.viewConstraints,
    this.viewPadding,
    this.shrinkWrap,
    this.textCapitalization,
    this.viewOnChanged,
    this.viewOnSubmitted,
    this.viewOnClose,
    this.viewOnOpen,
    this.textInputAction,
    this.keyboardType,
    this.enabled = true,
    this.smartDashesType,
    this.smartQuotesType,
    this.viewStyle,
    this.showViewClearButton = true,
    this.suggestionsAnnouncementBuilder,
    this.scrimColor,
  });

  /// Creates an anchor with a default [M3ESearchBar] child.
  factory M3ESearchAnchor.bar({
    Key? key,
    Widget? barLeading,
    Iterable<Widget>? barTrailing,
    String? barHintText,
    GestureTapCallback? onTap,
    ValueChanged<String>? onSubmitted,
    ValueChanged<String>? onChanged,
    VoidCallback? onClose,
    VoidCallback? onOpen,
    WidgetStateProperty<double?>? barElevation,
    WidgetStateProperty<Color?>? barBackgroundColor,
    WidgetStateProperty<Color?>? barOverlayColor,
    WidgetStateProperty<BorderSide?>? barSide,
    WidgetStateProperty<OutlinedBorder?>? barShape,
    WidgetStateProperty<EdgeInsetsGeometry?>? barPadding,
    EdgeInsetsGeometry? viewBarPadding,
    WidgetStateProperty<TextStyle?>? barTextStyle,
    WidgetStateProperty<TextStyle?>? barHintStyle,
    M3ESearchViewBuilder? viewBuilder,
    Widget? viewLeading,
    Iterable<Widget>? viewTrailing,
    String? viewHintText,
    Color? viewBackgroundColor,
    double? viewElevation,
    BorderSide? viewSide,
    OutlinedBorder? viewShape,
    double? viewHeaderHeight,
    TextStyle? viewHeaderTextStyle,
    TextStyle? viewHeaderHintStyle,
    Color? dividerColor,
    BoxConstraints? constraints,
    BoxConstraints? viewConstraints,
    EdgeInsetsGeometry? viewPadding,
    bool? shrinkWrap,
    bool? isFullScreen,
    bool expandOnFocus = true,
    double? expandRestPadding,
    required M3ESearchController searchController,
    TextCapitalization textCapitalization = TextCapitalization.none,
    required M3ESearchSuggestionsBuilder suggestionsBuilder,
    TextInputAction? textInputAction,
    TextInputType? keyboardType,
    EdgeInsets scrollPadding = const EdgeInsets.all(20),
    EditableTextContextMenuBuilder contextMenuBuilder =
        m3eDefaultSearchContextMenuBuilder,
    bool enabled = true,
    SmartDashesType? smartDashesType,
    SmartQuotesType? smartQuotesType,
    AlignmentGeometry barAlignment = AlignmentDirectional.centerStart,
    bool? wrapActions,
    Widget? barAvatar,
    M3ESearchViewStyle? viewStyle,
    bool showViewClearButton = true,
    M3ESearchAnnouncementBuilder? suggestionsAnnouncementBuilder,
    Color? scrimColor,
  }) {
    return M3ESearchAnchor(
      key: key,
      isFullScreen: isFullScreen,
      searchController: searchController,
      viewBuilder: viewBuilder,
      viewLeading: viewLeading,
      viewTrailing: viewTrailing,
      viewHintText: viewHintText ?? barHintText,
      viewBackgroundColor: viewBackgroundColor,
      viewElevation: viewElevation,
      viewSide: viewSide,
      viewShape: viewShape,
      viewBarPadding: viewBarPadding,
      headerHeight: viewHeaderHeight,
      headerTextStyle: viewHeaderTextStyle,
      headerHintStyle: viewHeaderHintStyle,
      dividerColor: dividerColor,
      viewConstraints: viewConstraints,
      viewPadding: viewPadding,
      shrinkWrap: shrinkWrap,
      textCapitalization: textCapitalization,
      viewOnSubmitted: onSubmitted,
      viewOnChanged: onChanged,
      viewOnClose: onClose,
      viewOnOpen: onOpen,
      textInputAction: textInputAction,
      keyboardType: keyboardType,
      enabled: enabled,
      smartDashesType: smartDashesType,
      smartQuotesType: smartQuotesType,
      viewStyle: viewStyle,
      showViewClearButton: showViewClearButton,
      suggestionsAnnouncementBuilder: suggestionsAnnouncementBuilder,
      scrimColor: scrimColor,
      suggestionsBuilder: suggestionsBuilder,
      builder: (BuildContext context, M3ESearchController controller) {
        return _M3ESearchAnchorBar(
          controller: controller,
          constraints: constraints,
          barLeading: barLeading,
          barTrailing: barTrailing,
          barAvatar: barAvatar,
          barHintText: barHintText,
          barAlignment: barAlignment,
          wrapActions: wrapActions,
          onTap: onTap,
          barElevation: barElevation,
          barBackgroundColor: barBackgroundColor,
          barOverlayColor: barOverlayColor,
          barSide: barSide,
          barShape: barShape,
          barPadding: barPadding,
          barTextStyle: barTextStyle,
          barHintStyle: barHintStyle,
          expandOnFocus: expandOnFocus,
          expandRestPadding: expandRestPadding,
          textCapitalization: textCapitalization,
          textInputAction: textInputAction,
          keyboardType: keyboardType,
          scrollPadding: scrollPadding,
          contextMenuBuilder: contextMenuBuilder,
          smartDashesType: smartDashesType,
          smartQuotesType: smartQuotesType,
        );
      },
    );
  }

  /// Full-screen (true) or docked (false) layout.
  ///
  /// Null picks full-screen below the view theme compact breakpoint (600)
  /// and docked at medium and expanded widths, swapping live on resize.
  final bool? isFullScreen;

  /// searchController.
  final M3ESearchController? searchController;

  /// builder.
  final M3ESearchAnchorChildBuilder builder;

  /// suggestionsBuilder.
  final M3ESearchSuggestionsBuilder suggestionsBuilder;

  /// viewBuilder.
  final M3ESearchViewBuilder? viewBuilder;

  /// viewLeading.
  final Widget? viewLeading;

  /// viewTrailing.
  final Iterable<Widget>? viewTrailing;

  /// viewHintText.
  final String? viewHintText;

  /// viewBackgroundColor.
  final Color? viewBackgroundColor;

  /// viewElevation.
  final double? viewElevation;

  /// viewSurfaceTintColor.
  final Color? viewSurfaceTintColor;

  /// viewSide.
  final BorderSide? viewSide;

  /// viewShape.
  final OutlinedBorder? viewShape;

  /// viewBarPadding.
  final EdgeInsetsGeometry? viewBarPadding;

  /// headerHeight.
  final double? headerHeight;

  /// headerTextStyle.
  final TextStyle? headerTextStyle;

  /// headerHintStyle.
  final TextStyle? headerHintStyle;

  /// dividerColor.
  final Color? dividerColor;

  /// viewConstraints.
  final BoxConstraints? viewConstraints;

  /// viewPadding.
  final EdgeInsetsGeometry? viewPadding;

  /// shrinkWrap.
  final bool? shrinkWrap;

  /// textCapitalization.
  final TextCapitalization? textCapitalization;

  /// viewOnChanged.
  final ValueChanged<String>? viewOnChanged;

  /// viewOnSubmitted.
  final ValueChanged<String>? viewOnSubmitted;

  /// viewOnClose.
  final VoidCallback? viewOnClose;

  /// viewOnOpen.
  final VoidCallback? viewOnOpen;

  /// textInputAction.
  final TextInputAction? textInputAction;

  /// keyboardType.
  final TextInputType? keyboardType;

  /// enabled.
  final bool enabled;

  /// smartDashesType.
  final SmartDashesType? smartDashesType;

  /// smartQuotesType.
  final SmartQuotesType? smartQuotesType;

  /// Contained (default) or divided view style. Null uses the view theme.
  final M3ESearchViewStyle? viewStyle;

  /// Whether the view header shows a clear (X) action while it has text.
  final bool showViewClearButton;

  /// Screen reader text sent when suggestions or results change.
  ///
  /// Defaults to "N results available".
  final M3ESearchAnnouncementBuilder? suggestionsAnnouncementBuilder;

  /// Docked scrim color. Defaults to scrim at 0.32.
  final Color? scrimColor;

  @override
  State<M3ESearchAnchor> createState() => _M3ESearchAnchorState();
}

class _M3ESearchAnchorBar extends StatefulWidget {
  const _M3ESearchAnchorBar({
    required this.controller,
    this.constraints,
    this.barLeading,
    this.barTrailing,
    this.barAvatar,
    this.barHintText,
    this.barAlignment = AlignmentDirectional.centerStart,
    this.wrapActions,
    this.onTap,
    this.barElevation,
    this.barBackgroundColor,
    this.barOverlayColor,
    this.barSide,
    this.barShape,
    this.barPadding,
    this.barTextStyle,
    this.barHintStyle,
    this.expandOnFocus = true,
    this.expandRestPadding,
    this.textCapitalization = TextCapitalization.none,
    this.textInputAction,
    this.keyboardType,
    this.scrollPadding = const EdgeInsets.all(20),
    this.contextMenuBuilder = m3eDefaultSearchContextMenuBuilder,
    this.smartDashesType,
    this.smartQuotesType,
  });

  final M3ESearchController controller;
  final BoxConstraints? constraints;
  final Widget? barLeading;
  final Iterable<Widget>? barTrailing;
  final Widget? barAvatar;
  final String? barHintText;
  final AlignmentGeometry barAlignment;
  final bool? wrapActions;
  final GestureTapCallback? onTap;
  final WidgetStateProperty<double?>? barElevation;
  final WidgetStateProperty<Color?>? barBackgroundColor;
  final WidgetStateProperty<Color?>? barOverlayColor;
  final WidgetStateProperty<BorderSide?>? barSide;
  final WidgetStateProperty<OutlinedBorder?>? barShape;
  final WidgetStateProperty<EdgeInsetsGeometry?>? barPadding;
  final WidgetStateProperty<TextStyle?>? barTextStyle;
  final WidgetStateProperty<TextStyle?>? barHintStyle;
  final bool expandOnFocus;
  final double? expandRestPadding;
  final TextCapitalization textCapitalization;
  final TextInputAction? textInputAction;
  final TextInputType? keyboardType;
  final EdgeInsets scrollPadding;
  final EditableTextContextMenuBuilder contextMenuBuilder;
  final SmartDashesType? smartDashesType;
  final SmartQuotesType? smartQuotesType;

  @override
  State<_M3ESearchAnchorBar> createState() => _M3ESearchAnchorBarState();
}

class _M3ESearchAnchorBarState extends State<_M3ESearchAnchorBar> {
  // Closed anchor is a keyboard Tab stop (opens the search view). Editing
  // happens in the view's search bar, so this field stays read-only.
  late final FocusNode _focusNode = FocusNode(debugLabel: 'M3ESearchAnchorBar');

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleControllerChanged);
  }

  @override
  void didUpdateWidget(covariant _M3ESearchAnchorBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_handleControllerChanged);
      widget.controller.addListener(_handleControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleControllerChanged);
    _focusNode.dispose();
    super.dispose();
  }

  void _handleControllerChanged() {
    setState(() {});
  }

  void _openView() {
    if (!widget.controller.isOpen) {
      widget.controller.openView();
    }
  }

  List<Widget>? _buildTrailing() {
    if (widget.barTrailing != null) {
      return widget.barTrailing!.toList();
    }
    if (widget.controller.text.isEmpty) {
      return null;
    }
    return <Widget>[
      M3EIconButton(
        variant: M3EIconButtonVariant.standard,
        icon: const Icon(M3EIcons.close),
        tooltip: M3ESearchConstants.clearButtonTooltip,
        onPressed: widget.controller.clear,
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.enter): _openView,
        const SingleActivator(LogicalKeyboardKey.numpadEnter): _openView,
        const SingleActivator(LogicalKeyboardKey.space): _openView,
      },
      child: M3ESearchBar(
        focusNode: _focusNode,
        constraints: widget.constraints,
        controller: widget.controller,
        readOnly: true,
        expandOnFocus: widget.expandOnFocus,
        expandRestPadding: widget.expandRestPadding,
        onTap: () {
          _openView();
          widget.onTap?.call();
        },
        hintText: widget.barHintText,
        hintStyle: widget.barHintStyle,
        textStyle: widget.barTextStyle,
        elevation: widget.barElevation,
        backgroundColor: widget.barBackgroundColor,
        overlayColor: widget.barOverlayColor,
        side: widget.barSide,
        shape: widget.barShape,
        padding: widget.barPadding,
        alignment: widget.barAlignment,
        wrapActions: widget.wrapActions,
        leading: widget.barLeading ?? const Icon(M3EIcons.search),
        trailing: _buildTrailing(),
        avatar: widget.barAvatar,
        textCapitalization: widget.textCapitalization,
        textInputAction: widget.textInputAction,
        keyboardType: widget.keyboardType,
        scrollPadding: widget.scrollPadding,
        contextMenuBuilder: widget.contextMenuBuilder,
        smartDashesType: widget.smartDashesType,
        smartQuotesType: widget.smartQuotesType,
      ),
    );
  }
}

class _M3ESearchAnchorState extends State<M3ESearchAnchor>
    implements M3ESearchAnchorHandle {
  bool _anchorIsVisible = true;
  final M3ESearchAnchorSurface _surface = M3ESearchAnchorSurface();
  bool _suppressFocusOpen = false;
  final GlobalKey _anchorKey = GlobalKey();
  M3ESearchController? _internalSearchController;
  M3ESearchViewRoute? _route;

  M3ESearchController get _searchController =>
      widget.searchController ??
      (_internalSearchController ??= M3ESearchController());

  @override
  bool get viewIsOpen => !_anchorIsVisible;

  @override
  bool get suppressFocusOpen => _suppressFocusOpen;

  @override
  void initState() {
    super.initState();
    _searchController.anchor = this;
  }

  @override
  void didUpdateWidget(M3ESearchAnchor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.searchController != widget.searchController) {
      oldWidget.searchController?.detach(this);
      _searchController.anchor = this;
    }
  }

  @override
  void dispose() {
    widget.searchController?.detach(this);
    _internalSearchController?.detach(this);
    final usingExternalController = widget.searchController != null;
    if (_route?.navigator != null) {
      if (_route!.isActive) {
        _route!.navigator?.removeRoute(_route!);
      }
      if (!usingExternalController) {
        _internalSearchController?.dispose();
      }
    } else {
      _internalSearchController?.dispose();
    }
    super.dispose();
  }

  @override
  void openView() {
    if (viewIsOpen) {
      return;
    }
    // Drop any anchor field focus before the view mounts so the soft keyboard
    // does not flash on the read-only bar then hide when the route takes over.
    FocusManager.instance.primaryFocus?.unfocus();
    final NavigatorState navigator = Navigator.of(context);
    _route = M3ESearchViewRoute(
      anchorKey: _anchorKey,
      surface: _surface,
      searchController: _searchController,
      suggestionsBuilder: widget.suggestionsBuilder,
      isFullScreen: widget.isFullScreen,
      transformDuration: m3eSearchSpringSettle(
        M3ETheme.of(context).searchViewTheme.containerTransformSpring,
      ),
      viewStyle: widget.viewStyle,
      showViewClearButton: widget.showViewClearButton,
      announcementBuilder: widget.suggestionsAnnouncementBuilder,
      scrimColor: widget.scrimColor,
      toggleVisibility: _toggleVisibility,
      viewBuilder: widget.viewBuilder,
      viewLeading: widget.viewLeading,
      viewTrailing: widget.viewTrailing,
      viewHintText: widget.viewHintText,
      viewBackgroundColor: widget.viewBackgroundColor,
      viewElevation: widget.viewElevation,
      viewSurfaceTintColor: widget.viewSurfaceTintColor,
      viewSide: widget.viewSide,
      viewShape: widget.viewShape,
      viewBarPadding: widget.viewBarPadding,
      viewHeaderHeight: widget.headerHeight,
      viewHeaderTextStyle: widget.headerTextStyle,
      viewHeaderHintStyle: widget.headerHintStyle,
      dividerColor: widget.dividerColor,
      viewConstraints: widget.viewConstraints,
      viewPadding: widget.viewPadding,
      shrinkWrap: widget.shrinkWrap,
      textCapitalization: widget.textCapitalization,
      viewOnChanged: widget.viewOnChanged,
      viewOnSubmitted: widget.viewOnSubmitted,
      viewOnOpen: widget.viewOnOpen,
      viewOnClose: widget.viewOnClose,
      textInputAction: widget.textInputAction,
      keyboardType: widget.keyboardType,
      smartDashesType: widget.smartDashesType,
      smartQuotesType: widget.smartQuotesType,
    );
    navigator.push(_route!);
  }

  @override
  void closeView(String? selectedText) {
    if (selectedText != null) {
      _searchController.value = TextEditingValue(text: selectedText);
    }
    Navigator.of(context).pop();
  }

  bool _toggleVisibility() {
    final bool viewClosing = !_anchorIsVisible;
    setState(() => _anchorIsVisible = !_anchorIsVisible);
    if (viewClosing) {
      _suppressFocusOpen = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_anchorKey.currentContext != null) {
          FocusManager.instance.primaryFocus?.unfocus();
        }
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _suppressFocusOpen = false;
          }
        });
      });
    }
    return _anchorIsVisible;
  }

  double _opacity() {
    if (!widget.enabled) {
      return M3ESearchConstants.disabledOpacity;
    }
    return _anchorIsVisible ? 1 : 0;
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      key: _anchorKey,
      opacity: _opacity(),
      duration: M3ESearchConstants.anchorFadeDuration,
      // Hidden anchors must not keep an EditableText text-input client or Tab
      // stop while the search view owns the same controller.
      child: ExcludeFocus(
        excluding: !_anchorIsVisible,
        child: IgnorePointer(
          ignoring: !widget.enabled || !_anchorIsVisible,
          child: GestureDetector(
            onTap: openView,
            behavior: HitTestBehavior.translucent,
            child: M3ESearchAnchorScope(
              surface: _surface,
              child: widget.builder(context, _searchController),
            ),
          ),
        ),
      ),
    );
  }
}
