part of '../m3e_dialogs.dart';

extension _M3EDialogLayout on M3EDialog {
  Widget _buildDialog(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EDialogTheme dialogTheme = theme.dialogTheme;
    final M3EDialogAppearance appearance = dialogTheme.appearance;
    final M3EColorScheme scheme = theme.colorScheme;
    return Semantics(
      scopesRoute: true,
      namesRoute: true,
      explicitChildNodes: true,
      role: SemanticsRole.alertDialog,
      label: semanticLabel ?? title,
      child: Container(
        constraints: BoxConstraints(
          minWidth: dialogTheme.minWidth,
          maxWidth: dialogTheme.maxWidth,
        ),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: m3eDialogTinted(
            appearance.resolveContainer(scheme),
            appearance.surfaceTintColor,
            dialogTheme.elevation,
          ),
          borderRadius: dialogTheme.borderRadius,
          boxShadow: M3EElevation.shadows(
            dialogTheme.elevation,
            shadowColor: scheme.shadow,
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: icon == null
              ? CrossAxisAlignment.start
              : CrossAxisAlignment.center,
          children: _buildChildren(theme),
        ),
      ),
    );
  }

  List<Widget> _buildChildren(M3EThemeData theme) {
    final M3EDialogTheme dialogTheme = theme.dialogTheme;
    final EdgeInsets padding = dialogTheme.padding;
    final hasContent = content != null;
    final bool hasActions = actions.isNotEmpty || leadingAction != null;
    final bool showTop = topDivider && (hasContent || hasActions);
    final bool showBottom = bottomDivider && hasActions;
    final double headerBottom = !(hasContent || hasActions)
        ? padding.bottom
        : (hasContent || showTop ? dialogTheme.gapAfterTitle : 0);

    return <Widget>[
      Padding(
        padding: padding.copyWith(bottom: headerBottom),
        child: _buildHeader(theme),
      ),
      if (showTop) _divider(theme),
      if (hasContent)
        Flexible(
          child: SingleChildScrollView(
            controller: scrollController,
            primary: false,
            padding:
                contentPadding ??
                EdgeInsets.fromLTRB(
                  padding.left,
                  showTop ? dialogTheme.gapAfterTitle : 0,
                  padding.right,
                  hasActions
                      ? (showBottom ? dialogTheme.gapBeforeActions : 0)
                      : padding.bottom,
                ),
            child: DefaultTextStyle(
              style: dialogTheme.appearance.resolveSupporting(theme),
              child: content!,
            ),
          ),
        ),
      if (showBottom) _divider(theme),
      if (hasActions)
        Padding(
          padding: padding.copyWith(top: dialogTheme.gapBeforeActions),
          child: M3EDialogActions(
            actions: actions,
            leadingAction: leadingAction,
          ),
        ),
    ];
  }

  Widget _buildHeader(M3EThemeData theme) {
    final M3EDialogTheme dialogTheme = theme.dialogTheme;
    final M3EDialogAppearance appearance = dialogTheme.appearance;
    final TextAlign align = icon == null ? TextAlign.start : TextAlign.center;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: icon == null
          ? CrossAxisAlignment.start
          : CrossAxisAlignment.center,
      children: <Widget>[
        if (icon != null) ...<Widget>[
          IconTheme.merge(
            data: IconThemeData(
              color: appearance.resolveIcon(theme.colorScheme),
              size: dialogTheme.iconSize,
            ),
            child: icon!,
          ),
          SizedBox(height: dialogTheme.gapAfterIcon),
        ],
        M3EDialogHeadline(
          text: title,
          style: appearance.resolveHeadline(theme),
          textAlign: align,
          maxLines: titleMaxLines,
        ),
        if (subhead != null)
          Text(
            subhead!,
            style: appearance.resolveSubhead(theme),
            textAlign: align,
          ),
      ],
    );
  }

  Widget _divider(M3EThemeData theme) {
    final M3EDialogTheme dialogTheme = theme.dialogTheme;
    return M3EDivider(
      color: dialogTheme.appearance.resolveDivider(theme.colorScheme),
      thickness: dialogTheme.dividerThickness,
    );
  }
}
