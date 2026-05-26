# EPUB UI Test Fixture

`reader_test_book.epub` is a deterministic EPUB fixture for Android instrumentation and UI tests.

Use it for reader flows that need stable EPUB content:

- open/import EPUB through a `content://` URI
- restore reading position
- navigate with chapter anchors
- create or verify bookmarks
- search unique fixture markers
- exercise highlight and CFI-related flows

The generated EPUB is stored at:

`app/src/androidTest/assets/epub/reader_test_book.epub`

The source files live in:

`app/src/androidTest/fixtures/epub/reader_test_book`

Regenerate the EPUB after editing the source:

```powershell
python app/src/androidTest/fixtures/epub/build_reader_test_book.py
```

Tests should copy the asset from the instrumentation context, not the target app context:

```kotlin
val testContext = InstrumentationRegistry.getInstrumentation().context
testContext.assets.open("epub/reader_test_book.epub")
```

Current basic UI coverage using this fixture lives in:

`app/src/androidTest/java/com/aryan/reader/epubreader/EpubReaderScreenTest.kt`

Stable markers intentionally embedded in the book:

- `POSITION_TARGET_ALPHA`
- `HIGHLIGHT_TARGET_BRAVO`
- `CFI_TARGET_CHARLIE`
- `SEARCH_TARGET_DELTA`
- `BOOKMARK_TARGET_ECHO`
- `POSITION_TARGET_FOXTROT`
- `ANNOTATION_TARGET_GOLF`
- `CFI_TARGET_HOTEL`
