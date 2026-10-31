#!/usr/bin/env python3
"""Finds the inline Japanese/English text pairs and rewrites interpolated ones as templates.

Linux and Dextop Auto UI code writes its text in place, as `t('ja', 'en')`,
`linuxText(context, 'ja', 'en')` (Dart) or `tr("ja", "en")` (Kotlin). Other languages
look the English text up in lib/l10n/inline/<locale>.json (see generate_inline_strings.py).
Text with interpolation cannot be looked up after it is built, so `--rewrite` turns
such calls into the template forms `tf(...)`/`linuxTextf(...)`/`trf(...)` with {0}, {1}.

  inline_strings.py --list      print every English text, one JSON string per line
  inline_strings.py --rewrite   rewrite interpolated calls in place
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DART = [p for p in (ROOT / 'lib').rglob('*.dart') if 'l10n' not in p.parts]
KOTLIN = list((ROOT / 'android' / 'app' / 'src' / 'main' / 'kotlin').rglob('*.kt'))


def dart_literal(src, i):
    """A Dart string literal at i ('...' or "...", optional r prefix): (end, parts, raw)."""
    raw = False
    if src[i] == 'r':
        raw = True
        i += 1
    q = src[i]
    if q not in '\'"':
        return None
    if src.startswith(q * 3, i):
        return None
    j = i + 1
    parts = []  # list of ('t', text) or ('e', expr)
    text = ''
    while j < len(src):
        c = src[j]
        if c == '\\' and not raw:
            text += src[j:j + 2]
            j += 2
            continue
        if c == q:
            parts.append(('t', text))
            return j + 1, parts
        if c == '$' and not raw:
            if src[j + 1] == '{':
                depth = 0
                k = j + 1
                while True:
                    if src[k] == '{':
                        depth += 1
                    elif src[k] == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    k += 1
                parts.append(('t', text))
                text = ''
                parts.append(('e', src[j + 2:k]))
                j = k + 1
                continue
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', src[j + 1:])
            if m:
                parts.append(('t', text))
                text = ''
                parts.append(('e', m.group(0)))
                j += 1 + len(m.group(0))
                continue
        if c == '\n':
            return None
        text += c
        j += 1
    return None


def kotlin_literal(src, i):
    if src[i] != '"' or src.startswith('"""', i):
        return None
    j = i + 1
    parts = []
    text = ''
    while j < len(src):
        c = src[j]
        if c == '\\':
            text += src[j:j + 2]
            j += 2
            continue
        if c == '"':
            parts.append(('t', text))
            return j + 1, parts
        if c == '$':
            if src[j + 1] == '{':
                depth = 0
                k = j + 1
                while True:
                    if src[k] == '{':
                        depth += 1
                    elif src[k] == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    k += 1
                parts.append(('t', text))
                text = ''
                parts.append(('e', src[j + 2:k]))
                j = k + 1
                continue
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', src[j + 1:])
            if m:
                parts.append(('t', text))
                text = ''
                parts.append(('e', m.group(0)))
                j += 1 + len(m.group(0))
                continue
        if c == '\n':
            return None
        text += c
        j += 1
    return None


def skip_space(src, i, kotlin):
    while i < len(src):
        if src[i] in ' \t\r\n':
            i += 1
        elif src.startswith('//', i):
            i = src.index('\n', i)
        else:
            break
    return i


def argument(src, i, kotlin):
    """Adjacent literals (Dart) or literals joined with + (Kotlin): (end, parts)."""
    literal = kotlin_literal if kotlin else dart_literal
    parts = []
    i = skip_space(src, i, kotlin)
    first = literal(src, i)
    if first is None:
        return None
    i, p = first
    parts += p
    while True:
        j = skip_space(src, i, kotlin)
        if kotlin:
            if j < len(src) and src[j] == '+':
                k = skip_space(src, j + 1, kotlin)
                nxt = literal(src, k)
                if nxt is None:
                    return None
                i, p = nxt
                parts += p
                continue
        else:
            nxt = literal(src, j) if j < len(src) and src[j] in '\'"r' else None
            if nxt is not None:
                i, p = nxt
                parts += p
                continue
        return i, parts


CALL = {
    False: re.compile(r'(?<![A-Za-z0-9_.])(t|tr|linuxText|localText)\('),
    True: re.compile(r'(?<![A-Za-z0-9_.])((?:moe\.n4tsu\.dextop\.)?(?:tr|t|message|localized|text|phase|localText))\('),
}


def calls(src, kotlin):
    """(start, end, name, prefix, ja_parts, en_parts, suffix) for each text call."""
    for m in CALL[kotlin].finditer(src):
        i = m.end()
        prefix = ''
        if m.group(1) in ('linuxText', 'localText') and not kotlin:
            c = re.match(r'\s*context\s*,', src[i:])
            if not c:
                continue
            prefix = src[i:i + c.end()]
            i += c.end()
        a = argument(src, i, kotlin)
        if a is None:
            continue
        i, ja = a
        j = skip_space(src, i, kotlin)
        if j >= len(src) or src[j] != ',':
            continue
        b = argument(src, j + 1, kotlin)
        if b is None:
            continue
        i, en = b
        k = skip_space(src, i, kotlin)
        if k < len(src) and src[k] == ',':
            k = skip_space(src, k + 1, kotlin)
        if k >= len(src) or src[k] != ')':
            continue
        yield m.start(), k + 1, m.group(1), prefix, ja, en


def template(parts, exprs):
    out = ''
    for kind, value in parts:
        if kind == 't':
            out += value
        else:
            key = value.strip()
            if key not in exprs:
                exprs.append(key)
            out += '{%d}' % exprs.index(key)
    return out


def unescape(text, kotlin):
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), text)


def literal_of(text, kotlin):
    if kotlin:
        return '"' + text.replace('$', '\\$') + '"'
    if "'" in text.replace("\\'", ''):
        return '"' + text.replace('$', '\\$') + '"'
    return "'" + text.replace('$', '\\$') + "'"


def main():
    mode = sys.argv[1]
    texts = []
    for path in DART + KOTLIN:
        kotlin = path.suffix == '.kt'
        src = open(path, encoding='utf-8', newline='').read()
        found = list(calls(src, kotlin))
        if not found:
            continue
        out = src
        for start, end, name, prefix, ja, en in reversed(found):
            exprs = []
            ja_t = template(ja, exprs)
            en_t = template(en, exprs)
            texts.append(unescape(en_t, kotlin))
            if mode == '--rewrite' and exprs:
                # The template is looked up, then filled: lookup keys never contain values.
                args = ', '.join(exprs)
                inner = '%s(%s%s, %s)' % (name, prefix.strip() + ' ' if prefix else '',
                                          literal_of(ja_t, kotlin), literal_of(en_t, kotlin))
                if kotlin:
                    call = 'moe.n4tsu.dextop.InlineStrings.format(%s, %s)' % (inner, args)
                else:
                    call = 'inlineFormat(%s, [%s])' % (inner, args)
                out = out[:start] + call + out[end:]
        if mode == '--rewrite' and out != src:
            if not kotlin and 'inline_text.dart' not in out:
                depth = len(path.relative_to(ROOT / 'lib').parts) - 1
                line = "import '%sl10n/inline_text.dart';\n" % ('../' * depth)
                first = re.search(r'^import .*;$', out, re.M)
                out = out[:first.start()] + line + out[first.start():] if first else line + out
            open(path, 'w', encoding='utf-8', newline='').write(out)
            print('rewrote', path.relative_to(ROOT))
    if mode == '--list':
        seen = set()
        for t in texts:
            if t not in seen:
                seen.add(t)
                print(json.dumps(t, ensure_ascii=False))


if __name__ == '__main__':
    main()
