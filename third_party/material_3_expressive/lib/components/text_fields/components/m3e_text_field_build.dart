part of '../m3e_text_fields.dart';

/// Layout and paint helpers for [M3ETextField].
extension _M3ETextFieldBuild on _M3ETextFieldState {
  bool get _outlined => widget.variant == M3ETextFieldVariant.outlined;

  /// Row alignment for [alignment]; [baseline] lines text up on its first line.
  static CrossAxisAlignment _crossAxisFor(
    M3ETextFieldSlotAlignment alignment, {
    bool baseline = false,
  }) {
    return switch (alignment) {
      M3ETextFieldSlotAlignment.firstLine =>
        baseline ? CrossAxisAlignment.baseline : CrossAxisAlignment.start,
      M3ETextFieldSlotAlignment.center => CrossAxisAlignment.center,
      M3ETextFieldSlotAlignment.bottom => CrossAxisAlignment.end,
    };
  }

  Widget _buildInteractive(M3EThemeData theme, M3ETextFieldTheme fieldTheme) {
    final bool enabled = widget.enabled;
    Widget field = ListenableBuilder(
      listenable: _motion,
      builder: (BuildContext context, Widget? _) =>
          _buildContainer(theme, fieldTheme),
    );
    // Outlined labels float on the top edge; keep them inside the bounds.
    if (_outlined && widget.label != null) {
      final double floatingLine = M3ETextFieldTheme.lineHeightOf(
        fieldTheme.resolveFloatingLabelStyle(theme.typeScale),
      );
      field = Padding(
        padding: EdgeInsets.only(top: floatingLine / 2),
        child: field,
      );
    }
    return TextFieldTapRegion(
      enabled: enabled,
      onTapOutside:
          widget.onTapOutside ?? M3EFocus.tapOutsideHandler(_focusNode),
      child: ExcludeFocus(
        excluding: !enabled,
        child: MouseRegion(
          cursor:
              widget.mouseCursor ??
              (enabled ? SystemMouseCursors.text : SystemMouseCursors.basic),
          onEnter: (_) => _updateHover(hovered: true),
          onExit: (_) => _updateHover(hovered: false),
          child: Listener(
            behavior: HitTestBehavior.translucent,
            // Pointer input hides keyboard focus rings right away; a tap on
            // a focused field may not schedule the frame a deferred
            // notification waits for. The field tree is stable, so the
            // rebuild cannot cancel the tap.
            onPointerDown: (_) => M3EFocusInteraction.instance
                .notePointerInteraction(immediate: true),
            child: IgnorePointer(
              ignoring: !enabled,
              child: _selectionBuilder.buildGestureDetector(
                behavior: HitTestBehavior.translucent,
                child: M3EFocusRing(
                  focused: _showFocusRing,
                  radius: fieldTheme.shapeFor(widget.variant),
                  color: _toColors?.focusRing,
                  width: fieldTheme.focusRingWidth,
                  gap: fieldTheme.focusRingGap,
                  child: field,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContainer(M3EThemeData theme, M3ETextFieldTheme fieldTheme) {
    final M3ETextFieldColors colors = _currentColors;
    final int density = _densityOf(fieldTheme);
    final double height = fieldTheme.heightFor(density);
    final List<Widget> trailing = _trailingSlots(fieldTheme, colors, height);
    final double start = widget.leading == null
        ? fieldTheme.horizontalPadding
        : fieldTheme.iconSlotWidth + fieldTheme.iconSlotTextPadding;
    final double end = trailing.isEmpty
        ? fieldTheme.horizontalPadding
        : fieldTheme.iconSlotWidth * trailing.length +
              fieldTheme.iconSlotTextPadding;
    final double progress = _labelMotion.value;
    final _M3ETextFieldNotch notch = _notch(theme, fieldTheme, start, progress);

    return CustomPaint(
      painter: M3ETextFieldContainerPainter(
        outlined: _outlined,
        shape: fieldTheme.shapeFor(widget.variant),
        containerColor: colors.container,
        stateLayerColor: colors.stateLayer,
        strokeColor: colors.stroke,
        strokeWidth: colors.strokeWidth,
        textDirection: Directionality.of(context),
        notchStart: notch.start,
        notchWidth: notch.width,
      ),
      child: Stack(
        clipBehavior: Clip.none,
        children: <Widget>[
          ConstrainedBox(
            constraints: BoxConstraints(minHeight: height),
            // Icon slots are one single-line field tall, so `start` keeps
            // them level with the first line as the field grows.
            child: Row(
              crossAxisAlignment: _crossAxisFor(
                widget.iconAlignment ?? fieldTheme.iconAlignment,
              ),
              children: <Widget>[
                ..._leadingSlot(fieldTheme, colors, height),
                Expanded(
                  child: _buildInputArea(theme, fieldTheme, colors, height),
                ),
                if (trailing.isEmpty)
                  SizedBox(width: fieldTheme.horizontalPadding)
                else ...<Widget>[
                  SizedBox(width: fieldTheme.iconSlotTextPadding),
                  ...trailing,
                ],
              ],
            ),
          ),
          if (widget.label != null)
            _buildLabel(theme, fieldTheme, colors, height, start, end),
        ],
      ),
    );
  }

  Widget _buildLabel(
    M3EThemeData theme,
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    double height,
    double start,
    double end,
  ) {
    final TextStyle resting = fieldTheme.resolveLabelStyle(theme.typeScale);
    final TextStyle floating = fieldTheme.resolveFloatingLabelStyle(
      theme.typeScale,
    );
    final double restingTop =
        (height - M3ETextFieldTheme.lineHeightOf(resting)) / 2;
    final double floatingTop = _outlined
        ? -M3ETextFieldTheme.lineHeightOf(floating) / 2
        : fieldTheme.verticalPaddingFor(_densityOf(fieldTheme));
    final double progress = _labelMotion.value;
    return PositionedDirectional(
      start: start,
      end: end,
      top: lerpDouble(restingTop, floatingTop, progress),
      // Tight width so the label keeps its intrinsic size.
      child: Align(
        alignment: AlignmentDirectional.topStart,
        heightFactor: 1,
        child: IgnorePointer(
          child: M3ETextFieldLabel(
            text: widget.label!,
            progress: progress,
            restingStyle: resting,
            floatingStyle: floating,
            color: colors.label,
            isRequired: widget.isRequired,
          ),
        ),
      ),
    );
  }

  /// Outlined top-edge gap; opens from its center as the label floats.
  _M3ETextFieldNotch _notch(
    M3EThemeData theme,
    M3ETextFieldTheme fieldTheme,
    double labelStart,
    double progress,
  ) {
    if (!_outlined || widget.label == null) {
      return const _M3ETextFieldNotch(0, 0);
    }
    final painter = TextPainter(
      text: TextSpan(
        text: M3ETextFieldLabel.compose(
          widget.label!,
          isRequired: widget.isRequired,
        ),
        style: fieldTheme.resolveFloatingLabelStyle(theme.typeScale),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final double full = painter.width + fieldTheme.notchPadding * 2;
    painter.dispose();
    final double width = full * progress.clamp(0, 1);
    return _M3ETextFieldNotch(
      labelStart - fieldTheme.notchPadding + (full - width) / 2,
      width,
    );
  }

  List<Widget> _leadingSlot(
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    double height,
  ) {
    if (widget.leading == null) {
      return <Widget>[SizedBox(width: fieldTheme.horizontalPadding)];
    }
    return <Widget>[
      SizedBox(
        width: fieldTheme.iconSlotWidth,
        height: height,
        child: Center(
          child: IconTheme.merge(
            data: IconThemeData(
              color: colors.leadingIcon,
              size: fieldTheme.iconSize,
            ),
            child: widget.leading!,
          ),
        ),
      ),
      SizedBox(width: fieldTheme.iconSlotTextPadding),
    ];
  }

  /// Clear, password toggle, then the custom trailing or error icon.
  List<Widget> _trailingSlots(
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    double height,
  ) {
    final bool canClear =
        widget.showClearButton &&
        _populated &&
        widget.enabled &&
        !widget.readOnly;
    final slots = <Widget>[
      if (canClear)
        _affordance(
          fieldTheme,
          colors,
          icon: M3EIcons.cancel,
          label: widget.clearButtonSemanticsLabel,
          onPressed: _clear,
        ),
      if (widget.showPasswordToggle)
        _affordance(
          fieldTheme,
          colors,
          icon: _isObscured ? M3EIcons.visibility : M3EIcons.visibility_off,
          label: _isObscured
              ? widget.showPasswordSemanticsLabel
              : widget.hidePasswordSemanticsLabel,
          onPressed: _toggleObscured,
        ),
      if (widget.trailing != null)
        IconTheme.merge(
          data: IconThemeData(
            color: colors.trailingIcon,
            size: fieldTheme.iconSize,
          ),
          child: widget.trailing!,
        )
      else if (widget.hasError && widget.showErrorIcon)
        _affordance(
          fieldTheme,
          colors,
          icon: M3EIcons.error,
          label: widget.errorIconSemanticsLabel,
        ),
    ];
    return <Widget>[
      for (final Widget slot in slots)
        SizedBox(
          width: fieldTheme.iconSlotWidth,
          height: height,
          child: Center(child: slot),
        ),
    ];
  }

  Widget _affordance(
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors, {
    required IconData icon,
    required String label,
    VoidCallback? onPressed,
  }) {
    return M3ETextFieldAffordance(
      icon: icon,
      color: colors.trailingIcon,
      iconSize: fieldTheme.iconSize,
      semanticLabel: label,
      onPressed: onPressed,
    );
  }

  Widget _buildInputArea(
    M3EThemeData theme,
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    double height,
  ) {
    final TextStyle input = fieldTheme
        .resolveInputStyle(theme.typeScale)
        .copyWith(color: colors.inputText);
    final double inputLine = M3ETextFieldTheme.lineHeightOf(input);
    final double centered = (height - inputLine) / 2;
    final bool stacked = !_outlined && widget.label != null;
    final double verticalPadding = fieldTheme.verticalPaddingFor(
      _densityOf(fieldTheme),
    );
    // Filled: label row above the input. Outlined: input centered.
    final padding = stacked
        ? EdgeInsets.only(
            top:
                verticalPadding +
                M3ETextFieldTheme.lineHeightOf(
                  fieldTheme.resolveFloatingLabelStyle(theme.typeScale),
                ),
            bottom: verticalPadding,
          )
        : EdgeInsets.symmetric(vertical: centered);
    return Padding(
      padding: padding,
      child: _buildInputRow(theme, fieldTheme, colors, input),
    );
  }

  Widget _buildInputRow(
    M3EThemeData theme,
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    TextStyle input,
  ) {
    // Prefix and suffix appear once the label has floated.
    final double affixOpacity = widget.label == null
        ? 1
        : _labelMotion.value.clamp(0, 1);
    final TextStyle affix = input.copyWith(color: colors.affix);
    final bool showPlaceholder =
        widget.placeholder != null &&
        !_populated &&
        (widget.label == null || (_focused && widget.enabled));
    return Row(
      crossAxisAlignment: _crossAxisFor(
        widget.affixAlignment ?? fieldTheme.affixAlignment,
        baseline: true,
      ),
      textBaseline: TextBaseline.alphabetic,
      children: <Widget>[
        if (widget.prefixText != null)
          _affix(
            widget.prefixText!,
            widget.prefixSemanticsLabel,
            affix,
            affixOpacity,
          ),
        Expanded(
          child: Stack(
            children: <Widget>[
              _buildEditable(theme, fieldTheme, colors, input),
              if (showPlaceholder)
                Positioned.fill(
                  child: IgnorePointer(
                    child: ExcludeSemantics(
                      child: Text(
                        widget.placeholder!,
                        style: affix,
                        textAlign: widget.textAlign,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
        if (widget.suffixText != null)
          _affix(
            widget.suffixText!,
            widget.suffixSemanticsLabel,
            affix,
            affixOpacity,
          ),
      ],
    );
  }

  Widget _affix(
    String text,
    String? semanticsLabel,
    TextStyle style,
    double opacity,
  ) {
    return Opacity(
      opacity: opacity,
      child: Semantics(
        label: semanticsLabel ?? text,
        child: ExcludeSemantics(child: Text(text, style: style)),
      ),
    );
  }

  Widget _buildEditable(
    M3EThemeData theme,
    M3ETextFieldTheme fieldTheme,
    M3ETextFieldColors colors,
    TextStyle input,
  ) {
    final String? label = widget.label == null
        ? null
        : M3ETextFieldLabel.compose(
            widget.label!,
            isRequired: widget.isRequired,
          );
    final int? maxLength = widget.maxLength;
    return Semantics(
      label: label,
      hint: widget.errorText ?? _visibleSupportingText,
      enabled: widget.enabled,
      child: CallbackShortcuts(
        bindings: M3EFocus.editableInputShortcuts(_focusNode),
        child: EditableText(
          key: _editableKey,
          controller: _controller,
          focusNode: _focusNode,
          readOnly: widget.readOnly || !widget.enabled,
          obscureText: _isObscured,
          autofocus: widget.autofocus,
          maxLines: widget.maxLines,
          minLines: widget.minLines,
          keyboardType: widget.keyboardType,
          textInputAction: widget.textInputAction,
          textCapitalization: widget.textCapitalization,
          textAlign: widget.textAlign,
          inputFormatters: <TextInputFormatter>[
            ...?widget.inputFormatters,
            if (maxLength != null) LengthLimitingTextInputFormatter(maxLength),
          ],
          onSubmitted: widget.onSubmitted,
          onEditingComplete: widget.onEditingComplete,
          onTapOutside: (_) {},
          style: input,
          cursorColor: colors.caret,
          backgroundCursorColor: theme.colorScheme.outlineVariant,
          selectionColor: theme.colorScheme.primary.withValues(
            alpha: fieldTheme.selectionOpacity,
          ),
          selectionControls: m3eTextFieldSelectionControls(
            defaultTargetPlatform,
          ),
          showSelectionHandles: _showSelectionHandles,
          onSelectionChanged: _handleSelectionChanged,
          onSelectionHandleTapped: _handleSelectionHandleTapped,
          contextMenuBuilder: widget.contextMenuBuilder,
          autofillClient: _autofillClient,
          rendererIgnoresPointer: true,
        ),
      ),
    );
  }

  String? get _visibleSupportingText =>
      !widget.supportingTextOnFocusOnly || _focused
      ? widget.supportingText
      : null;

  Widget _buildSupporting(M3EThemeData theme, M3ETextFieldTheme fieldTheme) {
    final int? maxLength = widget.maxLength;
    final bool counter = maxLength != null && (widget.showCounter ?? true);
    final int count = _controller.text.characters.length;
    return M3ETextFieldSupportingRow(
      style: fieldTheme.resolveSupportingStyle(theme.typeScale),
      color: _currentColors.supportingText,
      padding: EdgeInsetsDirectional.only(
        start: fieldTheme.supportingHorizontalPadding,
        top: fieldTheme.supportingTopPadding,
        end: fieldTheme.supportingHorizontalPadding,
      ),
      counterGap: fieldTheme.supportingCounterGap,
      supportingText: _visibleSupportingText,
      errorText: widget.errorText,
      counterText: counter ? '$count/$maxLength' : null,
      counterSemanticsLabel: counter
          ? widget.counterSemanticsLabelBuilder(count, maxLength)
          : null,
    );
  }
}

/// Outlined notch geometry from the leading edge.
@immutable
class _M3ETextFieldNotch {
  const _M3ETextFieldNotch(this.start, this.width);

  final double start;

  final double width;
}
