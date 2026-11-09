"""Find owned exception text without mistaking channel error codes for prose."""

import re
import inline_strings as parser


def literals(source, kotlin):
    index = 0
    while index < len(source):
        if source.startswith('//', index):
            end = source.find('\n', index)
            index = len(source) if end < 0 else end + 1
            continue
        if source.startswith('/*', index):
            end = source.find('*/', index + 2)
            index = len(source) if end < 0 else end + 2
            continue
        if source.startswith('"""', index) or (not kotlin and source.startswith("'''", index)):
            end = source.find(source[index:index + 3], index + 3)
            index = len(source) if end < 0 else end + 3
            continue
        if kotlin and source[index] == "'":
            character = re.match(r"'(?:\\.|[^'\\])'", source[index:])
            if character:
                index += character.end()
                continue
        if source[index] not in ('"',) if kotlin else source[index] not in ('"', "'"):
            index += 1
            continue
        parsed = (parser.kotlin_literal if kotlin else parser.dart_literal)(source, index)
        if parsed is None:
            index += 1
            continue
        end, parts = parsed
        arguments = []
        value = parser.unescape(parser.template(parts, arguments), kotlin)
        yield index, end, value, arguments
        index = end


def balanced_end(source, start):
    opening = source[start]
    closing = {'(': ')', '{': '}', '[': ']'}[opening]
    depth = 0
    index = start
    while index < len(source):
        if source.startswith('//', index):
            end = source.find('\n', index)
            index = len(source) if end < 0 else end + 1
            continue
        if source.startswith('/*', index):
            end = source.find('*/', index + 2)
            index = len(source) if end < 0 else end + 2
            continue
        if source.startswith('"""', index):
            end = source.find('"""', index + 3)
            index = len(source) if end < 0 else end + 3
            continue
        if source[index] == '"':
            literal = parser.kotlin_literal(source, index)
            if literal:
                index = literal[0]
                continue
        if source[index] == "'":
            character = re.match(r"'(?:\\.|[^'\\])'", source[index:])
            if character:
                index += character.end()
                continue
        if source[index] == opening:
            depth += 1
        if source[index] == closing:
            depth -= 1
            if depth == 0:
                return index + 1
        index += 1
    return len(source)


def failures(source, kotlin):
    ranges = []
    if kotlin:
        for match in re.finditer(r'\b(?:check|require|checkNotNull|requireNotNull)\(', source):
            end = balanced_end(source, match.end() - 1)
            next_token = parser.skip_space(source, end, True)
            if next_token < len(source) and source[next_token] == '{':
                ranges.append((next_token, balanced_end(source, next_token)))
    constructor = (r'(?:\b[A-Za-z][A-Za-z0-9_.]*Exception\(|(?<![A-Za-z0-9_.])error\()\s*'
                   if kotlin else
                   r'\b(?:[A-Za-z][A-Za-z0-9_.]*(?:Error|Exception)|Exception)\(\s*')
    for match in re.finditer(constructor, source):
        start = match.end()
        if start >= len(source) or source[start] not in ('"', "'"):
            continue
        literal = (parser.kotlin_literal if kotlin else parser.dart_literal)(source, start)
        if literal:
            ranges.append((start, literal[0]))
    for start, end, value, arguments in literals(source, kotlin):
        if not any(begin <= start < finish for begin, finish in ranges):
            continue
        before = source[max(0, start - 100):start]
        if re.search(r'\b(?:get|opt)String\(\s*\Z', before):
            continue  # JSON field names, including "error" and "output".
        if re.fullmatch(r'[A-Za-z_]+=\{\d+\}', value.strip()):
            continue  # Structured diagnostic field such as service={0}.
        yield start, end, value, arguments
