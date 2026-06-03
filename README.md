<div align="center">

  <h1>
    <img src="docs/ICON.png" height="48" width="48" align="absmiddle" alt="Episteme Reader Icon"/>
    <span>&nbsp;Episteme Reader</span>
  </h1>

  <p>A modern, offline-first, privacy-focused document and e-book reader for Android and desktop, built with Kotlin Multiplatform and Compose.</p>

  <a href="https://epistemereader.com"><img alt="Download from epistemereader.com" src="https://img.shields.io/badge/Download-epistemereader.com-2f6f5e?style=for-the-badge" height="44" align="absmiddle"/></a>&nbsp;&nbsp;<a href="https://f-droid.org/packages/com.aryan.reader.oss/"><img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="66" align="absmiddle"/></a>&nbsp;<a href="https://play.google.com/store/apps/details?id=com.aryan.reader"><img alt="Get it on Google Play" src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" height="44" align="absmiddle"/></a>&nbsp;&nbsp;&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aryan-Raj3112/episteme"><img alt="Get it on Obtainium" src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="44" align="absmiddle"/></a>

</div>

<br/>

<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/EPISTEME.png" alt="Episteme Reader on Android"/>
      <br/>
      <sub>Android</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/EPISTEME_desktop.png" alt="Episteme Reader on desktop"/>
      <br/>
      <sub>Desktop</sub>
    </td>
  </tr>
</table>

## Overview

Episteme Reader is a customizable reader for documents, e-books, comics, and text-heavy files. The app is designed around local-first reading, deep typography controls, flexible layouts, and a consistent Kotlin Multiplatform core across Android and desktop.

The same core reading experience is available across editions. The main differences are distribution channel, network access, and whether proprietary online services are included.

## Core Features

Available across supported editions unless noted in the edition table:

* **Formats:** PDF, EPUB, MOBI/AZW3, FB2, DOCX, ODT/FODT, TXT, Markdown, HTML, and comic archives.
* **Reading modes:** Paginated reading, vertical scroll, PDF multi-tab reading, PDF reflow, auto-scroll, and musician mode.
* **PDF tools:** Ink annotations, highlighting, erasing, text annotations, and reading-focused PDF controls.
* **Customization:** App themes, reader themes, custom local fonts, typography controls, spacing, margins, and layout tuning.
* **Library tools:** Local folder sync, library organization, bookmarks, progress tracking, and file management.
* **Accessibility:** System text-to-speech, app language selection, and reader settings that adapt to different reading preferences.

## Editions

| Edition | Platform | Network access | Distribution | Notes |
|---|---|---|---|---|
| **Play Store** | Android | Online-capable | Google Play | Full Android release with proprietary extras such as ML Kit OCR, cloud sync, AI tools, cloud TTS, and PDF bubble zoom. |
| **OSS** | Android | Online-capable | [epistemereader.com](https://epistemereader.com), GitHub, F-Droid, Obtainium | Fully open-source Android build with OPDS, downloadable fonts, and BYOK access to AI and cloud features. |
| **OSS Offline** | Android | Offline-only | [epistemereader.com](https://epistemereader.com), GitHub, Obtainium | Open-source Android build with network permissions removed. |
| **Standard** | Windows desktop | Online-capable | [epistemereader.com](https://epistemereader.com), GitHub | Full-featured desktop release with the shared KMP reader core and online-capable services. |
| **Offline** | Windows desktop | Offline-only | [epistemereader.com](https://epistemereader.com), GitHub | Desktop build focused on local reading with online services disabled. |

Future desktop platforms can use the same Standard and Offline model as support expands.

## Languages

Episteme Reader currently supports: English, Arabic, Belarusian, German, Spanish, Estonian, French, Hindi, Indonesian, Italian, Japanese, Korean, Dutch, Polish, Portuguese (Brazil), Russian, Turkish, Ukrainian, Vietnamese, and Chinese Simplified.

Want Episteme Reader in another language? Please request it through [GitHub Issues](https://github.com/Aryan-Raj3112/episteme/issues/new/choose) or start a thread in [Discussions](https://github.com/Aryan-Raj3112/episteme/discussions).

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Aryan-Raj3112/episteme.git
   cd episteme
   ```

2. Build Android:
   * Open in Android Studio and run the `ossDebug` or `ossOfflineDebug` variant, or
   * Build from the command line:
     ```bash
     ./gradlew assembleOssDebug
     ```
   The APK will be generated at:
   `app/build/outputs/apk/oss/debug/Episteme-oss-v{version}-oss-debug.apk`

3. Build desktop:
   ```bash
   ./gradlew :desktopApp:packageReleaseDistributionForCurrentOS
   ```
   For the offline desktop build, pass:
   ```bash
   ./gradlew :desktopApp:packageReleaseDistributionForCurrentOS -PdesktopFlavor=oss
   ```

## Open Source Libraries

Powered by the Kotlin, Android, and desktop OSS ecosystem:

* **Core and UI:** Kotlin Multiplatform, Compose Multiplatform, AndroidX, Jetpack Compose, Kotlinx Serialization
* **Document engines:** PdfiumAndroidKt, PDFium, libmobi
* **Parsers:** Jsoup, Flexmark, Apache Commons Compress
* **Media and image loading:** Coil, Media3
* **Utilities:** Room, Timber, JNA

## Contributors

| Contributor | Contribution |
|---|---|
| <img src="https://github.com/CCerrer.png?size=48" width="24" height="24" valign="middle" alt="CCerrer avatar"> [CCerrer](https://github.com/CCerrer) | Testing and QA |
| <img src="https://github.com/ottozumkeller.png?size=48" width="24" height="24" valign="middle" alt="ottozumkeller avatar"> [ottozumkeller](https://github.com/ottozumkeller) | German translation |
| <img src="https://github.com/TURBOKANTR.png?size=48" width="24" height="24" valign="middle" alt="TURBOKANTR avatar"> [TURBOKANTR](https://github.com/TURBOKANTR) | Turkish translation |
| <img src="https://github.com/eyadalkordy24.png?size=48" width="24" height="24" valign="middle" alt="eyadalkordy24 avatar"> [eyadalkordy24](https://github.com/eyadalkordy24) | Arabic translation |
| <img src="https://github.com/berebara.png?size=48" width="24" height="24" valign="middle" alt="berebara avatar"> [berebara](https://github.com/berebara) | Russian translation |
| <img src="https://github.com/mh4ckt3mh4ckt1c4s.png?size=48" width="24" height="24" valign="middle" alt="mh4ckt3mh4ckt1c4s avatar"> [mh4ckt3mh4ckt1c4s](https://github.com/mh4ckt3mh4ckt1c4s) | French translation |

## Supporters

Thank you to the people helping keep Episteme Reader moving:

| Supporter | Platform |
|---|---|
| <img src="https://github.com/Zorklo.png?size=48" width="24" height="24" valign="middle" alt="Zorklo avatar"> [Zorklo](https://github.com/Zorklo) | GitHub Sponsors |

## Support the Project

Help make Episteme Reader better:

* [Sponsor on GitHub](https://github.com/sponsors/Aryan-Raj3112)
* [Support on Patreon](https://www.patreon.com/c/epistemereader)
* Star the repository to help visibility
* Report bugs or request features via [GitHub Issues](https://github.com/Aryan-Raj3112/episteme/issues/new/choose)
* Share feedback in [Discussions](https://github.com/Aryan-Raj3112/episteme/discussions)
* Leave a review on the [Google Play Store](https://play.google.com/store/apps/details?id=com.aryan.reader)
* Tell a friend

## License

Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [LICENSE](LICENSE) file.
