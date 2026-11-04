#import <AppKit/AppKit.h>
#include <math.h>
#include <stdio.h>

@interface GridLeaf : NSView {
@public
    NSSize measured;
    CGFloat first, last;
}
@end
@implementation GridLeaf
- (NSSize)intrinsicContentSize { return measured; }
- (CGFloat)firstBaselineOffsetFromTop { return first; }
- (CGFloat)lastBaselineOffsetFromBottom { return last; }
@end

static GridLeaf *leaf(CGFloat w, CGFloat h, CGFloat first, CGFloat last) {
    GridLeaf *v = [[[GridLeaf alloc] initWithFrame:NSZeroRect] autorelease];
    v->measured = NSMakeSize(w, h); v->first = first; v->last = last;
    return v;
}
static BOOL equal(CGFloat a, CGFloat b) { return fabs(a-b) < 0.001; }

int main(void) {
    @autoreleasepool {
        GridLeaf *a = leaf(20,20,7,3), *b = leaf(30,30,12,4), *c = leaf(10,10,5,2);
        NSGridView *grid = [NSGridView gridViewWithViews:@[@[a,b], @[c]]];
        grid.columnSpacing = 4; grid.rowSpacing = 6;
        if (grid.numberOfRows != 2 || grid.numberOfColumns != 2) return 1;
        NSGridCell *cell = [[grid cellForView:b] retain];
        NSGridColumn *column = [[grid columnAtIndex:1] retain];
        if (cell.column != column || cell.row != [grid rowAtIndex:0]) return 2;
        if (!equal(grid.fittingSize.width,54) || !equal(grid.fittingSize.height,46)) return 3;
        grid.rowAlignment = NSGridRowAlignmentFirstBaseline;
        grid.frame = NSMakeRect(0,0,100,100);
        [grid layoutSubtreeIfNeeded];
        CGFloat ab = 100 - NSMaxY(a.frame) + a.firstBaselineOffsetFromTop;
        CGFloat bb = 100 - NSMaxY(b.frame) + b.firstBaselineOffsetFromTop;
        if (!equal(ab,bb) || !equal(b.frame.origin.x,24)) return 4;
        [grid moveColumnAtIndex:1 toIndex:0];
        if ([grid columnAtIndex:0] != column || [grid cellAtColumnIndex:0 rowIndex:0] != cell) return 5;
        [grid layoutSubtreeIfNeeded];
        if (!equal(b.frame.origin.x,0) || !equal(a.frame.origin.x,34)) return 6;
        [grid rowAtIndex:1].hidden = YES;
        if (!equal(grid.fittingSize.height,30)) return 7;
        [grid layoutSubtreeIfNeeded];
        if (!c.hidden || b.hidden) return 8;
        [grid rowAtIndex:1].hidden = NO;
        column.width = 50; column.leadingPadding = 3; column.trailingPadding = 7;
        if (!equal(grid.fittingSize.width,84)) return 9;
        [grid cellForView:b].xPlacement = NSGridCellPlacementCenter;
        [grid layoutSubtreeIfNeeded];
        if (!equal(b.frame.origin.x,13)) return 10;
        grid.userInterfaceLayoutDirection = NSUserInterfaceLayoutDirectionRightToLeft;
        [grid layoutSubtreeIfNeeded];
        if (!equal(b.frame.origin.x,57)) return 11;
        NSView *descendant = [[[NSView alloc] initWithFrame:NSZeroRect] autorelease];
        [b addSubview:descendant];
        if ([grid cellForView:descendant] != cell) return 12;
        [grid removeColumnAtIndex:0];
        if (column.gridView || cell.row || cell.column || b.superview) return 13;
        [column release]; [cell release];
        BOOL raised = NO;
        @try { [grid rowAtIndex:99]; } @catch (NSException *exception) { raised = YES; }
        if (!raised) return 14;
        NSControl *wrapper = [[[NSControl alloc] initWithFrame:NSZeroRect] autorelease];
        GridLeaf *wrappedLeaf = leaf(70,25,10,4);
        NSGridView *wrapped = [NSGridView gridViewWithViews:@[@[wrappedLeaf]]];
        wrapped.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
        wrapped.frame = wrapper.bounds;
        [wrapper addSubview:wrapped];
        if (!equal(wrapper.fittingSize.width,70) || !equal(wrapper.fittingSize.height,25)) return 15;
        if (!NSEqualRects(wrapper.frame,NSZeroRect) || !NSEqualRects(wrapped.frame,NSZeroRect)) return 16;
        NSView *outer = [[[NSView alloc] initWithFrame:NSMakeRect(0,0,200,100)] autorelease];
        wrapper.translatesAutoresizingMaskIntoConstraints = NO;
        [outer addSubview:wrapper];
        NSDictionary *views = [NSDictionary dictionaryWithObject:wrapper forKey:@"wrapper"];
        [outer addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:|-20-[wrapper]" options:0 metrics:nil views:views]];
        [outer addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:|-10-[wrapper]" options:0 metrics:nil views:views]];
        [outer layoutSubtreeIfNeeded];
        printf("normal container %.3f %.3f child %.3f %.3f\n", wrapper.frame.size.width,
            wrapper.frame.size.height, wrapped.frame.size.width, wrapped.frame.size.height);
        if (!equal(wrapper.frame.size.width,70) || !equal(wrapper.frame.size.height,25) ||
            !equal(wrapped.frame.size.width,70) || !equal(wrapped.frame.size.height,25)) return 17;
        puts("grid: model, fitting, baseline, reorder, hiding, padding and RTL OK");
    }
    return 0;
}
