#!/usr/bin/env python3
"""Localization QA gate for Episteme string resources.

Checks Android string resources (source of truth) and the generated JSON
catalogs consumed by iOS/desktop. See docs/localization-revamp-plan.md §4.

Usage:
    python3 scripts/i18n_check.py                 # full check
    python3 scripts/i18n_check.py --lang values-de --lang values-ar
    python3 scripts/i18n_check.py --skip-coverage # structural checks only
    python3 scripts/i18n_check.py --skip-json     # skip JSON catalog sync
    python3 scripts/i18n_check.py --strict        # warnings are errors
    python3 scripts/i18n_check.py --self-test     # run built-in self test

Exit code 1 if any error (or warning in --strict mode).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_RES = ROOT / "app/src/main/res"
IOS_JSON_DIR = ROOT / "shared/src/iosMain/composeResources/files/localization"

# Android legacy folder codes -> BCP-47 tag used by the generated JSON catalogs.
FOLDER_TO_TAG = {
    "values": "en",
    "values-ar": "ar",
    "values-be": "be",
    "values-de": "de",
    "values-es": "es",
    "values-et": "et",
    "values-fr": "fr",
    "values-hi": "hi",
    "values-in": "id",
    "values-it": "it",
    "values-ja": "ja",
    "values-ko": "ko",
    "values-nl": "nl",
    "values-pl": "pl",
    "values-pt-rBR": "pt-BR",
    "values-ru": "ru",
    "values-tr": "tr",
    "values-uk": "uk",
    "values-vi": "vi",
    "values-zh-rCN": "zh-CN",
}

# CLDR plural-rule exceptions: languages whose plural systems have no 'one' category
# (isolating languages: Vietnamese, Indonesian, Japanese, Korean, Chinese; Thai if added
# later). Omitting English's 'one' quantity there is expected, not drift — only 'other'
# is required. Slavic/Arabic languages REQUIRE all base quantities (default rule).
OTHER_ONLY_PLURAL_LANGS = {"id", "vi", "ja", "ko", "zh-CN", "th"}

MAX_EXAMPLES = 5

# Positional placeholders: %1$s, %2$d, %1$.2f, ...
POSITIONAL = re.compile(r"%(\d+)\$[-+#, .(]*\d*(?:\.\d+)?[a-zA-Z]")
# Unpositioned placeholders: %s, %d, %.2f. Android uses %% for a literal
# percent sign, so '%1$d%% complete' is valid; %% and positional placeholders
# are masked before this check runs. No space is allowed between '%' and the
# conversion (a space means it is a stray '%', not a placeholder).
UNPOSITIONED = re.compile(r"%(?!\d+\$)[-+#.]*\d*(?:\.\d+)?[a-zA-Z]")
# A '%' that is neither a placeholder nor part of '%%' crashes Android's
# String.format at runtime (e.g. "50% off"). Checked on masked text.
LONE_PERCENT = re.compile(r"%")
# Apostrophe not escaped with a backslash -- Android (AAPT) rejects these in
# unquoted resource values. Must be checked on the RAW XML text: parsed
# values have already lost the distinction between \' and '.
RAW_UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")
XML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)


@dataclass
class Report:
    lang: str
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)


def android_unescape(value: str) -> str:
    return (
        value.replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\'", "'")
        .replace('\\"', '"')
    )


def parse_strings_xml(path: Path, rep: Report) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        rep.error(f"{path.name}: XML parse error: {exc}")
        return {}
    strings: dict[str, str] = {}
    for element in root.findall("string"):
        name = element.attrib.get("name", "")
        if not name:
            rep.error(f"{path.name}: <string> without name attribute")
            continue
        if name in strings:
            rep.error(f"{path.name}: duplicate string name '{name}'")
        strings[name] = "".join(element.itertext())
    return strings


def parse_plurals_xml(path: Path, rep: Report) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        rep.error(f"{path.name}: XML parse error: {exc}")
        return {}
    plurals: dict[str, dict[str, str]] = {}
    for plural in root.findall("plurals"):
        name = plural.attrib.get("name", "")
        if not name:
            rep.error(f"{path.name}: <plurals> without name attribute")
            continue
        items = {
            item.attrib.get("quantity", ""): "".join(item.itertext())
            for item in plural.findall("item")
            if item.attrib.get("quantity")
        }
        if name in plurals:
            rep.error(f"{path.name}: duplicate plurals name '{name}'")
        plurals[name] = items
    return plurals


def placeholder_signature(value: str) -> list[tuple[int, str]]:
    """Sorted (index, type-char) pairs, e.g. [(1, 'd'), (2, 's')]."""
    return sorted((int(m.group(1)), m.group(0)[-1]) for m in POSITIONAL.finditer(value))


def mask_placeholders(value: str) -> str:
    """Mask %% and positional placeholders so only stray '%' patterns remain."""
    masked = value.replace("%%", "\x00")
    masked = POSITIONAL.sub("\x01", masked)
    return masked


def check_value(
    rep: Report,
    key: str,
    value: str,
    where: str,
    base_signature: list[tuple[int, str]] | None = None,
) -> None:
    """Structural checks on a single (parsed) string value.

    Note: ampersand escaping cannot be checked here -- ElementTree has already
    decoded entities, and any invalid entity would have failed XML parsing.
    Apostrophes are checked on raw XML text separately (see check_raw_xml).
    """
    masked = mask_placeholders(value)
    has_placeholders = bool(POSITIONAL.search(value))
    if UNPOSITIONED.search(masked):
        rep.error(f"{where} '{key}': unpositioned placeholder (use %1$s style)")
    elif has_placeholders and LONE_PERCENT.search(masked):
        # A bare '%' only crashes String.format when the string is actually
        # formatted (i.e. it has placeholders). Plain strings like "50% OFF"
        # are returned unformatted and are fine.
        rep.error(f"{where} '{key}': stray '%' (escape as %% or use a positional placeholder)")
    if base_signature is not None and placeholder_signature(value) != base_signature:
        rep.error(
            f"{where} '{key}': placeholder mismatch vs base "
            f"({placeholder_signature(value)} != {base_signature})"
        )


def check_raw_xml(path: Path, rep: Report) -> None:
    """Raw-text scan of a strings/plurals XML file for AAPT-hostile apostrophes.

    An apostrophe must be either backslash-escaped or inside a value wrapped
    in literal double quotes. Comments are ignored.
    """
    if not path.exists():
        return
    raw = XML_COMMENT.sub("", path.read_text(encoding="utf-8"))
    element_pattern = re.compile(r"<(string|item)\b([^>]*)>(.*?)</\1>", re.DOTALL)
    for match in element_pattern.finditer(raw):
        attrs, inner = match.group(2), match.group(3)
        name_match = re.search(r'name="([^"]*)"', attrs)
        key = name_match.group(1) if name_match else "?"
        stripped = inner.strip()
        is_quoted = len(stripped) >= 2 and stripped.startswith('"') and stripped.endswith('"')
        if is_quoted:
            continue
        if RAW_UNESCAPED_APOSTROPHE.search(inner):
            line = raw.count("\n", 0, match.start(3)) + 1
            rep.error(
                f"{path.name} '{key}' (line {line}): unescaped apostrophe "
                "(use \\' or wrap value in double quotes)"
            )


def check_base_comment_coverage(path: Path, rep: Report, min_words: int = 12) -> None:
    """English base only: strings that genuinely need context must have it.

    Targets the ambiguity class only -- long sentences (more than min_words
    words) and multi-paragraph strings (literal \\n\\n). One-word buttons and
    short labels are deliberately NOT required to carry comments. Enforces the
    conventions header added 2026-09-21; keeps future additions honest.
    """
    if not path.exists():
        return
    raw = path.read_text(encoding="utf-8")
    offenders: list[str] = []
    for match in re.finditer(r"<string\b([^>]*)>(.*?)</string>", raw, re.DOTALL):
        attrs, inner = match.group(1), match.group(2)
        if 'translatable="false"' in attrs:
            continue
        words = len(inner.split())
        multi_paragraph = "\\n\\n" in inner
        if words <= min_words and not multi_paragraph:
            continue
        before = raw[: match.start()]
        comment_end = before.rfind("-->")
        between = before[comment_end + 3 :] if comment_end != -1 else before
        if between.strip():
            name_match = re.search(r'name="([^"]*)"', attrs)
            offenders.append(name_match.group(1) if name_match else "?")
    if offenders:
        rep.warn(
            f"strings.xml: {len(offenders)} long/multi-paragraph strings lack a translator "
            f"comment (target 0; see the conventions header), e.g. {offenders[:5]}"
        )


def check_xml_file(
    rep: Report,
    strings: dict[str, str],
    base_strings: dict[str, str],
    base_translatable_false: set[str],
) -> None:
    for name, value in strings.items():
        if name in base_translatable_false:
            rep.error(f"strings.xml '{name}': translatable=false key must not be translated")
        if name == "app_name" and value != "Episteme":
            rep.error(f"strings.xml 'app_name': must not be translated (found '{value}')")
        base_value = base_strings.get(name)
        base_sig = placeholder_signature(base_value) if base_value is not None else None
        check_value(rep, name, value, "strings.xml", base_sig)
        if base_value is None and name not in base_translatable_false:
            rep.warn(f"strings.xml '{name}': not present in English base")
        elif (
            base_value is not None
            and android_unescape(value) == android_unescape(base_value)
            and re.search(r"[A-Za-z]{3}", value)
        ):
            rep.warn(f"strings.xml '{name}': identical to English (possible untranslated copy)")


def check_plurals_file(
    rep: Report,
    plurals: dict[str, dict[str, str]],
    base_plurals: dict[str, dict[str, str]],
    lang: str = "en",
) -> None:
    for name in plurals:
        if name not in base_plurals:
            rep.warn(f"plurals.xml '{name}': not present in English base")
    for name, base_items in base_plurals.items():
        items = plurals.get(name)
        if items is None:
            rep.error(f"plurals.xml: missing plurals key '{name}'")
            continue
        if "other" not in items:
            rep.error(f"plurals.xml '{name}': missing required 'other' quantity")
        for qty in base_items:
            if qty not in items:
                if lang in OTHER_ONLY_PLURAL_LANGS and qty != "other":
                    continue  # expected: these languages have no such plural category (CLDR)
                rep.warn(f"plurals.xml '{name}': missing quantity '{qty}' (base provides it)")
        for qty, value in items.items():
            base_value = base_items.get(qty)
            base_sig = placeholder_signature(base_value) if base_value is not None else None
            check_value(rep, f"{name}#{qty}", value, "plurals.xml", base_sig)


def load_json_catalog(tag: str) -> tuple[dict[str, str], dict[str, dict[str, str]]] | None:
    path = IOS_JSON_DIR / f"{tag}.json"
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    strings = dict(data.get("strings", {}))
    plurals = {k: dict(v) for k, v in data.get("plurals", {}).items()}
    return strings, plurals


def used_reader_keys(source_roots: tuple[Path, ...]) -> tuple[set[str], set[str]]:
    """Scan Kotlin sources for readerString/readerQuantityString keys.

    Returns (string_keys, plural_keys) -- mirrors the key selection of the
    catalog generator (scripts/generate_shared_mobile_localizations.py).
    Only literal-first calls are captured: `readerString("key", ...)`.
    Dynamic calls like `readerString(option.stringKey, ...)` resolve to keys
    that are also used literally elsewhere; extension functions named
    `readerString` are excluded by requiring no preceding dot/word char.
    """
    call_pattern = re.compile(r'(?<![.\w])reader(String|QuantityString)\s*\(\s*"([^"]+)"')
    string_keys: set[str] = set()
    plural_keys: set[str] = set()
    for root in source_roots:
        if not root.exists():
            continue
        for source in root.rglob("*.kt"):
            text = source.read_text(encoding="utf-8", errors="replace")
            for match in call_pattern.finditer(text):
                if match.group(1) == "String":
                    string_keys.add(match.group(2))
                else:
                    plural_keys.add(match.group(2))
    return string_keys, plural_keys


def check_json_sync(
    rep: Report,
    tag: str,
    xml_strings: dict[str, str],
    xml_plurals: dict[str, dict[str, str]],
    expected_string_keys: set[str],
    expected_plural_keys: set[str],
) -> None:
    """Generated JSON catalogs must mirror the XML source of truth plus the
    readerString()/readerQuantityString() keys used from shared code."""
    catalog = load_json_catalog(tag)
    if catalog is None:
        rep.error(f"json: missing or unreadable catalog {IOS_JSON_DIR.name}/{tag}.json")
        return
    json_strings, json_plurals = catalog
    expected_strings = set(xml_strings) | expected_string_keys
    missing = expected_strings - set(json_strings)
    extra = set(json_strings) - expected_strings
    if tag == "en":
        # Key-set shape is a base-file concern; report it once under en, not
        # once per language. 'missing' here = keys the generator cannot source
        # (used in code but absent from XML) -- they resolve to inline English
        # fallbacks on iOS until they are added to the base resource file.
        if missing:
            rep.warn(
                f"json: {len(missing)} readerString keys have no XML entry "
                f"(English fallback everywhere), e.g. {sorted(missing)[:3]}"
            )
        if extra:
            rep.warn(f"json en.json: {len(extra)} extra keys not in XML, e.g. {sorted(extra)[:3]}")
    for key in sorted(set(xml_strings) & set(json_strings)):
        xml_value = android_unescape(xml_strings[key])
        json_value = json_strings[key]
        if json_value != xml_value:
            rep.error(
                f"json {tag}.json '{key}': value drift vs XML "
                f"(json={json_value!r} xml={xml_value!r})"
            )
    for name in sorted(set(json_plurals) - expected_plural_keys):
        rep.warn(f"json {tag}.json: extra plurals '{name}'")
    for name in sorted(expected_plural_keys - set(json_plurals)):
        rep.warn(f"json {tag}.json: missing plurals '{name}' (used via readerQuantityString)")
    for name, xml_items in xml_plurals.items():
        if name not in expected_plural_keys:
            continue
        json_items = json_plurals.get(name)
        if json_items is None:
            rep.warn(f"json {tag}.json: missing plurals '{name}'")
            continue
        for qty, json_value in json_items.items():
            xml_value = android_unescape(xml_items.get(qty, ""))
            if xml_value and json_value != xml_value:
                rep.error(
                    f"json {tag}.json '{name}#{qty}': value drift vs XML "
                    f"(json={json_value!r} xml={xml_value!r})"
                )


def check_language(
    folder: str,
    base_strings: dict[str, str],
    base_plurals: dict[str, dict[str, str]],
    base_translatable_false: set[str],
    skip_coverage: bool,
    skip_json: bool,
    expected_string_keys: set[str] | None = None,
    expected_plural_keys: set[str] | None = None,
) -> Report:
    tag = FOLDER_TO_TAG.get(folder, folder.removeprefix("values-"))
    rep = Report(lang=tag)
    lang_dir = ANDROID_RES / folder

    xml_strings = parse_strings_xml(lang_dir / "strings.xml", rep)
    xml_plurals = parse_plurals_xml(lang_dir / "plurals.xml", rep)
    check_raw_xml(lang_dir / "strings.xml", rep)
    check_raw_xml(lang_dir / "plurals.xml", rep)

    if folder != "values":
        check_xml_file(rep, xml_strings, base_strings, base_translatable_false)
        check_plurals_file(rep, xml_plurals, base_plurals, tag)
        if not skip_coverage:
            missing_strings = set(base_strings) - set(xml_strings) - base_translatable_false
            if missing_strings:
                rep.error(
                    f"coverage: {len(missing_strings)} base strings missing, "
                    f"e.g. {sorted(missing_strings)[:MAX_EXAMPLES]}"
                )
            missing_plurals = set(base_plurals) - set(xml_plurals)
            if missing_plurals:
                rep.error(
                    f"coverage: {len(missing_plurals)} base plurals missing: "
                    f"{sorted(missing_plurals)[:MAX_EXAMPLES]}"
                )

    if not skip_json:
        # The catalogs exclude translatable=false keys (like the generator).
        translatable_xml_strings = {
            k: v for k, v in xml_strings.items() if k not in base_translatable_false
        }
        check_json_sync(
            rep,
            tag,
            translatable_xml_strings,
            xml_plurals,
            expected_string_keys or set(),
            expected_plural_keys or set(),
        )

    return rep


# ---------------------------------------------------------------------------
# Self test: synthetic fixtures verify each check fires (and passes).
# ---------------------------------------------------------------------------

def _self_test() -> int:
    failures: list[str] = []

    def expect(cond: bool, label: str) -> None:
        if not cond:
            failures.append(label)

    base = {"greeting": "Hello %1$s", "count": "%1$d items", "pct": "%1$d%% done"}
    translatable_false: set[str] = set()

    # Passing case, including the valid '%1$d%%' literal-percent form.
    good = {"greeting": "Hola %1$s", "count": "%1$d elementos", "pct": "%1$d%% hecho"}
    rep = Report(lang="t")
    check_xml_file(rep, good, base, translatable_false)
    expect(not rep.errors, f"good strings should pass, got {rep.errors}")
    expect(not rep.warnings, f"good strings should not warn, got {rep.warnings}")

    # Placeholder mismatch.
    rep = Report(lang="t")
    check_xml_file(rep, {"greeting": "Hola %2$s"}, base, translatable_false)
    expect(any("placeholder mismatch" in e for e in rep.errors), "placeholder mismatch should fire")

    # Stray single '%' (String.format crash) fires; '%%' does not.
    rep = Report(lang="t")
    check_xml_file(rep, {"greeting": "50% off %1$s"}, base, translatable_false)
    expect(any("stray '%'" in e for e in rep.errors), "stray percent should fire")
    rep = Report(lang="t")
    check_xml_file(rep, {"greeting": "50%% off %1$s"}, base, translatable_false)
    expect(not rep.errors, f"escaped percent should pass, got {rep.errors}")

    # Unpositioned placeholder.
    rep = Report(lang="t")
    check_xml_file(rep, {"greeting": "Hi %s"}, base, translatable_false)
    expect(any("unpositioned" in e for e in rep.errors), "unpositioned placeholder should fire")

    # Identical-to-English warning.
    rep = Report(lang="t")
    check_xml_file(rep, {"greeting": "Hello %1$s"}, base, translatable_false)
    expect(any("identical to English" in w for w in rep.warnings), "identical copy should warn")

    # Plurals: present key must not be reported missing; 'other' required.
    rep = Report(lang="t")
    check_plurals_file(rep, {"books": {"one": "%1$d libro"}}, {"books": {"one": "%1$d libro", "other": "%1$d libros"}})
    expect(any("missing required 'other'" in e for e in rep.errors), "missing 'other' should fire")
    expect(not any("missing plurals key" in e for e in rep.errors), "present key must not be flagged")

    # CLDR exception: other-only languages (vi/id/ja/ko/zh) may omit 'one' without warning.
    rep = Report(lang="t")
    check_plurals_file(
        rep, {"books": {"other": "%1$d books"}}, {"books": {"one": "%1$d book", "other": "%1$d books"}}, "vi"
    )
    expect(not any("missing quantity" in w for w in rep.warnings), "other-only language may omit 'one'")
    rep = Report(lang="t")
    check_plurals_file(
        rep, {"books": {"other": "%1$d books"}}, {"books": {"one": "%1$d book", "other": "%1$d books"}}, "de"
    )
    expect(any("missing quantity 'one'" in w for w in rep.warnings), "non-exception language still warns")

    # Raw XML apostrophe check via a temp fixture.
    with tempfile.TemporaryDirectory() as tmp:
        raw_path = Path(tmp) / "strings.xml"
        raw_path.write_text(
            "<resources>\n"
            "    <string name=\"ok\">it\\'s fine</string>\n"
            "    <string name=\"quoted\">\"it's fine\"</string>\n"
            "    <!-- <string name=\"commented\">it's ignored</string> -->\n"
            "    <string name=\"bad\">it's broken</string>\n"
            "</resources>\n",
            encoding="utf-8",
        )
        rep = Report(lang="t")
        check_raw_xml(raw_path, rep)
        expect(
            len(rep.errors) == 1 and "bad" in rep.errors[0],
            f"raw apostrophe check should flag only 'bad', got {rep.errors}",
        )

    # Base comment coverage: long/multi-paragraph strings need a comment.
    with tempfile.TemporaryDirectory() as tmp:
        raw_path = Path(tmp) / "strings.xml"
        raw_path.write_text(
            "<resources>\n"
            "    <string name=\"short\">Save</string>\n"
            "    <string name=\"long_no_comment\">This is a genuinely long sentence that "
            "exceeds twelve words and therefore must carry a translator comment above it.</string>\n"
            "    <!-- Explains the long sentence. -->\n"
            "    <string name=\"long_commented\">This is a genuinely long sentence that "
            "exceeds twelve words and therefore must carry a translator comment above it.</string>\n"
            "</resources>\n",
            encoding="utf-8",
        )
        rep = Report(lang="t")
        check_base_comment_coverage(raw_path, rep)
        expect(
            len(rep.warnings) == 1 and "long_no_comment" in rep.warnings[0],
            f"comment coverage should flag only 'long_no_comment', got {rep.warnings}",
        )
        expect("short" not in rep.warnings[0], "short strings must not be flagged")

    if failures:
        print("SELF-TEST FAILURES:")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("self-test: all checks behave as expected")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Localization QA gate (see docstring).")
    parser.add_argument("--lang", action="append", help="check only this folder (e.g. values-de); repeatable")
    parser.add_argument("--skip-coverage", action="store_true", help="skip base-key coverage checks")
    parser.add_argument("--skip-json", action="store_true", help="skip JSON catalog sync checks")
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    parser.add_argument("--quiet", action="store_true", help="only print per-language summary")
    parser.add_argument("--self-test", action="store_true", help="run built-in self test and exit")
    args = parser.parse_args(argv)

    if args.self_test:
        return _self_test()

    rep_base = Report(lang="en")
    base_root = ET.parse(ANDROID_RES / "values/strings.xml").getroot()
    base_translatable_false = {
        element.attrib["name"] for element in base_root.findall("string")
        if element.attrib.get("translatable") == "false"
    }
    base_strings = parse_strings_xml(ANDROID_RES / "values/strings.xml", rep_base)
    base_plurals = parse_plurals_xml(ANDROID_RES / "values/plurals.xml", rep_base)
    check_raw_xml(ANDROID_RES / "values/strings.xml", rep_base)
    check_raw_xml(ANDROID_RES / "values/plurals.xml", rep_base)
    check_base_comment_coverage(ANDROID_RES / "values/strings.xml", rep_base)
    # Structural checks on the English base itself (no base signature).
    for name, value in base_strings.items():
        check_value(rep_base, name, value, "base strings.xml")
    for name, items in base_plurals.items():
        if "other" not in items:
            rep_base.error(f"base plurals.xml '{name}': missing required 'other' quantity")
        for qty, value in items.items():
            check_value(rep_base, f"{name}#{qty}", value, "base plurals.xml")

    used_strings, used_plurals = used_reader_keys(
        (
            ROOT / "shared/src/commonMain",
            ROOT / "shared/src/mobileMain",
            ROOT / "shared/src/iosMain",
            ROOT / "desktopApp/src",
        )
    )

    folders = args.lang or list(FOLDER_TO_TAG)
    reports: list[Report] = [rep_base]
    for folder in folders:
        if folder not in FOLDER_TO_TAG:
            print(
                f"error: unknown language folder '{folder}' (known: {', '.join(FOLDER_TO_TAG)})",
                file=sys.stderr,
            )
            return 2
        reports.append(
            check_language(
                folder,
                base_strings,
                base_plurals,
                base_translatable_false,
                args.skip_coverage,
                args.skip_json,
                used_strings,
                used_plurals,
            )
        )

    total_errors = 0
    total_warnings = 0
    for rep in reports:
        errors, warnings = rep.errors, rep.warnings
        if args.strict and warnings:
            errors, warnings = errors + warnings, []
        total_errors += len(errors)
        total_warnings += len(warnings)
        status = "PASS" if not errors else "FAIL"
        print(f"{rep.lang}: {status} ({len(errors)} errors, {len(warnings)} warnings)")
        if not args.quiet:
            for line in errors:
                print(f"  ERROR {line}")
            for line in warnings:
                print(f"  WARN  {line}")

    print(
        f"\nTotal: {total_errors} errors, {total_warnings} warnings "
        f"across {len(reports)} catalogs" + (" [strict]" if args.strict else "")
    )
    return 1 if total_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
