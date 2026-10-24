import 'package:flutter/widgets.dart';

import '../../foundations/foundations.dart';
import '../cards/m3e_cards.dart';
import '../selection/controllers/m3e_selection_controller.dart';
import 'components/m3e_card_list_item.dart';
import 'components/m3e_expandable_expanded.dart';
import 'components/m3e_expandable_item.dart';
import 'components/m3e_expandable_nest_scope.dart';
import 'components/m3e_expandable_snap_collapse.dart';
import 'components/m3e_list_feature_host.dart';
import 'components/m3e_list_feature_scope.dart';
import 'components/m3e_list_interaction.dart';
import 'components/m3e_list_item_scope.dart';
import 'components/m3e_list_key_target.dart';
import 'components/m3e_list_keyboard.dart';
import 'components/m3e_list_reorder_host.dart';
import 'components/m3e_list_row_surface.dart';
import 'components/m3e_list_swipe_overflow.dart';
import 'components/m3e_list_trailing_override.dart';
import 'components/m3e_list_transform_publisher.dart';
import 'controllers/m3e_dismissible_card_controller.dart';
import 'controllers/m3e_dismissible_list_controller.dart';
import 'controllers/m3e_expandable_list_controller.dart';
import 'enums/m3e_list_enums.dart';
import 'enums/m3e_list_selection_enums.dart';
import 'enums/m3e_list_swipe_edge.dart';
import 'enums/m3e_list_swipe_mode.dart';
import 'models/m3e_list_item_swipe.dart';
import 'models/m3e_list_swipe_action.dart';
import 'styles/m3e_dismissible_list_style.dart';
import 'styles/m3e_expandable_style.dart';
import 'styles/m3e_list_reorder_state.dart';
import 'styles/m3e_list_selection_state.dart';
import 'styles/m3e_list_theme.dart';
import 'utils/m3e_list_immediate_tap.dart';
import 'utils/m3e_list_row_features.dart';
import 'utils/m3e_list_selection_fill.dart';

export 'components/m3e_card_list_item.dart'
    show calculateCardPosition, calculateCardRadius;
export 'components/m3e_expandable_data.dart';
export 'components/m3e_expandable_expanded.dart';
export 'components/m3e_expandable_item.dart';
export 'components/m3e_list_avatar.dart';
export 'components/m3e_list_feature_scope.dart';
export 'components/m3e_list_image.dart';
export 'components/m3e_list_key_target.dart';
export 'components/m3e_list_keyboard.dart';
export 'components/m3e_list_row_surface.dart';
export 'components/m3e_list_video.dart';
export 'controllers/m3e_dismissible_card_controller.dart';
export 'controllers/m3e_dismissible_list_controller.dart';
export 'controllers/m3e_expandable_list_controller.dart';
export 'enums/m3e_expandable_enums.dart';
export 'enums/m3e_list_enums.dart';
export 'enums/m3e_list_selection_enums.dart';
export 'enums/m3e_list_swipe_edge.dart';
export 'enums/m3e_list_swipe_mode.dart';
export 'models/m3e_dismissible_slot.dart';
export 'models/m3e_list_item_swipe.dart';
export 'models/m3e_list_swipe_action.dart';
export 'styles/m3e_dismissible_list_style.dart';
export 'styles/m3e_expandable_style.dart';
export 'styles/m3e_list_card_list_theme.dart';
export 'styles/m3e_list_dismissible_theme.dart';
export 'styles/m3e_list_expandable_theme.dart';
export 'styles/m3e_list_item_theme.dart';
export 'styles/m3e_list_reorder_state.dart';
export 'styles/m3e_list_selection_state.dart';
export 'styles/m3e_list_theme.dart';
export 'utils/m3e_measure_size.dart';

part 'components/m3e_list.dart';
part 'components/m3e_list_build.dart';
part 'components/m3e_list_expand.dart';

/// A Material 3 Expressive list item.
///
/// A single row with optional leading and trailing widgets, a headline, and
/// supporting text. Standalone rows use a flat surface. Inside a segmented
/// list, the parent owns that surface.
class M3EListItem extends StatelessWidget {
  /// M3EListItem.
  const M3EListItem({
    required this.headline,
    this.supportingText,
    this.overline,
    this.trailingText,
    this.leading,
    this.trailing,
    this.onTap,
    this.selected = false,
    this.enabled = true,
    this.showDivider = false,
    this.largeLeading = false,
    this.appearance,
    this.variant,
    this.border,
    this.transform,
    this.swipe,
    this.expanded,
    super.key,
  });

  /// headline.
  final String headline;

  /// supportingText.
  final String? supportingText;

  /// overline.
  final String? overline;

  /// Trailing meta text, such as a count or date.
  final String? trailingText;

  /// leading.
  final Widget? leading;

  /// trailing.
  final Widget? trailing;

  /// onTap.
  final VoidCallback? onTap;

  /// selected.
  final bool selected;

  /// When false, the row uses the disabled tokens and ignores taps.
  final bool enabled;

  /// Draws the optional inset divider under the row.
  final bool showDivider;

  /// Baseline large leading media uses the taller vertical inset.
  final bool largeLeading;

  /// Token set override. Null uses [M3EListItemTheme.appearance].
  final M3EListAppearance? appearance;

  /// Outline variant override; falls back to [M3EListItemTheme.variant].
  final M3ECardVariant? variant;

  /// Outline override; falls back to [M3EListItemTheme.border].
  final BorderSide? border;

  /// Full-screen destination. When set, activating the row morphs into it.
  ///
  /// Null keeps the row's current tap behavior. Works on a standalone item
  /// and on any item inside [M3EList].
  final Widget? transform;

  /// Swipe actions and dismiss for this row. Null means the row does not swipe.
  final M3EListItemSwipe? swipe;

  /// In-place or full-screen expansion. Null means the row does not expand.
  ///
  /// [M3EExpandableExpanded.list] is a nested [M3EList]. The parent joins that
  /// child to the group with embedded corners, surface, and variant.
  final M3EExpandableExpanded? expanded;

  /// Spoken label: headline, then supporting text.
  String get spokenLabel {
    final String? supporting = supportingText;
    if (supporting == null || supporting.isEmpty) {
      return headline;
    }
    final String lower = supporting.length == 1
        ? supporting.toLowerCase()
        : '${supporting[0].toLowerCase()}${supporting.substring(1)}';
    return '$headline, $lower';
  }

  int get _lineCount {
    var lines = 1;
    if (overline != null) {
      lines += 1;
    }
    if (supportingText != null) {
      lines += 1;
    }
    return lines;
  }

  bool get _tall => _lineCount >= 3;

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(builder: _buildItem);
  }

  Widget _buildItem(BuildContext context) {
    final theme = M3ETheme.of(context);
    final M3EListItemTheme itemTheme = _theme(theme);
    final M3EListItemScope? scope = M3EListItemScope.maybeOf(context);
    final embedded = scope != null;
    final Widget body = _buildPaddedBody(
      context,
      theme,
      itemTheme,
      includePadding: scope?.padsChild != true,
    );
    final Widget divided = showDivider
        ? Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[body, _divider(theme.colorScheme, itemTheme)],
          )
        : body;

    final Widget published = M3EListTransformPublisher(
      destination: enabled ? transform : null,
      child: divided,
    );
    if (embedded) {
      return _semantics(context, published);
    }

    final bool rowSelected = _isSelected(context);
    final M3EListFeatureScope? features = M3EListFeatureScope.maybeOf(context);
    final bool selection = features?.selectionEnabled ?? false;
    final bool single =
        selection &&
        features!.selectionState.mode == M3EListSelectionMode.single;
    final BorderRadius radius = _radius(itemTheme, rowSelected);
    final M3ECardVariant rowVariant = variant ?? itemTheme.variant;
    final BorderSide? outline = rowVariant == M3ECardVariant.outlined
        ? (border ?? itemTheme.border)
        : border;

    return M3EListRowSurface(
      variant: rowVariant,
      radius: radius,
      onTap: enabled ? onTap : null,
      enabled: enabled,
      selected: rowSelected,
      border: outline,
      semanticLabel: spokenLabel,
      semanticButton: !selection && (onTap != null || transform != null),
      semanticChecked: selection ? rowSelected : null,
      semanticInMutuallyExclusiveGroup: single,
      index: M3EListItemIndex.maybeOf(context),
      child: published,
    );
  }

  M3EListItemTheme _theme(M3EThemeData theme) {
    final M3EListItemTheme itemTheme = theme.listTheme.item;
    final M3EListAppearance? override = appearance;
    if (override == null || override == itemTheme.appearance) {
      return itemTheme;
    }
    return itemTheme.copyWith(appearance: override);
  }

  bool _isSelected(BuildContext context) {
    if (selected) {
      return true;
    }
    final int? index = M3EListItemIndex.maybeOf(context);
    if (index == null) {
      return false;
    }
    return M3EListFeatureScope.maybeOf(context)?.isSelected(index) ?? false;
  }

  BorderRadius _radius(M3EListItemTheme itemTheme, bool rowSelected) {
    if (rowSelected) {
      return BorderRadius.circular(itemTheme.selectedRadius);
    }
    if (itemTheme.isBaseline || itemTheme.style == M3EListStyle.standard) {
      return BorderRadius.zero;
    }
    return BorderRadius.circular(itemTheme.selectedRadius);
  }

  Widget _semantics(BuildContext context, Widget child) {
    final M3EListFeatureScope? features = M3EListFeatureScope.maybeOf(context);
    final bool selection = features?.selectionEnabled ?? false;
    final bool rowSelected = _isSelected(context);
    return Semantics(
      container: true,
      button: !selection && (onTap != null || transform != null) && enabled,
      checked: selection ? rowSelected : null,
      inMutuallyExclusiveGroup:
          selection &&
          features!.selectionState.mode == M3EListSelectionMode.single,
      enabled: enabled,
      label: spokenLabel,
      excludeSemantics: true,
      child: child,
    );
  }

  Widget _divider(M3EColorScheme scheme, M3EListItemTheme itemTheme) {
    return Padding(
      padding: EdgeInsets.only(
        left: itemTheme.dividerLeadingInset,
        right: itemTheme.dividerTrailingInset,
      ),
      child: SizedBox(
        height: itemTheme.dividerThickness,
        width: double.infinity,
        child: ColoredBox(color: itemTheme.resolveDivider(scheme)),
      ),
    );
  }

  Widget _buildPaddedBody(
    BuildContext context,
    M3EThemeData theme,
    M3EListItemTheme itemTheme, {
    required bool includePadding,
  }) {
    final bool tall = _tall;
    final double vertical = itemTheme.verticalPaddingFor(
      tall: tall,
      largeLeading: largeLeading,
    );
    final double height = itemTheme.heightForLines(_lineCount);
    final double slotHeight = includePadding ? height - vertical * 2 : height;
    final Widget row = _buildRow(
      context,
      theme,
      itemTheme,
      tall: tall,
      slotHeight: slotHeight,
      iconTop: itemTheme.leadingIconTopFor(tall: tall || largeLeading),
      contentTop: includePadding ? vertical : 0,
    );
    if (!includePadding) {
      return ConstrainedBox(
        constraints: BoxConstraints(minHeight: height),
        child: row,
      );
    }
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: height),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: itemTheme.horizontalPadding,
          vertical: vertical,
        ),
        child: row,
      ),
    );
  }

  Widget _buildRow(
    BuildContext context,
    M3EThemeData theme,
    M3EListItemTheme itemTheme, {
    required bool tall,
    required double slotHeight,
    required double iconTop,
    required double contentTop,
  }) {
    final bool rowSelected = _isSelected(context);
    final bool stateIcon = _hasRowIconState(context);
    final M3EColorScheme scheme = theme.colorScheme;
    final Color leadingIcons = itemTheme.iconColor(
      scheme,
      selected: rowSelected,
      stateIcon: rowSelected && stateIcon,
    );
    final Color trailingIcons = itemTheme.iconColor(
      scheme,
      selected: rowSelected,
      stateIcon: rowSelected && stateIcon,
      trailing: true,
    );
    final double iconSize = itemTheme.resolvedIconSize();
    final double gap = itemTheme.resolvedGap();
    final int? index = M3EListItemIndex.maybeOf(context);
    final Widget? resolvedLeading = m3eResolveListLeading(
      context: context,
      index: index,
      leading: leading,
    );
    final Widget? resolvedTrailing = _resolveRowTrailing(context, index);
    final CrossAxisAlignment rowAlign = tall
        ? CrossAxisAlignment.start
        : CrossAxisAlignment.center;
    final Widget text = _buildText(theme, itemTheme, rowSelected);
    final Widget? leadingSlot = _resolveLeadingSlot(
      resolvedLeading,
      itemTheme,
      slotHeight: slotHeight,
      iconTop: iconTop,
      contentTop: contentTop,
    );

    return Row(
      crossAxisAlignment: rowAlign,
      children: <Widget>[
        if (leadingSlot != null) ...<Widget>[
          IconTheme.merge(
            data: IconThemeData(color: leadingIcons, size: iconSize),
            child: leadingSlot,
          ),
          SizedBox(width: gap),
        ],
        Expanded(
          child: tall
              ? text
              : Align(alignment: Alignment.centerLeft, child: text),
        ),
        if (trailingText != null) ...<Widget>[
          SizedBox(width: gap),
          Text(
            trailingText!,
            style: itemTheme.trailingStyle(
              theme.typeScale,
              scheme,
              selected: rowSelected,
            ),
          ),
        ],
        if (resolvedTrailing != null) ...<Widget>[
          SizedBox(width: gap),
          IconTheme.merge(
            data: IconThemeData(color: trailingIcons, size: iconSize),
            child: resolvedTrailing,
          ),
        ],
      ],
    );
  }

  bool _hasRowIconState(BuildContext context) {
    final M3EListInteractionScope? interaction =
        M3EListInteractionScope.maybeOf(context);
    return interaction != null &&
        (interaction.state.hovered ||
            interaction.state.focused ||
            interaction.state.pressed ||
            interaction.state.dragged);
  }

  Widget? _resolveRowTrailing(BuildContext context, int? index) {
    final M3EListTrailingOverride? trailingOverride =
        M3EListTrailingOverride.maybeOf(context);
    final M3EListSwipeOverflow? overflow =
        trailingOverride == null && trailing == null
        ? M3EListSwipeOverflow.maybeOf(context)
        : null;
    final Widget? resolvedTrailing = m3eResolveListTrailing(
      context: context,
      trailing: trailing,
    );
    final VoidCallback? reveal = overflow?.onReveal;
    if (trailingOverride == null &&
        resolvedTrailing == null &&
        reveal != null &&
        index != null) {
      return M3EListKeyTarget(
        index: index,
        // After a leading selection action (slot 1).
        slot: 2,
        onActivate: reveal,
        child: GestureDetector(
          onTap: reveal,
          child: const Icon(M3EIcons.more_vert),
        ),
      );
    }
    return resolvedTrailing;
  }

  Widget? _resolveLeadingSlot(
    Widget? resolvedLeading,
    M3EListItemTheme itemTheme, {
    required double slotHeight,
    required double iconTop,
    required double contentTop,
  }) {
    if (resolvedLeading == null || !itemTheme.isBaseline || slotHeight <= 0) {
      return resolvedLeading;
    }
    final double extraTop = iconTop > contentTop ? iconTop - contentTop : 0;
    return SizedBox(
      height: slotHeight,
      child: Align(
        alignment: Alignment.topCenter,
        child: Padding(
          padding: EdgeInsets.only(top: extraTop),
          child: resolvedLeading,
        ),
      ),
    );
  }

  Widget _buildText(
    M3EThemeData theme,
    M3EListItemTheme itemTheme,
    bool rowSelected,
  ) {
    final M3EColorScheme scheme = theme.colorScheme;
    final M3ETypeScale type = theme.typeScale;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (overline != null)
          Text(
            overline!,
            style: itemTheme.overlineStyle(type, scheme, selected: rowSelected),
          ),
        Text(
          headline,
          style: itemTheme.headlineStyle(type, scheme, selected: rowSelected),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        if (supportingText != null)
          Text(
            supportingText!,
            style: itemTheme.supportingStyle(
              type,
              scheme,
              selected: rowSelected,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
      ],
    );
  }
}
