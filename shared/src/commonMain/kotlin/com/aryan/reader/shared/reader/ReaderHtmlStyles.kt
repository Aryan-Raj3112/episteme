package com.aryan.reader.shared.reader

import com.aryan.reader.shared.HighlightColor

internal fun readerDocumentStyles(
    settings: ReaderSettings,
    bookCss: String,
    customFontCss: String,
    appearance: ReaderDocumentAppearanceCss,
    align: String,
    family: String,
    verticalMarginY: Int
): String = """
          <style>
            $bookCss
            $customFontCss
            :root {
              color-scheme: ${appearance.colorScheme};
              --reader-bg: ${appearance.background};
              --reader-fg: ${appearance.foreground};
              --reader-link: ${appearance.linkColors.color};
              --reader-link-decoration: ${appearance.linkColors.decoration};
              --reader-link-bg: ${appearance.linkColors.background};
              --reader-highlight: ${appearance.highlight};
              --reader-scrollbar-track: color-mix(in srgb, var(--reader-bg) 88%, var(--reader-fg));
              --reader-scrollbar-thumb: color-mix(in srgb, var(--reader-fg) 48%, var(--reader-bg));
              --reader-scrollbar-thumb-hover: var(--reader-link);
              --reader-font-size: ${settings.fontSize}px;
              --reader-font-weight: ${settings.readerFontWeightCss()};
              --reader-letter-spacing: ${settings.letterSpacing}em;
              --reader-line-height: ${settings.lineSpacing};
              --reader-page-width: ${settings.pageWidth}px;
              --reader-margin: ${settings.margin}px;
              --reader-margin-x: ${settings.resolvedHorizontalMargin}px;
              --reader-margin-y: ${settings.resolvedVerticalMargin}px;
              --reader-vertical-margin-y: ${verticalMarginY}px;
              --reader-vertical-content-width: 92ch;
              --reader-vertical-page-width: max(0px, calc(100% - (var(--reader-margin-x) * 2)));
              --reader-paragraph-spacing: ${settings.paragraphSpacing};
              --reader-image-scale: ${settings.readerImageScaleCss()};
              --reader-hide-images: ${if (settings.hideImages) "none" else "block"};
              --reader-align: $align;
              --reader-family: $family;
            }
            html, body {
              min-height: 100%;
              /* Android benchmark (epub_reader.js) uses !important for body
                 padding/width so publication CSS cannot widen the side gaps.
                 Shared must do the same or book CSS with !important would
                 stack on top of the inner --reader-vertical-page-width margins
                 on iOS while Android stays at exactly 16px. */
              margin: 0 !important;
              background: var(--reader-bg);
              color: var(--reader-fg);
              font-family: var(--reader-family);
              font-size: var(--reader-font-size);
              font-weight: var(--reader-font-weight);
              letter-spacing: var(--reader-letter-spacing);
              line-height: var(--reader-line-height);
              /* iOS parity with Android's suppressed ActionMode: the reader page
                 draws its own selection menu, so never show WebKit's callout. */
              -webkit-touch-callout: none;
              /* iOS WKWebView auto-inflates small text and reflows on DOM
                 mutations (e.g. wrapping a highlight span). Android's
                 epub_reader.js pins this to 100%; shared must do the same or
                 creating a highlight visibly shifts content on iOS only. */
              -webkit-text-size-adjust: 100%;
              text-size-adjust: 100%;
            }
            html {
              scrollbar-color: var(--reader-scrollbar-thumb) var(--reader-scrollbar-track);
              scrollbar-width: thin;
            }
            /*
             * Publication CSS frequently pins root heights (e.g. html { height: calc(100% - 10px) }),
             * which collapses or clips the reader document. Keep the reader-owned height model.
             */
            html.reader-vertical-root,
            html.reader-paginated-root {
              height: auto !important;
            }
            html.reader-vertical-root {
              width: 100% !important;
              max-width: 100% !important;
              min-width: 0;
              overflow-x: hidden !important;
              overflow-y: scroll;
              scrollbar-width: thin;
            }
            html.reader-vertical-root::-webkit-scrollbar,
            body.reader-vertical::-webkit-scrollbar {
              width: 12px;
              height: 12px;
            }
            html::-webkit-scrollbar-track,
            body.reader-vertical::-webkit-scrollbar-track {
              background: var(--reader-scrollbar-track);
              border-radius: 999px;
            }
            html::-webkit-scrollbar-thumb,
            body.reader-vertical::-webkit-scrollbar-thumb {
              background: var(--reader-scrollbar-thumb);
              border: 3px solid var(--reader-bg);
              border-radius: 999px;
            }
            html::-webkit-scrollbar-thumb:hover,
            body.reader-vertical::-webkit-scrollbar-thumb:hover {
              background: var(--reader-scrollbar-thumb-hover);
            }
            body {
              box-sizing: border-box !important;
              padding: var(--reader-margin-y) var(--reader-margin-x) !important;
              overflow-wrap: anywhere;
              position: relative;
              width: 100% !important;
              max-width: 100% !important;
              overflow-x: hidden !important;
            }
            body.reader-vertical {
              width: 100% !important;
              max-width: 100% !important;
              height: auto !important;
              min-height: 100vh;
              min-height: 100dvh;
              min-width: 0;
              overflow-x: hidden !important;
              overflow-y: auto;
              padding: var(--reader-vertical-margin-y) 0 !important;
              /*
               * iOS WKWebView runs with contentInsetAdjustmentBehavior=never and the
               * Compose PageInfo bar overlays the WebView, so the document owns the
               * bottom clearance: reader margin plus the home-indicator safe area.
               * This keeps the chapter end above the bar instead of showing a gap
               * that only disappears after the first scroll.
               */
              padding-bottom: calc(var(--reader-vertical-margin-y) + env(safe-area-inset-bottom, 0px)) !important;
              scrollbar-gutter: stable;
            }
            /*
             * Android benchmark parity (epub_reader.js has no scrollbar-gutter
             * and no custom webkit-scrollbar width): touch WebViews use overlay
             * scrollbars with zero reservation, so the side gaps stay exactly
             * --reader-margin-x (16px default). Desktop keeps the stable gutter
             * + 12px custom track to avoid shifts between chapters. Without this,
             * iOS WKWebView honors the 12px track + stable gutter as classic
             * scrollbars while Android overlays, leaving iOS narrower with
             * visibly larger left/right gaps than the Android default.
             */
            @media (hover: none) {
              body.reader-vertical {
                scrollbar-gutter: auto;
              }
              html.reader-vertical-root::-webkit-scrollbar,
              body.reader-vertical::-webkit-scrollbar {
                width: 0;
                height: 0;
              }
            }
            body.reader-paginated {
              height: 100vh;
              overflow: hidden;
            }
            .chapter, .page {
              max-width: var(--reader-page-width);
              margin: 0 auto 48px;
              text-align: var(--reader-align);
              position: relative;
              z-index: 1;
            }
            body.reader-vertical .chapter {
              content-visibility: auto;
              contain-intrinsic-size: auto 1200px;
            }
            body.reader-vertical > .chapter,
            body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
            body.reader-vertical > .chapter > :not(.reader-content),
            body.reader-vertical > .chapter > .chapter-title,
            body.reader-vertical > .chapter > .reader-content {
              box-sizing: border-box !important;
              min-width: 0 !important;
            }
            body.reader-vertical > .chapter {
              width: 100% !important;
              max-width: none !important;
              margin: 0 !important;
            }
            body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
            body.reader-vertical > .chapter > :not(.reader-content),
            body.reader-vertical > .chapter > .chapter-title,
            body.reader-vertical > .chapter > .reader-content {
              width: var(--reader-vertical-page-width) !important;
              max-width: none !important;
              margin-left: auto !important;
              margin-right: auto !important;
            }
            body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
            body.reader-vertical > .chapter > :not(.reader-content) {
              position: static !important;
              left: auto !important;
              right: auto !important;
              top: auto !important;
              bottom: auto !important;
              transform: none !important;
              float: none !important;
              clear: none !important;
            }
            body.reader-vertical .reader-content :where(h1, h2, h3, h4, h5, h6, hgroup, center, [class*="title" i], [id*="title" i], [class*="heading" i], [id*="heading" i], [class*="dedication" i], [id*="dedication" i]) {
              box-sizing: border-box !important;
              width: auto !important;
              max-width: 100% !important;
              min-width: 0 !important;
              margin-left: 0 !important;
              margin-right: 0 !important;
              padding-left: 0 !important;
              padding-right: 0 !important;
              text-indent: 0 !important;
              position: static !important;
              left: auto !important;
              right: auto !important;
              transform: none !important;
              float: none !important;
              clear: none !important;
            }
            body.reader-vertical .reader-content,
            body.reader-vertical .reader-content p,
            body.reader-vertical .reader-content li,
            body.reader-vertical .reader-content div,
            body.reader-vertical .reader-content h1,
            body.reader-vertical .reader-content h2,
            body.reader-vertical .reader-content h3,
            body.reader-vertical .reader-content h4,
            body.reader-vertical .reader-content h5,
            body.reader-vertical .reader-content h6,
            body.reader-vertical .reader-content blockquote {
              text-align: var(--reader-align) !important;
            }
            body.reader-vertical .reader-content p,
            body.reader-vertical .reader-content div,
            body.reader-vertical .reader-content h1,
            body.reader-vertical .reader-content h2,
            body.reader-vertical .reader-content h3,
            body.reader-vertical .reader-content h4,
            body.reader-vertical .reader-content h5,
            body.reader-vertical .reader-content h6,
            body.reader-vertical .reader-content blockquote,
            body.reader-vertical .reader-content section,
            body.reader-vertical .reader-content article,
            body.reader-vertical .reader-content header,
            body.reader-vertical .reader-content footer,
            body.reader-vertical .reader-content aside,
            body.reader-vertical .reader-content figure,
            body.reader-vertical .reader-content table,
            body.reader-vertical .reader-content pre {
              box-sizing: border-box !important;
              max-width: 100% !important;
              min-width: 0 !important;
              position: static !important;
              left: auto !important;
              right: auto !important;
              top: auto !important;
              bottom: auto !important;
              transform: none !important;
              float: none !important;
              clear: none !important;
            }
            body.reader-vertical .reader-content div,
            body.reader-vertical .reader-content section,
            body.reader-vertical .reader-content article,
            body.reader-vertical .reader-content header,
            body.reader-vertical .reader-content footer,
            body.reader-vertical .reader-content aside,
            body.reader-vertical .reader-content figure {
              width: auto !important;
              margin-left: 0 !important;
              margin-right: 0 !important;
            }
            body.reader-vertical .reader-content > p,
            body.reader-vertical .reader-content > div,
            body.reader-vertical .reader-content > h1,
            body.reader-vertical .reader-content > h2,
            body.reader-vertical .reader-content > h3,
            body.reader-vertical .reader-content > h4,
            body.reader-vertical .reader-content > h5,
            body.reader-vertical .reader-content > h6,
            body.reader-vertical .reader-content > blockquote,
            body.reader-vertical .reader-content > section,
            body.reader-vertical .reader-content > article,
            body.reader-vertical .reader-content > header,
            body.reader-vertical .reader-content > footer,
            body.reader-vertical .reader-content > aside,
            body.reader-vertical .reader-content > figure,
            body.reader-vertical .reader-content > table,
            body.reader-vertical .reader-content > pre {
              margin-left: 0 !important;
              margin-right: 0 !important;
            }
            body.reader-paginated .page {
              box-sizing: border-box;
              height: calc(100vh - (var(--reader-margin-y) * 2));
              margin-bottom: 0;
              overflow: hidden;
            }
            body.reader-paginated .reader-content > :last-child {
              margin-bottom: 0 !important;
            }
            .reader-spread {
              display: grid;
              grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
              gap: 28px;
              width: min(100%, calc((var(--reader-page-width) * 2) + 28px));
              height: calc(100vh - (var(--reader-margin-y) * 2));
              margin: 0 auto;
              position: relative;
              z-index: 1;
            }
            .reader-spread .page {
              width: 100%;
              max-width: none;
              min-width: 0;
            }
            .chapter-title {
              text-align: left;
              font-size: 1.55em;
              line-height: 1.25;
              margin: 0 0 1.1em;
            }
            p, blockquote, pre, ul, ol, table, figure {
              margin-top: 0;
              margin-bottom: calc(1em * var(--reader-paragraph-spacing));
            }
            h1, h2, h3, h4, h5, h6 {
              margin-top: 0;
              margin-bottom: calc(1em * var(--reader-paragraph-spacing));
            }
            img, svg, video {
              max-width: var(--reader-image-scale);
              height: auto;
            }
            img {
              display: var(--reader-hide-images);
            }
            table {
              max-width: 100%;
              overflow-wrap: anywhere;
            }
            td, th {
              vertical-align: top;
            }
            /*
             * Publication CSS—especially legacy MOBI CSS—commonly carries fixed widths,
             * nowrap, and preformatted text sized for an old Kindle viewport. Keep those
             * declarations from expanding the WKWebView document beyond the reader page.
             */
            .reader-content {
              box-sizing: border-box;
              width: 100%;
              min-width: 0;
              max-width: 100%;
              overflow-wrap: anywhere;
              word-wrap: break-word;
            }
            .reader-virtual-chunk {
              box-sizing: border-box;
              width: 100%;
              min-width: 0;
              max-width: 100%;
              contain: inline-size;
            }
            .reader-content :where(*) {
              box-sizing: border-box;
              min-width: 0 !important;
              max-width: 100% !important;
              overflow-wrap: anywhere !important;
              word-wrap: break-word !important;
            }
            .reader-content nobr,
            .reader-content [style*="nowrap" i],
            .reader-content :where(p, li, div, blockquote, h1, h2, h3, h4, h5, h6, a, span, font, code, pre, td, th) {
              white-space: normal !important;
            }
            .reader-content pre,
            .reader-content code {
              white-space: pre-wrap !important;
              word-break: break-word !important;
            }
            .reader-content table {
              display: block;
              width: auto !important;
              max-width: 100% !important;
              overflow-x: auto;
              overflow-y: hidden;
              -webkit-overflow-scrolling: touch;
            }
            .reader-content img,
            .reader-content svg,
            .reader-content video,
            .reader-content canvas {
              box-sizing: border-box;
              max-width: 100% !important;
              height: auto !important;
            }
            /*
             * Placeholder for figures whose image file is missing from the archive
             * (Gutenberg img_ marker spans). Keeps the caption visible instead of a gap.
             */
            .reader-content figure.reader-missing-figure {
              display: block;
              width: auto !important;
              margin: 1.2em auto !important;
              padding: 1em 1.2em !important;
              border: 1px dashed color-mix(in srgb, var(--reader-fg) 35%, transparent) !important;
              border-radius: 8px;
              text-align: center !important;
              font-style: italic;
              color: color-mix(in srgb, var(--reader-fg) 82%, var(--reader-bg));
              background: color-mix(in srgb, var(--reader-fg) 4%, transparent);
            }
            /*
             * Shoulder-note headings (Standard Ebooks div.aside: print-style
             * float-right sidebars) cannot survive virtualized chunks: the float
             * context breaks across chunk attach/detach and the headings drop
             * out of paint. Linearize them the way the publisher's own narrow
             * fallback does; borders, margins and small-caps are preserved.
             */
            .reader-content div.aside {
              float: none !important;
              clear: both !important;
              max-width: 100% !important;
            }
            .reader-highlight {
              background: var(--reader-highlight);
              color: inherit;
              border-radius: 2px;
            }
            /*
             * Wrapping text in a highlight marker must never change layout.
             * Publication CSS is injected verbatim above, so a rule like
             * `span { padding: 2px }` or `span { display: inline-block }`
             * would newly match the inserted marker and look exactly like
             * "padding gets added and content shifts" when highlighting.
             * Pin every box/layout property that affects inline flow; paint
             * (background / text-decoration) stays in the per-color rules and
             * inline style declarations below.
             */
            span[class*="user-highlight-"],
            mark.reader-user-highlight,
            .reader-user-highlight,
            .reader-highlight {
              display: inline !important;
              padding: 0 !important;
              margin: 0 !important;
              border: 0 !important;
              outline: 0 !important;
              box-shadow: none !important;
              vertical-align: baseline !important;
              line-height: inherit !important;
              letter-spacing: inherit !important;
              word-spacing: inherit !important;
              text-indent: 0 !important;
              float: none !important;
              clear: none !important;
              position: static !important;
              left: auto !important;
              right: auto !important;
              top: auto !important;
              bottom: auto !important;
              transform: none !important;
              border-radius: 2px;
              -webkit-box-decoration-break: clone;
              box-decoration-break: clone;
            }
            span[class*="user-highlight-"],
            mark.reader-user-highlight,
            .reader-user-highlight {
              cursor: pointer;
            }
            ::highlight(reader-tts-highlight) {
              background: rgba(125, 211, 252, 0.52);
              color: inherit;
            }
            #reader-tts-highlight-layer {
              position: absolute;
              inset: 0;
              z-index: 3;
              pointer-events: none;
            }
            .reader-tts-highlight-rect {
              position: absolute;
              background: rgba(125, 211, 252, 0.42);
              border-radius: 3px;
              box-shadow: 0 0 0 1px rgba(14, 116, 144, 0.12);
            }
            ${HighlightColor.entries.joinToString("\n") { ".${it.cssClass} { background-color: ${it.color.toCssRgba(0.4f)} !important; }" }}
            #reader-selection-menu {
              position: fixed;
              z-index: 99999;
              display: none;
              flex-direction: column;
              width: max-content;
              max-width: min(280px, calc(100vw - 16px));
              padding: 0 0 6px;
              border-radius: 14px;
              background: color-mix(in srgb, var(--reader-bg) 92%, var(--reader-fg));
              border: 1px solid color-mix(in srgb, var(--reader-fg) 18%, transparent);
              box-shadow: 0 18px 44px rgba(0, 0, 0, 0.28);
              max-height: calc(100vh - 16px);
              overflow: auto;
            }
            #reader-selection-menu button {
              border: 0;
              background: transparent;
              color: var(--reader-fg);
              font: 600 12px system-ui, sans-serif;
              cursor: pointer;
            }
            #reader-selection-menu button:hover {
              background: color-mix(in srgb, var(--reader-fg) 10%, transparent);
            }
            #reader-selection-menu .reader-selection-styles {
              display: flex;
              justify-content: center;
              align-items: center;
              gap: 4px;
              width: 100%;
              box-sizing: border-box;
              padding: 8px 10px 0;
              overflow-x: auto;
            }
            #reader-selection-menu .reader-selection-style {
              width: 28px;
              height: 28px;
              flex: 0 0 auto;
              padding: 0;
              border-radius: 8px;
              font-size: 12px;
              font-weight: 700;
              border: 1px solid color-mix(in srgb, var(--reader-fg) 24%, transparent);
              background: color-mix(in srgb, var(--reader-fg) 5%, transparent);
            }
            #reader-selection-menu .reader-selection-style.selected {
              background: color-mix(in srgb, var(--reader-fg) 18%, transparent);
              border-color: color-mix(in srgb, var(--reader-fg) 55%, transparent);
            }
            #reader-selection-menu .reader-selection-colors {
              display: flex;
              justify-content: center;
              align-items: center;
              gap: 8px;
              width: 100%;
              box-sizing: border-box;
              padding: 8px 10px;
              border-bottom: 1px solid color-mix(in srgb, var(--reader-fg) 12%, transparent);
              overflow-x: auto;
            }
            #reader-selection-menu .reader-selection-color {
              width: 28px;
              height: 28px;
              flex: 0 0 auto;
              padding: 0;
              border-radius: 999px;
              background: var(--selection-color);
              box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--reader-fg) 18%, transparent);
            }
            #reader-selection-menu .reader-selection-spectrum {
              width: 28px;
              height: 28px;
              flex: 0 0 auto;
              padding: 0;
              border-radius: 999px;
              background: conic-gradient(#f44336, #ff7f00, #ffeb3b, #4caf50, #2196f3, #4b0082, #8b00ff, #f44336);
            }
            #reader-selection-menu .reader-selection-actions {
              display: grid;
              grid-template-columns: repeat(3, 70px);
              gap: 3px;
              padding: 5px 6px 2px;
            }
            #reader-selection-menu .reader-selection-action {
              min-height: 56px;
              border-radius: 10px;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
              gap: 4px;
              padding: 6px 4px 7px;
              line-height: 1.15;
              white-space: nowrap;
            }
            #reader-selection-menu .reader-selection-action span:last-child {
              display: block;
              line-height: 1.2;
              padding-bottom: 1px;
            }
            #reader-selection-menu .reader-selection-icon {
              display: grid;
              place-items: center;
              width: 22px;
              height: 22px;
              border-radius: 999px;
              background: color-mix(in srgb, var(--reader-fg) 9%, transparent);
              color: color-mix(in srgb, var(--reader-fg) 86%, transparent);
            }
            #reader-selection-menu .reader-selection-icon svg {
              width: 16px;
              height: 16px;
              display: block;
              fill: currentColor;
            }
            .reader-selection-handle {
              position: fixed;
              z-index: 99998;
              display: none;
              width: 24px;
              height: 24px;
              padding: 0;
              border: 0;
              background: transparent;
              color: #2563eb;
              cursor: ew-resize;
              touch-action: none;
            }
            .reader-selection-handle svg {
              width: 24px;
              height: 24px;
              display: block;
              fill: currentColor;
              filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.28));
            }
            .reader-selection-handle-start svg {
              transform: rotate(30deg);
              transform-origin: 50% 0;
            }
            .reader-selection-handle-end svg {
              transform: rotate(-30deg);
              transform-origin: 50% 0;
            }
            .reader-content a[href],
            .reader-content a[href]:link,
            .reader-content a[href]:visited,
            a[href],
            a[href]:link,
            a[href]:visited,
            a[data-reader-link="true"] {
              color: var(--reader-link) !important;
              cursor: pointer;
              text-decoration-line: underline !important;
              text-decoration-color: var(--reader-link-decoration) !important;
              text-decoration-thickness: 0.08em;
              text-decoration-thickness: max(1px, 0.08em);
              text-underline-offset: 0.14em;
              text-decoration-skip-ink: auto;
              background-image: linear-gradient(transparent 62%, var(--reader-link-bg) 62%);
              border-radius: 2px;
            }
            .reader-content a[href] *,
            a[href] *,
            a[data-reader-link="true"] * {
              color: var(--reader-link) !important;
              text-decoration-color: var(--reader-link-decoration) !important;
            }
          </style>
          <style id="reader-texture-style">${appearance.textureOverlayCss}</style>
""".trimIndent()
