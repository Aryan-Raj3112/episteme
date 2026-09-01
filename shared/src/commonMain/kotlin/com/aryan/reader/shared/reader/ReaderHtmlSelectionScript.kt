package com.aryan.reader.shared.reader

internal fun readerHtmlSelectionScript(): String = """
              function sendReaderHighlightClick(highlightId) {
                if (!highlightId) return false;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerHighlightClicked', JSON.stringify({ id: highlightId }));
                    return true;
                  } catch (error) {
                    readerConsoleLog('READER_HIGHLIGHT bridge_error id=' + highlightId + ' error=' + error);
                  }
                }
                return false;
              }
              function sendReaderHighlightCreated(payload, attempt) {
                attempt = attempt || 0;
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerHighlightCreated', JSON.stringify(payload));
                    readerHighlightFlowLog(
                      'bridge_send_success attempt=' + attempt +
                      ' cfi="' + readerTtsPreview(payload && payload.cfi, 120) + '"' +
                      ' color=' + ((payload && payload.colorId) || 'null') +
                      ' chapter=' + ((payload && payload.chapterIndex) !== undefined ? payload.chapterIndex : 'null') +
                      ' textChars=' + (((payload && payload.text) || '').length)
                    );
                    return true;
                  } catch (error) {
                    readerHighlightFlowLog('bridge_send_error attempt=' + attempt + ' error=' + readerTtsPreview(error, 180));
                    readerSelectionDebugLog('highlight_bridge_error attempt=' + attempt + ' error=' + readerTtsPreview(error, 180));
                  }
                }
                if (attempt < 3) {
                  window.setTimeout(function () {
                    sendReaderHighlightCreated(payload, attempt + 1);
                  }, attempt === 0 ? 80 : 240);
                  return true;
                }
                readerHighlightFlowLog('bridge_send_missing attempts=' + (attempt + 1));
                readerSelectionDebugLog('highlight_bridge_missing attempts=' + (attempt + 1));
                return false;
              }
              document.addEventListener('click', function (event) {
                var target = event.target;
                if (!target || !target.closest) return;
                var highlight = target.closest('.reader-user-highlight[data-reader-highlight-id], span[class*="user-highlight-"][data-reader-highlight-id]');
                if (highlight && !menu.contains(highlight)) {
                  var highlightId = highlight.getAttribute('data-reader-highlight-id') || '';
                  if (highlightId && sendReaderHighlightClick(highlightId)) {
                    event.preventDefault();
                    event.stopPropagation();
                    return;
                  }
                }
                var anchor = target.closest('a[href]');
                if (!anchor || menu.contains(anchor)) return;
                var href = anchor.getAttribute('href') || '';
                if (!href) return;
                var readerHost = nearestReaderHost(anchor);
                event.preventDefault();
                event.stopPropagation();
                sendReaderLinkClick({
                  href: href,
                  text: (anchor.textContent || '').trim().substring(0, 120),
                  chapterIndex: readerHost ? numberAttribute(readerHost, 'data-reader-chapter-index', null) : null,
                  chapterId: readerHost ? readerHost.getAttribute('data-reader-chapter-id') : null,
                  chapterHref: readerHost ? readerHost.getAttribute('data-reader-chapter-href') : null
                }, 0);
              }, true);
              function scheduleVisiblePageReport() {
                if (selectionPointerDown || activeSelectionHandle) {
                  if (reportTimer !== null) window.clearTimeout(reportTimer);
                  reportTimer = null;
                  return;
                }
                if (reportTimer !== null) return;
                reportTimer = window.setTimeout(function () {
                  reportTimer = null;
                  reportVisiblePage();
                }, 80);
              }
              function selectionText() {
                var selection = window.getSelection();
                return selection ? selection.toString().trim() : '';
              }
              function hideSelectionHandles() {
                [startHandle, endHandle].forEach(function (handle) {
                  if (!handle) return;
                  handle.hidden = true;
                  handle.style.display = 'none';
                });
              }
              function hideMenu() {
                if (selectionMenuTimer !== null) {
                  window.clearTimeout(selectionMenuTimer);
                  selectionMenuTimer = null;
                }
                menu.style.display = 'none';
                if (!activeSelectionHandle) hideSelectionHandles();
              }
              function selectionAnchorRect(selection) {
                if (!selection || selection.rangeCount === 0) return null;
                var range = selection.getRangeAt(0);
                var startRect = rangeBoundaryRect(range.startContainer, range.startOffset, false);
                var endRect = rangeBoundaryRect(range.endContainer, range.endOffset, true);
                if (startRect && endRect) {
                  var left = Math.min(startRect.left, endRect.left);
                  var top = Math.min(startRect.top, endRect.top);
                  var right = Math.max(startRect.right, endRect.right);
                  var bottom = Math.max(startRect.bottom, endRect.bottom);
                  return {
                    left: left,
                    top: top,
                    right: right,
                    bottom: bottom,
                    width: right - left,
                    height: bottom - top
                  };
                }
                return startRect || endRect || firstRangeRect(range, false);
              }
              function positionMenu(left, top, anchorRect) {
                menu.style.visibility = 'hidden';
                menu.style.display = 'flex';
                var margin = 8;
                var gap = 14;
                var viewportWidth = Math.max(0, window.innerWidth || 0);
                var viewportHeight = Math.max(0, window.innerHeight || 0);
                menu.style.maxHeight = Math.max(0, viewportHeight - margin * 2) + 'px';
                var menuWidth = menu.offsetWidth || 300;
                var menuHeight = menu.offsetHeight || 230;

                function clampMenuStart(preferred, size, viewportSize) {
                  if (viewportSize <= 0 || size <= 0) return 0;
                  if (viewportSize <= size) return 0;
                  var maxStart = viewportSize - size;
                  var minStart = Math.min(margin, maxStart);
                  var maxClampedStart = Math.max(minStart, viewportSize - size - margin);
                  return Math.max(minStart, Math.min(maxClampedStart, preferred));
                }
                function selectionMenuCandidate(x, y) {
                  return {
                    left: clampMenuStart(x, menuWidth, viewportWidth),
                    top: clampMenuStart(y, menuHeight, viewportHeight)
                  };
                }
                function overlapAreaWithSelection(candidate, rect) {
                  var overlapWidth = Math.min(candidate.left + menuWidth, rect.right) - Math.max(candidate.left, rect.left);
                  var overlapHeight = Math.min(candidate.top + menuHeight, rect.bottom) - Math.max(candidate.top, rect.top);
                  return Math.max(0, overlapWidth) * Math.max(0, overlapHeight);
                }
                function distanceFromSelection(candidate, rect) {
                  var dx = Math.max(rect.left - (candidate.left + menuWidth), candidate.left - rect.right, 0);
                  var dy = Math.max(rect.top - (candidate.top + menuHeight), candidate.top - rect.bottom, 0);
                  return dx * dx + dy * dy;
                }
                var rect = anchorRect ? {
                  left: Math.max(0, Math.min(viewportWidth, Math.min(anchorRect.left, anchorRect.right))),
                  top: Math.max(0, Math.min(viewportHeight, Math.min(anchorRect.top, anchorRect.bottom))),
                  right: Math.max(0, Math.min(viewportWidth, Math.max(anchorRect.left, anchorRect.right))),
                  bottom: Math.max(0, Math.min(viewportHeight, Math.max(anchorRect.top, anchorRect.bottom)))
                } : {
                  left: Math.max(0, Math.min(viewportWidth, left)),
                  top: Math.max(0, Math.min(viewportHeight, top)),
                  right: Math.max(0, Math.min(viewportWidth, left)),
                  bottom: Math.max(0, Math.min(viewportHeight, top))
                };
                var centerX = (rect.left + rect.right) / 2;
                var centerY = (rect.top + rect.bottom) / 2;
                var above = selectionMenuCandidate(centerX - menuWidth / 2, rect.top - gap - menuHeight);
                var below = selectionMenuCandidate(centerX - menuWidth / 2, rect.bottom + gap);
                var right = selectionMenuCandidate(rect.right + gap, centerY - menuHeight / 2);
                var leftSide = selectionMenuCandidate(rect.left - gap - menuWidth, centerY - menuHeight / 2);
                var nextLeft = above.left;
                var nextTop = above.top;
                if (above.top + menuHeight <= rect.top - gap && above.top >= margin) {
                  nextLeft = above.left;
                  nextTop = above.top;
                } else if (below.top >= rect.bottom + gap && below.top + menuHeight <= viewportHeight - margin) {
                  nextLeft = below.left;
                  nextTop = below.top;
                } else {
                  var leftSpace = rect.left - gap - margin;
                  var rightSpace = viewportWidth - rect.right - gap - margin;
                  var firstSide = rightSpace >= leftSpace ? right : leftSide;
                  var secondSide = rightSpace >= leftSpace ? leftSide : right;
                  var firstFits = firstSide === right
                    ? firstSide.left >= rect.right + gap
                    : firstSide.left + menuWidth <= rect.left - gap;
                  var secondFits = secondSide === right
                    ? secondSide.left >= rect.right + gap
                    : secondSide.left + menuWidth <= rect.left - gap;
                  if (firstFits && firstSide.top >= margin && firstSide.top + menuHeight <= viewportHeight - margin) {
                    nextLeft = firstSide.left;
                    nextTop = firstSide.top;
                  } else if (secondFits && secondSide.top >= margin && secondSide.top + menuHeight <= viewportHeight - margin) {
                    nextLeft = secondSide.left;
                    nextTop = secondSide.top;
                  } else {
                    var fallback = [above, below, firstSide, secondSide].sort(function (a, b) {
                      var overlapDelta = overlapAreaWithSelection(a, rect) - overlapAreaWithSelection(b, rect);
                      if (overlapDelta !== 0) return overlapDelta;
                      return distanceFromSelection(a, rect) - distanceFromSelection(b, rect);
                    })[0];
                    nextLeft = fallback.left;
                    nextTop = fallback.top;
                  }
                }
                menu.style.left = nextLeft + 'px';
                menu.style.top = nextTop + 'px';
                menu.style.visibility = 'visible';
              }
              function showSelectionHandle(handle, rect, x) {
                if (!handle || !rect) return;
                handle.hidden = false;
                handle.style.display = 'block';
                handle.style.left = (x - 12) + 'px';
                handle.style.top = rect.bottom + 'px';
              }
              function selectionDebugMode() {
                return document.body && document.body.classList.contains('reader-paginated') ? 'paginated' : 'vertical';
              }
              function selectionDebugRect(rect) {
                if (!rect) return 'none';
                return [
                  Math.round(rect.left),
                  Math.round(rect.top),
                  Math.round(rect.right),
                  Math.round(rect.bottom)
                ].join(',');
              }
              function selectionDebugNode(node) {
                if (!node) return 'null';
                var parent = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
                var label = readerElementLabel(parent);
                if (node.nodeType === Node.TEXT_NODE) label += ':text';
                return label.replace(/\s+/g, ' ').substring(0, 160);
              }
              function selectionDebugRange(range) {
                if (!range) return 'null';
                var startRect = rangeBoundaryRect(range.startContainer, range.startOffset, false);
                var endRect = rangeBoundaryRect(range.endContainer, range.endOffset, true);
                return 'collapsed=' + range.collapsed +
                  ' start=' + selectionDebugNode(range.startContainer) + '@' + range.startOffset +
                  ' startRect=' + selectionDebugRect(startRect) +
                  ' end=' + selectionDebugNode(range.endContainer) + '@' + range.endOffset +
                  ' endRect=' + selectionDebugRect(endRect);
              }
              function selectionDebugRangeTextLength(range) {
                if (!range) return -1;
                try { return range.toString().length; } catch (error) { return -1; }
              }
              function usableRangeRect(rect) {
                return rect && (rect.width > 0 || rect.height > 0) ? rect : null;
              }
              function firstRangeRect(range, preferLast) {
                var rects = Array.prototype.slice.call(range && range.getClientRects ? range.getClientRects() : []);
                rects = rects.filter(usableRangeRect);
                if (rects.length === 0) return null;
                return preferLast ? rects[rects.length - 1] : rects[0];
              }
              function rangeBoundaryRect(container, offset, preferPrevious) {
                if (!container) return null;
                var collapsed = document.createRange();
                try {
                  collapsed.setStart(container, offset);
                  collapsed.collapse(true);
                  var collapsedRect = firstRangeRect(collapsed, preferPrevious);
                  if (collapsedRect) return collapsedRect;
                } catch (error) {
                } finally {
                  collapsed.detach && collapsed.detach();
                }

                var expanded = document.createRange();
                try {
                  if (container.nodeType === Node.TEXT_NODE) {
                    var textLength = container.nodeValue ? container.nodeValue.length : 0;
                    var start = preferPrevious ? Math.max(0, offset - 1) : Math.min(offset, Math.max(0, textLength - 1));
                    var end = Math.min(textLength, start + 1);
                    if (end <= start && start > 0) {
                      start -= 1;
                      end = start + 1;
                    }
                    if (end > start) {
                      expanded.setStart(container, start);
                      expanded.setEnd(container, end);
                      return firstRangeRect(expanded, preferPrevious);
                    }
                  } else {
                    var childCount = container.childNodes ? container.childNodes.length : 0;
                    if (childCount > 0) {
                      var childIndex = preferPrevious ? Math.max(0, offset - 1) : Math.min(offset, childCount - 1);
                      expanded.selectNodeContents(container.childNodes[childIndex]);
                      return firstRangeRect(expanded, preferPrevious);
                    }
                  }
                } catch (error) {
                  return null;
                } finally {
                  expanded.detach && expanded.detach();
                }
                return null;
              }
              function positionSelectionHandles(selection) {
                if (!selection || selection.rangeCount === 0) return;
                var range = selection.getRangeAt(0);
                var first = rangeBoundaryRect(range.startContainer, range.startOffset, false) || firstRangeRect(range, false);
                var last = rangeBoundaryRect(range.endContainer, range.endOffset, true) || firstRangeRect(range, true);
                if (!first || !last) {
                  hideSelectionHandles();
                  return;
                }
                showSelectionHandle(startHandle, first, first.left);
                showSelectionHandle(endHandle, last, last.right);
              }
              function showMenu(event) {
                var selection = window.getSelection();
                if (!selection || selection.rangeCount === 0 || selectionText().length === 0) {
                  hideMenu();
                  return;
                }
                savedRange = selection.getRangeAt(0).cloneRange();
                if (savedRange.collapsed) {
                  hideMenu();
                  return;
                }
                var rect = event ? null : selectionAnchorRect(selection);
                positionMenu(event ? event.clientX : 0, event ? event.clientY : 0, rect);
                positionSelectionHandles(selection);
              }
              function scheduleMenuFromSelection() {
                if (selectionMenuTimer !== null) window.clearTimeout(selectionMenuTimer);
                selectionMenuTimer = window.setTimeout(function () {
                  selectionMenuTimer = null;
                  if (selectionPointerDown || activeSelectionHandle) return;
                  if (selectionText().length > 0) showMenu(null);
                  else hideMenu();
                }, 90);
              }
              function restoreRange() {
                if (!savedRange) return false;
                var selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(savedRange);
                return true;
              }
              function selectionChromeElement(node) {
                if (!node) return null;
                var element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
                if (!element || !element.closest) return null;
                return element.closest('#reader-selection-menu, .reader-selection-handle');
              }
              function rangeTouchesSelectionChrome(range) {
                return !!range && (!!selectionChromeElement(range.startContainer) || !!selectionChromeElement(range.endContainer));
              }
              function caretRangeFromPoint(clientX, clientY) {
                if (document.caretRangeFromPoint) {
                  var range = document.caretRangeFromPoint(clientX, clientY);
                  return rangeTouchesSelectionChrome(range) ? null : range;
                }
                if (document.caretPositionFromPoint) {
                  var position = document.caretPositionFromPoint(clientX, clientY);
                  if (!position) return null;
                  var range = document.createRange();
                  range.setStart(position.offsetNode, position.offset);
                  range.collapse(true);
                  return rangeTouchesSelectionChrome(range) ? null : range;
                }
                return null;
              }
              function selectionRangeForHandle(handleName, pointRange) {
                if (!savedRange || !pointRange) return null;
                if (rangeTouchesSelectionChrome(pointRange)) return null;
                var next = document.createRange();
                try {
                  if (handleName === 'start') {
                    next.setStart(pointRange.startContainer, pointRange.startOffset);
                    next.setEnd(savedRange.endContainer, savedRange.endOffset);
                  } else {
                    next.setStart(savedRange.startContainer, savedRange.startOffset);
                    next.setEnd(pointRange.startContainer, pointRange.startOffset);
                  }
                } catch (error) {
                  return null;
                }
                if (next.collapsed) return null;
                if (rangeTouchesSelectionChrome(next)) return null;
                return next;
              }
              function updateSelectionHandle(event) {
                if (!activeSelectionHandle) return;
                var pointRange = caretRangeFromPoint(event.clientX, event.clientY);
                if (!pointRange) {
                  var nowMissing = Date.now();
                  if (nowMissing - selectionDebugLastAt > 350) {
                    selectionDebugLastAt = nowMissing;
                    readerSelectionDebugLog(
                      'drag_point_missing seq=' + (++selectionDebugSequence) +
                      ' mode=' + selectionDebugMode() +
                      ' handle=' + activeSelectionHandle +
                      ' x=' + Math.round(event.clientX) +
                      ' y=' + Math.round(event.clientY) +
                      ' scrollY=' + Math.round(window.scrollY)
                    );
                  }
                  return;
                }
                var nextRange = selectionRangeForHandle(activeSelectionHandle, pointRange);
                if (!nextRange) {
                  var nowInvalid = Date.now();
                  if (nowInvalid - selectionDebugLastAt > 350) {
                    selectionDebugLastAt = nowInvalid;
                    readerSelectionDebugLog(
                      'drag_range_invalid seq=' + (++selectionDebugSequence) +
                      ' mode=' + selectionDebugMode() +
                      ' handle=' + activeSelectionHandle +
                      ' x=' + Math.round(event.clientX) +
                      ' y=' + Math.round(event.clientY) +
                      ' point=' + selectionDebugRange(pointRange) +
                      ' saved=' + selectionDebugRange(savedRange)
                    );
                  }
                  return;
                }
                var pointRect = rangeBoundaryRect(pointRange.startContainer, pointRange.startOffset, activeSelectionHandle === 'start');
                var lineKey = pointRect
                  ? [Math.round(pointRect.top), Math.round(pointRect.bottom)].join(':')
                  : 'none';
                var now = Date.now();
                var shouldLogLine = lineKey !== selectionDebugLastLineKey || now - selectionDebugLastAt > 650;
                var selection = window.getSelection();
                var previousRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0).cloneRange() : null;
                if (shouldLogLine) {
                  selectionDebugLastLineKey = lineKey;
                  selectionDebugLastAt = now;
                  readerSelectionDebugLog(
                    'drag_line seq=' + (++selectionDebugSequence) +
                    ' mode=' + selectionDebugMode() +
                    ' handle=' + activeSelectionHandle +
                    ' x=' + Math.round(event.clientX) +
                    ' y=' + Math.round(event.clientY) +
                    ' line=' + lineKey +
                    ' point=' + selectionDebugRange(pointRange) +
                    ' previous=' + selectionDebugRange(previousRange) +
                    ' next=' + selectionDebugRange(nextRange) +
                    ' nextChars=' + selectionDebugRangeTextLength(nextRange) +
                    ' scrollY=' + Math.round(window.scrollY)
                  );
                }
                savedRange = nextRange.cloneRange();
                selection.removeAllRanges();
                selection.addRange(savedRange);
                positionSelectionHandles(selection);
              }
              function requestSelectionHandleUpdate(event) {
                if (!activeSelectionHandle) return;
                pendingSelectionHandleEvent = { clientX: event.clientX, clientY: event.clientY };
                if (selectionHandleFrame !== null) return;
                selectionHandleFrame = window.requestAnimationFrame(function () {
                  selectionHandleFrame = null;
                  var pending = pendingSelectionHandleEvent;
                  pendingSelectionHandleEvent = null;
                  if (pending) updateSelectionHandle(pending);
                });
              }
              function cancelSelectionHandleFrame() {
                if (selectionHandleFrame !== null) {
                  window.cancelAnimationFrame(selectionHandleFrame);
                  selectionHandleFrame = null;
                }
                pendingSelectionHandleEvent = null;
              }
              function beginSelectionHandleDrag(handleName, event) {
                if (!savedRange && !restoreRange()) return;
                cancelSelectionHandleFrame();
                activeSelectionHandle = handleName;
                selectionPointerDown = true;
                menu.style.display = 'none';
                selectionDebugLastLineKey = null;
                selectionDebugLastAt = 0;
                readerSelectionDebugLog(
                  'drag_begin seq=' + (++selectionDebugSequence) +
                  ' mode=' + selectionDebugMode() +
                  ' handle=' + activeSelectionHandle +
                  ' x=' + Math.round(event.clientX) +
                  ' y=' + Math.round(event.clientY) +
                  ' saved=' + selectionDebugRange(savedRange) +
                  ' chars=' + selectionDebugRangeTextLength(savedRange) +
                  ' scrollY=' + Math.round(window.scrollY)
                );
                event.preventDefault();
                event.stopPropagation();
                if (event.currentTarget && event.currentTarget.setPointerCapture) {
                  try { event.currentTarget.setPointerCapture(event.pointerId); } catch (error) {}
                }
              }
              function finishSelectionHandleDrag(event) {
                if (!activeSelectionHandle) return;
                event.preventDefault();
                event.stopPropagation();
                cancelSelectionHandleFrame();
                updateSelectionHandle(event);
                readerSelectionDebugLog(
                  'drag_end seq=' + (++selectionDebugSequence) +
                  ' mode=' + selectionDebugMode() +
                  ' handle=' + activeSelectionHandle +
                  ' x=' + Math.round(event.clientX) +
                  ' y=' + Math.round(event.clientY) +
                  ' saved=' + selectionDebugRange(savedRange) +
                  ' chars=' + selectionDebugRangeTextLength(savedRange) +
                  ' scrollY=' + Math.round(window.scrollY)
                );
                activeSelectionHandle = null;
                selectionPointerDown = false;
                scheduleMenuFromSelection();
              }
              function nativeClipboardCopy(text) {
                if (!window.kmpJsBridge || !window.kmpJsBridge.callNative) return null;
                try {
                  var response = window.kmpJsBridge.callNative(
                    'readerCopyText',
                    JSON.stringify({ text: text })
                  );
                  if (response === true || response === 'true') return true;
                  if (response === false || response === 'false') return false;
                } catch (error) {
                  readerConsoleLog('READER_COPY native_error=' + readerTtsPreview(error, 180));
                  return false;
                }
                return null;
              }
              function execCommandClipboardCopy(text) {
                var textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.setAttribute('readonly', 'true');
                textarea.style.position = 'fixed';
                textarea.style.left = '-9999px';
                document.body.appendChild(textarea);
                textarea.select();
                var copied = false;
                try {
                  copied = document.execCommand('copy') === true;
                } catch (error) {
                  readerConsoleLog('READER_COPY exec_error=' + readerTtsPreview(error, 180));
                }
                document.body.removeChild(textarea);
                return copied;
              }
              function reportClipboardFailure(error) {
                readerConsoleLog('READER_COPY failed' + (error ? ' error=' + readerTtsPreview(error, 180) : ''));
                return false;
              }
              function fallbackClipboardCopy(text, error) {
                if (execCommandClipboardCopy(text)) {
                  readerConsoleLog('READER_COPY fallback_success method=execCommand');
                  return true;
                }
                var nativeResult = nativeClipboardCopy(text);
                if (nativeResult === true) {
                  readerConsoleLog('READER_COPY fallback_success method=native');
                  return true;
                }
                return reportClipboardFailure(error || 'fallback_unavailable');
              }
              function copyText(text) {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                  try {
                    var writeResult = navigator.clipboard.writeText(text);
                    if (writeResult && typeof writeResult.then === 'function') {
                      writeResult.then(function () {
                        readerConsoleLog('READER_COPY success method=navigator');
                      }).catch(function (error) {
                        fallbackClipboardCopy(text, error);
                      });
                      return true;
                    }
                    return fallbackClipboardCopy(text, 'navigator_no_promise');
                  } catch (error) {
                    return fallbackClipboardCopy(text, error);
                  }
                }
                return fallbackClipboardCopy(text, 'navigator_unavailable');
              }
              function fallbackSelectionAction(action, text) {
                if (action === 'web-search') {
                  window.open('https://www.google.com/search?q=' + encodeURIComponent(text), '_blank');
                }
              }
              function sendSelectionAction(action, text) {
                var payload = {
                  action: action,
                  text: text
                };
                var actionRange = savedRange;
                if (!actionRange) {
                  var actionSelection = window.getSelection && window.getSelection();
                  if (actionSelection && actionSelection.rangeCount > 0) actionRange = actionSelection.getRangeAt(0);
                }
                var actionSegments = selectionSegmentsForRange(actionRange);
                if (actionSegments.length) {
                  var firstSegment = actionSegments[0];
                  var lastSegment = actionSegments[actionSegments.length - 1];
                  var sameChapter = actionSegments.every(function (segment) {
                    return segment.chapterIndex === firstSegment.chapterIndex;
                  });
                  if (sameChapter) {
                    var pageIndex = firstSegment.pageIndex;
                    if (pageIndex < 0 && firstSegment.startOffset !== null) {
                      var anchorPage = pageForLocator(firstSegment.chapterIndex, firstSegment.startOffset);
                      if (anchorPage) pageIndex = anchorPage.pageIndex;
                    }
                    var cfi = readerHighlightCfiForRange(
                      firstSegment,
                      lastSegment,
                      firstSegment.chapterIndex,
                      firstSegment.startOffset,
                      lastSegment.endOffset
                    );
                    payload.locator = {
                      chapterIndex: firstSegment.chapterIndex,
                      chapterId: firstSegment.chapterId,
                      href: firstSegment.chapterHref || null,
                      pageIndex: pageIndex >= 0 ? pageIndex : null,
                      startOffset: firstSegment.startOffset,
                      endOffset: lastSegment.endOffset,
                      blockIndex: firstSegment.blockIndex,
                      charOffset: firstSegment.charOffset,
                      textQuote: text,
                      cfi: cfi
                    };
                  }
                }
                if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                  try {
                    window.kmpJsBridge.callNative('readerSelectionAction', JSON.stringify(payload));
                    return true;
                  } catch (error) {
                    readerConsoleLog('READER_SELECTION_ACTION bridge_error action=' + action + ' error=' + error);
                  }
                }
                fallbackSelectionAction(action, text);
                return false;
              }
              function selectionOffsetsWithin(host, range) {
                var rawStart = offsetForBoundary(host, range.startContainer, range.startOffset);
                var rawEnd = offsetForBoundary(host, range.endContainer, range.endOffset);
                if (rawStart === null || rawEnd === null || rawEnd < rawStart) {
                  return { start: null, end: null };
                }
                var selectedText = textBetweenOffsets(host, rawStart, rawEnd);
                var trimmedText = selectedText.trim();
                if (!trimmedText) return { start: null, end: null };
                var leadingWhitespace = selectedText.length - selectedText.replace(/^\s+/, '').length;
                var trailingWhitespace = selectedText.length - selectedText.replace(/\s+$/, '').length;
                return { start: rawStart + leadingWhitespace, end: rawEnd - trailingWhitespace };
              }
              function offsetForBoundary(host, container, offset) {
                var includeWhitespace = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                var nodes = textNodesUnder(host, includeWhitespace);
                var boundary = document.createRange();
                try {
                  boundary.setStart(container, offset);
                  boundary.collapse(true);
                } catch (error) {
                  boundary.detach && boundary.detach();
                  return null;
                }
                var cursor = 0;
                for (var n = 0; n < nodes.length; n++) {
                  var node = nodes[n];
                  var length = (node.nodeValue || '').length;
                  if (node === container) {
                    boundary.detach && boundary.detach();
                    return cursor + Math.max(0, Math.min(length, offset));
                  }
                  var nodeRange = document.createRange();
                  nodeRange.selectNodeContents(node);
                  var nodeEndsBeforeBoundary = nodeRange.compareBoundaryPoints(Range.END_TO_START, boundary) <= 0;
                  nodeRange.detach && nodeRange.detach();
                  if (nodeEndsBeforeBoundary) {
                    cursor += length;
                  } else {
                    boundary.detach && boundary.detach();
                    return cursor;
                  }
                }
                boundary.detach && boundary.detach();
                return cursor;
              }
              function textBetweenOffsets(host, startOffset, endOffset) {
                var includeWhitespace = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                var nodes = textNodesUnder(host, includeWhitespace);
                var cursor = 0;
                var text = '';
                for (var n = 0; n < nodes.length; n++) {
                  var value = nodes[n].nodeValue || '';
                  var next = cursor + value.length;
                  if (next <= startOffset) {
                    cursor = next;
                    continue;
                  }
                  if (cursor >= endOffset) break;
                  var startInNode = Math.max(0, startOffset - cursor);
                  var endInNode = Math.min(value.length, endOffset - cursor);
                  if (endInNode > startInNode) text += value.substring(startInNode, endInNode);
                  cursor = next;
                }
                return text;
              }
              function readerTextBlock(node) {
                var parent = node && node.parentElement;
                return parent && parent.closest
                  ? parent.closest('p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, div, section')
                  : null;
              }
              function contentStartOffset(content) {
                var pageHost = content && content.closest ? content.closest('[data-reader-page-start]') : null;
                return numberAttribute(content, 'data-reader-content-start', numberAttribute(pageHost, 'data-reader-page-start', 0));
              }
              function normalizedOffsetForBoundary(root, container, offset) {
                if (!root || !container) return null;
                var nodes = textNodesUnder(root, true);
                if (!nodes.length) return 0;
                var boundary = document.createRange();
                try {
                  boundary.setStart(container, offset);
                  boundary.collapse(true);
                } catch (error) {
                  boundary.detach && boundary.detach();
                  return null;
                }
                var state = {
                  cursor: 0,
                  sawText: false,
                  inWhitespace: false,
                  previousBlock: null
                };
                function applyBlockBoundary(node) {
                  var currentBlock = readerTextBlock(node);
                  if (state.previousBlock && currentBlock && currentBlock !== state.previousBlock && state.sawText && !state.inWhitespace) {
                    state.inWhitespace = true;
                    state.cursor += 1;
                  }
                  if (currentBlock) state.previousBlock = currentBlock;
                }
                function consumeNormalizedText(value, limit) {
                  var safeLimit = Math.max(0, Math.min(value.length, limit));
                  for (var i = 0; i < safeLimit; i++) {
                    if (/^\s$/.test(value[i])) {
                      if (!state.sawText) continue;
                      if (!state.inWhitespace) {
                        state.inWhitespace = true;
                        state.cursor += 1;
                      }
                      continue;
                    }
                    if (state.inWhitespace) state.inWhitespace = false;
                    state.sawText = true;
                    state.cursor += 1;
                  }
                }
                try {
                  for (var n = 0; n < nodes.length; n++) {
                    var node = nodes[n];
                    var value = node.nodeValue || '';
                    if (node === container) {
                      applyBlockBoundary(node);
                      consumeNormalizedText(value, offset);
                      return state.cursor;
                    }
                    var nodeRange = document.createRange();
                    var endsBeforeBoundary = false;
                    var startsAfterBoundary = false;
                    try {
                      nodeRange.selectNodeContents(node);
                      endsBeforeBoundary = nodeRange.compareBoundaryPoints(Range.END_TO_START, boundary) <= 0;
                      startsAfterBoundary = nodeRange.compareBoundaryPoints(Range.START_TO_START, boundary) >= 0;
                    } catch (error) {
                      startsAfterBoundary = true;
                    } finally {
                      nodeRange.detach && nodeRange.detach();
                    }
                    if (endsBeforeBoundary) {
                      applyBlockBoundary(node);
                      consumeNormalizedText(value, value.length);
                      continue;
                    }
                    if (startsAfterBoundary) return state.cursor;
                    return state.cursor;
                  }
                  return state.cursor;
                } finally {
                  boundary.detach && boundary.detach();
                }
              }
              function explicitTextHostForBoundary(root, container) {
                if (!root || !container) return null;
                var element = container.nodeType === Node.TEXT_NODE ? container.parentElement : container;
                if (!element || !element.closest) return null;
                var host = element.closest('[data-reader-text-start][data-reader-text-end]');
                if (host && (host === root || root.contains(host))) return host;
                return null;
              }
              function readerTextHostForOffset(content, offset, preferEnd) {
                if (!content || !Number.isFinite(offset)) return null;
                var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end][data-reader-cfi]'));
                for (var i = 0; i < hosts.length; i++) {
                  var host = hosts[i];
                  var start = numberAttribute(host, 'data-reader-text-start', null);
                  var end = numberAttribute(host, 'data-reader-text-end', null);
                  var cfi = host.getAttribute && host.getAttribute('data-reader-cfi');
                  if (start === null || end === null || !cfi || cfi.charAt(0) !== '/') continue;
                  if (preferEnd) {
                    if (offset > start && offset <= end) return host;
                  } else if (offset >= start && offset < end) {
                    return host;
                  }
                }
                return null;
              }
              function readerCfiPointForOffset(content, offset, preferEnd) {
                var host = readerTextHostForOffset(content, offset, preferEnd);
                if (!host) return null;
                var baseCfi = String(host.getAttribute('data-reader-cfi') || '').split(':')[0];
                var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                if (!baseCfi || hostStart === null || hostEnd === null) return null;
                var localOffset = Math.max(0, Math.min(offset - hostStart, Math.max(0, hostEnd - hostStart)));
                return baseCfi + ':' + localOffset;
              }
              function readerBlockPositionForOffset(content, offset, preferEnd) {
                if (!content || !Number.isFinite(offset)) return null;
                var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end][data-reader-block-index]'));
                var host = null;
                for (var i = 0; i < hosts.length; i++) {
                  var candidate = hosts[i];
                  var start = numberAttribute(candidate, 'data-reader-text-start', null);
                  var end = numberAttribute(candidate, 'data-reader-text-end', null);
                  if (start === null || end === null || end < start) continue;
                  if (preferEnd) {
                    if (offset > start && offset <= end) {
                      host = candidate;
                      break;
                    }
                  } else if (offset >= start && offset < end) {
                    host = candidate;
                    break;
                  }
                }
                if (!host) return null;
                var blockIndex = numberAttribute(host, 'data-reader-block-index', null);
                if (blockIndex === null) return null;
                return {
                  blockIndex: blockIndex,
                  charOffset: offset
                };
              }
              function readerHighlightCfiForRange(startSegment, endSegment, chapterIndex, startOffset, endOffset) {
                var startPoint = readerCfiPointForOffset(startSegment && startSegment.content, startOffset, false);
                var endPoint = readerCfiPointForOffset(endSegment && endSegment.content, endOffset, true);
                if (startPoint && endPoint) return startPoint + '|' + endPoint;
                return 'desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset;
              }
              function absoluteOffsetForBoundary(root, container, offset) {
                var host = explicitTextHostForBoundary(root, container);
                if (!host) return null;
                var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                if (hostStart === null) return null;
                var localOffset = offsetForBoundary(host, container, offset);
                return localOffset === null ? null : hostStart + localOffset;
              }
              function boundaryOffsetWithinContent(content, container, offset) {
                var explicitOffset = absoluteOffsetForBoundary(content, container, offset);
                if (explicitOffset !== null) return explicitOffset;
                var normalizedOffset = normalizedOffsetForBoundary(content, container, offset);
                return normalizedOffset === null ? null : contentStartOffset(content) + normalizedOffset;
              }
              function trimSourceOffsets(rawStart, rawEnd, text) {
                if (rawStart === null || rawEnd === null || rawEnd < rawStart) return { start: null, end: null };
                var selectedText = String(text || '');
                var trimmedText = selectedText.trim();
                if (!trimmedText) return { start: null, end: null };
                var leadingWhitespace = selectedText.length - selectedText.replace(/^\s+/, '').length;
                var trailingWhitespace = selectedText.length - selectedText.replace(/\s+$/, '').length;
                return { start: rawStart + leadingWhitespace, end: rawEnd - trailingWhitespace };
              }
              function selectionSourceOffsetsWithin(content, range) {
                return rangeOffsetsWithinContent(content, range);
              }
              function rangeOffsetsWithinContent(content, range) {
                if (!content || !range) return { start: null, end: null };
                var rawStart = boundaryOffsetWithinContent(content, range.startContainer, range.startOffset);
                var rawEnd = boundaryOffsetWithinContent(content, range.endContainer, range.endOffset);
                return trimSourceOffsets(rawStart, rawEnd, range.toString());
              }
              function rangeIntersectsRange(range, candidate) {
                if (!range || !candidate) return false;
                try {
                  return range.compareBoundaryPoints(Range.END_TO_START, candidate) > 0 &&
                    range.compareBoundaryPoints(Range.START_TO_END, candidate) < 0;
                } catch (error) {
                  return false;
                }
              }
              function nodeInside(root, node) {
                return !!root && !!node && (root === node || (root.contains && root.contains(node)));
              }
              function firstTextBoundary(root) {
                var nodes = textNodesUnder(root, false);
                if (nodes.length) return { node: nodes[0], offset: 0 };
                return { node: root, offset: 0 };
              }
              function lastTextBoundary(root) {
                var nodes = textNodesUnder(root, false);
                if (nodes.length) {
                  var last = nodes[nodes.length - 1];
                  return { node: last, offset: (last.nodeValue || '').length };
                }
                return { node: root, offset: root && root.childNodes ? root.childNodes.length : 0 };
              }
              function clippedRangeForContent(content, range) {
                if (!content || !range) return null;
                var contentRange = document.createRange();
                try {
                  contentRange.selectNodeContents(content);
                  var boundaryInside = nodeInside(content, range.startContainer) || nodeInside(content, range.endContainer);
                  var intersectsContent = boundaryInside;
                  if (!intersectsContent && range.intersectsNode) {
                    try { intersectsContent = range.intersectsNode(content); } catch (error) {}
                  }
                  if (!intersectsContent && !rangeIntersectsRange(range, contentRange)) return null;
                  var clipped = document.createRange();
                  if (nodeInside(content, range.startContainer)) {
                    clipped.setStart(range.startContainer, range.startOffset);
                  } else {
                    var first = firstTextBoundary(content);
                    clipped.setStart(first.node, first.offset);
                  }
                  if (nodeInside(content, range.endContainer)) {
                    clipped.setEnd(range.endContainer, range.endOffset);
                  } else {
                    var last = lastTextBoundary(content);
                    clipped.setEnd(last.node, last.offset);
                  }
                  if (clipped.collapsed) {
                    clipped.detach && clipped.detach();
                    return null;
                  }
                  return clipped;
                } catch (error) {
                  return null;
                } finally {
                  contentRange.detach && contentRange.detach();
                }
              }
              function selectionSegmentsForRange(range) {
                if (!range) return [];
                var contents = Array.prototype.slice.call(document.querySelectorAll('.page[data-reader-page-index] .reader-content'));
                if (!contents.length) {
                  contents = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index] .reader-content'));
                }
                if (!contents.length) {
                  var container = range.commonAncestorContainer;
                  if (container && container.nodeType !== Node.ELEMENT_NODE) container = container.parentElement;
                  var content = container && container.closest ? container.closest('.reader-content') : null;
                  if (content) contents = [content];
                }
                var diagnostics = {
                  noRange: 0,
                  badOffsets: [],
                  missingHost: 0,
                  blankText: 0
                };
                var segments = contents.map(function (content) {
                  var segmentRange = clippedRangeForContent(content, range);
                  if (!segmentRange) {
                    diagnostics.noRange += 1;
                    return null;
                  }
                  var offsets = rangeOffsetsWithinContent(content, segmentRange);
                  if (offsets.start === null || offsets.end === null || offsets.end <= offsets.start) {
                    if (diagnostics.badOffsets.length < 4) {
                      diagnostics.badOffsets.push(
                        'start=' + offsets.start +
                        ',end=' + offsets.end +
                        ',textChars=' + segmentRange.toString().trim().length
                      );
                    }
                    segmentRange.detach && segmentRange.detach();
                    return null;
                  }
                  var readerHost = content.closest ? content.closest('[data-reader-chapter-index]') : null;
                  if (!readerHost) {
                    diagnostics.missingHost += 1;
                    segmentRange.detach && segmentRange.detach();
                    return null;
                  }
                  var segmentText = segmentRange.toString().trim();
                  if (!segmentText) {
                    diagnostics.blankText += 1;
                    segmentRange.detach && segmentRange.detach();
                    return null;
                  }
                  var blockPosition = readerBlockPositionForOffset(content, offsets.start, false);
                  readerDesktopHighlightMapLog(
                    'web_selection_segment chapter=' + parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10) +
                    ' page=' + parseInt(readerHost.getAttribute('data-reader-page-index') || '-1', 10) +
                    ' offsets=' + offsets.start + '..' + offsets.end +
                    ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                    ' char=' + (blockPosition ? blockPosition.charOffset : offsets.start) +
                    ' textChars=' + segmentText.length +
                    ' text="' + readerTtsPreview(segmentText, 120) + '"'
                  );
                  return {
                    range: segmentRange,
                    content: content,
                    readerHost: readerHost,
                    text: segmentText,
                    chapterIndex: parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10),
                    chapterId: readerHost.getAttribute('data-reader-chapter-id'),
                    chapterHref: readerHost.getAttribute('data-reader-chapter-href'),
                    pageIndex: parseInt(readerHost.getAttribute('data-reader-page-index') || '-1', 10),
                    startOffset: offsets.start,
                    endOffset: offsets.end,
                    blockIndex: blockPosition ? blockPosition.blockIndex : null,
                    charOffset: blockPosition ? blockPosition.charOffset : offsets.start
                  };
                }).filter(Boolean);
                if (!segments.length) {
                  readerHighlightFlowLog(
                    'selection_segments_rejected contents=' + contents.length +
                    ' noRange=' + diagnostics.noRange +
                    ' badOffsets=' + diagnostics.badOffsets.length +
                    ' missingHost=' + diagnostics.missingHost +
                    ' blankText=' + diagnostics.blankText +
                    ' common=' + selectionDebugNode(range.commonAncestorContainer) +
                    ' badOffsetSamples="' + diagnostics.badOffsets.join('; ') + '"'
                  );
                }
                return segments;
              }
              function rangeMatchesStoredOffsets(content, range, startOffset, endOffset) {
                var offsets = rangeOffsetsWithinContent(content, range);
                if (offsets.start === null || offsets.end === null) return false;
                return Math.abs(offsets.start - startOffset) <= 1 && Math.abs(offsets.end - endOffset) <= 1;
              }
              function readerHighlightCssColor(colorArgb) {
                if (colorArgb === undefined || colorArgb === null) return null;
                var value = Number(colorArgb);
                if (!Number.isFinite(value)) return null;
                var rgb = (value >>> 0) & 0xFFFFFF;
                return '#' + rgb.toString(16).padStart(6, '0').toUpperCase();
              }
              function createReaderHighlightMarker(highlightId, colorId, startOffset, endOffset, colorArgb) {
                var marker = document.createElement('span');
                marker.className = 'reader-user-highlight user-highlight-' + (colorId || 'yellow');
                var cssColor = readerHighlightCssColor(colorArgb);
                if (cssColor) marker.style.setProperty('background-color', cssColor, 'important');
                if (highlightId) marker.setAttribute('data-reader-highlight-id', highlightId);
                if (startOffset !== undefined && startOffset !== null) {
                  marker.setAttribute('data-reader-start-offset', String(startOffset));
                }
                if (endOffset !== undefined && endOffset !== null) {
                  marker.setAttribute('data-reader-end-offset', String(endOffset));
                }
                return marker;
              }
              function rangeIntersectsTextNode(range, node) {
                var nodeRange = document.createRange();
                try {
                  if (range.intersectsNode) return range.intersectsNode(node);
                  nodeRange.selectNodeContents(node);
                  return range.compareBoundaryPoints(Range.END_TO_START, nodeRange) > 0 &&
                    range.compareBoundaryPoints(Range.START_TO_END, nodeRange) < 0;
                } catch (error) {
                  return false;
                } finally {
                  nodeRange.detach && nodeRange.detach();
                }
              }
              function textSegmentsInRange(range) {
                var root = range.commonAncestorContainer;
                if (root.nodeType === Node.TEXT_NODE) root = root.parentNode;
                if (!root) return [];
                var nodes = textNodesUnder(root, true).filter(function (node) {
                  return rangeIntersectsTextNode(range, node);
                });
                return nodes.map(function (node) {
                  var length = (node.nodeValue || '').length;
                  var start = node === range.startContainer ? range.startOffset : 0;
                  var end = node === range.endContainer ? range.endOffset : length;
                  start = Math.max(0, Math.min(length, start));
                  end = Math.max(0, Math.min(length, end));
                  return { node: node, start: start, end: end };
                }).filter(function (segment) {
                  return segment.end > segment.start;
                });
              }
""".trimIndent()
