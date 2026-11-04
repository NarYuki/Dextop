#import <AppKit/NSGridView.h>
#import <Foundation/NSException.h>
#import <Foundation/NSValue.h>
#include <math.h>
#include <stdlib.h>

const CGFloat NSGridViewSizeForContent = 0;
#include "NSGridModel.inc"

static void GridIndex(NSInteger index, NSUInteger count, BOOL insertion) {
    if (index < 0 || (NSUInteger)index > count || (!insertion && (NSUInteger)index == count))
        [NSException raise:NSRangeException format:@"Grid index %ld outside %lu entries", (long)index, (unsigned long)count];
}
static NSSize GridContentSize(NSView *view) {
    if (!view) return NSZeroSize;
    NSSize size = [view intrinsicContentSize], fallback = [view fittingSize];
    if (size.width == NSViewNoIntrinsicMetric) size.width = fallback.width;
    if (size.height == NSViewNoIntrinsicMetric) size.height = fallback.height;
    if (!isfinite(size.width) || size.width < 0) size.width = 0;
    if (!isfinite(size.height) || size.height < 0) size.height = 0;
    return size;
}

@implementation NSGridView
- (id)initWithFrame:(NSRect)frame {
    if ((self = [super initWithFrame:frame])) {
        _gridRows = [NSMutableArray new]; _gridColumns = [NSMutableArray new]; _gridCells = [NSMutableArray new];
        _rowSpacing = 5; _columnSpacing = 5;
        _xPlacement = NSGridCellPlacementLeading; _yPlacement = NSGridCellPlacementCenter;
        _rowAlignment = NSGridRowAlignmentNone;
    }
    return self;
}
- (void)dealloc {
    for (NSGridRow *row in _gridRows) row->_grid = nil;
    for (NSGridColumn *column in _gridColumns) column->_grid = nil;
    for (NSArray *row in _gridCells) for (NSGridCell *cell in row) cell->_grid = nil;
    [_gridCells release]; [_gridRows release]; [_gridColumns release];
    [super dealloc];
}
+ (instancetype)gridViewWithNumberOfColumns:(NSInteger)columns rows:(NSInteger)rows {
    if (columns < 0 || rows < 0) [NSException raise:NSInvalidArgumentException format:@"Negative grid dimensions"];
    NSGridView *grid = [[[self alloc] initWithFrame:NSZeroRect] autorelease];
    for (NSInteger c = 0; c < columns; ++c) [grid addColumnWithViews:[NSArray array]];
    for (NSInteger r = 0; r < rows; ++r) [grid addRowWithViews:[NSArray array]];
    return grid;
}
+ (instancetype)gridViewWithViews:(NSArray *)rows {
    NSGridView *grid = [[[self alloc] initWithFrame:NSZeroRect] autorelease];
    for (NSArray *row in rows) [grid addRowWithViews:row];
    return grid;
}
- (NSInteger)numberOfRows { return [_gridRows count]; }
- (NSInteger)numberOfColumns { return [_gridColumns count]; }
- (NSGridRow *)rowAtIndex:(NSInteger)index { GridIndex(index, [_gridRows count], NO); return [_gridRows objectAtIndex:index]; }
- (NSGridColumn *)columnAtIndex:(NSInteger)index { GridIndex(index, [_gridColumns count], NO); return [_gridColumns objectAtIndex:index]; }
- (NSInteger)indexOfRow:(NSGridRow *)row { return [_gridRows indexOfObjectIdenticalTo:row]; }
- (NSInteger)indexOfColumn:(NSGridColumn *)column { return [_gridColumns indexOfObjectIdenticalTo:column]; }
- (NSGridCell *)cellAtColumnIndex:(NSInteger)c rowIndex:(NSInteger)r {
    GridIndex(r, [_gridRows count], NO); GridIndex(c, [_gridColumns count], NO);
    return [[_gridCells objectAtIndex:r] objectAtIndex:c];
}
- (NSGridCell *)cellForView:(NSView *)view {
    if (!view) return nil;
    for (NSArray *row in _gridCells) for (NSGridCell *cell in row)
        if (cell->_contentView && [view isDescendantOf:cell->_contentView]) return cell;
    return nil;
}
- (NSGridRow *)addRowWithViews:(NSArray *)views { return [self insertRowAtIndex:[self numberOfRows] withViews:views]; }
- (NSGridColumn *)addColumnWithViews:(NSArray *)views { return [self insertColumnAtIndex:[self numberOfColumns] withViews:views]; }
- (NSGridRow *)insertRowAtIndex:(NSInteger)index withViews:(NSArray *)views {
    GridIndex(index, [_gridRows count], YES);
    while ([views count] > [_gridColumns count]) [self addColumnWithViews:[NSArray array]];
    NSGridRow *row = [[[NSGridRow alloc] init] autorelease]; row->_grid = self;
    NSMutableArray *cells = [NSMutableArray array];
    for (NSUInteger c = 0; c < [_gridColumns count]; ++c) {
        NSGridCell *cell = [[[NSGridCell alloc] init] autorelease]; cell->_grid = self;
        [cells addObject:cell];
    }
    [_gridRows insertObject:row atIndex:index]; [_gridCells insertObject:cells atIndex:index];
    for (NSUInteger c = 0; c < [views count]; ++c) [[cells objectAtIndex:c] setContentView:[views objectAtIndex:c]];
    GridChanged(self); return row;
}
- (NSGridColumn *)insertColumnAtIndex:(NSInteger)index withViews:(NSArray *)views {
    GridIndex(index, [_gridColumns count], YES);
    while ([views count] > [_gridRows count]) [self addRowWithViews:[NSArray array]];
    NSGridColumn *column = [[[NSGridColumn alloc] init] autorelease]; column->_grid = self;
    [_gridColumns insertObject:column atIndex:index];
    for (NSUInteger r = 0; r < [_gridRows count]; ++r) {
        NSGridCell *cell = [[[NSGridCell alloc] init] autorelease]; cell->_grid = self;
        [[_gridCells objectAtIndex:r] insertObject:cell atIndex:index];
        if (r < [views count]) [cell setContentView:[views objectAtIndex:r]];
    }
    GridChanged(self); return column;
}
- (void)removeRowAtIndex:(NSInteger)index {
    NSGridRow *row = [self rowAtIndex:index]; row->_grid = nil;
    for (NSGridCell *cell in [_gridCells objectAtIndex:index]) { [cell->_contentView removeFromSuperview]; cell->_grid = nil; }
    [_gridCells removeObjectAtIndex:index]; [_gridRows removeObjectAtIndex:index]; GridChanged(self);
}
- (void)removeColumnAtIndex:(NSInteger)index {
    NSGridColumn *column = [self columnAtIndex:index]; column->_grid = nil;
    for (NSMutableArray *row in _gridCells) {
        NSGridCell *cell = [row objectAtIndex:index]; [cell->_contentView removeFromSuperview]; cell->_grid = nil;
        [row removeObjectAtIndex:index];
    }
    [_gridColumns removeObjectAtIndex:index]; GridChanged(self);
}
- (void)moveRowAtIndex:(NSInteger)from toIndex:(NSInteger)to {
    GridIndex(from, [_gridRows count], NO); GridIndex(to, [_gridRows count], NO);
    id row = [[_gridRows objectAtIndex:from] retain], cells = [[_gridCells objectAtIndex:from] retain];
    [_gridRows removeObjectAtIndex:from]; [_gridCells removeObjectAtIndex:from];
    [_gridRows insertObject:row atIndex:to]; [_gridCells insertObject:cells atIndex:to];
    [row release]; [cells release]; GridChanged(self);
}
- (void)moveColumnAtIndex:(NSInteger)from toIndex:(NSInteger)to {
    GridIndex(from, [_gridColumns count], NO); GridIndex(to, [_gridColumns count], NO);
    id column = [[_gridColumns objectAtIndex:from] retain];
    [_gridColumns removeObjectAtIndex:from]; [_gridColumns insertObject:column atIndex:to]; [column release];
    for (NSMutableArray *row in _gridCells) {
        id cell = [[row objectAtIndex:from] retain]; [row removeObjectAtIndex:from];
        [row insertObject:cell atIndex:to]; [cell release];
    }
    GridChanged(self);
}
- (NSSize)_measureGridWidths:(CGFloat *)widths heights:(CGFloat *)heights {
    NSInteger nc = [self numberOfColumns], nr = [self numberOfRows];
    for (NSInteger c = 0; c < nc; ++c) widths[c] = 0;
    for (NSInteger r = 0; r < nr; ++r) heights[r] = 0;
    for (NSInteger r = 0; r < nr; ++r) for (NSInteger c = 0; c < nc; ++c) {
        NSGridRow *row = [self rowAtIndex:r]; NSGridColumn *column = [self columnAtIndex:c];
        if (row.hidden || column.hidden) continue;
        NSSize size = GridContentSize([[self cellAtColumnIndex:c rowIndex:r] contentView]);
        widths[c] = fmax(widths[c], size.width); heights[r] = fmax(heights[r], size.height);
    }
    for (NSInteger r = 0; r < nr; ++r) {
        NSGridRow *row = [self rowAtIndex:r];
        CGFloat aboveFirst = 0, belowFirst = 0, aboveLast = 0, belowLast = 0;
        for (NSInteger c = 0; c < nc; ++c) {
            NSGridCell *cell = [self cellAtColumnIndex:c rowIndex:r];
            NSView *view = cell.contentView;
            if (!view || row.hidden || [[self columnAtIndex:c] isHidden]) continue;
            NSGridRowAlignment alignment = cell.rowAlignment ?: (row.rowAlignment ?: _rowAlignment);
            NSSize size = GridContentSize(view);
            CGFloat first = [view firstBaselineOffsetFromTop];
            CGFloat last = size.height - [view lastBaselineOffsetFromBottom];
            if (alignment == NSGridRowAlignmentFirstBaseline) {
                aboveFirst = fmax(aboveFirst, first); belowFirst = fmax(belowFirst, size.height - first);
            } else if (alignment == NSGridRowAlignmentLastBaseline) {
                aboveLast = fmax(aboveLast, last); belowLast = fmax(belowLast, size.height - last);
            }
        }
        heights[r] = fmax(heights[r], fmax(aboveFirst + belowFirst, aboveLast + belowLast));
    }
    NSSize total = NSZeroSize; NSInteger visible = 0;
    for (NSInteger c = 0; c < nc; ++c) {
        NSGridColumn *column = [self columnAtIndex:c]; if (column.hidden) continue;
        if (column.width != NSGridViewSizeForContent) widths[c] = column.width;
        total.width += widths[c] + column.leadingPadding + column.trailingPadding;
        if (visible++) total.width += _columnSpacing;
    }
    visible = 0;
    for (NSInteger r = 0; r < nr; ++r) {
        NSGridRow *row = [self rowAtIndex:r]; if (row.hidden) continue;
        if (row.height != NSGridViewSizeForContent) heights[r] = row.height;
        total.height += heights[r] + row.topPadding + row.bottomPadding;
        if (visible++) total.height += _rowSpacing;
    }
    return total;
}
- (NSSize)intrinsicContentSize {
    CGFloat *widths = calloc(MAX(1, [self numberOfColumns]), sizeof(CGFloat));
    CGFloat *heights = calloc(MAX(1, [self numberOfRows]), sizeof(CGFloat));
    NSSize size = [self _measureGridWidths:widths heights:heights]; free(widths); free(heights); return size;
}
- (NSSize)fittingSize { return [self intrinsicContentSize]; }
- (void)layout {
    [super layout];
    if (getenv("DEXTOP_DARLING_GRID_TRACE"))
        NSLog(@"Dextop grid layout frame=%@ bounds=%@ parent=%@ parentFrame=%@ hidden=%d", NSStringFromRect([self frame]), NSStringFromRect([self bounds]), NSStringFromClass([[self superview] class]), NSStringFromRect([[self superview] frame]), [self isHiddenOrHasHiddenAncestor]);
    NSInteger nc = [self numberOfColumns], nr = [self numberOfRows];
    CGFloat *widths = calloc(MAX(1, nc), sizeof(CGFloat)), *heights = calloc(MAX(1, nr), sizeof(CGFloat));
    [self _measureGridWidths:widths heights:heights];
    NSRect bounds = [self bounds]; CGFloat top = 0;
    BOOL rtl = [self userInterfaceLayoutDirection] == NSUserInterfaceLayoutDirectionRightToLeft;
    for (NSInteger r = 0; r < nr; ++r) {
        NSGridRow *row = [self rowAtIndex:r]; CGFloat leading = 0;
        CGFloat firstBaseline = 0, lastBaseline = 0;
        for (NSInteger c = 0; c < nc; ++c) {
            NSGridCell *cell = [self cellAtColumnIndex:c rowIndex:r];
            if (!cell.contentView || [[self columnAtIndex:c] isHidden]) continue;
            NSGridRowAlignment alignment = cell.rowAlignment ?: (row.rowAlignment ?: _rowAlignment);
            if (alignment == NSGridRowAlignmentFirstBaseline)
                firstBaseline = fmax(firstBaseline, [cell.contentView firstBaselineOffsetFromTop]);
            else if (alignment == NSGridRowAlignmentLastBaseline)
                lastBaseline = fmax(lastBaseline, GridContentSize(cell.contentView).height - [cell.contentView lastBaselineOffsetFromBottom]);
        }
        for (NSInteger c = 0; c < nc; ++c) {
            NSGridColumn *column = [self columnAtIndex:c]; NSGridCell *cell = [self cellAtColumnIndex:c rowIndex:r];
            NSView *view = cell.contentView; view.hidden = row.hidden || column.hidden;
            if (row.hidden || column.hidden) continue;
            NSSize size = GridContentSize(view);
            NSGridCellPlacement xp = cell.xPlacement ?: (column.xPlacement ?: _xPlacement);
            NSGridCellPlacement yp = cell.yPlacement ?: (row.yPlacement ?: _yPlacement);
            if (xp == NSGridCellPlacementFill) size.width = widths[c];
            if (yp == NSGridCellPlacementFill) size.height = heights[r];
            CGFloat x = leading + column.leadingPadding, y = top + row.topPadding;
            if (xp == NSGridCellPlacementCenter) x += (widths[c] - size.width) / 2;
            else if (xp == NSGridCellPlacementTrailing) x += widths[c] - size.width;
            if (yp == NSGridCellPlacementCenter) y += (heights[r] - size.height) / 2;
            else if (yp == NSGridCellPlacementBottom) y += heights[r] - size.height;
            NSGridRowAlignment alignment = cell.rowAlignment ?: (row.rowAlignment ?: _rowAlignment);
            if (alignment == NSGridRowAlignmentFirstBaseline)
                y = top + row.topPadding + firstBaseline - [view firstBaselineOffsetFromTop];
            else if (alignment == NSGridRowAlignmentLastBaseline)
                y = top + row.topPadding + lastBaseline - size.height + [view lastBaselineOffsetFromBottom];
            if (rtl) x = bounds.size.width - x - size.width;
            if (![self isFlipped]) y = bounds.size.height - y - size.height;
            if (view && xp != NSGridCellPlacementNone && yp != NSGridCellPlacementNone)
                view.frame = NSMakeRect(bounds.origin.x + x, bounds.origin.y + y, size.width, size.height);
            leading += column.leadingPadding + widths[c] + column.trailingPadding + _columnSpacing;
        }
        if (!row.hidden) top += row.topPadding + heights[r] + row.bottomPadding + _rowSpacing;
    }
    free(widths); free(heights);
}
#define GRID_SELF_PROPERTY(TYPE, GETTER, SETTER, IVAR) \
- (TYPE)GETTER { return IVAR; } \
- (void)SETTER:(TYPE)value { if (IVAR != value) { IVAR = value; GridChanged(self); } }
GRID_SELF_PROPERTY(CGFloat, rowSpacing, setRowSpacing, _rowSpacing)
GRID_SELF_PROPERTY(CGFloat, columnSpacing, setColumnSpacing, _columnSpacing)
GRID_SELF_PROPERTY(NSGridCellPlacement, xPlacement, setXPlacement, _xPlacement)
GRID_SELF_PROPERTY(NSGridCellPlacement, yPlacement, setYPlacement, _yPlacement)
GRID_SELF_PROPERTY(NSGridRowAlignment, rowAlignment, setRowAlignment, _rowAlignment)
#undef GRID_SELF_PROPERTY
@end
