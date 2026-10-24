import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../res/m3e_date_picker_constants.dart';

/// Header for single- and range-date picker dialogs.
class M3EDatePickerHeader extends StatelessWidget {
  /// M3EDatePickerHeader.
  const M3EDatePickerHeader({
    required this.helpText,
    required this.titleText,
    required this.showTitle,
    required this.orientation,
    this.titleSemanticsLabel,
    this.isShort = false,
    this.alignHelpWithSubHeader = false,
    this.entryModeButton,
    super.key,
  });

  /// helpText.
  final String helpText;

  /// titleText.
  final String titleText;

  /// showTitle.
  final bool showTitle;

  /// titleSemanticsLabel.
  final String? titleSemanticsLabel;

  /// orientation.
  final Orientation orientation;

  /// isShort.
  final bool isShort;

  /// When true (calendar landscape), help sits in the month-label band.
  /// Input mode keeps a compact top so the headline has more room.
  final bool alignHelpWithSubHeader;

  /// Entry-mode toggle: beside title in portrait, at bottom in landscape.
  final Widget? entryModeButton;

  @override
  Widget build(BuildContext context) {
    final theme = M3ETheme.of(context);
    final dateTheme = theme.datePickerTheme;
    final scheme = theme.colorScheme;
    final TextStyle helpStyle = dateTheme.headerHelpStyle(
      theme.typeScale,
      scheme,
    );
    final TextStyle titleStyle = (isShort
        ? dateTheme.headerHeadlineShortStyle
        : dateTheme.headerHeadlineStyle)(theme.typeScale, scheme);

    final Widget title = Semantics(
      label: titleSemanticsLabel ?? titleText,
      child: Text(
        titleText,
        style: titleStyle,
        maxLines: orientation == Orientation.portrait ? 1 : 2,
        overflow: TextOverflow.ellipsis,
      ),
    );

    final Widget help = Text(
      helpText,
      style: helpStyle,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );

    if (orientation == Orientation.landscape) {
      const double landscapePad = M3EDatePickerConstants.headerPaddingLandscape;
      // Match the right content column: EdgeInsets.all(16) around picker +
      // actions. Calendar mode pins help to the month-label band; input mode
      // keeps a compact top so the headline ("Tue, Aug …") has more room.
      const double contentInset = 16;
      final Widget helpBlock = alignHelpWithSubHeader
          ? SizedBox(
              height: M3EDatePickerConstants.subHeaderHeight,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: landscapePad),
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: help,
                ),
              ),
            )
          : Padding(
              padding: const EdgeInsets.symmetric(horizontal: landscapePad),
              child: help,
            );
      return SizedBox(
        width: dateTheme.headerLandscapeWidth,
        child: ColoredBox(
          color: dateTheme.headerBackgroundColor(scheme),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const SizedBox(height: contentInset),
              helpBlock,
              if (!alignHelpWithSubHeader) SizedBox(height: isShort ? 16 : 56),
              if (showTitle)
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: landscapePad,
                    ),
                    child: title,
                  ),
                )
              else
                const Spacer(),
              if (entryModeButton != null)
                SizedBox(
                  height: M3EDatePickerConstants.actionsMinHeight,
                  child: Padding(
                    padding: const EdgeInsetsDirectional.only(start: 8, end: 4),
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: Semantics(container: true, child: entryModeButton),
                    ),
                  ),
                ),
              const SizedBox(height: contentInset),
            ],
          ),
        ),
      );
    }

    return SizedBox(
      height: dateTheme.headerPortraitHeight,
      child: ColoredBox(
        color: dateTheme.headerBackgroundColor(scheme),
        child: Padding(
          padding: const EdgeInsetsDirectional.only(
            start: 24,
            end: 12,
            bottom: 12,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              const SizedBox(height: 16),
              help,
              const Flexible(child: SizedBox(height: 38)),
              Row(
                children: <Widget>[
                  Expanded(
                    child: Visibility(visible: showTitle, child: title),
                  ),
                  if (entryModeButton != null)
                    Semantics(container: true, child: entryModeButton),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Icon button that switches date picker entry mode.
class M3EDatePickerEntryModeButton extends StatelessWidget {
  /// M3EDatePickerEntryModeButton.
  const M3EDatePickerEntryModeButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    super.key,
  });

  /// icon.

  final IconData icon;

  /// tooltip.
  final String tooltip;

  /// onPressed.
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return M3EIconButton(
      variant: M3EIconButtonVariant.standard,
      icon: Icon(icon),
      tooltip: tooltip,
      onPressed: onPressed,
    );
  }
}
