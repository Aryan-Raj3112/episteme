#!/usr/bin/env python3
"""Regenerate the bundled Google Fonts catalog from Google's public metadata.

The app lists downloadable families in `google_fonts.json` and fetches each one
through the Google Fonts CSS endpoint. That catalog must contain *only* families
Google actually serves; a family from any other foundry is listed in search but
fails on download (for example OpenDyslexic, which is not a Google Font).

Usage:
    python3 scripts/update_google_fonts.py            # rewrite the three assets
    python3 scripts/update_google_fonts.py --check    # fail if an asset is stale
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request

METADATA_URL = "https://fonts.google.com/metadata/fonts"

# Every module that ships the catalog. Keep these in sync.
ASSET_PATHS = (
    "app/src/main/assets/google_fonts.json",
    "shared/src/iosMain/composeResources/files/google_fonts.json",
    "desktopApp/src/desktopMain/resources/google_fonts.json",
)


def fetch_families() -> list[dict]:
    request = urllib.request.Request(
        METADATA_URL,
        headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) reader-build-script"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = json.load(response)

    families = {
        entry["family"].strip(): entry
        for entry in payload["familyMetadataList"]
        if entry.get("family", "").strip()
    }
    # Case-insensitive alphabetical order matches how the picker presents them.
    return [families[name] for name in sorted(families, key=lambda name: (name.lower(), name))]


def css_face_suffix(faces: dict) -> str:
    """Pick a face the CSS endpoint can serve when 400 Regular is missing.

    `GET css?family=X` requests normal 400, so Google answers 400 for families
    that only ship other weights or only italics (Molle, Buda, Sunflower,
    UnifrakturCook). The v1 endpoint accepts an explicit face suffix such as
    `:300` or `:400italic`.
    """
    if "400" in faces:
        return ""
    normal_weights = sorted(int(face) for face in faces if face.isdigit())
    if normal_weights:
        return f":{normal_weights[0]}"
    italic_weights = sorted(int(face[:-1]) for face in faces if face.endswith("i"))
    if italic_weights:
        return f":{italic_weights[0]}italic"
    return ""


def format_catalog(entries: list[str]) -> str:
    body = ",\n".join(f'    {json.dumps(entry, ensure_ascii=False)}' for entry in entries)
    return f"[\n{body}\n]\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the checked-in assets without fetching Google's metadata",
    )
    args = parser.parse_args()

    if args.check:
        expected: list[str] | None = None
        stale = False
        for path in ASSET_PATHS:
            with open(path, encoding="utf-8") as handle:
                entries = json.load(handle)
            if not isinstance(entries, list) or not all(isinstance(entry, str) for entry in entries):
                print(f"{path}: expected a JSON array of family entries", file=sys.stderr)
                stale = True
            elif not entries:
                print(f"{path}: catalog is empty", file=sys.stderr)
                stale = True
            elif expected is None:
                expected = entries
            elif entries != expected:
                print(f"{path}: does not match {ASSET_PATHS[0]}", file=sys.stderr)
                stale = True
        return 1 if stale else 0

    entries = [
        family["family"].strip() + css_face_suffix(family.get("fonts", {}))
        for family in fetch_families()
    ]
    if not entries:
        print("Google metadata returned no families", file=sys.stderr)
        return 1

    serialized = format_catalog(entries)
    for path in ASSET_PATHS:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(serialized)
        print(f"{path}: {len(entries)} families")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
