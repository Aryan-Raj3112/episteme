# Episteme Reader

A native Android document reader application built with Kotlin and Jetpack Compose.

![Episteme Reader](docs/feature_graphic.png)

## Overview

Episteme Reader is an offline-first application designed for reading various document formats. It leverages native Android technologies and C++ libraries to provide a performant reading experience with customization capabilities.

## Features

### Supported Formats
*   **Documents:** PDF
*   **E-books:** EPUB, MOBI, AZW3
*   **Text:** MD, TXT, HTML

### PDF Features
*   **Viewing Modes:** Vertical Scroll and Paginated view.
*   **Ink Annotations:** Draw directly on pages using Pen, Highlighter, and Eraser tools.
*   **Text Annotations:** Add text notes anywhere on the page using system or custom fonts.

### E-Book Features
*   **Parsing:** Native parsing for MOBI/AZW3 via `libmobi` and EPUB via `Jsoup`.
*   **Customization:** Adjust font size, line spacing, and margins.
*   **Custom Fonts:** Support for importing user-provided font files (`.ttf`, `.otf`).

### General
*   **Text-to-Speech (TTS):** Read documents aloud using the system TTS engine.
*   **File Management:** Built-in file browser and library organization.

## Architecture

*   **UI:** 100% Jetpack Compose (Material3).
*   **Architecture:** MVVM with Unidirectional Data Flow.
*   **Database:** Room (SQLite) for metadata and annotations.
*   **PDF Engine:** `pdfium-android` (Native PDFium bindings).
*   **Mobi Engine:** Custom JNI bindings to `libmobi`.

## Building from Source

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/episteme-oss.git
    cd episteme-oss
    ```

2.  **Build:**
    Open in Android Studio and run the `ossDebug` variant.
    ```bash
    ./gradlew assembleOssDebug
    ```

## Open Source Libraries

This project uses the following open-source libraries:

*   [pdfium-android](https://github.com/barteksc/PdfiumAndroid)
*   [libmobi](https://github.com/bfabiszewski/libmobi)
*   [Coil](https://coil-kt.github.io/coil/)
*   [Jsoup](https://jsoup.org/)
*   [Flexmark](https://github.com/vsch/flexmark-java)

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the [LICENSE](LICENSE) file for details.