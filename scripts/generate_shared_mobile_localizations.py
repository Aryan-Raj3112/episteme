#!/usr/bin/env python3
"""Package Android translations for shared Compose UI.

All string entries are included so model-owned English labels can be translated
at the rendering boundary as well as resource-keyed UI copy.
"""

from __future__ import annotations

import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_RES = ROOT / "app/src/main/res"
OUTPUT = ROOT / "shared/src/mobileMain/composeResources/files/localization"
SOURCE_ROOTS = (
    ROOT / "shared/src/commonMain",
    ROOT / "shared/src/mobileMain",
)

STRING_PATTERN = re.compile(r'readerString\(\s*"([^"]+)"')
QUANTITY_PATTERN = re.compile(r'readerQuantityString\(\s*"([^"]+)"')

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


def used_keys(pattern: re.Pattern[str]) -> set[str]:
    keys: set[str] = set()
    for source_root in SOURCE_ROOTS:
        for source in source_root.rglob("*.kt"):
            keys.update(pattern.findall(source.read_text(encoding="utf-8")))
    return keys


def element_text(element: ET.Element) -> str:
    value = "".join(element.itertext())
    return (
        value.replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\'", "'")
        .replace('\\"', '"')
    )


def parse_strings(path: Path, wanted: set[str]) -> dict[str, str]:
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: element_text(element)
        for element in root.findall("string")
        if element.attrib.get("name") in wanted and element.attrib.get("translatable", "true") != "false"
    }


def parse_plurals(path: Path, wanted: set[str]) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    result: dict[str, dict[str, str]] = {}
    for plural in root.findall("plurals"):
        name = plural.attrib.get("name")
        if name not in wanted:
            continue
        result[name] = {
            item.attrib["quantity"]: element_text(item)
            for item in plural.findall("item")
            if item.attrib.get("quantity")
        }
    return result


def main() -> None:
    english_root = ET.parse(ANDROID_RES / "values/strings.xml").getroot()
    string_keys = {
        element.attrib["name"]
        for element in english_root.findall("string")
        if element.attrib.get("name") and element.attrib.get("translatable", "true") != "false"
    }
    string_keys.update(used_keys(STRING_PATTERN))
    plural_keys = used_keys(QUANTITY_PATTERN)
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    OUTPUT.mkdir(parents=True)
    for folder, tag in FOLDER_TO_TAG.items():
        source = ANDROID_RES / folder
        payload = {
            "strings": parse_strings(source / "strings.xml", string_keys),
            "plurals": parse_plurals(source / "plurals.xml", plural_keys),
        }
        (OUTPUT / f"{tag}.json").write_text(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
