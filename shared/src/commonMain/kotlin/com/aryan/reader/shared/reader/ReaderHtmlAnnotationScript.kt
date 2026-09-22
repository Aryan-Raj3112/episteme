package com.aryan.reader.shared.reader

internal fun readerHtmlAnnotationScript(): String = """
              function wrapRangeTextSegments(range, markerFactory, shiftContext) {
                var segments = textSegmentsInRange(range);
                var shiftCtx = shiftContext || '';
                var shiftBeforeDoc = '';
                var shiftBeforeRange = '';
                var shiftBeforeBlocks = '';
                try {
                  if (window.readerHighlightShiftLog) {
                    shiftBeforeDoc = readerHighlightShiftDocSnapshot();
                    shiftBeforeRange = readerHighlightShiftRangeRects(range);
                    var seenBlocks = {};
                    var blockSnaps = [];
                    for (var bi = 0; bi < segments.length; bi++) {
                      var block = readerHighlightShiftBlockOf(segments[bi].node);
                      if (!block) continue;
                      var key = readerElementLabel(block);
                      if (seenBlocks[key]) continue;
                      seenBlocks[key] = true;
                      blockSnaps.push(readerHighlightShiftBlockSnapshot(block));
                      if (blockSnaps.length >= 3) break;
                    }
                    shiftBeforeBlocks = blockSnaps.join(' | ');
                    var segSnaps = [];
                    for (var si = 0; si < segments.length && si < 3; si++) {
                      try {
                        var segNode = segments[si].node;
                        var segParentLabel = readerElementLabel(segNode && segNode.parentElement);
                        var segSelected = readerTtsPreview((segNode.nodeValue || '').substring(segments[si].start, segments[si].end), 20);
                        segSnaps.push(segParentLabel + '[' + segments[si].start + '..' + segments[si].end + ']="' + segSelected + '"');
                      } catch (error) {}
                    }
                    var rangeText = '';
                    try { rangeText = readerTtsPreview(range.toString(), 80); } catch (error) {}
                    readerHighlightShiftLog('wrap_before', 'ctx=' + shiftCtx + ' segs=' + segments.length +
                      ' rangeRects=' + shiftBeforeRange + ' doc=' + shiftBeforeDoc +
                      ' text="' + rangeText + '" segDetail=[' + segSnaps.join(' | ') + ']' +
                      ' blocks=[' + shiftBeforeBlocks + ']');
                  }
                } catch (error) {}
                var wrapped = 0;
                var createdMarkers = [];
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
                  createdMarkers.push(marker);
                  if (segment.end < value.length) fragment.appendChild(document.createTextNode(value.substring(segment.end)));
                  parent.replaceChild(fragment, node);
                  wrapped++;
                }
                try {
                  if (window.readerHighlightShiftLog) {
                    var afterDoc = readerHighlightShiftDocSnapshot();
                    var markerSnaps = [];
                    for (var mi = 0; mi < createdMarkers.length && mi < 3; mi++) {
                      markerSnaps.push(readerHighlightShiftMarkerSnapshot(createdMarkers[mi]));
                    }
                    var afterBlocks = '';
                    try {
                      var afterSeen = {};
                      var afterSnaps = [];
                      for (var ai = 0; ai < createdMarkers.length; ai++) {
                        var afterBlock = readerHighlightShiftBlockOf(createdMarkers[ai]);
                        if (!afterBlock) continue;
                        var afterKey = readerElementLabel(afterBlock);
                        if (afterSeen[afterKey]) continue;
                        afterSeen[afterKey] = true;
                        afterSnaps.push(readerHighlightShiftBlockSnapshot(afterBlock));
                        if (afterSnaps.length >= 3) break;
                      }
                      afterBlocks = afterSnaps.join(' | ');
                    } catch (error) {}
                    readerHighlightShiftLog('wrap_after', 'ctx=' + shiftCtx + ' wrapped=' + wrapped +
                      ' markers=' + createdMarkers.length + ' docBefore=' + shiftBeforeDoc + ' docAfter=' + afterDoc +
                      ' rangeBefore=' + shiftBeforeRange +
                      ' markerSnaps=[' + markerSnaps.join(' | ') + ']' +
                      ' blocksBefore=[' + shiftBeforeBlocks + '] blocksAfter=[' + afterBlocks + ']');
                  }
                } catch (error) {}
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
              // Reflow-free user-highlight painting. Wrapping words in spans
              // perturbs justified line breaking at subpixel level (proven by
              // HIGHLIGHT_SHIFT logs: identical metrics, changed breaks, whole
              // paragraph reflow). CSS Custom Highlights paint without touching
              // the DOM, so layout cannot shift. DOM spans remain as fallback.
              var readerUserHighlightRegistryOk = null;
              var readerUserHighlightPaints = {};
              var readerUserHighlightGroups = {};
              var readerUserHighlightsPainted = {};
              function userHighlightRegistryUsable() {
                if (readerUserHighlightRegistryOk !== null) return readerUserHighlightRegistryOk;
                try {
                  readerUserHighlightRegistryOk = !!(
                    window.CSS && window.Highlight &&
                    window.CSS.highlights && window.CSS.highlights.set
                  );
                } catch (error) {
                  readerUserHighlightRegistryOk = false;
                }
                return readerUserHighlightRegistryOk;
              }
              function readerUserHighlightPaintName(colorId, styleId, colorArgb) {
                var color = String(colorId || 'yellow').toLowerCase().replace(/[^a-z0-9]+/g, '') || 'yellow';
                var style = String(styleId || 'background').toLowerCase().replace(/[^a-z0-9]+/g, '') || 'background';
                var suffix = '';
                try {
                  if (colorArgb !== undefined && colorArgb !== null && Number.isFinite(Number(colorArgb))) {
                    suffix = '-c' + ((Number(colorArgb) >>> 0) & 0xFFFFFF).toString(16);
                  }
                } catch (error) {}
                return 'reader-hl-' + color + '-' + style + suffix;
              }
              function readerUserHighlightPaintColor(colorId, colorArgb) {
                try {
                  if (colorArgb !== undefined && colorArgb !== null && Number.isFinite(Number(colorArgb))) {
                    var rgb = (Number(colorArgb) >>> 0) & 0xFFFFFF;
                    return '#' + rgb.toString(16).padStart(6, '0').toUpperCase();
                  }
                } catch (error) {}
                try {
                  var probe = document.createElement('span');
                  probe.className = 'reader-user-highlight user-highlight-' + (colorId || 'yellow');
                  probe.setAttribute('style', 'position:absolute !important; visibility:hidden !important;');
                  probe.textContent = 'x';
                  document.body.appendChild(probe);
                  var bg = window.getComputedStyle ? window.getComputedStyle(probe).getPropertyValue('background-color') : '';
                  if (probe.parentNode) probe.parentNode.removeChild(probe);
                  if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return bg;
                } catch (error) {}
                return '';
              }
              function readerEnsureUserHighlightPaint(paintName, styleId, colorCss) {
                if (readerUserHighlightPaints[paintName]) return true;
                try {
                  var styleEl = document.getElementById('reader-user-highlight-paint');
                  if (!styleEl) {
                    styleEl = document.createElement('style');
                    styleEl.id = 'reader-user-highlight-paint';
                    document.head.appendChild(styleEl);
                  }
                  var style = String(styleId || 'background');
                  var rule = '';
                  if (style === 'underline' || style === 'wavy_underline') {
                    rule = '::highlight(' + paintName + ') { background-color: transparent; ' +
                      'text-decoration-line: underline !important; ' +
                      'text-decoration-style: ' + (style === 'wavy_underline' ? 'wavy' : 'solid') + ' !important;' +
                      (colorCss ? ' text-decoration-color: ' + colorCss + ' !important;' : '') + ' }';
                  } else if (style === 'strikethrough') {
                    rule = '::highlight(' + paintName + ') { background-color: transparent; ' +
                      'text-decoration-line: line-through !important; text-decoration-style: solid !important;' +
                      (colorCss ? ' text-decoration-color: ' + colorCss + ' !important;' : '') + ' }';
                  } else {
                    rule = '::highlight(' + paintName + ') { ' +
                      (colorCss ? 'background-color: ' + colorCss + ' !important;' : '') + ' }';
                  }
                  styleEl.sheet.insertRule(rule, styleEl.sheet.cssRules.length);
                  readerUserHighlightPaints[paintName] = true;
                  return true;
                } catch (error) {
                  readerUserHighlightRegistryOk = false;
                  try {
                    if (window.readerHighlightShiftLog) readerHighlightShiftLog('registry_unavailable_fallback', 'reason=paint_rule_failed');
                  } catch (ignored) {}
                  return false;
                }
              }
              function paintedEntryLive(entry) {
                try {
                  if (!entry || !entry.ranges || !entry.ranges.length) return false;
                  for (var i = 0; i < entry.ranges.length; i++) {
                    var r = entry.ranges[i];
                    if (!r || r.collapsed) return false;
                    if (r.startContainer && r.startContainer.isConnected === false) return false;
                  }
                  return true;
                } catch (error) {
                  return false;
                }
              }
              function paintedEntryMatches(entry, highlight) {
                var locator = (highlight && highlight.locator) || {};
                if ((entry.styleId || 'background') !== (highlight.style || 'background')) return false;
                if ((entry.colorId || 'yellow') !== (highlight.colorId || 'yellow')) return false;
                var s = locator.startOffset;
                var e = locator.endOffset;
                if (s === undefined || s === null || e === undefined || e === null) return true;
                return String(entry.startOffset) === String(s) && String(entry.endOffset) === String(e);
              }
              function unpaintUserHighlightByKey(key) {
                var entry = readerUserHighlightsPainted[key];
                if (!entry) return;
                try {
                  var group = readerUserHighlightGroups[entry.paintName];
                  if (group) {
                    for (var i = 0; i < entry.ranges.length; i++) {
                      try { group.delete(entry.ranges[i]); } catch (error) {}
                    }
                    var empty = true;
                    try { empty = group.size === 0; } catch (error) {}
                    if (empty) {
                      try { window.CSS.highlights.delete(entry.paintName); } catch (error) {}
                      delete readerUserHighlightGroups[entry.paintName];
                    }
                  }
                } catch (error) {}
                delete readerUserHighlightsPainted[key];
              }
              function paintRangeWithUserHighlightRegistry(range, p) {
                try {
                  if (!range || range.collapsed) return false;
                  if (!userHighlightRegistryUsable()) return wrapRangeTextSegments(range, p.markerFactory, p.ctx);
                  readerEnsureUserHighlightPaint(p.paintName, p.styleId, p.colorCss);
                  if (!readerUserHighlightPaints[p.paintName]) {
                    return wrapRangeTextSegments(range, p.markerFactory, p.ctx);
                  }
                  var cloned = range.cloneRange();
                  var group = readerUserHighlightGroups[p.paintName];
                  if (!group) {
                    group = new Highlight();
                    readerUserHighlightGroups[p.paintName] = group;
                    window.CSS.highlights.set(p.paintName, group);
                  }
                  group.add(cloned);
                  var entry = readerUserHighlightsPainted[p.key];
                  if (!entry) {
                    entry = {
                      paintName: p.paintName, ranges: [], spans: [],
                      chapterIndex: p.chapterIndex, startOffset: p.startOffset, endOffset: p.endOffset,
                      cfi: p.cfi, colorId: p.colorId, styleId: p.styleId,
                      temp: !p.realId, id: p.realId || ''
                    };
                    readerUserHighlightsPainted[p.key] = entry;
                  }
                  entry.ranges.push(cloned);
                  entry.spans.push({ chapterIndex: p.chapterIndex, startOffset: p.startOffset, endOffset: p.endOffset });
                  try {
                    if (window.readerHighlightShiftLog) {
                      readerHighlightShiftLog('registry_paint', 'key=' + p.key +
                        ' style=' + p.styleId + ' color=' + p.colorId +
                        ' chapter=' + p.chapterIndex + ' offsets=' + p.startOffset + '..' + p.endOffset +
                        ' rangeRects=' + readerHighlightShiftRangeRects(cloned) +
                        ' doc=' + readerHighlightShiftDocSnapshot());
                    }
                  } catch (error) {}
                  return true;
                } catch (error) {
                  try { readerUserHighlightRegistryOk = false; } catch (ignored) {}
                  return wrapRangeTextSegments(range, p.markerFactory, p.ctx);
                }
              }
              function paintParamsForHighlight(highlight, chapterIndex, segStart, segEnd, key, realId, markerFactory, ctx) {
                var colorCss = readerUserHighlightPaintColor(highlight.colorId || 'yellow', highlight.colorArgb);
                return {
                  paintName: readerUserHighlightPaintName(highlight.colorId || 'yellow', highlight.style || 'background', highlight.colorArgb),
                  key: key, realId: !!realId,
                  chapterIndex: (chapterIndex === undefined || chapterIndex === null) ? highlight.chapterIndex : chapterIndex,
                  startOffset: segStart, endOffset: segEnd,
                  cfi: ((highlight.locator || {}).cfi || highlight.cfi || ''),
                  colorId: highlight.colorId || 'yellow', styleId: highlight.style || 'background',
                  colorCss: colorCss, markerFactory: markerFactory, ctx: ctx
                };
              }
              function findIncomingForPaintedEntry(incoming, entry) {
                for (var i = 0; i < incoming.length; i++) {
                  var candidate = incoming[i];
                  if (!candidate) continue;
                  if (String(candidate.cfi || '') !== String(entry.cfi || '')) continue;
                  var locator = candidate.locator || {};
                  if ((candidate.style || 'background') !== (entry.styleId || 'background')) continue;
                  if ((candidate.colorId || 'yellow') !== (entry.colorId || 'yellow')) continue;
                  return candidate;
                }
                return null;
              }
              function cfiPresentInIncoming(incoming, entry) {
                for (var i = 0; i < incoming.length; i++) {
                  if (incoming[i] && String(incoming[i].cfi || '') === String(entry.cfi || '')) return true;
                }
                return false;
              }
              function reconcileUserHighlightRegistry(incoming) {
                var list = Array.isArray(incoming) ? incoming : [];
                var byId = {};
                list.forEach(function (h) { if (h && h.id) byId[h.id] = h; });
                Object.keys(readerUserHighlightsPainted).forEach(function (key) {
                  var entry = readerUserHighlightsPainted[key];
                  if (!entry) return;
                  if (entry.temp) {
                    var match = findIncomingForPaintedEntry(list, entry);
                    if (match && match.id) {
                      delete readerUserHighlightsPainted[key];
                      entry.temp = false;
                      entry.id = match.id;
                      readerUserHighlightsPainted[match.id] = entry;
                      try {
                        if (window.readerHighlightShiftLog) readerHighlightShiftLog('registry_adopt', 'temp=' + key + ' id=' + match.id);
                      } catch (error) {}
                    } else if (!cfiPresentInIncoming(list, entry)) {
                      unpaintUserHighlightByKey(key);
                    }
                  } else {
                    var current = byId[key];
                    if (!current) {
                      unpaintUserHighlightByKey(key);
                    } else if (!paintedEntryLive(entry) || !paintedEntryMatches(entry, current)) {
                      unpaintUserHighlightByKey(key);
                      applyHighlightObject(current);
                    }
                  }
                });
                list.forEach(function (h) {
                  if (h && h.id && !readerUserHighlightsPainted[h.id]) applyHighlightObject(h);
                });
              }
              function userHighlightIdFromPoint(x, y) {
                try {
                  var range = null;
                  if (document.caretRangeFromPoint) {
                    range = document.caretRangeFromPoint(x, y);
                  } else if (document.caretPositionFromPoint) {
                    var pos = document.caretPositionFromPoint(x, y);
                    if (pos && pos.offsetNode) {
                      var len = (pos.offsetNode.nodeValue || '').length;
                      var start = Math.max(0, Math.min(pos.offset, len));
                      range = document.createRange();
                      range.setStart(pos.offsetNode, start);
                      range.setEnd(pos.offsetNode, Math.min(len, start + 1));
                    }
                  }
                  if (!range) return '';
                  if (range.collapsed) {
                    try {
                      var node = range.startContainer;
                      var textLen = (node.nodeValue || '').length;
                      var probeStart = range.startOffset;
                      if (probeStart >= textLen && textLen > 0) probeStart = textLen - 1;
                      var probe = document.createRange();
                      probe.setStart(node, probeStart);
                      probe.setEnd(node, Math.min(textLen, probeStart + 1));
                      if (!probe.collapsed) range = probe;
                      else return '';
                    } catch (error) {
                      return '';
                    }
                  }
                  var contents = Array.prototype.slice.call(document.querySelectorAll('.page[data-reader-page-index] .reader-content'));
                  if (!contents.length) {
                    contents = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index] .reader-content'));
                  }
                  var bestId = '';
                  var bestSpan = Number.MAX_SAFE_INTEGER;
                  contents.forEach(function (content) {
                    var segmentRange = null;
                    try {
                      segmentRange = clippedRangeForContent(content, range);
                      if (!segmentRange || segmentRange.collapsed) return;
                      var offsets = rangeOffsetsWithinContent(content, segmentRange);
                      if (offsets.start === null || offsets.end === null) return;
                      var readerHost = content.closest ? content.closest('[data-reader-chapter-index]') : null;
                      var chapterIndex = readerHost ? parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10) : 0;
                      Object.keys(readerUserHighlightsPainted).forEach(function (key) {
                        var entry = readerUserHighlightsPainted[key];
                        if (!entry || entry.temp || !entry.id || !entry.spans) return;
                        entry.spans.forEach(function (span) {
                          if (span.chapterIndex !== undefined && span.chapterIndex !== null && span.chapterIndex !== chapterIndex) return;
                          if (offsets.start >= span.startOffset && offsets.start < span.endOffset) {
                            var width = span.endOffset - span.startOffset;
                            if (width < bestSpan) {
                              bestSpan = width;
                              bestId = entry.id;
                            }
                          }
                        });
                      });
                    } finally {
                      if (segmentRange && segmentRange.detach) segmentRange.detach();
                    }
                  });
                  return bestId;
                } catch (error) {
                  return '';
                }
              }
              function adoptServerRenderedHighlightMarkers(incoming) {
                var spans = Array.prototype.slice.call(document.querySelectorAll('span[data-reader-highlight-id], span[data-cfi].reader-user-highlight, span[data-cfi][class*="user-highlight-"]'));
                if (!spans.length) return;
                var list = Array.isArray(incoming) ? incoming : [];
                var byId = {};
                list.forEach(function (h) { if (h && h.id) byId[h.id] = h; });
                spans.forEach(function (marker) {
                  try {
                    var id = marker.getAttribute('data-reader-highlight-id') || '';
                    var cfi = marker.getAttribute('data-cfi') || '';
                    var highlight = (id && byId[id]) || null;
                    if (!highlight) {
                      for (var i = 0; i < list.length; i++) {
                        var candidate = list[i];
                        if (candidate && candidate.cfi && String(candidate.cfi) === String(cfi) &&
                          markerMatchesHighlight(marker, candidate)) {
                          highlight = candidate;
                          break;
                        }
                      }
                    }
                    var range = document.createRange();
                    range.selectNodeContents(marker);
                    if (highlight && highlight.id && !range.collapsed) {
                      var locator = highlight.locator || {};
                      var colorCss = readerUserHighlightPaintColor(highlight.colorId || 'yellow', highlight.colorArgb);
                      var paintName = readerUserHighlightPaintName(highlight.colorId || 'yellow', highlight.style || 'background', highlight.colorArgb);
                      readerEnsureUserHighlightPaint(paintName, highlight.style || 'background', colorCss);
                      if (readerUserHighlightPaints[paintName]) {
                        var cloned = range.cloneRange();
                        var group = readerUserHighlightGroups[paintName];
                        if (!group) {
                          group = new Highlight();
                          readerUserHighlightGroups[paintName] = group;
                          window.CSS.highlights.set(paintName, group);
                        }
                        group.add(cloned);
                        readerUserHighlightsPainted[highlight.id] = {
                          paintName: paintName, ranges: [cloned],
                          spans: [{ chapterIndex: locator.chapterIndex, startOffset: locator.startOffset, endOffset: locator.endOffset }],
                          chapterIndex: locator.chapterIndex, startOffset: locator.startOffset, endOffset: locator.endOffset,
                          cfi: highlight.cfi || '', colorId: highlight.colorId || 'yellow',
                          styleId: highlight.style || 'background', temp: false, id: highlight.id
                        };
                      }
                    }
                    var parent = marker.parentNode;
                    if (parent) {
                      while (marker.firstChild) parent.insertBefore(marker.firstChild, marker);
                      parent.removeChild(marker);
                      parent.normalize();
                    }
                    if (range.detach) range.detach();
                  } catch (error) {}
                });
                try {
                  if (window.readerHighlightShiftLog) {
                    readerHighlightShiftLog('registry_adopt_server', 'converted=' + spans.length +
                      ' doc=' + readerHighlightShiftDocSnapshot());
                  }
                } catch (error) {}
              }
              function applyHighlightObject(highlight) {
                if (!highlight) return;
                var locator = highlight.locator || {};
                if (userHighlightRegistryUsable()) {
                  var painted = highlight.id ? readerUserHighlightsPainted[highlight.id] : null;
                  if (painted && paintedEntryLive(painted) && paintedEntryMatches(painted, highlight)) {
                    readerDesktopHighlightMapLog('web_apply_skip_painted id=' + highlight.id);
                    return;
                  }
                  if (painted) unpaintUserHighlightByKey(highlight.id);
                }
                if (highlight.id) {
                  // Android parity: never re-derive a highlight that is already painted
                  // correctly — re-wrapping from stored offsets can drift the span.
                  var selector = 'span[data-reader-highlight-id]';
                  var allPainted = document.querySelectorAll(selector);
                  var paintedMarkers = [];
                  for (var paintIndex = 0; paintIndex < allPainted.length; paintIndex++) {
                    if (allPainted[paintIndex].getAttribute('data-reader-highlight-id') === highlight.id) {
                      paintedMarkers.push(allPainted[paintIndex]);
                    }
                  }
                  if (paintedMarkers.length > 0) {
                    var allMatch = true;
                    for (var matchIndex = 0; matchIndex < paintedMarkers.length; matchIndex++) {
                      if (!markerMatchesHighlight(paintedMarkers[matchIndex], highlight)) {
                        allMatch = false;
                        break;
                      }
                    }
                    if (allMatch) {
                      readerDesktopHighlightMapLog('web_apply_skip_painted id=' + highlight.id);
                      return;
                    }
                    paintedMarkers.forEach(function (marker) {
                      var parent = marker.parentNode;
                      if (!parent) return;
                      while (marker.firstChild) parent.insertBefore(marker.firstChild, marker);
                      parent.removeChild(marker);
                      parent.normalize();
                    });
                  }
                }
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
                try {
                  if (window.readerHighlightShiftLog) {
                    readerHighlightShiftLog('apply_create',
                      'id=' + (highlight.id || '') + ' style=' + (highlight.style || 'background') +
                      ' color=' + (highlight.colorId || 'yellow') + ' chapter=' + chapterIndex +
                      ' offsets=' + startOffset + '..' + endOffset +
                      ' textChars=' + String(expectedText || '').length +
                      ' text="' + readerTtsPreview(expectedText, 80) + '"' +
                      ' doc=' + readerHighlightShiftDocSnapshot());
                  }
                } catch (error) {}
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
                  paintRangeWithUserHighlightRegistry(range, paintParamsForHighlight(
                    highlight, chapterIndex, segmentStart, segmentEnd, highlight.id || ('cfi:' + (sourceCfi || highlight.cfi || '')),
                    highlight.id,
                    function () {
                      var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', segmentStart, segmentEnd, highlight.colorArgb, highlight.style || 'background');
                      marker.setAttribute('data-cfi', sourceCfi || highlight.cfi || ('desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset));
                      return marker;
                    },
                    'apply id=' + (highlight.id || '') + ' style=' + (highlight.style || 'background') + ' seg=' + segmentStart + '..' + segmentEnd
                  ));
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
                paintRangeWithUserHighlightRegistry(range, paintParamsForHighlight(
                  highlight, chapterIndex, null, null, highlight.id || ('cfi:' + (locator.cfi || highlight.cfi || '')),
                  highlight.id,
                  function () {
                    var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', null, null, highlight.colorArgb, highlight.style || 'background');
                    marker.setAttribute('data-cfi', locator.cfi || highlight.cfi || '');
                    return marker;
                  },
                  'fallback id=' + ((highlight && highlight.id) || '') + ' style=' + ((highlight && highlight.style) || 'background')
                ));
                readerDesktopHighlightMapLog(
                  'web_text_fallback_result id=' + ((highlight && highlight.id) || '') +
                  ' applied=true text="' + readerTtsPreview(range.toString(), 120) + '"'
                );
                range.detach && range.detach();
                return true;
              }
              function readerHighlightColorIdFromMarker(marker) {
                var match = String(marker.className || '').match(/user-highlight-([a-z]+)/);
                return match ? match[1] : '';
              }
              function markerMatchesHighlight(marker, highlight) {
                var locator = highlight.locator || {};
                if ((marker.getAttribute('data-reader-highlight-style') || 'background') !== (highlight.style || 'background')) return false;
                if (readerHighlightColorIdFromMarker(marker) !== (highlight.colorId || 'yellow')) return false;
                var start = locator.startOffset;
                var end = locator.endOffset;
                if (start === undefined || start === null || end === undefined || end === null) return true;
                return String(marker.getAttribute('data-reader-start-offset') || '') === String(start) &&
                  String(marker.getAttribute('data-reader-end-offset') || '') === String(end);
              }
              window.readerApplyHighlights = function (highlights) {
                var previousX = window.scrollX;
                var previousY = window.scrollY;
                try {
                  if (window.readerHighlightShiftLog) {
                    var shiftCount = Array.isArray(highlights) ? highlights.length : -1;
                    readerHighlightShiftLog('reconcile_before',
                      'incoming=' + shiftCount + ' doc=' + readerHighlightShiftDocSnapshot());
                  }
                } catch (error) {}
                readerCurrentHighlights = Array.isArray(highlights) ? highlights.slice() : [];
                window.readerCurrentHighlightsSnapshot = function () {
                  return readerCurrentHighlights.slice();
                };
                var incomingIds = {};
                readerCurrentHighlights.forEach(function (highlight) {
                  if (highlight && highlight.id) incomingIds[highlight.id] = highlight;
                });
                if (userHighlightRegistryUsable()) {
                  adoptServerRenderedHighlightMarkers(readerCurrentHighlights);
                  reconcileUserHighlightRegistry(readerCurrentHighlights);
                  try {
                    if (window.readerHighlightShiftLog) {
                      readerHighlightShiftLog('reconcile_registry_done',
                        'painted=' + Object.keys(readerUserHighlightsPainted).length +
                        ' doc=' + readerHighlightShiftDocSnapshot());
                    }
                  } catch (error) {}
                } else {
                // Selective reconcile (Android parity: Android's WebView mutates the live
                // DOM on highlight changes and never repaints from stored offsets). Keep
                // markers that already match, so a just-painted selection is never
                // re-derived and shifted; unwrap everything else.
                var marks = Array.prototype.slice.call(document.querySelectorAll('span[data-reader-highlight-id], span[data-cfi].reader-user-highlight, span[data-cfi][class*="user-highlight-"]'));
                marks.forEach(function (marker) {
                  var id = marker.getAttribute('data-reader-highlight-id');
                  var highlight = id ? incomingIds[id] : null;
                  if (!highlight) {
                    highlight = readerCurrentHighlights.find(function (candidate) {
                      return candidate && candidate.cfi &&
                        String(candidate.cfi) === String(marker.getAttribute('data-cfi') || '') &&
                        markerMatchesHighlight(marker, candidate);
                    }) || null;
                  }
                  if (highlight) {
                    if (highlight.id && !id) marker.setAttribute('data-reader-highlight-id', highlight.id);
                    return;
                  }
                  var parent = marker.parentNode;
                  if (!parent) return;
                  while (marker.firstChild) parent.insertBefore(marker.firstChild, marker);
                  parent.removeChild(marker);
                  parent.normalize();
                });
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
                }
                try {
                  if (window.readerHighlightShiftLog) {
                    readerHighlightShiftLog('reconcile_after',
                      'restoredScroll=' + previousX + ',' + previousY +
                      ' doc=' + readerHighlightShiftDocSnapshot());
                  }
                } catch (error) {}
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
                var range = trimRangeWhitespace(selection.getRangeAt(0)) || selection.getRangeAt(0);
                var text = range.toString().trim();
                if (!text) return;
                var styleId = readerSelectedHighlightStyle();
                readerHighlightFlowLog(
                  'selection_begin mode=' + selectionDebugMode() +
                  ' color=' + (colorId || 'yellow') +
                  ' style=' + styleId +
                  ' textChars=' + text.length +
                  ' range=' + selectionDebugRange(range)
                );
                try {
                  if (window.readerHighlightShiftLog) {
                    readerHighlightShiftLog('create_before',
                      'color=' + (colorId || 'yellow') + ' style=' + styleId +
                      ' textChars=' + text.length + ' text="' + readerTtsPreview(text, 80) + '"' +
                      ' rangeRects=' + readerHighlightShiftRangeRects(range) +
                      ' doc=' + readerHighlightShiftDocSnapshot());
                  }
                } catch (error) {}
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
                    styleId: styleId,
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
                      styleId: styleId,
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
                      var localPaint = {
                        colorId: colorId || 'yellow', style: styleId, colorArgb: null,
                        cfi: payload.cfi, chapterIndex: chapterIndex
                      };
                      wrappedSingle = paintRangeWithUserHighlightRegistry(localRange, paintParamsForHighlight(
                        localPaint, chapterIndex, payload.locator.startOffset, payload.locator.endOffset,
                        'local:' + payload.cfi, false,
                        function () {
                          var marker = createReaderHighlightMarker(null, colorId || 'yellow', payload.locator.startOffset, payload.locator.endOffset, null, styleId);
                          marker.setAttribute('data-cfi', payload.cfi);
                          return marker;
                        },
                        'create color=' + (colorId || 'yellow') + ' style=' + styleId
                      ));
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
                      var localSegPaint = {
                        colorId: colorId || 'yellow', style: styleId, colorArgb: null,
                        cfi: payload.cfi, chapterIndex: segment.chapterIndex
                      };
                      var wrappedSegment = paintRangeWithUserHighlightRegistry(segment.range, paintParamsForHighlight(
                        localSegPaint, segment.chapterIndex, segment.startOffset, segment.endOffset,
                        'local:' + payload.cfi, false,
                        function () {
                          var marker = createReaderHighlightMarker(null, colorId || 'yellow', segment.startOffset, segment.endOffset, null, styleId);
                          marker.setAttribute('data-cfi', payload.cfi);
                          return marker;
                        },
                        'create-multiseg color=' + (colorId || 'yellow') + ' style=' + styleId + ' seg=' + index
                      ));
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
                if (action === 'select-style') {
                  readerSelectedHighlightStyleId = target.getAttribute('data-style-id') || 'background';
                  syncReaderStyleSelection();
                  return;
                }
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
