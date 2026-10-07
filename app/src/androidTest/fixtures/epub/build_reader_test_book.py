from __future__ import annotations

import struct
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parent
SOURCE_DIR = ROOT / "reader_test_book"
OUTPUT = ROOT.parents[1] / "assets" / "epub" / "reader_test_book.epub"
FIXED_TIMESTAMP = (2026, 1, 1, 0, 0, 0)

# The narration the overlay fixture points at. Synthesized rather than committed so the fixture
# stays a few kilobytes of text and the bytes are reproducible: a checked-in WAV would be an
# opaque blob whose duration nobody could check, and this file is regenerated rarely enough that
# a generator is cheaper than an explanation.
#
# One file per narrated chapter, with a slightly different tone so a test that listens to the wrong
# chapter is audible rather than merely wrong.
NARRATION_SAMPLE_RATE = 8_000
NARRATION_AMPLITUDE = 8_000
NARRATION_CHAPTERS = {
    # archive path: (duration in ms, tone in Hz) — matching the SMILs' clip windows.
    "OEBPS/audio/chapter-01.wav": (4_500, 330.0),
    "OEBPS/audio/chapter-02.wav": (3_000, 392.0),
}


def narration_wav_bytes(duration_ms: int, frequency_hz: float) -> bytes:
    """16-bit mono PCM WAV, a square wave so silence cannot be optimised away."""
    frame_count = NARRATION_SAMPLE_RATE * duration_ms // 1_000
    samples = bytearray(frame_count * 2)
    period_frames = NARRATION_SAMPLE_RATE / frequency_hz
    for frame in range(frame_count):
        high = (frame % period_frames) < (period_frames / 2)
        value = NARRATION_AMPLITUDE if high else -NARRATION_AMPLITUDE
        struct.pack_into("<h", samples, frame * 2, value)

    byte_rate = NARRATION_SAMPLE_RATE * 2
    return b"".join(
        (
            b"RIFF",
            struct.pack("<I", 36 + len(samples)),
            b"WAVEfmt ",
            struct.pack("<IHHIIHH", 16, 1, 1, NARRATION_SAMPLE_RATE, byte_rate, 2, 16),
            b"data",
            struct.pack("<I", len(samples)),
            bytes(samples),
        )
    )


def add_file(epub: ZipFile, source: Path, archive_name: str, compression: int) -> None:
    info = ZipInfo(archive_name, FIXED_TIMESTAMP)
    info.compress_type = compression
    info.external_attr = 0o644 << 16
    epub.writestr(info, source.read_bytes())


def add_bytes(epub: ZipFile, payload: bytes, archive_name: str, compression: int) -> None:
    info = ZipInfo(archive_name, FIXED_TIMESTAMP)
    info.compress_type = compression
    info.external_attr = 0o644 << 16
    epub.writestr(info, payload)


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    narration = {
        path: narration_wav_bytes(duration_ms, frequency_hz)
        for path, (duration_ms, frequency_hz) in NARRATION_CHAPTERS.items()
    }

    with ZipFile(OUTPUT, "w") as epub:
        info = ZipInfo("mimetype", FIXED_TIMESTAMP)
        info.compress_type = ZIP_STORED
        info.external_attr = 0o644 << 16
        epub.writestr(info, b"application/epub+zip")

        for source in sorted(SOURCE_DIR.rglob("*")):
            if not source.is_file() or source.name == "mimetype":
                continue
            archive_name = source.relative_to(SOURCE_DIR).as_posix()
            if archive_name in narration:
                # Listed in the OPF, generated here rather than read from the source tree.
                continue
            add_file(epub, source, archive_name, ZIP_DEFLATED)

        for archive_name, payload in narration.items():
            add_bytes(epub, payload, archive_name, ZIP_DEFLATED)

    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
