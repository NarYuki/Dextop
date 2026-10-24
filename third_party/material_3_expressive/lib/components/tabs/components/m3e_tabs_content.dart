part of '../m3e_tabs.dart';

/// Icon/label content composition and badge placement for [M3ETabs].
extension _M3ETabsContent on _M3ETabsState {
  Widget _content(
    M3ETabTheme theme,
    M3ETab tab,
    bool selected,
    bool interacting,
  ) {
    final data = M3ETheme.of(context);
    final scheme = data.colorScheme;
    final Color color = theme.contentColor(
      scheme,
      variant: widget.variant,
      selected: selected,
      interacting: interacting,
    );
    final Widget? icon = tab.icon == null
        ? null
        : IconTheme.merge(
            data: IconThemeData(color: color, size: theme.iconSize),
            child: tab.icon!,
          );
    final Widget? label = tab.label == null
        ? null
        : Text(
            tab.label!,
            maxLines: theme.labelMaxLines,
            overflow: TextOverflow.ellipsis,
            style: theme.labelStyle(
              data.typeScale,
              scheme,
              selected: selected,
              variant: widget.variant,
              interacting: interacting,
            ),
          );
    final bool stack =
        widget.variant == M3ETabsVariant.primary &&
        icon != null &&
        label != null;
    final bool inlineBadge = _inlineBadge(tab);
    final Widget? badgeIcon = icon == null || inlineBadge
        ? icon
        : _badge(tab, child: icon);
    if (stack) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          badgeIcon!,
          SizedBox(height: theme.stackedIconLabelGap),
          _labeled(theme, tab, label, inlineBadge),
        ],
      );
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        ?badgeIcon,
        if (badgeIcon != null && label != null)
          SizedBox(width: theme.inlineIconLabelGap),
        if (label != null) _labeled(theme, tab, label, inlineBadge),
        if (label == null && inlineBadge) _badge(tab, inline: true),
      ],
    );
  }

  /// Secondary badges stay after the label, including when an icon is set.
  bool _inlineBadge(M3ETab tab) {
    return tab.hasBadge &&
        (widget.variant == M3ETabsVariant.secondary ||
            tab.badgeInline ||
            tab.icon == null);
  }

  Widget _labeled(
    M3ETabTheme theme,
    M3ETab tab,
    Widget label,
    bool inlineBadge,
  ) {
    if (!inlineBadge) {
      return label;
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Flexible(child: label),
        SizedBox(width: theme.inlineBadgeGap),
        _badge(tab, inline: true),
      ],
    );
  }

  Widget _badge(M3ETab tab, {Widget? child, bool inline = false}) {
    final String? raw = tab.badgeLabel;
    final String? label = raw == null
        ? null
        : (raw.length <= 4 ? raw : raw.substring(0, 4));
    final bool dot = tab.badgeDot;
    final badges = M3ETheme.of(context).badgeTheme;
    final double mark = dot ? badges.dotSize : badges.labelMinSize;
    return M3EBadge(
      showDot: dot,
      label: dot ? null : label,
      count: dot || label != null ? null : tab.badgeCount,
      // Null keeps the badge theme corner placement, so the mark sits on the
      // icon and the icon and label stay where they are.
      offset: inline ? Offset(0, mark) : null,
      alignment: inline
          ? M3EBadgeAlignment.topCenter
          : M3EBadgeAlignment.topRight,
      child: child ?? SizedBox(width: dot ? 6 : 32, height: mark),
    );
  }
}
