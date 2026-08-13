package com.aryan.reader.shared.reader

internal fun readerHtmlNavigationScript(pageAnchorJson: String): String = """
          <script>
            (function () {
              var readerDiagnosticsConsoleEnabled = ${SharedReaderDiagnosticsEnabled};
              function readerConsoleLog(line) {
                if (readerDiagnosticsConsoleEnabled) {
                  try { console.log(line); } catch (error) {}
                }
              }
              var menu = document.getElementById('reader-selection-menu');
              var startHandle = document.getElementById('reader-selection-start-handle');
              var endHandle = document.getElementById('reader-selection-end-handle');
              var savedRange = null;
              var readerPageAnchors = $pageAnchorJson;
              window.readerSetPageAnchors = function (anchors) {
                if (Array.isArray(anchors)) {
                  readerPageAnchors = anchors;
                }
              };
              var lastReportedPageIndex = -1;
              var lastReportedStartOffset = -1;
              var pendingRestoreLocator = null;
              var pendingRestoreUntil = 0;
              var reportTimer = null;
              var selectionMenuTimer = null;
              var readerCurrentHighlights = [];
              var readerHighlightReconcileTimer = null;
              var selectionPointerDown = false;
              var activeSelectionHandle = null;
              var selectionHandleFrame = null;
              var pendingSelectionHandleEvent = null;
              var selectionDebugSequence = 0;
              var selectionDebugLastLineKey = null;
              var selectionDebugLastAt = 0;
              function numberAttribute(element, name, fallback) {
                if (!element) return fallback;
                var value = parseInt(element.getAttribute(name) || '', 10);
                return Number.isFinite(value) ? value : fallback;
              }
              function selectorValue(value) {
                if (window.CSS && window.CSS.escape) return window.CSS.escape(String(value));
                return String(value).replace(/"/g, '\\"');
              }
              function readerTtsLog(message) {
                var line = 'EPUB_TTS_HIGHLIGHT ' + message;
                readerConsoleLog(line);
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try { window.kmpJsBridge.callNative('readerTtsHighlightLog', JSON.stringify({ message: line })); } catch (error) {}
                }
              }
              window.readerTtsLog = readerTtsLog;
              function readerSelectionDebugLog(message) {
                var line = 'EPUB_SELECTION_DEBUG ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerSelectionDebugLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              window.readerSelectionDebugLog = readerSelectionDebugLog;
              function readerHighlightFlowLog(message) {
                var line = 'EpistemeEpubHighlightFlow ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerHighlightFlowLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              window.readerHighlightFlowLog = readerHighlightFlowLog;
              function readerDesktopHighlightMapLog(message) {
                var line = 'EpistemeDesktopHighlightMap ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerDesktopHighlightMapLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              window.readerDesktopHighlightMapLog = readerDesktopHighlightMapLog;
              function readerDesktopPositionTraceLog(message) {
                var line = 'EpistemeDesktopPositionTrace ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerDesktopPositionTraceLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              window.readerDesktopPositionTraceLog = readerDesktopPositionTraceLog;
              function readerTtsStartTraceLog(message) {
                var line = 'EpistemeDesktopTtsStartTrace ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerTtsStartTraceLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              window.readerTtsStartTraceLog = readerTtsStartTraceLog;
              function readerTtsPreview(value, limit) {
                return String(value || '').replace(/\s+/g, ' ').trim().substring(0, limit || 120);
              }
              function readerTtsNormalized(value) {
                return String(value || '').replace(/\s+/g, ' ').trim();
              }
              function readerElementLabel(element) {
                if (!element || !element.tagName) return 'null';
                var label = element.tagName.toLowerCase();
                if (element.id) label += '#' + element.id;
                var cfi = element.getAttribute && element.getAttribute('data-reader-cfi');
                var start = element.getAttribute && element.getAttribute('data-reader-text-start');
                var end = element.getAttribute && element.getAttribute('data-reader-text-end');
                if (cfi) label += '[cfi=' + readerTtsPreview(cfi, 80) + ']';
                if (start !== null && end !== null) label += '[range=' + start + '..' + end + ']';
                return label;
              }
              function readerPaginationLog(message) {
                var line = 'EpistemeEpubPagination ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerPaginationLayoutLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              function readerGapLog(message) {
                var line = 'EpistemeReaderGap ' + message;
                var delivered = false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerGapLayoutLog', JSON.stringify({ message: message }));
                    delivered = true;
                  } catch (error) {}
                }
                if (!delivered) {
                  readerConsoleLog(line);
                }
              }
              function readerPaginationLayoutLog(reason) {
                if (!document.body || !document.body.classList.contains('reader-paginated')) return;
                var pages = Array.prototype.slice.call(document.querySelectorAll('.page[data-reader-page-index]'));
                var mode = document.querySelector('.reader-spread') ? 'spread' : 'single';
                var bodyStyle = window.getComputedStyle(document.body);
                var bodyPaddingTop = parseFloat(bodyStyle.paddingTop) || 0;
                var bodyPaddingBottom = parseFloat(bodyStyle.paddingBottom) || 0;
                var bodyPaddingY = bodyPaddingTop + bodyPaddingBottom;
                pages.forEach(function (page) {
                  var content = page.querySelector('.reader-content') || page;
                  var pageRect = page.getBoundingClientRect();
                  var contentRect = content.getBoundingClientRect();
                  var children = Array.prototype.slice.call(content.children || []);
                  var last = children.length ? children[children.length - 1] : null;
                  var lastRect = last ? last.getBoundingClientRect() : null;
                  var lastStyle = last ? window.getComputedStyle(last) : null;
                  var lastMarginBottom = lastStyle ? (parseFloat(lastStyle.marginBottom) || 0) : 0;
                  var pageOverflow = (page.scrollHeight || 0) - (page.clientHeight || 0);
                  var contentOverflow = contentRect.bottom - pageRect.bottom;
                  var lastOverflow = lastRect ? (lastRect.bottom + lastMarginBottom - pageRect.bottom) : 0;
                  var overflowPx = Math.ceil(Math.max(0, pageOverflow, contentOverflow, lastOverflow));
                  var lastBottomWithMargin = lastRect ? lastRect.bottom + lastMarginBottom : contentRect.bottom;
                  var contentTopGap = contentRect.top - pageRect.top;
                  var contentBottomGap = pageRect.bottom - contentRect.bottom;
                  var lastBottomGap = pageRect.bottom - lastBottomWithMargin;
                  var pageIndex = numberAttribute(page, 'data-reader-page-index', -1);
                  var chapterIndex = numberAttribute(page, 'data-reader-chapter-index', -1);
                  var startOffset = numberAttribute(page, 'data-reader-page-start', -1);
                  var endOffset = numberAttribute(page, 'data-reader-page-end', -1);
                  var contentText = (content.textContent || '').replace(/\s+/g, ' ').trim();
                  var lastText = last ? (last.textContent || '').replace(/\s+/g, ' ').trim().substring(0, 80) : '';
                  readerPaginationLog(
                    'render_layout reason=' + (reason || 'load') +
                    ' mode=' + mode +
                    ' page=' + (pageIndex + 1) +
                    ' chapter=' + chapterIndex +
                    ' range=' + startOffset + '..' + endOffset +
                    ' overflowPx=' + overflowPx +
                    ' pageClient=' + page.clientWidth + 'x' + page.clientHeight +
                    ' pageScroll=' + page.scrollWidth + 'x' + page.scrollHeight +
                    ' pageRect=' + Math.round(pageRect.width) + 'x' + Math.round(pageRect.height) +
                    ' contentBottom=' + Math.round(contentRect.bottom - pageRect.top) +
                    ' last=' + readerElementLabel(last) +
                    ' lastBottom=' + (lastRect ? Math.round(lastRect.bottom - pageRect.top) : 'null') +
                    ' lastMarginBottom=' + Math.round(lastMarginBottom) +
                    ' bodyPaddingY=' + Math.round(bodyPaddingY) +
                    ' textChars=' + contentText.length +
                    ' lastText="' + lastText.replace(/"/g, '\\"') + '"'
                  );
                  readerGapLog(
                    'web_page layer=paginated_dom reason=' + (reason || 'load') +
                    ' mode=' + mode +
                    ' page=' + (pageIndex + 1) +
                    ' chapter=' + chapterIndex +
                    ' viewport=' + window.innerWidth + 'x' + window.innerHeight +
                    ' documentClient=' + document.documentElement.clientWidth + 'x' + document.documentElement.clientHeight +
                    ' bodyClient=' + document.body.clientWidth + 'x' + document.body.clientHeight +
                    ' bodyScroll=' + document.body.scrollWidth + 'x' + document.body.scrollHeight +
                    ' bodyPaddingTop=' + Math.round(bodyPaddingTop) +
                    ' bodyPaddingBottom=' + Math.round(bodyPaddingBottom) +
                    ' pageTop=' + Math.round(pageRect.top) +
                    ' pageBottom=' + Math.round(pageRect.bottom) +
                    ' pageHeight=' + Math.round(pageRect.height) +
                    ' pageClient=' + page.clientWidth + 'x' + page.clientHeight +
                    ' pageScroll=' + page.scrollWidth + 'x' + page.scrollHeight +
                    ' contentTopGap=' + Math.round(contentTopGap) +
                    ' contentBottomGap=' + Math.round(contentBottomGap) +
                    ' lastBottomGap=' + Math.round(lastBottomGap) +
                    ' lastMarginBottom=' + Math.round(lastMarginBottom) +
                    ' overflowPx=' + overflowPx +
                    ' range=' + startOffset + '..' + endOffset
                  );
                });
              }
              window.readerPaginationLayoutLog = readerPaginationLayoutLog;
              function readerHostsForLocator(chapterIndex, startOffset, endOffset) {
                var selector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                var hosts = Array.prototype.slice.call(document.querySelectorAll(selector));
                if (!hosts.length) return [];
                var parsedStart = parseInt(startOffset, 10);
                var parsedEnd = parseInt(endOffset === undefined || endOffset === null ? parsedStart : endOffset, 10);
                var hasOffsets = Number.isFinite(parsedStart);
                if (!hasOffsets) return [hosts[0]];
                var rangeEnd = Number.isFinite(parsedEnd) && parsedEnd >= parsedStart ? parsedEnd : parsedStart;
                var containing = hosts.filter(function (host) {
                  var pageStart = numberAttribute(host, 'data-reader-page-start', null);
                  var pageEnd = numberAttribute(host, 'data-reader-page-end', null);
                  if (pageStart === null || pageEnd === null) return true;
                  if (parsedStart === rangeEnd) return parsedStart >= pageStart && parsedStart <= pageEnd;
                  return parsedStart < pageEnd && rangeEnd > pageStart;
                });
                if (containing.length) return containing;
                var best = hosts.reduce(function (best, host) {
                  var bestStart = numberAttribute(best, 'data-reader-page-start', 0);
                  var hostStart = numberAttribute(host, 'data-reader-page-start', 0);
                  return Math.abs(hostStart - parsedStart) < Math.abs(bestStart - parsedStart) ? host : best;
                }, hosts[0]);
                return [best];
              }
              function readerHostForLocator(chapterIndex, startOffset, endOffset) {
                var hosts = readerHostsForLocator(chapterIndex, startOffset, endOffset);
                return hosts.length ? hosts[0] : null;
              }
              function readerBlockElementsForLocator(chapterIndex, blockIndex) {
                var parsedBlock = parseInt(blockIndex, 10);
                if (!Number.isFinite(parsedBlock)) return [];
                var chapterSelector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                var chapters = Array.prototype.slice.call(document.querySelectorAll(chapterSelector));
                var blockSelector = '[data-reader-block-index="' + selectorValue(parsedBlock) + '"]';
                return chapters.reduce(function (elements, chapter) {
                  return elements.concat(Array.prototype.slice.call(chapter.querySelectorAll(blockSelector)));
                }, []);
              }
              function readerElementForBlockLocator(chapterIndex, blockIndex, charOffset) {
                var elements = readerBlockElementsForLocator(chapterIndex, blockIndex);
                if (!elements.length) return null;
                var parsedChar = parseInt(charOffset, 10);
                if (!Number.isFinite(parsedChar)) return elements[0];
                var fallback = null;
                for (var i = 0; i < elements.length; i++) {
                  var element = elements[i];
                  if (!fallback) fallback = element;
                  var textStart = numberAttribute(element, 'data-reader-text-start', null);
                  var textEnd = numberAttribute(element, 'data-reader-text-end', null);
                  if (textStart === null || textEnd === null) continue;
                  var host = element.closest ? element.closest('[data-reader-chapter-index]') : null;
                  var pageStart = host ? numberAttribute(host, 'data-reader-page-start', null) : null;
                  var pageEnd = host ? numberAttribute(host, 'data-reader-page-end', null) : null;
                  if (pageStart !== null && pageEnd !== null && !((parsedChar >= pageStart && parsedChar < pageEnd) || (pageStart === pageEnd && parsedChar === pageStart))) continue;
                  if ((parsedChar >= textStart && parsedChar < textEnd) || (textStart === textEnd && parsedChar === textStart)) {
                    return element;
                  }
                }
                return fallback;
              }
              function readerHostForBlockLocator(chapterIndex, blockIndex, charOffset) {
                var element = readerElementForBlockLocator(chapterIndex, blockIndex, charOffset);
                if (!element) return null;
                return element.closest ? element.closest('[data-reader-chapter-index]') : null;
              }
              function readerLocatorTrace(locator) {
                locator = locator || {};
                return 'chapter=' + (locator.chapterIndex === undefined || locator.chapterIndex === null ? 'null' : locator.chapterIndex) +
                  ' page=' + (locator.pageIndex === undefined || locator.pageIndex === null ? 'null' : locator.pageIndex) +
                  ' offsets=' + (locator.startOffset === undefined || locator.startOffset === null ? 'null' : locator.startOffset) +
                  '..' + (locator.endOffset === undefined || locator.endOffset === null ? 'null' : locator.endOffset) +
                  ' block=' + (locator.blockIndex === undefined || locator.blockIndex === null ? 'null' : locator.blockIndex) +
                  ' char=' + (locator.charOffset === undefined || locator.charOffset === null ? 'null' : locator.charOffset) +
                  ' cfi=' + readerTtsPreview(locator.cfi, 160);
              }
              function stableReaderCfi(cfi) {
                if (cfi === undefined || cfi === null) return null;
                var value = String(cfi);
                if (value.indexOf('desktop-scroll:') !== 0) return value;
                var parts = value.split(':');
                if (parts.length < 4) return value;
                var stable = parts.slice(3).join(':');
                return stable || value;
              }
              function stableDesktopCfi(chapterIndex, startOffset, endOffset) {
                return 'desktop:' + chapterIndex + ':' + startOffset + ':' + (endOffset === undefined || endOffset === null ? startOffset : endOffset);
              }
              function locatorStartOffset(locator) {
                locator = locator || {};
                var start = parseInt(locator.startOffset, 10);
                if (Number.isFinite(start)) return start;
                var charOffset = parseInt(locator.charOffset, 10);
                if (Number.isFinite(charOffset)) return charOffset;
                var cfi = stableReaderCfi(locator.cfi);
                if (cfi && cfi.indexOf('desktop:') === 0) {
                  var parts = cfi.split(':');
                  var parsed = parseInt(parts[2], 10);
                  if (Number.isFinite(parsed)) return parsed;
                }
                if (cfi && cfi.indexOf('android-locator:') === 0) {
                  var androidParts = cfi.split(':');
                  var androidChar = parseInt(androidParts[3], 10);
                  if (Number.isFinite(androidChar)) return androidChar;
                }
                return null;
              }
              function prepareVerticalScrollMeasurement(targetChapter) {
                if (!isVerticalReaderDocument()) return;
                var chapters = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                chapters.forEach(function (chapter) {
                  chapter.style.contentVisibility = 'visible';
                  chapter.style.containIntrinsicSize = 'auto';
                });
                if (targetChapter) {
                  targetChapter.style.contentVisibility = 'visible';
                  targetChapter.style.containIntrinsicSize = 'auto';
                }
                if (document.body) void document.body.offsetHeight;
                if (targetChapter) void targetChapter.offsetHeight;
              }
              function scrollToTopWithTrace(targetTop, strategy, locator, extra) {
                var before = verticalScrollMetrics();
                var top = Math.max(0, Math.round(Number(targetTop) || 0));
                window.scrollTo({ top: top, left: 0, behavior: 'auto' });
                var after = verticalScrollMetrics();
                var clamped = Math.abs(after.scrollY - top) > 2 && (top > after.maxScroll + 2 || top < 0);
                readerDesktopPositionTraceLog(
                  'event=web_scroll_to_locator_done mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                  ' strategy=' + strategy +
                  ' targetY=' + top +
                  ' beforeY=' + before.scrollY +
                  ' afterY=' + after.scrollY +
                  ' maxScroll=' + after.maxScroll +
                  ' clamped=' + clamped +
                  ' ' + readerLocatorTrace(locator) +
                  (extra ? ' ' + extra : '')
                );
                return { targetY: top, beforeY: before.scrollY, afterY: after.scrollY, maxScroll: after.maxScroll, clamped: clamped };
              }
              function shouldCenterScrollTarget(options) {
                return !!(options && options.align === 'center' && isVerticalReaderDocument());
              }
              function scrollTargetTopFromRect(rect, options) {
                var documentTop = (rect ? rect.top : 0) + window.scrollY;
                if (shouldCenterScrollTarget(options)) {
                  var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                  if (viewportHeight > 0) {
                    var rectHeight = rect && Number.isFinite(rect.height) ? Math.max(0, Math.min(rect.height, viewportHeight)) : 0;
                    return documentTop - Math.round((viewportHeight - rectHeight) / 2);
                  }
                }
                return documentTop - 24;
              }
              function scrollTargetTopFromY(documentY, options) {
                var top = Number(documentY) || 0;
                if (shouldCenterScrollTarget(options)) {
                  var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                  if (viewportHeight > 0) return top - Math.round(viewportHeight / 2);
                }
                return top - 24;
              }
              function scrollTraceExtra(extra, options) {
                if (!shouldCenterScrollTarget(options)) return extra;
                return extra ? extra + ' align=center' : 'align=center';
              }
              function shouldTrackScrollRestore(options) {
                return !(options && options.trackRestore === false);
              }
              function scrollToLocator(locator, options) {
                locator = locator || {};
                options = options || {};
                if (isVerticalReaderDocument()) {
                  if (shouldTrackScrollRestore(options)) {
                    pendingRestoreLocator = locator;
                    pendingRestoreUntil = Date.now() + 5000;
                  } else {
                    clearPendingRestoreLocator('transient_scroll');
                  }
                }
                readerDesktopPositionTraceLog(
                  'event=web_scroll_to_locator_start mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                  ' ' + readerLocatorTrace(locator) +
                  (shouldCenterScrollTarget(options) ? ' align=center' : '')
                );
                if (scrollToVerticalPage(locator, options)) return;
                var chapterIndex = locator.chapterIndex;
                if (chapterIndex === undefined || chapterIndex === null || chapterIndex === '') {
                  chapterIndex = document.body.getAttribute('data-reader-active-chapter-index');
                }
                if (chapterIndex === null || chapterIndex === '') {
                  readerDesktopPositionTraceLog('event=web_scroll_to_locator_skip reason=missing_chapter ' + readerLocatorTrace(locator));
                  return;
                }
                var activeStart = locatorStartOffset(locator);
                if (activeStart === undefined || activeStart === null) {
                  activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                }
                var requestedPageIndex = parseInt(locator.pageIndex, 10);
                var chapter = Number.isFinite(requestedPageIndex)
                  ? document.querySelector('[data-reader-page-index="' + selectorValue(requestedPageIndex) + '"]')
                  : null;
                if (!chapter) chapter = readerHostForLocator(chapterIndex, activeStart, locator.endOffset);
                if (!chapter) {
                  readerDesktopPositionTraceLog(
                    'event=web_scroll_to_locator_skip reason=missing_host requestedChapter=' + chapterIndex +
                    ' activeStart=' + activeStart + ' ' + readerLocatorTrace(locator)
                  );
                  return;
                }
                prepareVerticalScrollMeasurement(chapter);
                var stableCfi = stableReaderCfi(locator.cfi);
                var locatorCfiBase = stableCfi ? String(stableCfi).split('|')[0].split(':')[0] : null;
                var exactCfi = locatorCfiBase
                  ? chapter.querySelector('[data-reader-cfi="' + selectorValue(locatorCfiBase) + '"]')
                  : null;
                if (exactCfi && (activeStart === undefined || activeStart === null)) {
                  var cfiRect = exactCfi.getBoundingClientRect();
                  scrollToTopWithTrace(scrollTargetTopFromRect(cfiRect, options), 'exact_cfi', locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
                  return;
                }
                var exact = activeStart === null
                  ? null
                  : chapter.querySelector('[data-reader-start-offset="' + selectorValue(activeStart) + '"]');
                var blockIndex = parseInt(locator.blockIndex, 10);
                var exactBlock = Number.isFinite(blockIndex)
                  ? chapter.querySelector('[data-reader-block-index="' + selectorValue(blockIndex) + '"]')
                  : null;
                var target = exact || exactBlock || chapter;
                var content = chapter.querySelector('.reader-content') || chapter;
                if (!exact && activeStart !== null && content) {
                  var parsedStart = parseInt(activeStart, 10);
                  var parsedEnd = parseInt(locator.endOffset === undefined || locator.endOffset === null ? activeStart : locator.endOffset, 10);
                  if (Number.isFinite(parsedStart)) {
                    var rangeEnd = Number.isFinite(parsedEnd) && parsedEnd > parsedStart ? parsedEnd : parsedStart + 1;
                    var exactRange = rangeForOffsets(parseInt(chapterIndex, 10), parsedStart, rangeEnd, stableCfi);
                    if (exactRange) {
                      var rangeRects = exactRange.getClientRects();
                      var rangeRect = shouldCenterScrollTarget(options) ? exactRange.getBoundingClientRect() : (rangeRects.length ? rangeRects[0] : exactRange.getBoundingClientRect());
                      exactRange.detach && exactRange.detach();
                      if (rangeRect && (rangeRect.top !== 0 || rangeRect.bottom !== 0)) {
                        var exactResult = scrollToTopWithTrace(scrollTargetTopFromRect(rangeRect, options), 'exact_range', locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
                        if (!exactResult.clamped || !isVerticalReaderDocument()) {
                          return;
                        }
                        readerDesktopPositionTraceLog(
                          'event=web_scroll_to_locator_clamped strategy=exact_range requestedChapter=' + chapterIndex +
                          ' targetY=' + exactResult.targetY +
                          ' afterY=' + exactResult.afterY +
                          ' maxScroll=' + exactResult.maxScroll +
                          ' fallback=content_ratio'
                        );
                      }
                    }
                  }
                  var contentStart = numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0));
                  var contentEnd = numberAttribute(content, 'data-reader-content-end', numberAttribute(chapter, 'data-reader-page-end', contentStart));
                  if (contentEnd > contentStart && activeStart > contentStart) {
                    var ratio = Math.max(0, Math.min(1, (activeStart - contentStart) / (contentEnd - contentStart)));
                    var contentRect = content.getBoundingClientRect();
                    var approximateY = contentRect.top + window.scrollY + (content.scrollHeight * ratio);
                    scrollToTopWithTrace(scrollTargetTopFromY(approximateY, options), 'content_ratio', locator, scrollTraceExtra('requestedChapter=' + chapterIndex + ' ratio=' + ratio.toFixed(4), options));
                    return;
                  }
                }
                var rect = target.getBoundingClientRect();
                scrollToTopWithTrace(scrollTargetTopFromRect(rect, options), exact ? 'exact_marker' : (exactBlock ? 'exact_block' : 'host_top'), locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
              }
              function scrollToActiveLocator() {
                scrollToLocator({
                  chapterIndex: document.body.getAttribute('data-reader-active-chapter-index'),
                  pageIndex: numberAttribute(document.body, 'data-reader-active-page-index', null),
                  startOffset: numberAttribute(document.body, 'data-reader-active-start-offset', null),
                  endOffset: numberAttribute(document.body, 'data-reader-active-end-offset', null),
                  blockIndex: numberAttribute(document.body, 'data-reader-active-block-index', null),
                  charOffset: numberAttribute(document.body, 'data-reader-active-char-offset', null),
                  cfi: document.body.getAttribute('data-reader-active-cfi')
                });
              }
              window.readerScrollToLocator = scrollToLocator;
              function textNodesUnder(root, includeWhitespace) {
                includeWhitespace = includeWhitespace === undefined ? true : includeWhitespace;
                var nodes = [];
                var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                  acceptNode: function (node) {
                    if (!node.nodeValue) return NodeFilter.FILTER_REJECT;
                    var parent = node.parentElement;
                    if (parent && parent.closest && parent.closest('#reader-selection-menu')) return NodeFilter.FILTER_REJECT;
                    if (parent && parent.closest && parent.closest('script, style')) return NodeFilter.FILTER_REJECT;
                    if (!includeWhitespace && node.nodeValue.trim().length === 0) return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                  }
                });
                var node;
                while ((node = walker.nextNode())) nodes.push(node);
                return nodes;
              }
              function normalizedRangeForText(root, expectedText, debugTts) {
                var expected = readerTtsNormalized(expectedText);
                if (!root || !expected) return null;
                var nodes = textNodesUnder(root, true);
                var flatText = '';
                var flatMap = [];
                var sawText = false;
                var pendingWhitespace = false;
                nodes.forEach(function (node) {
                  var value = node.nodeValue || '';
                  for (var i = 0; i < value.length; i++) {
                    if (/^\s$/.test(value[i])) {
                      if (sawText) pendingWhitespace = true;
                      continue;
                    }
                    if (pendingWhitespace && flatText.length > 0) {
                      flatText += ' ';
                      flatMap.push({ node: node, start: i, end: i });
                      pendingWhitespace = false;
                    }
                    flatText += value[i];
                    flatMap.push({ node: node, start: i, end: i + 1 });
                    sawText = true;
                  }
                });
                var index = flatText.indexOf(expected);
                if (debugTts) {
                  readerTtsLog(
                    'range_text_search root=' + readerElementLabel(root) +
                    ' expectedChars=' + expected.length +
                    ' flatChars=' + flatText.length +
                    ' index=' + index +
                    ' expected="' + readerTtsPreview(expected, 120) + '"'
                  );
                }
                if (index < 0 || index >= flatMap.length) return null;
                var endIndex = Math.min(flatMap.length - 1, index + expected.length - 1);
                var startMap = flatMap[index];
                var endMap = flatMap[endIndex];
                if (!startMap || !endMap) return null;
                var range = document.createRange();
                try {
                  range.setStart(startMap.node, startMap.start);
                  range.setEnd(endMap.node, endMap.end);
                } catch (error) {
                  range.detach && range.detach();
                  if (debugTts) readerTtsLog('range_text_search_failed reason=set_range_error error=' + readerTtsPreview(error, 140));
                  return null;
                }
                return range;
              }
              function readerProbeY() {
                var height = window.innerHeight || document.documentElement.clientHeight || 0;
                if (height <= 0) return 8;
                return Math.max(1, Math.min(height - 1, 8));
              }
              function firstVisibleOffsetInContent(content, preferredY) {
                var nodes = textNodesUnder(content, false);
                var contentStart = numberAttribute(content, 'data-reader-content-start', 0);
                var offset = contentStart;
                var viewportTop = Number.isFinite(preferredY) ? preferredY : readerProbeY();
                var viewportBottom = window.innerHeight - 8;
                var fallback = null;
                function visibleResult(node, localOffset, rawOffset) {
                  var sourceOffset = boundaryOffsetWithinContent(content, node, localOffset);
                  return { offset: sourceOffset === null ? rawOffset : sourceOffset, textNode: node };
                }
                function usableLineRect(rect) {
                  return rect && rect.width > 0 && rect.height > 0;
                }
                function firstVisibleLineRect() {
                  var best = null;
                  function better(candidate, current) {
                    if (!current) return true;
                    var candidateCrossesTop = candidate.top <= viewportTop + 0.5 && candidate.bottom >= viewportTop;
                    var currentCrossesTop = current.top <= viewportTop + 0.5 && current.bottom >= viewportTop;
                    if (candidateCrossesTop !== currentCrossesTop) return candidateCrossesTop;
                    if (candidateCrossesTop) {
                      if (Math.abs(candidate.top - current.top) > 0.5) return candidate.top > current.top;
                    } else if (Math.abs(candidate.top - current.top) > 0.5) {
                      return candidate.top < current.top;
                    }
                    return candidate.left < current.left;
                  }
                  for (var ln = 0; ln < nodes.length; ln++) {
                    var lineWhole = document.createRange();
                    lineWhole.selectNodeContents(nodes[ln]);
                    var lineRects = lineWhole.getClientRects();
                    lineWhole.detach && lineWhole.detach();
                    for (var lr = 0; lr < lineRects.length; lr++) {
                      var rect = lineRects[lr];
                      if (!usableLineRect(rect)) continue;
                      if (rect.bottom < viewportTop || rect.top > viewportBottom) continue;
                      if (better(rect, best)) best = rect;
                    }
                  }
                  return best;
                }
                function sameVisualLine(rect, lineRect) {
                  if (!usableLineRect(rect) || !usableLineRect(lineRect)) return false;
                  var rectMid = (rect.top + rect.bottom) / 2;
                  var lineMid = (lineRect.top + lineRect.bottom) / 2;
                  var tolerance = Math.max(2, Math.min(8, lineRect.height * 0.35));
                  return Math.abs(rectMid - lineMid) <= tolerance;
                }
                function firstVisibleLineStart(targetLineRect) {
                  var lineOffset = contentStart;
                  var best = null;
                  var direction = 'ltr';
                  try { direction = window.getComputedStyle(content).direction || 'ltr'; } catch (error) {}
                  for (var ln = 0; ln < nodes.length; ln++) {
                    var lineNode = nodes[ln];
                    var lineText = lineNode.nodeValue || '';
                    for (var li = 0; li < lineText.length; li++) {
                      if (!lineText[li] || /^\s$/.test(lineText[li])) continue;
                      var lineRange = document.createRange();
                      lineRange.setStart(lineNode, li);
                      lineRange.setEnd(lineNode, Math.min(li + 1, lineText.length));
                      var charLineRect = lineRange.getBoundingClientRect();
                      lineRange.detach && lineRange.detach();
                      if (sameVisualLine(charLineRect, targetLineRect)) {
                        var visualEdge = direction === 'rtl' ? -charLineRect.right : charLineRect.left;
                        if (!best || visualEdge < best.visualEdge - 0.5 || (
                          Math.abs(visualEdge - best.visualEdge) <= 0.5 && lineOffset + li < best.rawOffset
                        )) {
                          best = {
                            node: lineNode,
                            localOffset: li,
                            rawOffset: lineOffset + li,
                            visualEdge: visualEdge,
                            rect: charLineRect
                          };
                        }
                      }
                    }
                    lineOffset += lineText.length;
                  }
                  if (!best) return null;
                  var result = visibleResult(best.node, best.localOffset, best.rawOffset);
                  readerTtsStartTraceLog(
                    'event=web_line_start_choice offset=' + result.offset +
                    ' rawOffset=' + best.rawOffset +
                    ' lineTop=' + Math.round(targetLineRect.top) +
                    ' lineBottom=' + Math.round(targetLineRect.bottom) +
                    ' charLeft=' + Math.round(best.rect.left) +
                    ' charRight=' + Math.round(best.rect.right) +
                    ' text="' + readerTtsPreview(snippetFromContentOffset(content, result.offset), 120) + '"'
                  );
                  return result;
                }
                var topLineRect = firstVisibleLineRect();
                var topLineStart = topLineRect ? firstVisibleLineStart(topLineRect) : null;
                if (topLineStart) return topLineStart;
                for (var n = 0; n < nodes.length; n++) {
                  var node = nodes[n];
                  var text = node.nodeValue || '';
                  var whole = document.createRange();
                  whole.selectNodeContents(node);
                  var rects = whole.getClientRects();
                  whole.detach && whole.detach();
                  var visible = false;
                  for (var r = 0; r < rects.length; r++) {
                    if (rects[r].bottom >= viewportTop && rects[r].top <= viewportBottom) {
                      visible = true;
                      break;
                    }
                  }
                  if (!visible) {
                    offset += text.length;
                    continue;
                  }
                  for (var i = 0; i < text.length; i++) {
                    if (!text[i] || /^\s$/.test(text[i])) continue;
                    var charRange = document.createRange();
                    charRange.setStart(node, i);
                    charRange.setEnd(node, Math.min(i + 1, text.length));
                    var charRect = charRange.getBoundingClientRect();
                    charRange.detach && charRange.detach();
                    if (charRect.bottom >= viewportTop && charRect.top <= viewportBottom) {
                      return visibleResult(node, i, offset + i);
                    }
                  }
                  offset += text.length;
                }
                return fallback || { offset: contentStart, textNode: null };
              }
              function snippetFromContentOffset(content, startOffset) {
                var nodes = textNodesUnder(content, false);
                var contentStart = numberAttribute(content, 'data-reader-content-start', 0);
                var remaining = Math.max(0, startOffset - contentStart);
                var text = '';
                for (var n = 0; n < nodes.length; n++) {
                  var value = nodes[n].nodeValue || '';
                  if (remaining >= value.length) {
                    remaining -= value.length;
                    continue;
                  }
                  text += value.substring(remaining);
                  remaining = 0;
                  if (text.length >= 160) break;
                }
                return text.replace(/\s+/g, ' ').trim().substring(0, 140);
              }
              function pageForLocator(chapterIndex, offset) {
                if (!readerPageAnchors.length) return null;
                var sameChapter = readerPageAnchors.filter(function (page) { return page.chapterIndex === chapterIndex; });
                if (!sameChapter.length) return null;
                var best = sameChapter[0];
                for (var p = 0; p < sameChapter.length; p++) {
                  var page = sameChapter[p];
                  if (offset >= page.startOffset && offset < page.endOffset) return page;
                  if (Math.abs(page.startOffset - offset) < Math.abs(best.startOffset - offset)) best = page;
                }
                return best;
              }
              function isVerticalReaderDocument() {
                return !!(document.body && document.body.classList.contains('reader-vertical'));
              }
              function renderedVerticalPageAnchors() {
                if (!isVerticalReaderDocument() || !readerPageAnchors.length) return readerPageAnchors;
                var hosts = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                if (!hosts.length) return readerPageAnchors;
                var renderedChapters = {};
                hosts.forEach(function (host) {
                  var chapterIndex = numberAttribute(host, 'data-reader-chapter-index', null);
                  if (chapterIndex !== null) renderedChapters[chapterIndex] = true;
                });
                var filtered = readerPageAnchors.filter(function (page) {
                  return !!renderedChapters[page.chapterIndex];
                });
                return filtered.length ? filtered : readerPageAnchors;
              }
              function verticalScrollMetrics() {
                var scrollY = Math.round(window.scrollY || window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);
                var scrollHeight = Math.round(Math.max(
                  document.body ? document.body.scrollHeight : 0,
                  document.documentElement ? document.documentElement.scrollHeight : 0
                ));
                var clientHeight = Math.round(document.documentElement.clientHeight || window.innerHeight || 0);
                var maxScroll = Math.max(0, scrollHeight - clientHeight);
                return {
                  scrollY: Math.max(0, Math.min(scrollY, maxScroll)),
                  scrollHeight: scrollHeight,
                  clientHeight: clientHeight,
                  maxScroll: maxScroll
                };
              }
              function pageForVerticalScroll() {
                if (!isVerticalReaderDocument() || !readerPageAnchors.length) return null;
                var anchors = renderedVerticalPageAnchors();
                if (!anchors.length) return null;
                var metrics = verticalScrollMetrics();
                if (anchors.length === 1 || metrics.maxScroll <= 0) return anchors[0];
                var ratio = Math.max(0, Math.min(1, metrics.scrollY / metrics.maxScroll));
                var index = Math.round((anchors.length - 1) * ratio);
                return anchors[Math.max(0, Math.min(anchors.length - 1, index))];
              }
              function scrollToVerticalPage(locator, options) {
                if (!isVerticalReaderDocument() || !locator) return false;
                var cfi = String(locator.cfi || '');
                if (cfi.indexOf('desktop-scroll-page:') !== 0) return false;
                var pageIndex = parseInt(locator.pageIndex, 10);
                if (!Number.isFinite(pageIndex) || !readerPageAnchors.length) return false;
                var chapterIndex = parseInt(locator.chapterIndex, 10);
                var startOffset = parseInt(locator.startOffset, 10);
                if (Number.isFinite(chapterIndex) && Number.isFinite(startOffset)) {
                  var chapter = readerHostForLocator(chapterIndex, startOffset, locator.endOffset);
                  if (chapter) {
                    prepareVerticalScrollMeasurement(chapter);
                    var content = chapter.querySelector('.reader-content') || chapter;
                    var contentStart = numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0));
                    var contentEnd = numberAttribute(content, 'data-reader-content-end', numberAttribute(chapter, 'data-reader-page-end', contentStart));
                    var targetY = chapter.getBoundingClientRect().top + window.scrollY;
                    if (contentEnd > contentStart && startOffset > contentStart) {
                      var ratioInContent = Math.max(0, Math.min(1, (startOffset - contentStart) / (contentEnd - contentStart)));
                      var contentRect = content.getBoundingClientRect();
                      targetY = contentRect.top + window.scrollY + (content.scrollHeight * ratioInContent);
                    }
                    scrollToTopWithTrace(scrollTargetTopFromY(targetY, options), 'vertical_page_content', locator, scrollTraceExtra('ratioSource=content', options));
                    return true;
                  }
                }
                var anchors = renderedVerticalPageAnchors();
                if (!anchors.length) return false;
                var metrics = verticalScrollMetrics();
                var anchorPosition = anchors.findIndex(function (page) { return page.pageIndex === pageIndex; });
                var ratio = anchorPosition >= 0
                  ? anchorPosition / Math.max(1, anchors.length - 1)
                  : pageIndex / Math.max(1, readerPageAnchors.length - 1);
                ratio = Math.max(0, Math.min(1, ratio));
                scrollToTopWithTrace(Math.round(metrics.maxScroll * ratio), 'vertical_page_ratio', locator, 'ratio=' + ratio.toFixed(4));
                return true;
              }
              function readerHostIsVisible(host) {
                if (!host) return false;
                var rect = host.getBoundingClientRect();
                return rect.bottom >= 8 && rect.top <= window.innerHeight - 8;
              }
              function readerHostVisibilityScore(host, probeY) {
                if (!host) return null;
                var rect = host.getBoundingClientRect();
                var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                var visibleTop = Math.max(0, rect.top);
                var visibleBottom = Math.min(viewportHeight, rect.bottom);
                var visibleHeight = Math.max(0, visibleBottom - visibleTop);
                var containsProbe = rect.top <= probeY && rect.bottom >= probeY;
                var distance = containsProbe ? 0 : Math.min(Math.abs(rect.top - probeY), Math.abs(rect.bottom - probeY));
                return {
                  visibleHeight: visibleHeight,
                  containsProbe: containsProbe,
                  distance: distance,
                  top: Math.round(rect.top),
                  bottom: Math.round(rect.bottom)
                };
              }
              function bestVisibleReaderHost() {
                var chapters = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                if (!chapters.length) return null;
                var probeY = readerProbeY();
                var probeX = Math.max(0, Math.min((window.innerWidth || 0) - 1, Math.round((window.innerWidth || 0) / 2)));
                var probeElement = document.elementFromPoint(probeX, probeY);
                var probeHost = nearestReaderHost(probeElement);
                if (probeHost && readerHostIsVisible(probeHost)) {
                  return { host: probeHost, probeY: probeY, source: 'element_from_point', score: readerHostVisibilityScore(probeHost, probeY) };
                }
                var best = null;
                chapters.forEach(function (chapter) {
                  if (!readerHostIsVisible(chapter)) return;
                  var score = readerHostVisibilityScore(chapter, probeY);
                  if (!score || score.visibleHeight <= 0) return;
                  if (!best ||
                    (score.containsProbe && !best.score.containsProbe) ||
                    (score.containsProbe === best.score.containsProbe && score.distance < best.score.distance) ||
                    (score.containsProbe === best.score.containsProbe && score.distance === best.score.distance && score.visibleHeight > best.score.visibleHeight)
                  ) {
                    best = { host: chapter, probeY: probeY, source: 'visibility_score', score: score };
                  }
                });
                return best;
              }
              function positionFromReaderHost(host, preferredOffset, preferredY, source) {
                if (!host) return null;
                var content = host.querySelector('.reader-content') || host;
                var chapterIndex = numberAttribute(host, 'data-reader-chapter-index', 0);
                var pageStart = numberAttribute(host, 'data-reader-page-start', null);
                var pageEnd = numberAttribute(host, 'data-reader-page-end', null);
                var visible = firstVisibleOffsetInContent(content, preferredY);
                var usePreferredOffset = Number.isFinite(preferredOffset) &&
                  (pageStart === null || preferredOffset >= pageStart) &&
                  (pageEnd === null || preferredOffset <= pageEnd);
                var offset = usePreferredOffset ? preferredOffset : visible.offset;
                var renderedAnchors = renderedVerticalPageAnchors();
                var page = pageForLocator(chapterIndex, offset) || pageForVerticalScroll() || renderedAnchors[0] || readerPageAnchors[0];
                if (!page) return null;
                var positionCfi = readerCfiPointForOffset(content, offset, false) || stableDesktopCfi(chapterIndex, offset, offset);
                var blockPosition = readerBlockPositionForOffset(content, offset, false);
                var metrics = isVerticalReaderDocument() ? verticalScrollMetrics() : null;
                if (isVerticalReaderDocument()) {
                  positionCfi = stableReaderCfi(positionCfi) || stableDesktopCfi(chapterIndex, offset, offset);
                }
                readerDesktopHighlightMapLog(
                  'web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                  ' page=' + page.pageIndex +
                  ' chapter=' + chapterIndex +
                  ' offsets=' + offset + '..' + offset +
                  ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                  ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                  ' chapterId=' + readerTtsPreview(host.getAttribute('data-reader-chapter-id'), 80) +
                  ' href=' + readerTtsPreview(host.getAttribute('data-reader-chapter-href'), 120) +
                  ' cfi=' + readerTtsPreview(positionCfi, 160) +
                  ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 120) + '"'
                );
                readerDesktopPositionTraceLog(
                  'event=web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                  ' source=' + (source || 'unknown') +
                  ' page=' + page.pageIndex +
                  ' chapter=' + chapterIndex +
                  ' offsets=' + offset + '..' + offset +
                  ' preferredOffset=' + (Number.isFinite(preferredOffset) ? preferredOffset : 'null') +
                  ' preferredY=' + (Number.isFinite(preferredY) ? Math.round(preferredY) : 'null') +
                  ' visibleNode=' + (visible.textNode ? readerElementLabel(visible.textNode.parentElement) : 'null') +
                  ' scrollY=' + (metrics ? metrics.scrollY : 'null') +
                  ' maxScroll=' + (metrics ? metrics.maxScroll : 'null') +
                  ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                  ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                  ' cfi=' + readerTtsPreview(positionCfi, 160) +
                  ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 120) + '"'
                );
                readerTtsStartTraceLog(
                  'event=web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                  ' source=' + (source || 'unknown') +
                  ' page=' + page.pageIndex +
                  ' chapter=' + chapterIndex +
                  ' offsets=' + offset + '..' + offset +
                  ' preferredY=' + (Number.isFinite(preferredY) ? Math.round(preferredY) : 'null') +
                  ' visibleNode=' + (visible.textNode ? readerElementLabel(visible.textNode.parentElement) : 'null') +
                  ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                  ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                  ' cfi=' + readerTtsPreview(positionCfi, 160) +
                  ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 160) + '"'
                );
                return {
                  pageIndex: page.pageIndex,
                  chapterIndex: chapterIndex,
                  chapterId: host.getAttribute('data-reader-chapter-id'),
                  href: host.getAttribute('data-reader-chapter-href'),
                  startOffset: offset,
                  endOffset: offset,
                  blockIndex: blockPosition ? blockPosition.blockIndex : null,
                  charOffset: blockPosition ? blockPosition.charOffset : null,
                  textQuote: snippetFromContentOffset(content, offset),
                  cfi: positionCfi
                };
              }
              function locatorChapterIndex(locator) {
                locator = locator || {};
                var chapterIndex = parseInt(locator.chapterIndex, 10);
                if (Number.isFinite(chapterIndex)) return chapterIndex;
                var cfi = stableReaderCfi(locator.cfi);
                if (cfi && cfi.indexOf('desktop:') === 0) {
                  var desktopParts = cfi.split(':');
                  var desktopChapter = parseInt(desktopParts[1], 10);
                  if (Number.isFinite(desktopChapter)) return desktopChapter;
                }
                if (cfi && cfi.indexOf('android-locator:') === 0) {
                  var androidParts = cfi.split(':');
                  var androidChapter = parseInt(androidParts[1], 10);
                  if (Number.isFinite(androidChapter)) return androidChapter;
                }
                return null;
              }
              function positionMatchesRestoreLocator(position, locator) {
                if (!position || !locator) return false;
                var chapterIndex = locatorChapterIndex(locator);
                if (chapterIndex !== null && position.chapterIndex !== chapterIndex) return false;
                var requestedPage = parseInt(locator.pageIndex, 10);
                var requestedOffset = locatorStartOffset(locator);
                if (requestedOffset !== null) {
                  if (Math.abs(position.startOffset - requestedOffset) <= 512) return true;
                  if (Number.isFinite(requestedPage) && position.pageIndex === requestedPage) return true;
                  return false;
                }
                return Number.isFinite(requestedPage) ? position.pageIndex === requestedPage : true;
              }
              function pendingRestoreVisiblePosition() {
                if (!pendingRestoreLocator || !isVerticalReaderDocument()) return null;
                if (Date.now() > pendingRestoreUntil) {
                  readerDesktopPositionTraceLog(
                    'event=web_restore_guard_expired ' + readerLocatorTrace(pendingRestoreLocator)
                  );
                  pendingRestoreLocator = null;
                  pendingRestoreUntil = 0;
                  return null;
                }
                var chapterIndex = locatorChapterIndex(pendingRestoreLocator);
                var requestedOffset = locatorStartOffset(pendingRestoreLocator);
                if (chapterIndex === null || requestedOffset === null) return null;
                var chapter = readerHostForLocator(chapterIndex, requestedOffset, pendingRestoreLocator.endOffset);
                if (!chapter || !readerHostIsVisible(chapter)) return null;
                var range = rangeForOffsets(chapterIndex, requestedOffset, requestedOffset + 1, stableReaderCfi(pendingRestoreLocator.cfi));
                var preferredY = readerProbeY();
                var visible = false;
                if (range) {
                  var rects = range.getClientRects();
                  var rect = rects.length ? rects[0] : range.getBoundingClientRect();
                  range.detach && range.detach();
                  if (rect && rect.bottom >= 8 && rect.top <= (window.innerHeight || document.documentElement.clientHeight || 0) - 8) {
                    visible = true;
                    preferredY = Math.max(8, Math.min((window.innerHeight || document.documentElement.clientHeight || 0) - 8, Math.round(rect.top)));
                  }
                }
                if (!visible) return null;
                readerDesktopPositionTraceLog(
                  'event=web_restore_guard_visible chapter=' + chapterIndex +
                  ' offset=' + requestedOffset +
                  ' preferredY=' + preferredY +
                  ' ' + readerLocatorTrace(pendingRestoreLocator)
                );
                return positionFromReaderHost(chapter, requestedOffset, preferredY, 'restore_locator_visible');
              }
              function clearPendingRestoreLocator(reason) {
                if (!pendingRestoreLocator) return;
                readerDesktopPositionTraceLog(
                  'event=web_restore_guard_cleared reason=' + reason +
                  ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                );
                pendingRestoreLocator = null;
                pendingRestoreUntil = 0;
              }
              function currentVisiblePosition() {
                var activePageIndex = numberAttribute(document.body, 'data-reader-active-page-index', null);
                if (document.body.classList.contains('reader-paginated') && activePageIndex !== null) {
                  var activePage = document.querySelector('.page[data-reader-page-index="' + selectorValue(activePageIndex) + '"]');
                  if (readerHostIsVisible(activePage)) {
                    var activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                    var activePosition = positionFromReaderHost(activePage, activeStart, readerProbeY(), 'active_page');
                    if (activePosition) return activePosition;
                  }
                }
                var best = bestVisibleReaderHost();
                if (best && best.host) {
                  readerDesktopPositionTraceLog(
                    'event=web_visible_host_selected source=' + best.source +
                    ' chapter=' + numberAttribute(best.host, 'data-reader-chapter-index', -1) +
                    ' probeY=' + best.probeY +
                    ' visibleHeight=' + (best.score ? Math.round(best.score.visibleHeight) : 'null') +
                    ' containsProbe=' + (best.score ? best.score.containsProbe : false) +
                    ' distance=' + (best.score ? Math.round(best.score.distance) : 'null') +
                    ' rect=' + (best.score ? best.score.top + '..' + best.score.bottom : 'null')
                  );
                  var position = positionFromReaderHost(best.host, null, best.probeY, best.source);
                  if (position) return position;
                }
                return null;
              }
              function reportVisiblePage() {
                var position = pendingRestoreVisiblePosition() || currentVisiblePosition();
                if (!position) {
                  readerDesktopPositionTraceLog('event=web_position_report_skip reason=no_position');
                  return;
                }
                if (pendingRestoreLocator) {
                  if (!positionMatchesRestoreLocator(position, pendingRestoreLocator)) {
                    readerDesktopPositionTraceLog(
                      'event=web_position_report_skip reason=pending_restore page=' + position.pageIndex +
                      ' chapter=' + position.chapterIndex +
                      ' offsets=' + position.startOffset + '..' + position.endOffset +
                      ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                    );
                    return;
                  }
                  readerDesktopPositionTraceLog(
                    'event=web_restore_guard_resolved page=' + position.pageIndex +
                    ' chapter=' + position.chapterIndex +
                    ' offsets=' + position.startOffset + '..' + position.endOffset +
                    ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                  );
                  pendingRestoreLocator = null;
                  pendingRestoreUntil = 0;
                }
                if (position.pageIndex === lastReportedPageIndex && Math.abs(position.startOffset - lastReportedStartOffset) < 8) {
                  return;
                }
                lastReportedPageIndex = position.pageIndex;
                lastReportedStartOffset = position.startOffset;
                readerDesktopPositionTraceLog(
                  'event=web_position_report_send page=' + position.pageIndex +
                  ' chapter=' + position.chapterIndex +
                  ' offsets=' + position.startOffset + '..' + position.endOffset +
                  ' block=' + (position.blockIndex === null || position.blockIndex === undefined ? 'null' : position.blockIndex) +
                  ' char=' + (position.charOffset === null || position.charOffset === undefined ? 'null' : position.charOffset) +
                  ' cfi=' + readerTtsPreview(position.cfi, 160) +
                  ' text="' + readerTtsPreview(position.textQuote, 120) + '"'
                );
                readerTtsStartTraceLog(
                  'event=web_position_report_send page=' + position.pageIndex +
                  ' chapter=' + position.chapterIndex +
                  ' offsets=' + position.startOffset + '..' + position.endOffset +
                  ' block=' + (position.blockIndex === null || position.blockIndex === undefined ? 'null' : position.blockIndex) +
                  ' char=' + (position.charOffset === null || position.charOffset === undefined ? 'null' : position.charOffset) +
                  ' cfi=' + readerTtsPreview(position.cfi, 160) +
                  ' text="' + readerTtsPreview(position.textQuote, 160) + '"'
                );
                if (window.kmpJsBridge) {
                  window.kmpJsBridge.callNative('readerPositionChanged', JSON.stringify(position));
                }
              }
              function nearestReaderHost(element) {
                return element && element.closest ? element.closest('[data-reader-chapter-index]') : null;
              }
              function fallbackReaderLinkNavigation(payload, reason) {
                try {
                  var encoded = encodeURIComponent(JSON.stringify(payload));
                  readerConsoleLog('READER_LINK fallback_navigation href=' + payload.href + ' reason=' + reason);
                  window.location.href = 'readerlink://click?payload=' + encoded;
                } catch (error) {
                  readerConsoleLog('READER_LINK fallback_navigation_error href=' + payload.href + ' error=' + error);
                }
              }
              function sendReaderLinkClick(payload, attempt) {
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerLinkClicked', JSON.stringify(payload));
                    readerConsoleLog('READER_LINK bridge_sent href=' + payload.href + ' attempt=' + attempt);
                    if (window.readerDisableLinkFallback === true) return true;
                    window.setTimeout(function () {
                      fallbackReaderLinkNavigation(payload, 'post_bridge');
                    }, 260);
                    return true;
                  } catch (error) {
                    readerConsoleLog('READER_LINK bridge_error href=' + payload.href + ' attempt=' + attempt + ' error=' + error);
                  }
                } else {
                  readerConsoleLog('READER_LINK bridge_missing href=' + payload.href + ' attempt=' + attempt);
                }
                if (attempt < 3) {
                  window.setTimeout(function () {
                    sendReaderLinkClick(payload, attempt + 1);
                  }, attempt === 0 ? 60 : 220);
                  return true;
                }
                readerConsoleLog('READER_LINK bridge_gave_up href=' + payload.href);
                fallbackReaderLinkNavigation(payload, 'bridge_gave_up');
                return false;
              }
""".trimIndent()
