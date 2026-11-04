"""Provide positioned popup menus using AppKit's existing menu tracking loop."""
import argparse
from pathlib import Path

METHOD = r'''
- (BOOL)popUpMenuPositioningItem:(NSMenuItem *)positioningItem atLocation:(NSPoint)location inView:(NSView *)view {
    [self update];
    if ([[self itemArray] count] == 0) return NO;
    if (positioningItem && [self indexOfItem:positioningItem] == NSNotFound)
        [NSException raise:NSInvalidArgumentException format:@"Positioning item is not in this menu"];
    NSPoint screenPoint = location;
    if (view) {
        if (![view window]) return NO;
        screenPoint = [[view window] convertBaseToScreen:[view convertPoint:location toView:nil]];
    }
    NSMenuWindow *popup = [[NSMenuWindow alloc] initWithMenu:self];
    [popup setReleasedWhenClosed:NO];
    NSMenuItem *selected = nil;
    @try {
        NSMenuView *menuView = [popup menuView];
        if (positioningItem) {
            NSArray *visible = [menuView visibleItemArray];
            NSUInteger index = [visible indexOfObjectIdenticalTo:positioningItem];
            if (index != NSNotFound) {
                NSRect bounds = [menuView bounds], row = NSZeroRect;
                for (CGFloat y = NSMinY(bounds); y < NSMaxY(bounds); y += 1.0) {
                    if ([menuView itemIndexAtPoint:NSMakePoint(NSMidX(bounds),y) rect:&row] == index) {
                        screenPoint.y += [menuView isFlipped] ? NSMidY(row) - NSMinY(bounds) : NSMaxY(bounds) - NSMidY(row);
                        break;
                    }
                }
            }
        }
        [popup setFrameTopLeftPoint:screenPoint];
        [popup orderFront:nil];
        if (getenv("DEXTOP_DARLING_MENU_TRACE"))
            NSLog(@"Dextop popup mapped frame=%@ key=%@", NSStringFromRect([popup frame]), [NSApp keyWindow]);
        NSEvent *event = [NSApp currentEvent];
        if (!event || ![event window]) {
            event = [NSEvent mouseEventWithType:NSLeftMouseDown
                location:[popup mouseLocationOutsideOfEventStream] modifierFlags:0
                timestamp:[NSDate timeIntervalSinceReferenceDate] windowNumber:[popup windowNumber]
                context:nil eventNumber:0 clickCount:1 pressure:1.0];
        }
        selected = [[menuView trackForEvent:event] retain];
        if (getenv("DEXTOP_DARLING_MENU_TRACE"))
            NSLog(@"Dextop popup tracking returned item=%@", selected);
    } @finally {
        [popup close];
        [popup release];
    }
    BOOL chosen = selected != nil;
    @try {
        if (chosen) [NSApp sendAction:[selected action] to:[selected target] from:selected];
    } @finally { [selected release]; }
    return chosen;
}

'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    path = args.source / 'src/external/cocotron/AppKit/NSMenu.subproj/NSMenu.m'
    text = path.read_text()
    if '- (BOOL)popUpMenuPositioningItem:' in text:
        start = text.index('- (BOOL)popUpMenuPositioningItem:')
        end = text.index('+ (NSZone *) menuZone {', start)
        text = text[:start] + METHOD.lstrip('\n') + text[end:]
        path.write_text(text)
    else:
        anchor = '+ (NSZone *) menuZone {'
        if text.count(anchor) != 1: raise RuntimeError('Pinned menu implementation changed')
        path.write_text(text.replace(anchor, METHOD + anchor))
    path = args.source / 'framework-include/AppKit/NSMenu.h'
    text = path.read_text()
    declaration = '- (BOOL)popUpMenuPositioningItem:(NSMenuItem *)item atLocation:(NSPoint)location inView:(NSView *)view;'
    if declaration not in text:
        anchor = '- (NSInteger) indexOfItem: (NSMenuItem *) item;'
        if text.count(anchor) != 1: raise RuntimeError('Pinned menu header changed')
        path.write_text(text.replace(anchor, declaration + '\n' + anchor))

if __name__ == '__main__': main()
