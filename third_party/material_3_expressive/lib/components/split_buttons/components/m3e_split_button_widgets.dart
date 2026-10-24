part of '../m3e_split_buttons.dart';

class _LeadingContent extends StatelessWidget {
  const _LeadingContent({
    required this.size,
    required this.icon,
    required this.label,
    required this.color,
    required this.customSize,
  });

  final M3EButtonSize size;
  final IconData? icon;
  final String? label;
  final Color color;
  final M3EButtonSize? customSize;

  @override
  Widget build(BuildContext context) {
    final splitTheme = M3ETheme.of(context).splitButtonTheme;
    final iconSize = customSize?.iconSize ?? splitTheme.splitIcon(size);
    final lp =
        customSize?.hPadding ?? splitTheme.splitLeadingButtonLeadingSpace(size);
    final rp =
        customSize?.hPadding ??
        splitTheme.splitLeadingButtonTrailingSpace(size);
    final iconBlock = iconSize;
    final gap = customSize?.iconGap ?? splitTheme.splitGapIconToLabel(size);

    final type = M3ETheme.of(context).typeScale;
    final base = switch (size.name) {
      'xs' => type.labelSmall,
      'sm' => type.labelMedium,
      'md' => type.labelLarge,
      'lg' => type.titleMedium,
      'xl' => type.titleLarge,
      _ => type.labelLarge,
    };
    final labelStyle = base.copyWith(
      color: color,
      overflow: TextOverflow.ellipsis,
    );

    return Padding(
      padding: EdgeInsetsDirectional.only(start: lp, end: rp),
      child: _buildLeadingBody(
        iconSize: iconSize,
        iconBlock: iconBlock,
        gap: gap,
        labelStyle: labelStyle,
      ),
    );
  }

  Widget _buildLeadingBody({
    required double iconSize,
    required double iconBlock,
    required double gap,
    required TextStyle labelStyle,
  }) {
    if (icon != null && (label?.isNotEmpty ?? false)) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: iconBlock,
            child: Center(
              child: Icon(icon, size: iconSize, color: color),
            ),
          ),
          SizedBox(width: gap),
          Flexible(
            child: Text(
              label!,
              overflow: TextOverflow.ellipsis,
              style: labelStyle,
            ),
          ),
        ],
      );
    }
    if (icon != null) {
      return SizedBox(
        width: iconBlock,
        child: Center(
          child: Icon(icon, size: iconSize, color: color),
        ),
      );
    }
    return Text(
      label ?? '',
      overflow: TextOverflow.ellipsis,
      style: labelStyle,
    );
  }
}

class _CornerRadii {
  const _CornerRadii({
    required this.topStart,
    required this.bottomStart,
    required this.topEnd,
    required this.bottomEnd,
  });

  final double topStart, bottomStart, topEnd, bottomEnd;

  BorderRadius toBorderRadius(TextDirection direction) {
    return BorderRadiusDirectional.only(
      topStart: Radius.circular(topStart),
      bottomStart: Radius.circular(bottomStart),
      topEnd: Radius.circular(topEnd),
      bottomEnd: Radius.circular(bottomEnd),
    ).resolve(direction);
  }
}
