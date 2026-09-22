package com.aryan.reader.shared.reader

/**
 * Contrast fixup for WebView rendering.
 *
 * Pagination adapts author colors per-span via [com.aryan.reader.paginatedreader.CssParser.adaptColorForTheme]:
 * neutral low-contrast foregrounds become the theme text color, light backgrounds on dark theme
 * (and vice versa) become transparent, saturated author colors are preserved.
 *
 * WebView injects book CSS verbatim, so stylesheet rules like `p.P_Plat { color: #000000 }`
 * beat the inherited `body { color: var(--reader-fg) }` and stay black on dark backgrounds.
 * This script mirrors the pagination thresholds in JS so both pipelines agree.
 */
internal fun readerHtmlThemeFixupScript(): String = """
    <script>
      (function () {
        var SUSPEND_ATTR = 'data-reader-contrast-suspended';
        function parseRgb(colorStr) {
          if (!colorStr) return null;
          var parts = String(colorStr).match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
          if (parts) {
            return { r: parseInt(parts[1], 10), g: parseInt(parts[2], 10), b: parseInt(parts[3], 10) };
          }
          return null;
        }
        function parseHex(hex) {
          var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(String(hex || ''));
          if (!result) return null;
          return { r: parseInt(result[1], 16), g: parseInt(result[2], 16), b: parseInt(result[3], 16) };
        }
        function relativeLuminance(rgb) {
          function channel(value) {
            var normalized = value / 255;
            return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
          }
          return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b);
        }
        function contrastRatio(lumA, lumB) {
          var lighter = Math.max(lumA, lumB);
          var darker = Math.min(lumA, lumB);
          return (lighter + 0.05) / (darker + 0.05);
        }
        function chroma(rgb) {
          var max = Math.max(rgb.r, rgb.g, rgb.b) / 255;
          var min = Math.min(rgb.r, rgb.g, rgb.b) / 255;
          return max - min;
        }
        function shouldSkip(element) {
          if (!element || !element.tagName) return true;
          var tag = element.tagName.toUpperCase();
          if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'LINK' || tag === 'META' ||
              tag === 'IMG' || tag === 'VIDEO' || tag === 'CANVAS' ||
              tag === 'INPUT' || tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA') {
            return true;
          }
          if (element.closest) {
            if (element.closest('svg')) return true;
            if (element.closest('a[href]')) return true;
            if (element.closest('#reader-selection-menu, .reader-selection-handle, #reader-tts-highlight-layer')) return true;
            if (element.closest('span[class*="user-highlight-"], mark.reader-user-highlight, .reader-highlight')) return true;
          }
          return false;
        }
        function restoreElement(element) {
          if (element.hasAttribute('data-reader-orig-color')) {
            var origColor = element.getAttribute('data-reader-orig-color');
            var origColorPriority = element.getAttribute('data-reader-orig-color-priority') || '';
            if (origColor) {
              element.style.setProperty('color', origColor, origColorPriority);
            } else {
              element.style.removeProperty('color');
            }
            element.removeAttribute('data-reader-orig-color');
            element.removeAttribute('data-reader-orig-color-priority');
          } else if (element.hasAttribute('data-reader-contrast-fg')) {
            element.style.removeProperty('color');
            element.removeAttribute('data-reader-contrast-fg');
          }
          if (element.hasAttribute('data-reader-orig-bg')) {
            var origBg = element.getAttribute('data-reader-orig-bg');
            var origBgPriority = element.getAttribute('data-reader-orig-bg-priority') || '';
            if (origBg) {
              element.style.setProperty('background-color', origBg, origBgPriority);
            } else {
              element.style.removeProperty('background-color');
            }
            element.removeAttribute('data-reader-orig-bg');
            element.removeAttribute('data-reader-orig-bg-priority');
          } else if (element.hasAttribute('data-reader-contrast-bg')) {
            element.style.removeProperty('background-color');
            element.removeAttribute('data-reader-contrast-bg');
          }
        }
        function rememberOriginal(element, kind) {
          if (kind === 'color') {
            if (!element.hasAttribute('data-reader-orig-color') && !element.hasAttribute('data-reader-contrast-fg')) {
              element.setAttribute('data-reader-orig-color', element.style.getPropertyValue('color') || '');
              element.setAttribute('data-reader-orig-color-priority', element.style.getPropertyPriority('color') || '');
            }
          } else {
            if (!element.hasAttribute('data-reader-orig-bg') && !element.hasAttribute('data-reader-contrast-bg')) {
              element.setAttribute('data-reader-orig-bg', element.style.getPropertyValue('background-color') || '');
              element.setAttribute('data-reader-orig-bg-priority', element.style.getPropertyPriority('background-color') || '');
            }
          }
        }
        window.readerAdjustAuthorColorsForContrast = function (isDark, bgHex, textHex) {
          try {
            if (document.documentElement && document.documentElement.hasAttribute(SUSPEND_ATTR)) return 0;
            var bgRgb = parseHex(bgHex) || { r: isDark ? 23 : 255, g: isDark ? 26 : 252, b: isDark ? 23 : 245 };
            var bgLum = relativeLuminance(bgRgb);
            var text = String(textHex || (isDark ? '#E7E3D8' : '#24231F'));
            if (!document.body) return 0;
            var fixed = 0;
            // Restore previous pass first so toggling themes never stacks on our own overrides
            // and author inline styles are preserved across theme switches.
            var previouslyFixed = document.querySelectorAll(
              '[data-reader-orig-color], [data-reader-orig-bg], [data-reader-contrast-fg], [data-reader-contrast-bg]'
            );
            for (var restoreIndex = 0; restoreIndex < previouslyFixed.length; restoreIndex++) {
              restoreElement(previouslyFixed[restoreIndex]);
            }
            var elements = document.body.querySelectorAll('*');
            for (var index = 0; index < elements.length; index++) {
              var element = elements[index];
              if (shouldSkip(element)) continue;
              var computed = null;
              try {
                computed = window.getComputedStyle(element);
              } catch (error) {
                continue;
              }
              if (!computed) continue;
              var fg = parseRgb(computed.color);
              if (fg) {
                // Mirror CssParser.adaptColorForTheme: keep saturated author colors,
                // remap only neutral low-contrast text to the theme foreground.
                if (chroma(fg) < 0.2 && contrastRatio(relativeLuminance(fg), bgLum) < 4.5) {
                  rememberOriginal(element, 'color');
                  element.style.setProperty('color', text, 'important');
                  element.setAttribute('data-reader-contrast-fg', 'true');
                  fixed++;
                }
              }
              var bgStr = computed.backgroundColor;
              if (bgStr && bgStr !== 'rgba(0, 0, 0, 0)' && bgStr !== 'transparent') {
                var bgImage = computed.backgroundImage;
                if (!bgImage || bgImage === 'none') {
                  var elementBg = parseRgb(bgStr);
                  if (elementBg) {
                    var elementBgLum = relativeLuminance(elementBg);
                    var shouldClear = (isDark && elementBgLum > 0.5) || (!isDark && elementBgLum < 0.2);
                    if (shouldClear) {
                      rememberOriginal(element, 'background');
                      element.style.setProperty('background-color', 'transparent', 'important');
                      element.setAttribute('data-reader-contrast-bg', 'true');
                      fixed++;
                    }
                  }
                }
              }
            }
            window.__readerLastContrastArgs = { isDark: !!isDark, bgHex: bgHex, textHex: text };
            return fixed;
          } catch (error) {
            return 0;
          }
        };
        window.readerSuspendContrastFixup = function (suspended) {
          try {
            if (suspended) {
              document.documentElement.setAttribute(SUSPEND_ATTR, 'true');
            } else if (document.documentElement) {
              document.documentElement.removeAttribute(SUSPEND_ATTR);
            }
          } catch (error) {}
        };
        var contrastObserver = null;
        var contrastDebounce = null;
        function scheduleContrastReapply() {
          if (contrastDebounce !== null) return;
          contrastDebounce = window.setTimeout(function () {
            contrastDebounce = null;
            var args = window.__readerLastContrastArgs;
            if (args && window.readerAdjustAuthorColorsForContrast) {
              window.readerAdjustAuthorColorsForContrast(args.isDark, args.bgHex, args.textHex);
            }
          }, 250);
        }
        function installContrastObserver() {
          if (contrastObserver || !window.MutationObserver || !document.body) return;
          contrastObserver = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
              if (mutations[i].addedNodes && mutations[i].addedNodes.length) {
                scheduleContrastReapply();
                return;
              }
            }
          });
          try {
            contrastObserver.observe(document.body, { childList: true, subtree: true });
          } catch (error) {}
        }
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', installContrastObserver, { once: true });
        } else {
          installContrastObserver();
        }
      })();
    </script>
""".trimIndent()
