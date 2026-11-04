"""Correct sparse NSIndexSet cache counts and insertion order."""
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    path = args.source / 'src/external/foundation/src/NSIndexSet.m'
    text = path.read_text()
    pairs = [
        ('        NSIndexSetBuildCache(&CACHE(set), MULTIPLE_RANGE_DATA(set)); \\',
         '        NSIndexSetBuildCache(&CACHE(set), MULTIPLE_RANGE_DATA(set)); \\\n        SET_CACHE_VALID(set, CACHE(set) != NULL); \\'),
        ('            values = malloc(capacity * sizeof(RangeList *));',
         '            capacity = STACK_SIZE * 2;\n            values = malloc(capacity * sizeof(RangeList *));'),
        ('    (*cache)->rangeCount = rangeCount;', '    (*cache)->rangeCount = rangeCount;\n    (*cache)->count = count;'),
        ('            DL_INSERT(MULTIPLE_RANGE_DATA(self), ptr, newRange);',
         '            RangeList *following = ptr->next;\n            DL_INSERT(MULTIPLE_RANGE_DATA(self), following, newRange);'),
        ('if (rangeCount > STACK_SIZE && values == &stack_values[0])',
         'if (rangeCount >= STACK_SIZE && values == &stack_values[0])'),
        ('        else if (rangeCount > STACK_SIZE && rangeCount + 1 > capacity)',
         '        else if (rangeCount >= STACK_SIZE && rangeCount + 1 > capacity)'),
        ('        values[rangeCount] = entry;',
         '        if (rangeCount == STACK_SIZE && values != &stack_values[0]) {\n            for (NSUInteger i = 0; i < STACK_SIZE; ++i) values[i] = stack_values[i];\n        }\n        values[rangeCount] = entry;'),
    ]
    for old, new in pairs:
        if 'ptr, newRange' in old and 'DL_INSERT(MULTIPLE_RANGE_DATA(self), ptr->next, newRange);' in text:
            text = text.replace('            DL_INSERT(MULTIPLE_RANGE_DATA(self), ptr->next, newRange);', new)
        if new in text: continue
        if text.count(old) != 1: raise RuntimeError('Pinned NSIndexSet anchor changed')
        text = text.replace(old, new)
    path.write_text(text)

if __name__ == '__main__': main()
