from __future__ import annotations

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parent
SOURCE_DIR = ROOT / "reader_test_book"
OUTPUT = ROOT.parents[1] / "assets" / "epub" / "reader_test_book.epub"
FIXED_TIMESTAMP = (2026, 1, 1, 0, 0, 0)


def add_file(epub: ZipFile, source: Path, archive_name: str, compression: int) -> None:
    info = ZipInfo(archive_name, FIXED_TIMESTAMP)
    info.compress_type = compression
    info.external_attr = 0o644 << 16
    epub.writestr(info, source.read_bytes())


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    with ZipFile(OUTPUT, "w") as epub:
        info = ZipInfo("mimetype", FIXED_TIMESTAMP)
        info.compress_type = ZIP_STORED
        info.external_attr = 0o644 << 16
        epub.writestr(info, b"application/epub+zip")

        for source in sorted(SOURCE_DIR.rglob("*")):
            if not source.is_file() or source.name == "mimetype":
                continue
            archive_name = source.relative_to(SOURCE_DIR).as_posix()
            add_file(epub, source, archive_name, ZIP_DEFLATED)

    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
