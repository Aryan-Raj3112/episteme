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

Episteme Reader is a comprehensive, customizable reader for documents and e-books on Android. It features a modern Jetpack Compose UI, powerful reading tools, and extensive theming. 

To best serve different user preferences regarding privacy and network usage, Episteme Reader is available in three distinct editions:
*   **PlayStore Version:** The full-featured release which includes proprietary code and features.
*   **OSS Version (GitHub/F-Droid):** A fully open-source build.
*   **OSS Offline Version (GitHub):** A strictly offline build with network permissions completely removed.

---

## Feature Comparison

### 📚 Supported Formats
| Feature | PlayStore | OSS | OSS Offline |
| :--- | :---: | :---: | :---: |
| **Documents:** PDF, DOCX, ODT/FODT | ✅ | ✅ | ✅ |
| **E-books & Text:** EPUB, MOBI, AZW3, FB2, MD, HTML, TXT | ✅ | ✅ | ✅ |
| **Comics:** CBZ, CBR, CB7 | ✅ | ✅ | ✅ |
| **View-Only:** CSV, TSV, JSON, XML, Logs, Code Files | ✅ | ✅ | ✅ |

### 📖 Core Reading Experience
| Feature | PlayStore | OSS | OSS Offline |
| :--- | :---: | :---: | :---: |
| **Display Modes:** Paginated & Vertical Scroll | ✅ | ✅ | ✅ |
| **PDF Multi-Tab Reading & Reflow** | ✅ | ✅ | ✅ |
| **PDF Annotations:** Ink (Pen, Highlight, Erase) & Text | ✅ | ✅ | ✅ |
| **App-wide Customization & Reader Theming** | ✅ | ✅ | ✅ |
| **Custom Fonts:** Local Import (TTF and OTF) | ✅ | ✅ | ✅ |
| **Typography Control** | ✅ | ✅ | ✅ |
| **Auto-Scroll & Musician Mode** | ✅ | ✅ | ✅ |
| **System Text-to-Speech (TTS)** | ✅ | ✅ | ✅ |

### ⚙️ Advanced & Network Features
| Feature | PlayStore | OSS (F-Droid) | OSS Offline |
| :--- | :---: | :---: | :---: |
| **Local Folder Sync & Library Management** | ✅ | ✅ | ✅ |
| **Download Google Fonts** | ✅ | ✅ | ❌ |
| **OPDS Catalog Support** | ✅ | ✅ | ❌ |
| **PDF Bubble Zoom Magnifier** | ✅ | ❌ | ❌ |
| **ML Kit OCR** (Scanned PDF Text Selection) | ✅ | ❌ | ❌ |
| **Cross-Device Cloud Sync** | ✅ | ❌ | ❌ |
| **AI Tools** (Summaries, Story Recap, Dictionary) | ✅ | 🔜 | ❌ |
| **Cloud Text-to-Speech** | ✅ | 🔜 | ❌ |

---

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Aryan-Raj3112/episteme.git
   cd episteme
   ```

2. Build:
   * Open in Android Studio and run the `ossDebug` or `ossOfflineDebug` variant, or
   * Build from the command line:
     ```bash
     ./gradlew assembleOssDebug
     ```
   The APK will be generated at:
   `app/build/outputs/apk/oss/debug/Episteme-oss-v{version}-oss-debug.apk`

## Open Source Libraries

Powered by the Android OSS ecosystem:
*   **Core & UI:** AndroidX, Jetpack Compose, Kotlinx Serialization
*   **Document Engines:** PdfiumAndroidKt (PDF), libmobi (MOBI/AZW3)
*   **Parsers:** Jsoup (HTML/EPUB), Flexmark (Markdown)
*   **Media & Image Loading:** Coil, Media3 (ExoPlayer)
*   **Utilities:** Room (Database), Timber (Logging)

## Contributors

| Contributor | Contribution |
|---|---|
| <img src="https://github.com/CCerrer.png?size=48" width="24" height="24" valign="middle" alt="CCerrer avatar">[CCerrer](https://github.com/CCerrer) | Testing & QA |
| <img src="https://github.com/ottozumkeller.png?size=48" width="24" height="24" valign="middle" alt="ottozumkeller avatar"> [ottozumkeller](https://github.com/ottozumkeller) | Translation (German) |
| <img src="https://github.com/TURBOKANTR.png?size=48" width="24" height="24" valign="middle" alt="TURBOKANTR avatar"> [TURBOKANTR](https://github.com/TURBOKANTR) | Translation (Turkish) |
| <img src="https://github.com/eyadalkordy24.png?size=48" width="24" height="24" valign="middle" alt="eyadalkordy24 avatar">[eyadalkordy24](https://github.com/eyadalkordy24) | Translation (Arabic) |
| <img src="https://github.com/berebara.png?size=48" width="24" height="24" valign="middle" alt="berebara avatar">[berebara](https://github.com/berebara) | Translation (Russian) |
| <img src="https://github.com/mh4ckt3mh4ckt1c4s.png?size=48" width="24" height="24" valign="middle" alt="mh4ckt3mh4ckt1c4s avatar">[mh4ckt3mh4ckt1c4s](https://github.com/mh4ckt3mh4ckt1c4s) | Translation (French) |

## Translations

Help translate Episteme Reader into your native language! [Weblate](https://hosted.weblate.org/engage/episteme/) is used to manage localization.

[![Translation status](https://hosted.weblate.org/widget/episteme/multi-auto.svg)](https://hosted.weblate.org/engage/episteme/)

## License

Licensed under the GNU Affero General Public License v3.0 (AGPL‑3.0). See the [LICENSE](LICENSE) file.

## Support the Project

Help make Episteme Reader even better:

*   ❤️ [Support on Patreon](https://www.patreon.com/c/epistemereader)
*   ⭐ Star the repository to help visibility
*   🐞 Report bugs or request features via [GitHub Issues](https://github.com/Aryan-Raj3112/episteme/issues/new/choose)
*   💬 Share feedback in [Discussions](https://github.com/Aryan-Raj3112/episteme/discussions)
*   ✍️ Leave a review on the [Google Play Store](https://play.google.com/store/apps/details?id=com.aryan.reader)
*   📣 Tell a friend!
