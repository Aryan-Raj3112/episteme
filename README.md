<div align="center">

  <h1>
    <img src="docs/ICON.png" height="48" width="48" align="absmiddle" alt="Episteme Reader Icon"/>
    <span>&nbsp;Episteme Reader</span>
  </h1>

  <p>A modern, offline‑first, privacy‑focused document & e‑book reader for Android, built with Kotlin and Jetpack Compose.</p>

  <a href="https://f-droid.org/packages/com.aryan.reader.oss/"><img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="66" align="absmiddle"/></a>&nbsp;<a href="https://play.google.com/store/apps/details?id=com.aryan.reader"><img alt="Get it on Google Play" src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" height="44" align="absmiddle"/></a>&nbsp;&nbsp;&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aryan-Raj3112/episteme"><img alt="Get it on Obtainium" src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="44" align="absmiddle"/></a>

</div>

<br/>

![Episteme Reader Preview](docs/EPISTEME.png)

## Overview

Episteme Reader is an offline-first reader for documents and e‑books on Android. It focuses on a clean and customizable reading experience.

> Note: This is the Open Source (OSS) edition of Episteme Reader. The Google Play version is built from this core and may include additional proprietary features.

## Features

### Supported Formats
- Documents: PDF, DOCX
- E‑books: EPUB, MOBI, AZW3, FB2
- Comics: CBZ, CBR, CB7
- Text: MD, TXT, HTML

### Reading Experience
- Themes (PDF & EPUB): Light, Dark, Sepia, OLED, Auto (system) and custom
- PDF Modes: Vertical scroll and paginated view
- EPUB Modes: WebView-based vertical scrolling and custom paginated engine
- Custom Fonts: Import user-provided fonts (`.ttf`, `.otf`)
- Typography: Adjustable font size, line spacing, and margins

### Annotations (PDF)
- Ink Annotations: Pen, Highlighter, Eraser
- Text Annotations: Place notes anywhere, with system or custom fonts

### Library & OPDS
- Library: Built-in organization and quick access to your files
- OPDS Support: Browse, search, and add books from OPDS 1.x/2.0 catalogs, with optional authentication

### Accessibility & Utilities
- Text-to-Speech (TTS): Read documents aloud using the system TTS engine
- Offline‑first: Core features work fully offline; no analytics or tracking

## Architecture

- UI: 100% Jetpack Compose (Material 3)
- Pattern: MVVM with Unidirectional Data Flow
- Database: Room (SQLite) for metadata and annotations
- PDF Engine: `pdfium-android` (Native PDFium bindings)
- EPUB Engine: WebView for vertical mode; custom renderer for paginated mode
- MOBI/AZW3: Custom JNI bindings to `libmobi`
- OPDS: Lightweight feed parsing and downloading (on-device)

## Building from Source

1. Clone the repository:
   ~~~bash
   git clone https://github.com/Aryan-Raj3112/episteme.git
   cd episteme
   ~~~

2. Build:
   - Open in Android Studio and run the `ossDebug` variant, or
   - Build from the command line:
     ~~~bash
     ./gradlew assembleOssDebug
     ~~~
   The APK will be generated at:
   ~~~
   app/build/outputs/apk/oss/debug/Episteme-oss-v{version}-oss-debug.apk
   ~~~

## Open Source Libraries

Powered by the Android OSS ecosystem:
- Core & UI: AndroidX, Jetpack Compose, Kotlinx Serialization
- Document Engines: PdfiumAndroidKt (PDF), libmobi (MOBI/AZW3), Google WOFF2 (fonts)
- Parsers: Jsoup (HTML/EPUB), Flexmark (Markdown)
- Media & Image Loading: Coil, Media3 (ExoPlayer)
- Utilities: Room (Database), Timber (Logging)

## Contributors

- [CCerrer](https://github.com/CCerrer) — Testing & QA

## Translations

Help translate Episteme Reader into your native language! We use Weblate to manage localization.

<a href="https://hosted.weblate.org/engage/episteme/">
<img src="https://hosted.weblate.org/widgets/episteme/-/svg-badge.svg" alt="Translation status" />
</a>

## License

Licensed under the GNU Affero General Public License v3.0 (AGPL‑3.0). See the [LICENSE](LICENSE) file.

## Support the Project

Help make Episteme Reader even better.

- ❤️ [Sponsor on GitHub](https://github.com/sponsors/Aryan-Raj3112)
- ⭐ Star the repository to help visibility
- 🐞 Report bugs or request features via [GitHub Issues](https://github.com/Aryan-Raj3112/episteme/issues/new/choose)
- 💬 Share feedback in Discussions (ideas/UX): [Discussions](https://github.com/Aryan-Raj3112/episteme/discussions)
- ✍️ Leave a review on the [Google Play Store](https://play.google.com/store/apps/details?id=com.aryan.reader)
- 📣 Tell a friend!
