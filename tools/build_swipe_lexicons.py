#!/usr/bin/env python3
"""Build compact offline swipe lexicons from pinned open-source dictionaries."""

from pathlib import Path
import gzip
import re
import unicodedata
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "android/app/src/main/res/raw"
OUT.mkdir(parents=True, exist_ok=True)

FREQUENCY = "525f9b560de45753a5ea01069454e72e9aa541c6"
KAGIROI = "1951bab3742495f763b79809af253884c33358e9"
RIME_ICE = "fbb516b2786e4d5444383706d13c31c2e4d10c08"


def fetch(url: str) -> str:
    with urllib.request.urlopen(url) as response:
        return response.read().decode("utf-8")


def write(name: str, rows: list[tuple[str, str]]) -> None:
    seen = set()
    with gzip.open(OUT / name, "wt", encoding="utf-8", newline="\n") as stream:
        for trace, value in rows:
            trace = "".join(
                c for c in unicodedata.normalize("NFD", trace)
                if unicodedata.category(c) != "Mn"
            )
            trace = re.sub(r"[^a-zа-яіїєґㄱ-ㅎㅏ-ㅣ]", "", trace.lower())
            if len(trace) < 2 or not value or value in seen:
                continue
            seen.add(value)
            stream.write(f"{trace}\t{value}\n")


def frequency(language: str, limit: int) -> list[tuple[str, str]]:
    url = ("https://raw.githubusercontent.com/hermitdave/FrequencyWords/"
           f"{FREQUENCY}/content/2018/{language}/{language}_50k.txt")
    rows = []
    for line in fetch(url).splitlines():
        word = line.rsplit(" ", 1)[0].strip().lower()
        if word.isalpha():
            rows.append((word, word))
        if len(rows) >= limit:
            break
    return rows


KANA = {
    "あ":"a","い":"i","う":"u","え":"e","お":"o","か":"ka","き":"ki","く":"ku","け":"ke","こ":"ko",
    "さ":"sa","し":"shi","す":"su","せ":"se","そ":"so","た":"ta","ち":"chi","つ":"tsu","て":"te","と":"to",
    "な":"na","に":"ni","ぬ":"nu","ね":"ne","の":"no","は":"ha","ひ":"hi","ふ":"fu","へ":"he","ほ":"ho",
    "ま":"ma","み":"mi","む":"mu","め":"me","も":"mo","や":"ya","ゆ":"yu","よ":"yo",
    "ら":"ra","り":"ri","る":"ru","れ":"re","ろ":"ro","わ":"wa","を":"wo","ん":"n",
    "が":"ga","ぎ":"gi","ぐ":"gu","げ":"ge","ご":"go","ざ":"za","じ":"ji","ず":"zu","ぜ":"ze","ぞ":"zo",
    "だ":"da","ぢ":"ji","づ":"zu","で":"de","ど":"do","ば":"ba","び":"bi","ぶ":"bu","べ":"be","ぼ":"bo",
    "ぱ":"pa","ぴ":"pi","ぷ":"pu","ぺ":"pe","ぽ":"po","ゔ":"vu",
}
COMBO = {"きゃ":"kya","きゅ":"kyu","きょ":"kyo","しゃ":"sha","しゅ":"shu","しょ":"sho","ちゃ":"cha","ちゅ":"chu","ちょ":"cho",
         "にゃ":"nya","にゅ":"nyu","にょ":"nyo","ひゃ":"hya","ひゅ":"hyu","ひょ":"hyo","みゃ":"mya","みゅ":"myu","みょ":"myo",
         "りゃ":"rya","りゅ":"ryu","りょ":"ryo","ぎゃ":"gya","ぎゅ":"gyu","ぎょ":"gyo","じゃ":"ja","じゅ":"ju","じょ":"jo",
         "びゃ":"bya","びゅ":"byu","びょ":"byo","ぴゃ":"pya","ぴゅ":"pyu","ぴょ":"pyo"}


def romaji(reading: str) -> str:
    reading = "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in reading)
    out, i, geminate = [], 0, False
    while i < len(reading):
        if reading[i] == "っ":
            geminate = True; i += 1; continue
        token = reading[i:i+2] if reading[i:i+2] in COMBO else reading[i]
        value = COMBO.get(token, KANA.get(token, ""))
        if geminate and value:
            out.append(value[0]); geminate = False
        out.append(value); i += len(token)
    return "".join(out)


def japanese(limit: int) -> list[tuple[str, str]]:
    url = ("https://raw.githubusercontent.com/rimeinn/rime-kagiroi/"
           f"{KAGIROI}/kagiroi.mozc.dict.yaml")
    ranked = []
    for line in fetch(url).splitlines():
        parts = line.split("\t")
        if len(parts) != 3 or "|" not in parts[0]:
            continue
        value = parts[0].split("|", 1)[0]
        trace = romaji(parts[1])
        if trace and len(value) <= 12:
            ranked.append((int(parts[2]) if parts[2].isdigit() else 99999, trace, value))
    ranked.sort(key=lambda item: item[0])
    return [(trace, value) for _, trace, value in ranked[:limit]]


def chinese(limit: int) -> list[tuple[str, str]]:
    url = ("https://raw.githubusercontent.com/iDvel/rime-ice/"
           f"{RIME_ICE}/cn_dicts/base.dict.yaml")
    readings = {}
    for line in fetch(url).splitlines():
        parts = line.split("\t")
        if len(parts) < 2 or not parts[0] or parts[0].startswith("#"):
            continue
        readings.setdefault(parts[0], parts[1].replace(" ", ""))
    # Rime's weights are tuned for its language model rather than being a
    # global word-frequency order. Preserve FrequencyWords order and only use
    # Rime for the corresponding pinyin reading.
    words = frequency("zh_cn", limit * 4)
    rows = [(readings[word], word) for _, word in words if word in readings]
    return rows[:limit]


INITIAL = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
MEDIAL = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
FINAL = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
JAMO_KEYS = set("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ")
JAMO_EXPANSION = {
    "ㄲ":"ㄱㄱ", "ㄸ":"ㄷㄷ", "ㅃ":"ㅂㅂ", "ㅆ":"ㅅㅅ", "ㅉ":"ㅈㅈ",
    "ㅒ":"ㅑㅣ", "ㅖ":"ㅕㅣ", "ㅘ":"ㅗㅏ", "ㅙ":"ㅗㅐ", "ㅚ":"ㅗㅣ",
    "ㅝ":"ㅜㅓ", "ㅞ":"ㅜㅔ", "ㅟ":"ㅜㅣ", "ㅢ":"ㅡㅣ",
    "ㄳ":"ㄱㅅ", "ㄵ":"ㄴㅈ", "ㄶ":"ㄴㅎ", "ㄺ":"ㄹㄱ", "ㄻ":"ㄹㅁ",
    "ㄼ":"ㄹㅂ", "ㄽ":"ㄹㅅ", "ㄾ":"ㄹㅌ", "ㄿ":"ㄹㅍ", "ㅀ":"ㄹㅎ", "ㅄ":"ㅂㅅ",
}


def keyboard_jamo(value: str) -> str:
    return "".join(c if c in JAMO_KEYS else JAMO_EXPANSION.get(c, "") for c in value)


def hangul_trace(word: str) -> str:
    out = []
    for char in word:
        code = ord(char) - 0xAC00
        if 0 <= code < 11172:
            out += [keyboard_jamo(INITIAL[code // 588]), keyboard_jamo(MEDIAL[(code % 588) // 28])]
            final = FINAL[code % 28]
            if final != " ": out.append(keyboard_jamo(final))
    return "".join(out)


def korean(limit: int) -> list[tuple[str, str]]:
    words = frequency("ko", limit * 2)
    return [(hangul_trace(word), word) for _, word in words if hangul_trace(word)][:limit]


write("swipe_en.gz", frequency("en", 12000))
write("swipe_fr.gz", frequency("fr", 12000))
write("swipe_de.gz", frequency("de", 12000))
write("swipe_es.gz", frequency("es", 12000))
write("swipe_it.gz", frequency("it", 12000))
write("swipe_pt.gz", frequency("pt_br", 12000))
write("swipe_ru.gz", frequency("ru", 12000))
write("swipe_uk.gz", frequency("uk", 12000))
write("swipe_ko.gz", korean(8000))
write("swipe_ja.gz", japanese(8000))
write("swipe_zh_pinyin.gz", chinese(8000))
