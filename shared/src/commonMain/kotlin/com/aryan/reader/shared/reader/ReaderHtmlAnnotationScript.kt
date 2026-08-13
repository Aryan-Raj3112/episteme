package com.aryan.reader.shared.reader

internal fun readerHtmlAnnotationScript(): String = """
              function wrapRangeTextSegments(range, markerFactory) {
                var segments = textSegmentsInRange(range);
                var wrapped = 0;
                for (var index = segments.length - 1; index >= 0; index--) {
                  var segment = segments[index];
                  var node = segment.node;
                  var parent = node.parentNode;
                  if (!parent) continue;
                  if (parent.closest && parent.closest('span[class*="user-highlight-"], mark.reader-user-highlight')) continue;
                  var value = node.nodeValue || '';
                  var selected = value.substring(segment.start, segment.end);
                  if (!selected) continue;
                  var fragment = document.createDocumentFragment();
                  if (segment.start > 0) fragment.appendChild(document.createTextNode(value.substring(0, segment.start)));
                  var marker = markerFactory();
                  marker.textContent = selected;
                  fragment.appendChild(marker);
                  if (segment.end < value.length) fragment.appendChild(document.createTextNode(value.substring(segment.end)));
                  parent.replaceChild(fragment, node);
                  wrapped++;
                }
                return wrapped > 0;
              }
              function unwrapReaderHighlights() {
                var marks = Array.prototype.slice.call(document.querySelectorAll('span[class*="user-highlight-"], mark.reader-user-highlight'));
                marks.forEach(function (mark) {
                  var parent = mark.parentNode;
                  if (!parent) return;
                  while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                  parent.removeChild(mark);
                  parent.normalize();
                });
              }
              function rangeForOffsets(chapterIndex, startOffset, endOffset, sourceCfi, debugTts) {
                debugTts = debugTts === true;
                function ttsRangeLog(message) {
                  if (debugTts) readerTtsLog(message);
                }
                var chapter = readerHostForLocator(chapterIndex, startOffset, endOffset);
                if (!chapter) {
                  ttsRangeLog('range_failed reason=missing_chapter chapter=' + chapterIndex + ' offsets=' + startOffset + '..' + endOffset + ' cfi=' + readerTtsPreview(sourceCfi, 100));
                  return null;
                }
                var content = chapter.querySelector('.reader-content') || chapter;
                var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end]'));
                ttsRangeLog(
                  'range_start chapter=' + chapterIndex +
                  ' offsets=' + startOffset + '..' + endOffset +
                  ' cfi=' + readerTtsPreview(sourceCfi, 100) +
                  ' hosts=' + hosts.length +
                  ' contentRange=' + numberAttribute(content, 'data-reader-content-start', 'null') + '..' + numberAttribute(content, 'data-reader-content-end', 'null') +
                  ' pageRange=' + numberAttribute(chapter, 'data-reader-page-start', 'null') + '..' + numberAttribute(chapter, 'data-reader-page-end', 'null')
                );
                function readerTextBlock(node) {
                  var parent = node && node.parentElement;
                  return parent && parent.closest
                    ? parent.closest('p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, div, section')
                    : null;
                }
                function textHostForOffset(offset, preferEnd) {
                  if (sourceCfi) {
                    var sourceCfiBases = readerCfiBases(sourceCfi);
                    var cfiHosts = hosts.filter(function (host) {
                      return readerHostMatchesCfi(host, sourceCfiBases);
                    });
                    var cfiBest = null;
                    var cfiBestSpan = Number.MAX_SAFE_INTEGER;
                    cfiHosts.forEach(function (host) {
                      var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                      var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                      if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                      var contains = preferEnd
                        ? offset > hostStart && offset <= hostEnd
                        : offset >= hostStart && offset < hostEnd;
                      if (!contains) return;
                      var span = hostEnd - hostStart;
                      if (span < cfiBestSpan) {
                        cfiBest = host;
                        cfiBestSpan = span;
                      }
                    });
                    if (cfiBest) {
                      ttsRangeLog('range_host_cfi_match offset=' + offset + ' preferEnd=' + preferEnd + ' host=' + readerElementLabel(cfiBest));
                      return cfiBest;
                    }
                    if (cfiHosts.length > 0) {
                      ttsRangeLog(
                        'range_cfi_hosts_no_offset_match offset=' + offset +
                        ' preferEnd=' + preferEnd +
                        ' cfiHosts=' + cfiHosts.length +
                        ' firstHost=' + readerElementLabel(cfiHosts[0])
                      );
                    } else {
                      ttsRangeLog('range_cfi_host_missing cfi=' + readerTtsPreview(sourceCfi, 100) + ' hostCount=' + hosts.length);
                    }
                  }
                  var best = null;
                  var bestSpan = Number.MAX_SAFE_INTEGER;
                  hosts.forEach(function (host) {
                    var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                    var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                    if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                    var contains = preferEnd
                      ? offset > hostStart && offset <= hostEnd
                      : offset >= hostStart && offset < hostEnd;
                    if (!contains && offset === hostStart && offset === hostEnd) contains = true;
                    if (!contains) return;
                    var span = hostEnd - hostStart;
                    if (span < bestSpan) {
                      best = host;
                      bestSpan = span;
                    }
                  });
                  if (best) return best;
                  var fallback = null;
                  var fallbackDistance = Number.MAX_SAFE_INTEGER;
                  hosts.forEach(function (host) {
                    var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                    var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                    if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                    var distance = preferEnd
                      ? Math.abs(offset - hostEnd)
                      : Math.abs(offset - hostStart);
                    if (distance < fallbackDistance) {
                      fallback = host;
                      fallbackDistance = distance;
                    }
                  });
                  return fallback || content;
                }
                function boundaryForOffset(offset, preferEnd) {
                  var host = textHostForOffset(offset, preferEnd);
                  var hasExplicitTextOffsets = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                  var nodes = textNodesUnder(host, true);
                  var cursor = numberAttribute(
                    host,
                    'data-reader-text-start',
                    numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0))
                  );
                  if (nodes.length === 0) {
                    ttsRangeLog('boundary_failed reason=no_nodes offset=' + offset + ' preferEnd=' + preferEnd + ' host=' + readerElementLabel(host));
                    return null;
                  }
                  if (!hasExplicitTextOffsets) {
                    var normalizedTarget = Math.max(0, offset - cursor);
                    var normalizedCursor = 0;
                    var sawText = false;
                    var inWhitespace = false;
                    var lastBoundary = { node: nodes[0], offset: 0 };
                    var previousBlock = null;
                    for (var nn = 0; nn < nodes.length; nn++) {
                      var value = nodes[nn].nodeValue || '';
                      var currentBlock = readerTextBlock(nodes[nn]);
                      if (previousBlock && currentBlock && currentBlock !== previousBlock && sawText && !inWhitespace) {
                        if (normalizedCursor >= normalizedTarget) return { node: nodes[nn], offset: 0 };
                        inWhitespace = true;
                        normalizedCursor += 1;
                        if (normalizedCursor >= normalizedTarget) return { node: nodes[nn], offset: 0 };
                      }
                      if (currentBlock) previousBlock = currentBlock;
                      for (var ii = 0; ii < value.length; ii++) {
                        var before = { node: nodes[nn], offset: ii };
                        var after = { node: nodes[nn], offset: ii + 1 };
                        var isWhitespace = /^\s$/.test(value[ii]);
                        if (isWhitespace) {
                          lastBoundary = after;
                          if (!sawText) continue;
                          if (!inWhitespace) {
                            if (normalizedCursor >= normalizedTarget) return before;
                            inWhitespace = true;
                            normalizedCursor += 1;
                          }
                          continue;
                        }
                        if (inWhitespace) {
                          if (normalizedCursor >= normalizedTarget) return before;
                          inWhitespace = false;
                        }
                        if (normalizedCursor >= normalizedTarget) return before;
                        sawText = true;
                        normalizedCursor += 1;
                        lastBoundary = after;
                        if (normalizedCursor >= normalizedTarget) return after;
                      }
                    }
                    return lastBoundary;
                  }
                  for (var n = 0; n < nodes.length; n++) {
                    var node = nodes[n];
                    var length = (node.nodeValue || '').length;
                    var next = cursor + length;
                    var contains = preferEnd ? offset >= cursor && offset <= next : offset >= cursor && offset < next;
                    if (contains || (n === nodes.length - 1 && offset >= next)) {
                      return {
                        node: node,
                        offset: Math.max(0, Math.min(length, offset - cursor))
                      };
                    }
                    cursor = next;
                  }
                  var last = nodes[nodes.length - 1];
                  return { node: last, offset: (last.nodeValue || '').length };
                }
                var startBoundary = boundaryForOffset(startOffset, false);
                var endBoundary = boundaryForOffset(endOffset, true);
                if (!startBoundary || !endBoundary) {
                  ttsRangeLog(
                    'range_failed reason=missing_boundary startBoundary=' + !!startBoundary +
                    ' endBoundary=' + !!endBoundary +
                    ' offsets=' + startOffset + '..' + endOffset +
                    ' cfi=' + readerTtsPreview(sourceCfi, 100)
                  );
                  return null;
                }
                var range = document.createRange();
                try {
                  range.setStart(startBoundary.node, startBoundary.offset);
                  range.setEnd(endBoundary.node, endBoundary.offset);
                } catch (error) {
                  range.detach && range.detach();
                  ttsRangeLog(
                    'range_failed reason=set_range_error error=' + readerTtsPreview(error, 140) +
                    ' startHost=' + readerElementLabel(startBoundary.node && startBoundary.node.parentElement) +
                    ' endHost=' + readerElementLabel(endBoundary.node && endBoundary.node.parentElement)
                  );
                  return null;
                }
                ttsRangeLog(
                  'range_success text="' + readerTtsPreview(range.toString(), 140) +
                  '" startHost=' + readerElementLabel(startBoundary.node && startBoundary.node.parentElement) +
                  ' endHost=' + readerElementLabel(endBoundary.node && endBoundary.node.parentElement)
                );
                return range;
              }
              function readerCfiPointBase(cfiPoint) {
                return String(cfiPoint || '').split(':')[0];
              }
              function readerCfiBases(sourceCfi) {
                var stable = stableReaderCfi(sourceCfi);
                if (!stable) return [];
                var seen = {};
                return String(stable).split('|').map(function (part) {
                  return readerCfiPointBase(part);
                }).filter(function (base) {
                  if (!base || base.charAt(0) !== '/' || seen[base]) return false;
                  seen[base] = true;
                  return true;
                });
              }
              function readerHostMatchesCfi(host, cfiBases) {
                if (!host || !host.getAttribute || !cfiBases || !cfiBases.length) return false;
                return cfiBases.indexOf(host.getAttribute('data-reader-cfi')) >= 0;
              }
              function readerElementForSourceCfi(root, sourceCfi) {
                if (!root || !root.querySelector) return null;
                var bases = readerCfiBases(sourceCfi);
                for (var i = 0; i < bases.length; i++) {
                  var element = root.querySelector('[data-reader-cfi="' + selectorValue(bases[i]) + '"]');
                  if (element) return element;
                }
                return null;
              }
              function readerCfiPointLocalOffset(cfiPoint) {
                var parts = String(cfiPoint || '').split(':');
                if (parts.length < 2) return 0;
                var parsed = parseInt(parts[1], 10);
                return Number.isFinite(parsed) ? parsed : 0;
              }
              function readerHostElementForCfiPoint(chapterIndex, cfiPoint) {
                var baseCfi = readerCfiPointBase(cfiPoint);
                if (!baseCfi || baseCfi.charAt(0) !== '/') return null;
                var chapterSelector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                var hosts = Array.prototype.slice.call(document.querySelectorAll(chapterSelector));
                for (var i = 0; i < hosts.length; i++) {
                  var content = hosts[i].querySelector('.reader-content') || hosts[i];
                  var cfiHost = content.querySelector('[data-reader-cfi="' + selectorValue(baseCfi) + '"]');
                  if (cfiHost) return cfiHost;
                }
                return null;
              }
              function readerContentOffsetForCfiPoint(chapterIndex, cfiPoint) {
                var cfiHost = readerHostElementForCfiPoint(chapterIndex, cfiPoint);
                if (!cfiHost) return null;
                var hostStart = numberAttribute(cfiHost, 'data-reader-text-start', null);
                var hostEnd = numberAttribute(cfiHost, 'data-reader-text-end', null);
                if (hostStart === null || hostEnd === null || hostEnd < hostStart) return null;
                var localOffset = Math.max(0, readerCfiPointLocalOffset(cfiPoint));
                return Math.max(hostStart, Math.min(hostEnd, hostStart + localOffset));
              }
              function readerOffsetsForSourceCfi(chapterIndex, sourceCfi, expectedText) {
                if (!sourceCfi || sourceCfi.charAt(0) !== '/') return null;
                var parts = String(sourceCfi).split('|');
                var startPoint = parts[0];
                var endPoint = parts[parts.length - 1] || startPoint;
                var startOffset = readerContentOffsetForCfiPoint(chapterIndex, startPoint);
                var endOffset = readerContentOffsetForCfiPoint(chapterIndex, endPoint);
                if (startOffset === null || endOffset === null || endOffset < startOffset) return null;
                if (endOffset === startOffset && expectedText) endOffset = startOffset + String(expectedText).length;
                return { startOffset: startOffset, endOffset: endOffset };
              }
              function applyHighlightObject(highlight) {
                if (!highlight) return;
                var locator = highlight.locator || {};
                var chapterIndex = locator.chapterIndex;
                if (chapterIndex === undefined || chapterIndex === null) chapterIndex = highlight.chapterIndex;
                var startOffset = locator.startOffset;
                var endOffset = locator.endOffset;
                var sourceCfi = locator.cfi || highlight.cfi;
                var expectedText = locator.textQuote || highlight.text || '';
                var sourceCfiIsStructural = sourceCfi && String(sourceCfi).charAt(0) === '/';
                readerDesktopHighlightMapLog(
                  'web_apply_start id=' + (highlight.id || '') +
                  ' chapter=' + chapterIndex +
                  ' page=' + locator.pageIndex +
                  ' offsets=' + startOffset + '..' + endOffset +
                  ' block=' + locator.blockIndex +
                  ' char=' + locator.charOffset +
                  ' textChars=' + String(expectedText || '').length +
                  ' cfi=' + readerTtsPreview(sourceCfi, 160)
                );
                var cfiOffsets = readerOffsetsForSourceCfi(chapterIndex, sourceCfi, expectedText);
                if (cfiOffsets) {
                  startOffset = cfiOffsets.startOffset;
                  endOffset = cfiOffsets.endOffset;
                  readerDesktopHighlightMapLog(
                    'web_apply_cfi_offsets id=' + (highlight.id || '') +
                    ' offsets=' + startOffset + '..' + endOffset
                  );
                } else if (sourceCfiIsStructural) {
                  readerDesktopHighlightMapLog(
                    'web_apply_cfi_offsets_missing id=' + (highlight.id || '') +
                    ' cfi=' + readerTtsPreview(sourceCfi, 160)
                  );
                  startOffset = null;
                  endOffset = null;
                }
                if (startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                  var blockChar = parseInt(locator.charOffset, 10);
                  if (Number.isFinite(blockChar) && expectedText) {
                    startOffset = blockChar;
                    endOffset = blockChar + String(expectedText).length;
                    readerDesktopHighlightMapLog(
                      'web_apply_block_offsets id=' + (highlight.id || '') +
                      ' offsets=' + startOffset + '..' + endOffset +
                      ' block=' + locator.blockIndex
                    );
                  }
                }
                var hasPreciseOffsets = !(chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset);
                if (chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                  readerDesktopHighlightMapLog(
                    'web_apply_fallback_request id=' + (highlight.id || '') +
                    ' reason=invalid_offsets chapter=' + chapterIndex +
                    ' offsets=' + startOffset + '..' + endOffset
                  );
                  if (sourceCfiIsStructural) return;
                  applyHighlightTextFallback(highlight);
                  return;
                }
                var targetChapters = readerHostsForLocator(chapterIndex, startOffset, endOffset);
                if (!targetChapters.length) {
                  readerDesktopHighlightMapLog(
                    'web_apply_fallback_request id=' + (highlight.id || '') +
                    ' reason=no_target_chapters chapter=' + chapterIndex +
                    ' offsets=' + startOffset + '..' + endOffset
                  );
                  if (hasPreciseOffsets) return;
                  applyHighlightTextFallback(highlight);
                  return;
                }
                var expectedNormalized = readerTtsNormalized(expectedText);
                var applied = false;
                targetChapters.forEach(function (targetChapter) {
                  var pageStart = numberAttribute(targetChapter, 'data-reader-page-start', null);
                  var pageEnd = numberAttribute(targetChapter, 'data-reader-page-end', null);
                  if (pageStart !== null && pageEnd !== null && (startOffset >= pageEnd || endOffset <= pageStart)) return;
                  var segmentStart = pageStart === null ? startOffset : Math.max(startOffset, pageStart);
                  var segmentEnd = pageEnd === null ? endOffset : Math.min(endOffset, pageEnd);
                  if (segmentEnd <= segmentStart) return;
                  var range = rangeForOffsets(chapterIndex, segmentStart, segmentEnd, sourceCfi);
                  var actualNormalized = range && !range.collapsed ? readerTtsNormalized(range.toString()) : '';
                  var isSegment = segmentStart !== startOffset || segmentEnd !== endOffset || targetChapters.length > 1;
                  if (expectedNormalized && isSegment && actualNormalized && expectedNormalized.indexOf(actualNormalized) < 0) {
                    if (range && range.detach) range.detach();
                    readerSelectionDebugLog(
                      'highlight_segment_mismatch id=' + (highlight.id || '') +
                      ' offsets=' + segmentStart + '..' + segmentEnd +
                      ' expected="' + readerTtsPreview(expectedText, 120) + '"' +
                      ' actual="' + readerTtsPreview(actualNormalized, 120) + '"'
                    );
                    return;
                  }
                  if (expectedNormalized && !isSegment && (!range || range.collapsed || actualNormalized !== expectedNormalized)) {
                    var chapter = targetChapter;
                    var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : null;
                    var searchRoot = content;
                    if (content && sourceCfi) {
                      searchRoot = readerElementForSourceCfi(content, sourceCfi) || content;
                    }
                    if (content && searchRoot === content) {
                      var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end]'));
                      var containing = null;
                      var bestSpan = Number.MAX_SAFE_INTEGER;
                      hosts.forEach(function (host) {
                        var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                        var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                        if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                        if (segmentStart >= hostEnd || segmentEnd <= hostStart) return;
                        var span = hostEnd - hostStart;
                        if (span < bestSpan) {
                          containing = host;
                          bestSpan = span;
                        }
                      });
                      searchRoot = containing || content;
                    }
                    var textRange = normalizedRangeForText(searchRoot, expectedNormalized, false);
                    if (textRange && !textRange.collapsed && rangeMatchesStoredOffsets(content, textRange, segmentStart, segmentEnd)) {
                      if (range && range.detach) range.detach();
                      range = textRange;
                    } else {
                      if (textRange && textRange.detach) textRange.detach();
                      if (range && range.detach) range.detach();
                      readerSelectionDebugLog(
                        'highlight_expected_mismatch id=' + (highlight.id || '') +
                        ' offsets=' + segmentStart + '..' + segmentEnd +
                        ' expected="' + readerTtsPreview(expectedText, 120) + '"' +
                        ' actual="' + readerTtsPreview(actualNormalized, 120) + '"'
                      );
                      return;
                    }
                  }
                  if (!range || range.collapsed) {
                    if (range && range.detach) range.detach();
                    return;
                  }
                  wrapRangeTextSegments(range, function () {
                    var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', segmentStart, segmentEnd, highlight.colorArgb);
                    marker.setAttribute('data-cfi', sourceCfi || highlight.cfi || ('desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset));
                    return marker;
                  });
                  readerDesktopHighlightMapLog(
                    'web_apply_segment id=' + (highlight.id || '') +
                    ' chapter=' + chapterIndex +
                    ' page=' + numberAttribute(targetChapter, 'data-reader-page-index', 'null') +
                    ' segment=' + segmentStart + '..' + segmentEnd +
                    ' expectedChars=' + expectedNormalized.length +
                    ' actualChars=' + actualNormalized.length +
                    ' root=' + readerElementLabel(targetChapter)
                  );
                  applied = true;
                  range.detach && range.detach();
                });
                if (!applied) {
                  readerDesktopHighlightMapLog(
                    'web_apply_fallback_request id=' + (highlight.id || '') +
                    ' reason=no_segments_applied chapter=' + chapterIndex +
                    ' offsets=' + startOffset + '..' + endOffset
                  );
                  if (hasPreciseOffsets) return;
                  applyHighlightTextFallback(highlight);
                }
              }
              function applyHighlightTextFallback(highlight) {
                var locator = highlight && highlight.locator ? highlight.locator : {};
                var chapterIndex = locator.chapterIndex;
                if (chapterIndex === undefined || chapterIndex === null) chapterIndex = highlight.chapterIndex;
                var expectedText = readerTtsNormalized(locator.textQuote || highlight.text || '');
                if (!expectedText) return false;
                var blockElement = chapterIndex === undefined || chapterIndex === null
                  ? null
                  : readerElementForBlockLocator(chapterIndex, locator.blockIndex, locator.charOffset);
                var root = blockElement || (chapterIndex === undefined || chapterIndex === null
                  ? document.body
                  : readerHostForLocator(chapterIndex, locator.startOffset, locator.endOffset));
                if (!root) root = document.body;
                var content = root.querySelector ? (root.querySelector('.reader-content') || root) : root;
                var sourceCfi = locator.cfi || highlight.cfi;
                var cfiPoint = sourceCfi ? String(sourceCfi).split('|')[0] : null;
                var cfiHost = readerHostElementForCfiPoint(chapterIndex, cfiPoint);
                if (cfiHost) content = cfiHost;
                readerDesktopHighlightMapLog(
                  'web_text_fallback_start id=' + ((highlight && highlight.id) || '') +
                  ' chapter=' + chapterIndex +
                  ' root=' + readerElementLabel(content) +
                  ' expectedChars=' + expectedText.length +
                  ' cfi=' + readerTtsPreview(sourceCfi, 160)
                );
                var range = normalizedRangeForText(content, expectedText, false);
                if (!range || range.collapsed) {
                  readerDesktopHighlightMapLog(
                    'web_text_fallback_result id=' + ((highlight && highlight.id) || '') +
                    ' applied=false reason=' + (!range ? 'no_range' : 'collapsed')
                  );
                  return false;
                }
                wrapRangeTextSegments(range, function () {
                  var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', null, null, highlight.colorArgb);
                  marker.setAttribute('data-cfi', locator.cfi || highlight.cfi || '');
                  return marker;
                });
                readerDesktopHighlightMapLog(
                  'web_text_fallback_result id=' + ((highlight && highlight.id) || '') +
                  ' applied=true text="' + readerTtsPreview(range.toString(), 120) + '"'
                );
                range.detach && range.detach();
                return true;
              }
              window.readerApplyHighlights = function (highlights) {
                var previousX = window.scrollX;
                var previousY = window.scrollY;
                readerCurrentHighlights = Array.isArray(highlights) ? highlights.slice() : [];
                unwrapReaderHighlights();
                if (readerCurrentHighlights.length > 0) {
                  readerCurrentHighlights
                    .slice()
                    .sort(function (a, b) {
                      var aStart = (a.locator && a.locator.startOffset) || 0;
                      var bStart = (b.locator && b.locator.startOffset) || 0;
                      return bStart - aStart;
                    })
                    .forEach(applyHighlightObject);
                }
                window.scrollTo({ top: previousY, left: previousX, behavior: 'auto' });
              };
              function scheduleReaderHighlightReconcile() {
                if (readerHighlightReconcileTimer !== null) window.clearTimeout(readerHighlightReconcileTimer);
                readerHighlightReconcileTimer = window.setTimeout(function () {
                  readerHighlightReconcileTimer = null;
                  if (window.readerApplyHighlights) window.readerApplyHighlights(readerCurrentHighlights);
                }, 1200);
              }
              var readerTtsLocator = null;
              var readerTtsOverlayTimer = null;
              function ensureTtsLayer() {
                var layer = document.getElementById('reader-tts-highlight-layer');
                if (!layer) {
                  layer = document.createElement('div');
                  layer.id = 'reader-tts-highlight-layer';
                  document.body.appendChild(layer);
                }
                return layer;
              }
              function clearTtsHighlight() {
                if (window.CSS && CSS.highlights && CSS.highlights.delete) {
                  CSS.highlights.delete('reader-tts-highlight');
                }
                var layer = document.getElementById('reader-tts-highlight-layer');
                if (layer) layer.innerHTML = '';
              }
              function paintTtsOverlay(range) {
                var layer = ensureTtsLayer();
                layer.innerHTML = '';
                var rects = Array.prototype.slice.call(range.getClientRects());
                var painted = 0;
                rects.forEach(function (rect) {
                  if (!rect || rect.width <= 0 || rect.height <= 0) return;
                  var marker = document.createElement('div');
                  marker.className = 'reader-tts-highlight-rect';
                  marker.style.left = (rect.left + window.scrollX) + 'px';
                  marker.style.top = (rect.top + window.scrollY) + 'px';
                  marker.style.width = rect.width + 'px';
                  marker.style.height = rect.height + 'px';
                  layer.appendChild(marker);
                  painted++;
                });
                readerTtsLog('overlay_paint rects=' + rects.length + ' painted=' + painted);
              }
              function applyTtsLocator(locator) {
                clearTtsHighlight();
                readerTtsLocator = locator || null;
                if (!readerTtsLocator) {
                  readerTtsLog('locator_clear');
                  return;
                }
                var chapterIndex = readerTtsLocator.chapterIndex;
                var startOffset = readerTtsLocator.startOffset;
                var endOffset = readerTtsLocator.endOffset;
                var sourceCfi = readerTtsLocator.cfi;
                readerTtsLog(
                  'locator_apply chapter=' + chapterIndex +
                  ' page=' + readerTtsLocator.pageIndex +
                  ' offsets=' + startOffset + '..' + endOffset +
                  ' cfi=' + readerTtsPreview(sourceCfi, 100) +
                  ' expected="' + readerTtsPreview(readerTtsLocator.textQuote, 140) + '"'
                );
                if (chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                  readerTtsLog('locator_ignored reason=invalid_locator');
                  return;
                }
                var range = rangeForOffsets(chapterIndex, startOffset, endOffset, sourceCfi, true);
                var expectedText = readerTtsLocator.textQuote;
                var expectedNormalized = readerTtsNormalized(expectedText);
                var actualNormalized = range && !range.collapsed ? readerTtsNormalized(range.toString()) : '';
                if (expectedNormalized) {
                  readerTtsLog(
                    'range_expected_compare expectedChars=' + expectedNormalized.length +
                    ' actualChars=' + actualNormalized.length +
                    ' exact=' + (actualNormalized === expectedNormalized) +
                    ' actual="' + readerTtsPreview(actualNormalized, 140) + '"'
                  );
                }
                if (expectedNormalized && (!range || range.collapsed || actualNormalized !== expectedNormalized)) {
                  var chapter = readerHostForLocator(chapterIndex, startOffset, endOffset);
                  var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : null;
                  var searchRoot = content;
                  if (content && sourceCfi) {
                    searchRoot = readerElementForSourceCfi(content, sourceCfi) || content;
                  }
                  var textRange = normalizedRangeForText(searchRoot, expectedNormalized, true);
                  if (textRange && !textRange.collapsed) {
                    if (range && range.detach) range.detach();
                    range = textRange;
                    readerTtsLog('range_expected_fallback used=true root=' + readerElementLabel(searchRoot));
                  } else {
                    readerTtsLog('range_expected_fallback used=false root=' + readerElementLabel(searchRoot));
                  }
                }
                if (!range || range.collapsed) {
                  readerTtsLog('locator_failed reason=' + (!range ? 'no_range' : 'collapsed_range'));
                  return;
                }
                if (window.CSS && window.Highlight && CSS.highlights && CSS.highlights.set) {
                  CSS.highlights.set('reader-tts-highlight', new Highlight(range));
                  readerTtsLog('css_highlight_set supported=true');
                } else {
                  readerTtsLog('css_highlight_set supported=false');
                }
                paintTtsOverlay(range);
              }
              window.readerSetTtsLocator = function (locator, follow) {
                try {
                  applyTtsLocator(locator);
                  if (follow && locator) scrollToLocator(locator, { align: 'center', trackRestore: false });
                } catch (error) {
                  readerTtsLog('locator_exception error=' + readerTtsPreview(error, 180));
                }
              };
              function refreshTtsHighlight() {
                if (!readerTtsLocator) return;
                applyTtsLocator(readerTtsLocator);
              }
              window.addEventListener('resize', function () {
                if (readerTtsOverlayTimer !== null) window.clearTimeout(readerTtsOverlayTimer);
                readerTtsOverlayTimer = window.setTimeout(refreshTtsHighlight, 80);
              });
              function highlightRange(colorId) {
                if (!restoreRange()) return;
                var selection = window.getSelection();
                if (!selection || selection.rangeCount === 0) return;
                var range = selection.getRangeAt(0);
                var text = selection.toString().trim();
                if (!text) return;
                readerHighlightFlowLog(
                  'selection_begin mode=' + selectionDebugMode() +
                  ' color=' + (colorId || 'yellow') +
                  ' textChars=' + text.length +
                  ' range=' + selectionDebugRange(range)
                );
                var segments = selectionSegmentsForRange(range);
                if (!segments.length) {
                  readerHighlightFlowLog(
                    'selection_segments_missing mode=' + selectionDebugMode() +
                    ' text="' + readerTtsPreview(text, 120) + '"' +
                    ' range=' + selectionDebugRange(range)
                  );
                  readerSelectionDebugLog('highlight_selection_segments_missing text="' + readerTtsPreview(text, 120) + '"');
                  return;
                }
                readerHighlightFlowLog(
                  'selection_segments count=' + segments.length +
                  ' details="' + segments.map(function (segment) {
                    return 'chapter=' + segment.chapterIndex +
                      ',page=' + segment.pageIndex +
                      ',offsets=' + segment.startOffset + '..' + segment.endOffset +
                      ',chars=' + segment.text.length;
                  }).join('; ') + '"'
                );
                var firstSegment = segments[0];
                var lastSegment = segments[segments.length - 1];
                var sameChapter = segments.every(function (segment) {
                  return segment.chapterIndex === firstSegment.chapterIndex;
                });
                var payloads = [];
                if (sameChapter) {
                  var chapterIndex = firstSegment.chapterIndex;
                  var startOffset = firstSegment.startOffset;
                  var endOffset = lastSegment.endOffset;
                  var pageIndex = firstSegment.pageIndex;
                  if (pageIndex < 0 && startOffset !== null) {
                    var anchorPage = pageForLocator(chapterIndex, startOffset);
                    if (anchorPage) pageIndex = anchorPage.pageIndex;
                  }
                  var cfi = readerHighlightCfiForRange(firstSegment, lastSegment, chapterIndex, startOffset, endOffset);
                  readerDesktopHighlightMapLog(
                    'web_payload_build sameChapter=true chapter=' + chapterIndex +
                    ' page=' + pageIndex +
                    ' offsets=' + startOffset + '..' + endOffset +
                    ' block=' + firstSegment.blockIndex +
                    ' char=' + firstSegment.charOffset +
                    ' textChars=' + text.length +
                    ' cfi=' + readerTtsPreview(cfi, 160)
                  );
                  payloads.push({
                    cfi: cfi,
                    text: text,
                    colorId: colorId || 'yellow',
                    chapterIndex: chapterIndex,
                    locator: {
                      chapterIndex: chapterIndex,
                      chapterId: firstSegment.chapterId,
                      href: firstSegment.chapterHref || null,
                      pageIndex: pageIndex >= 0 ? pageIndex : null,
                      startOffset: startOffset,
                      endOffset: endOffset,
                      blockIndex: firstSegment.blockIndex,
                      charOffset: firstSegment.charOffset,
                      textQuote: text,
                      cfi: cfi
                    }
                  });
                } else {
                  segments.forEach(function (segment) {
                    var cfi = readerHighlightCfiForRange(segment, segment, segment.chapterIndex, segment.startOffset, segment.endOffset);
                    readerDesktopHighlightMapLog(
                      'web_payload_build sameChapter=false chapter=' + segment.chapterIndex +
                      ' page=' + segment.pageIndex +
                      ' offsets=' + segment.startOffset + '..' + segment.endOffset +
                      ' block=' + segment.blockIndex +
                      ' char=' + segment.charOffset +
                      ' textChars=' + segment.text.length +
                      ' cfi=' + readerTtsPreview(cfi, 160)
                    );
                    payloads.push({
                      cfi: cfi,
                      text: segment.text,
                      colorId: colorId || 'yellow',
                      chapterIndex: segment.chapterIndex,
                      locator: {
                        chapterIndex: segment.chapterIndex,
                        chapterId: segment.chapterId,
                        href: segment.chapterHref || null,
                        pageIndex: segment.pageIndex >= 0 ? segment.pageIndex : null,
                        startOffset: segment.startOffset,
                        endOffset: segment.endOffset,
                        blockIndex: segment.blockIndex,
                        charOffset: segment.charOffset,
                        textQuote: segment.text,
                        cfi: cfi
                      }
                    });
                  });
                }
                readerHighlightFlowLog(
                  'payloads_built count=' + payloads.length +
                  ' sameChapter=' + sameChapter +
                  ' details="' + payloads.map(function (payload) {
                    var locator = payload.locator || {};
                    return 'cfi=' + readerTtsPreview(payload.cfi, 80) +
                      ',color=' + payload.colorId +
                      ',chapter=' + payload.chapterIndex +
                      ',page=' + locator.pageIndex +
                      ',offsets=' + locator.startOffset + '..' + locator.endOffset +
                      ',textChars=' + (payload.text || '').length;
                  }).join('; ') + '"'
                );
                try {
                  if (sameChapter) {
                    var payload = payloads[0];
                    var localRange = range.cloneRange ? range.cloneRange() : range;
                    var wrappedSingle = false;
                    try {
                      wrappedSingle = wrapRangeTextSegments(localRange, function () {
                        var marker = createReaderHighlightMarker(null, colorId || 'yellow', payload.locator.startOffset, payload.locator.endOffset, null);
                        marker.setAttribute('data-cfi', payload.cfi);
                        return marker;
                      });
                    } finally {
                      if (localRange !== range && localRange.detach) localRange.detach();
                    }
                    readerHighlightFlowLog(
                      'local_wrap_done sameChapter=true wrapped=' + wrappedSingle +
                      ' cfi="' + readerTtsPreview(payload.cfi, 120) + '"'
                    );
                  } else {
                    segments.forEach(function (segment, index) {
                      var payload = payloads[index];
                      var wrappedSegment = wrapRangeTextSegments(segment.range, function () {
                        var marker = createReaderHighlightMarker(null, colorId || 'yellow', segment.startOffset, segment.endOffset, null);
                        marker.setAttribute('data-cfi', payload.cfi);
                        return marker;
                      });
                      readerHighlightFlowLog(
                        'local_wrap_done sameChapter=false segment=' + index +
                        ' wrapped=' + wrappedSegment +
                        ' cfi="' + readerTtsPreview(payload.cfi, 120) + '"'
                      );
                    });
                  }
                } catch (error) {
                  readerHighlightFlowLog('local_wrap_error error=' + readerTtsPreview(error, 180));
                  readerSelectionDebugLog('highlight_local_wrap_error error=' + readerTtsPreview(error, 180));
                } finally {
                  segments.forEach(function (segment) {
                    if (segment.range && segment.range.detach) segment.range.detach();
                  });
                }
                payloads.forEach(function (payload) {
                  if (payload.text.length > 0) sendReaderHighlightCreated(payload, 0);
                });
                readerHighlightFlowLog('selection_end sentPayloads=' + payloads.length);
                scheduleReaderHighlightReconcile();
                selection.removeAllRanges();
                hideMenu();
              }
              menu.addEventListener('mousedown', function (event) {
                event.preventDefault();
              });
              if (startHandle && endHandle) {
                startHandle.addEventListener('pointerdown', function (event) {
                  beginSelectionHandleDrag('start', event);
                });
                endHandle.addEventListener('pointerdown', function (event) {
                  beginSelectionHandleDrag('end', event);
                });
                [startHandle, endHandle].forEach(function (handle) {
                  handle.addEventListener('pointermove', function (event) {
                    if (!activeSelectionHandle) return;
                    event.preventDefault();
                    event.stopPropagation();
                    requestSelectionHandleUpdate(event);
                  });
                  handle.addEventListener('mousedown', function (event) {
                    event.preventDefault();
                    event.stopPropagation();
                  });
                  handle.addEventListener('pointerup', finishSelectionHandleDrag);
                  handle.addEventListener('pointercancel', finishSelectionHandleDrag);
                });
              }
              menu.addEventListener('click', function (event) {
                var target = event.target && event.target.closest ? event.target.closest('button[data-action]') : event.target;
                var action = target && target.getAttribute('data-action');
                var text = selectionText();
                if (!text && restoreRange()) text = selectionText();
                if (!text) {
                  hideMenu();
                  return;
                }
                if (action === 'copy') copyText(text);
                if (action === 'highlight') highlightRange(target.getAttribute('data-color-id') || 'yellow');
                if (action === 'palette') sendSelectionAction('palette', text);
                if (action === 'define') sendSelectionAction('define', text);
                if (action === 'speak') sendSelectionAction('speak', text);
                if (action === 'dictionary') sendSelectionAction('dictionary', text);
                if (action === 'translate') sendSelectionAction('translate', text);
                if (action === 'web-search') sendSelectionAction('web-search', text);
                if (action === 'note') sendSelectionAction('note', text);
                if (action === 'clear') {
                  window.getSelection().removeAllRanges();
                  hideMenu();
                }
                if (action !== 'highlight' && action !== 'clear') hideMenu();
              });
              document.addEventListener('contextmenu', function (event) {
                if (selectionText().length > 0) {
                  event.preventDefault();
                  showMenu(event);
                }
              });
              document.addEventListener('selectionchange', function () {
                if (menu.contains(document.activeElement)) return;
                if (activeSelectionHandle) return;
                if (selectionPointerDown) return;
                scheduleMenuFromSelection();
              });
              document.addEventListener('selectstart', function (event) {
                if (!activeSelectionHandle) return;
                event.preventDefault();
                event.stopPropagation();
              }, true);
              document.addEventListener('pointermove', function (event) {
                if (!activeSelectionHandle) return;
                event.preventDefault();
                event.stopPropagation();
                requestSelectionHandleUpdate(event);
              });
              document.addEventListener('pointerup', function (event) {
                if (activeSelectionHandle) {
                  finishSelectionHandleDrag(event);
                  return;
                }
                if (selectionPointerDown && !menu.contains(event.target)) {
                  selectionPointerDown = false;
                  scheduleMenuFromSelection();
                  scheduleVisiblePageReport();
                }
              });
              document.addEventListener('pointercancel', function () {
                if (!activeSelectionHandle) {
                  selectionPointerDown = false;
                  scheduleMenuFromSelection();
                  scheduleVisiblePageReport();
                }
              });
              document.addEventListener('mouseup', function (event) {
                if (menu.contains(event.target)) return;
                if (activeSelectionHandle) return;
                selectionPointerDown = false;
                scheduleMenuFromSelection();
                scheduleVisiblePageReport();
              });
              document.addEventListener('touchend', function (event) {
                if (menu.contains(event.target)) return;
                if (activeSelectionHandle) return;
                selectionPointerDown = false;
                scheduleMenuFromSelection();
                scheduleVisiblePageReport();
              }, { passive: true });
              document.addEventListener('touchcancel', function () {
                if (activeSelectionHandle) return;
                selectionPointerDown = false;
                scheduleMenuFromSelection();
                scheduleVisiblePageReport();
              }, { passive: true });
              document.addEventListener('keyup', function () {
                scheduleMenuFromSelection();
              });
              document.addEventListener('scroll', function () {
                if (!selectionPointerDown && !activeSelectionHandle) {
                  hideMenu();
                }
              }, true);
              document.addEventListener('scroll', scheduleVisiblePageReport, true);
              window.addEventListener('scroll', scheduleVisiblePageReport, { passive: true });
              window.addEventListener('wheel', function () { clearPendingRestoreLocator('wheel'); }, { passive: true });
              window.addEventListener('touchstart', function () { clearPendingRestoreLocator('touchstart'); }, { passive: true });
              window.addEventListener('keydown', function () { clearPendingRestoreLocator('keydown'); });
              document.addEventListener('pointerdown', function (event) {
                clearPendingRestoreLocator('pointerdown');
                if (event.button === 0 && !menu.contains(event.target)) {
                  selectionPointerDown = true;
                  hideMenu();
                }
              });
              document.addEventListener('mousedown', function (event) {
                if (event.button === 0 && !menu.contains(event.target)) {
                  selectionPointerDown = true;
                  hideMenu();
                }
              });
              scrollToActiveLocator();
              reportVisiblePage();
              window.setTimeout(function () { readerPaginationLayoutLog('initial_timeout'); }, 80);
              window.addEventListener('load', scrollToActiveLocator, { once: true });
              window.addEventListener('load', reportVisiblePage, { once: true });
              window.addEventListener('load', function () { readerPaginationLayoutLog('window_load'); }, { once: true });
            })();
          </script>
""".trimIndent()
