// Dextop's shared AppKit grid implementation.
#import <AppKit/NSView.h>

typedef NS_ENUM(NSInteger, NSGridCellPlacement) {
    NSGridCellPlacementInherited = 0,
    NSGridCellPlacementNone = 1,
    NSGridCellPlacementLeading = 2,
    NSGridCellPlacementTop = 2,
    NSGridCellPlacementTrailing = 3,
    NSGridCellPlacementBottom = 3,
    NSGridCellPlacementCenter = 4,
    NSGridCellPlacementFill = 5,
};
typedef NS_ENUM(NSInteger, NSGridRowAlignment) {
    NSGridRowAlignmentInherited = 0,
    NSGridRowAlignmentNone = 1,
    NSGridRowAlignmentFirstBaseline = 2,
    NSGridRowAlignmentLastBaseline = 3,
};
APPKIT_EXPORT const CGFloat NSGridViewSizeForContent;
@class NSGridView, NSGridRow, NSGridColumn;

@interface NSGridCell : NSObject {
@package
    NSGridView *_grid;
    NSView *_contentView;
    NSGridCellPlacement _xPlacement, _yPlacement;
    NSGridRowAlignment _rowAlignment;
    NSArray *_customPlacementConstraints;
}
@property(retain) NSView *contentView;
@property NSGridCellPlacement xPlacement;
@property NSGridCellPlacement yPlacement;
@property NSGridRowAlignment rowAlignment;
@property(copy) NSArray *customPlacementConstraints;
@property(readonly) NSGridRow *row;
@property(readonly) NSGridColumn *column;
+ (NSView *)emptyContentView;
@end

@interface NSGridRow : NSObject {
@package
    NSGridView *_grid;
    CGFloat _height, _topPadding, _bottomPadding;
    NSGridCellPlacement _yPlacement;
    NSGridRowAlignment _rowAlignment;
    BOOL _hidden;
}
@property CGFloat height;
@property CGFloat topPadding;
@property CGFloat bottomPadding;
@property NSGridCellPlacement yPlacement;
@property NSGridRowAlignment rowAlignment;
@property(getter=isHidden) BOOL hidden;
@property(readonly) NSGridView *gridView;
@property(readonly) NSInteger numberOfCells;
- (NSGridCell *)cellAtIndex:(NSInteger)index;
@end

@interface NSGridColumn : NSObject {
@package
    NSGridView *_grid;
    CGFloat _width, _leadingPadding, _trailingPadding;
    NSGridCellPlacement _xPlacement;
    BOOL _hidden;
}
@property CGFloat width;
@property CGFloat leadingPadding;
@property CGFloat trailingPadding;
@property NSGridCellPlacement xPlacement;
@property(getter=isHidden) BOOL hidden;
@property(readonly) NSGridView *gridView;
@property(readonly) NSInteger numberOfCells;
- (NSGridCell *)cellAtIndex:(NSInteger)index;
@end

@interface NSGridView : NSView {
@package
    NSMutableArray *_gridRows, *_gridColumns, *_gridCells;
    CGFloat _rowSpacing, _columnSpacing;
    NSGridCellPlacement _xPlacement, _yPlacement;
    NSGridRowAlignment _rowAlignment;
}
+ (instancetype)gridViewWithViews:(NSArray *)rows;
+ (instancetype)gridViewWithNumberOfColumns:(NSInteger)columns rows:(NSInteger)rows;
@property(readonly) NSInteger numberOfRows;
@property(readonly) NSInteger numberOfColumns;
@property CGFloat rowSpacing;
@property CGFloat columnSpacing;
@property NSGridCellPlacement xPlacement;
@property NSGridCellPlacement yPlacement;
@property NSGridRowAlignment rowAlignment;
- (NSGridRow *)rowAtIndex:(NSInteger)index;
- (NSGridColumn *)columnAtIndex:(NSInteger)index;
- (NSInteger)indexOfRow:(NSGridRow *)row;
- (NSInteger)indexOfColumn:(NSGridColumn *)column;
- (NSGridCell *)cellAtColumnIndex:(NSInteger)column rowIndex:(NSInteger)row;
- (NSGridCell *)cellForView:(NSView *)view;
- (NSGridRow *)addRowWithViews:(NSArray *)views;
- (NSGridColumn *)addColumnWithViews:(NSArray *)views;
- (NSGridRow *)insertRowAtIndex:(NSInteger)index withViews:(NSArray *)views;
- (NSGridColumn *)insertColumnAtIndex:(NSInteger)index withViews:(NSArray *)views;
- (void)removeRowAtIndex:(NSInteger)index;
- (void)removeColumnAtIndex:(NSInteger)index;
- (void)moveRowAtIndex:(NSInteger)from toIndex:(NSInteger)to;
- (void)moveColumnAtIndex:(NSInteger)from toIndex:(NSInteger)to;
@end
