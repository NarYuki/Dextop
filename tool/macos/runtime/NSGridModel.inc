// Included by NSGridView.m. Rows own cell arrays; cells borrow their grid.
static void GridChanged(NSGridView *grid) {
    [grid invalidateIntrinsicContentSize];
    [grid setNeedsLayout:YES];
    [grid setNeedsDisplay:YES];
}

@implementation NSGridCell
@synthesize customPlacementConstraints = _customPlacementConstraints;
+ (NSView *)emptyContentView {
    static NSView *empty;
    if (!empty) empty = [[NSView alloc] initWithFrame:NSZeroRect];
    return empty;
}
- (void)dealloc { [_contentView release]; [_customPlacementConstraints release]; [super dealloc]; }
- (NSView *)contentView { return _contentView; }
- (void)setContentView:(NSView *)view {
    if (view == [[self class] emptyContentView]) view = nil;
    if (_contentView == view) return;
    if (view && [_grid cellForView:view])
        [NSException raise:NSInvalidArgumentException format:@"A view already belongs to this grid"];
    [view retain];
    [_contentView removeFromSuperview]; [_contentView release];
    _contentView = view;
    if (view) { [view setTranslatesAutoresizingMaskIntoConstraints:YES]; [_grid addSubview:view]; }
    GridChanged(_grid);
}
- (NSGridRow *)row {
    if (!_grid) return nil;
    for (NSInteger r = 0; r < [_grid numberOfRows]; ++r)
        if ([[_grid->_gridCells objectAtIndex:r] indexOfObjectIdenticalTo:self] != NSNotFound)
            return [_grid rowAtIndex:r];
    return nil;
}
- (NSGridColumn *)column {
    if (!_grid) return nil;
    for (NSArray *row in _grid->_gridCells) {
        NSUInteger c = [row indexOfObjectIdenticalTo:self];
        if (c != NSNotFound) return [_grid columnAtIndex:c];
    }
    return nil;
}
@end

@implementation NSGridRow
- (NSGridView *)gridView { return _grid; }
- (NSInteger)numberOfCells { return [_grid numberOfColumns]; }
- (NSGridCell *)cellAtIndex:(NSInteger)index {
    return [_grid cellAtColumnIndex:index rowIndex:[_grid indexOfRow:self]];
}
@end

@implementation NSGridColumn
- (NSGridView *)gridView { return _grid; }
- (NSInteger)numberOfCells { return [_grid numberOfRows]; }
- (NSGridCell *)cellAtIndex:(NSInteger)index {
    return [_grid cellAtColumnIndex:[_grid indexOfColumn:self] rowIndex:index];
}
@end

// Shared setters invalidate the grid, including when a hidden row changes size.
#define GRID_PROPERTY(TYPE, GETTER, SETTER, IVAR) \
- (TYPE)GETTER { return IVAR; } \
- (void)SETTER:(TYPE)value { if (IVAR != value) { IVAR = value; GridChanged(_grid); } }

@implementation NSGridCell (GridProperties)
GRID_PROPERTY(NSGridCellPlacement, xPlacement, setXPlacement, _xPlacement)
GRID_PROPERTY(NSGridCellPlacement, yPlacement, setYPlacement, _yPlacement)
GRID_PROPERTY(NSGridRowAlignment, rowAlignment, setRowAlignment, _rowAlignment)
@end
@implementation NSGridRow (GridProperties)
GRID_PROPERTY(CGFloat, height, setHeight, _height)
GRID_PROPERTY(CGFloat, topPadding, setTopPadding, _topPadding)
GRID_PROPERTY(CGFloat, bottomPadding, setBottomPadding, _bottomPadding)
GRID_PROPERTY(NSGridCellPlacement, yPlacement, setYPlacement, _yPlacement)
GRID_PROPERTY(NSGridRowAlignment, rowAlignment, setRowAlignment, _rowAlignment)
GRID_PROPERTY(BOOL, isHidden, setHidden, _hidden)
@end
@implementation NSGridColumn (GridProperties)
GRID_PROPERTY(CGFloat, width, setWidth, _width)
GRID_PROPERTY(CGFloat, leadingPadding, setLeadingPadding, _leadingPadding)
GRID_PROPERTY(CGFloat, trailingPadding, setTrailingPadding, _trailingPadding)
GRID_PROPERTY(NSGridCellPlacement, xPlacement, setXPlacement, _xPlacement)
GRID_PROPERTY(BOOL, isHidden, setHidden, _hidden)
@end
#undef GRID_PROPERTY
