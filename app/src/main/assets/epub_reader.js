// epub_reader.js
(function () {
        function applyMobileOptimizationsAndSelection() {
            var viewport=document.querySelector("meta[name=viewport]");

            if ( !viewport) {
                viewport=document.createElement('meta');
                viewport.setAttribute('name', 'viewport');
                document.head.appendChild(viewport);
            }

            viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.5, user-scalable=yes');

            var style=document.getElementById('customMobileStyle');

            if ( !style) {
                style=document.createElement('style');
                style.setAttribute('id', 'customMobileStyle');
                document.head.appendChild(style);
            }

            // CHANGED: Colors now use rgba() with 0.5 opacity for blending
            style.innerHTML=` html {
                margin: 0; padding: 0; height: 100%; overflow-y: scroll;
            }

            body {
                margin: 0; padding: 0px !important;
                word-wrap: break-word; overflow-wrap: break-word;
                word-break: break-word; -webkit-text-size-adjust: 100%;
                text-rendering: optimizeLegibility; -webkit-user-select: none;
                -moz-user-select: none; -ms-user-select: none; user-select: none;
                scroll-behavior: auto !important;
            }

            #content-container {
                will-change: transform;
                transition: none !important;
            }

            p, div, li, td, th, span {
                font-size: 1em; line-height: inherit;
            }

            p, ul, ol {
                margin-top: 0.5em; margin-bottom: 0.5em;
            }

            li {
                margin-bottom: 0.25em;
            }

            h1, h2, h3, h4, h5, h6 {
                line-height: 1.3; margin-top: 1em; margin-bottom: 0.5em; font-weight: bold;
            }

            h1 {
                font-size: 2em;
            }

            h2 {
                font-size: 1.75em;
            }

            h3 {
                font-size: 1.5em;
            }

            h4 {
                font-size: 1.25em;
            }

            h5 {
                font-size: 1.1em;
            }

            h6 {
                font-size: 1em;
            }

            img, svg, video, canvas {
                max-width: 100%; width: 100%; height: auto; display: block; margin-left: auto; margin-right: auto; background-color: transparent; object-fit: contain;
            }

            figure img {
                height: auto !important;
            }

            div:has(> img:only-child), p:has(> img:only-child) {
                height: auto !important; line-height: normal !important;
            }

            .tts-highlight {
                background-color: rgba(255, 236, 179, 0.8);
                /* Semi-transparent Gold */
                color: black !important;
                padding: 0.1em 0;
                border-radius: 3px;
            }

            html.dark-theme span.tts-highlight {
                background-color: rgba(160, 140, 90, 0.8) !important;
                color: #E0E0E0 !important;
            }

            /* User Highlights */
            .user-highlight-yellow {
                    background-color: rgba(251, 192, 45, 0.4); cursor: pointer;
                }
                .user-highlight-green {
                    background-color: rgba(56, 142, 60, 0.4); cursor: pointer;
                }
                .user-highlight-blue {
                    background-color: rgba(25, 118, 210, 0.4); cursor: pointer;
                }
                .user-highlight-red {
                    background-color: rgba(211, 47, 47, 0.4); cursor: pointer;
                }
                .user-highlight-purple {
                    background-color: rgba(123, 31, 162, 0.4); cursor: pointer;
                }
                .user-highlight-orange {
                    background-color: rgba(245, 124, 0, 0.4); cursor: pointer;
                }
                .user-highlight-cyan {
                    background-color: rgba(0, 151, 167, 0.4); cursor: pointer;
                }
                .user-highlight-magenta {
                    background-color: rgba(194, 24, 91, 0.4); cursor: pointer;
                }
                .user-highlight-lime {
                    background-color: rgba(175, 180, 43, 0.4); cursor: pointer;
                }
                .user-highlight-pink {
                    background-color: rgba(233, 30, 99, 0.4); cursor: pointer;
                }
                .user-highlight-teal {
                    background-color: rgba(0, 121, 107, 0.4); cursor: pointer;
                }
                .user-highlight-indigo {
                    background-color: rgba(48, 63, 159, 0.4); cursor: pointer;
                }
                .user-highlight-black {
                    background-color: rgba(0, 0, 0, 0.4); cursor: pointer;
                }
                .user-highlight-white {
                    background-color: rgba(255, 255, 255, 0.4); cursor: pointer;
                    /* Optional: slight border so white is visible on white paper */
                    border-bottom: 1px solid rgba(0,0,0,0.1);
                }

                /* Active State (Darkens slightly when pressed) */
                span[class^="user-highlight-"]:active {
                    filter: brightness(0.9);
                }

            mark.search-highlight {
                background-color: rgba(160, 207, 241, 0.8);
                color: black;
                border-radius: 3px;
            }

            html.dark-theme mark.search-highlight {
                background-color: rgba(0, 90, 156, 0.8);
                color: #E0E0E0;
            }

            `;

            window.setTextSelectionEnabled=function (enabled) {
                var selectStyle=enabled ? 'auto' : 'none';

                if (document.body) {
                    document.body.style.webkitUserSelect=selectStyle;
                    document.body.style.mozUserSelect=selectStyle;
                    document.body.style.msUserSelect=selectStyle;
                    document.body.style.userSelect=selectStyle;
                }
            }

            ;
            window.setTextSelectionEnabled(true);
        }

        window.VIEWPORT_PADDING_TOP=0;
        window.VIEWPORT_PADDING_BOTTOM=0;

        window.setViewportPadding=function (top, bottom) {
            window.VIEWPORT_PADDING_TOP=top || 0;
            window.VIEWPORT_PADDING_BOTTOM=bottom || 0;
        }

        ;

        window.applyReaderTheme=function (isDark) {
            var styleId='readerThemeStyle';
            var themeStyleElement=document.getElementById(styleId);

            if ( !themeStyleElement) {
                themeStyleElement=document.createElement('style');
                themeStyleElement.setAttribute('id', styleId);
                document.head.appendChild(themeStyleElement);
            }

            // Set a class on the root element for theme state
            var themeClassName=isDark ? 'dark-theme' : 'light-theme';
            var oppositeThemeClassName=isDark ? 'light-theme' : 'dark-theme';
            document.documentElement.classList.remove(oppositeThemeClassName);
            document.documentElement.classList.add(themeClassName);

            var css="";

            if (isDark) {
                css=` html.dark-theme, html.dark-theme body {
                    background-color: #121212 !important;
                    color: #E0E0E0 !important;
                }

                html.dark-theme a {
                    color: #BB86FC !important;
                }

                html.dark-theme a p,
                html.dark-theme a div,
                html.dark-theme a span,
                html.dark-theme a li,
                html.dark-theme a h1,
                html.dark-theme a h2,
                html.dark-theme a h3,
                html.dark-theme a h4,
                html.dark-theme a h5,
                html.dark-theme a h6 {
                    color: #E0E0E0 !important;
                    background-color: transparent !important;
                }

                html.dark-theme blockquote, html.dark-theme pre,
                html.dark-theme figcaption, html.dark-theme caption,
                html.dark-theme label, html.dark-theme dt, html.dark-theme dd {
                    color: inherit !important;
                    background-color: transparent !important;
                }

                html.dark-theme hr {
                    border-color: #444444 !important;
                    background-color: #444444 !important;
                }

                html.dark-theme table, html.dark-theme tr, html.dark-theme td, html.dark-theme th {
                    background-color: transparent !important;
                    border-color: #555 !important;
                }

                `;
            }

            else {
                css=` html.light-theme {
                    background-color: #FFFFFF;
                }

                `;
            }

            themeStyleElement.innerHTML=css;
        }

        ;

        function handleHighlightInteraction(e) {
            var target=e.target;
            var highlightSpan=null;

            while (target && target !==document.body) {
                var isHighlight = false;
                if (target.nodeType === Node.ELEMENT_NODE && target.classList) {
                    for (var i = 0; i < target.classList.length; i++) {
                        if (target.classList[i].startsWith('user-highlight-')) {
                            isHighlight = true;
                            break;
                        }
                    }
                }

                if (isHighlight) {
                    highlightSpan=target;
                    break;
                }

                target=target.parentNode;
            }

            if (highlightSpan) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                if (window.getSelection) {
                    window.getSelection().removeAllRanges();
                }

                var text=highlightSpan.textContent;
                var rect=highlightSpan.getBoundingClientRect();
                var rawCfi=highlightSpan.getAttribute('data-cfi');

                if ( !rawCfi) {
                    var cfiResult=window.getCfiPathForElement(highlightSpan, 0);
                    rawCfi=cfiResult.cfi;
                }

                var cfiToReport=rawCfi;

                if (rawCfi && rawCfi.includes('|')) {
                    var cfiParts=rawCfi.split('|');
                    cfiToReport=cfiParts[cfiParts.length - 1];
                    console.log("HandleInteraction: Multi-CFI detected on single span. Reporting top layer: " + cfiToReport);
                }

                if (window.HighlightBridge) {
                    window.HighlightBridge.onHighlightClicked(cfiToReport,
                        text,
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom);
                }

                return true;
            }

            return false;
        }

        // 1. Handle Taps (Click)
        document.addEventListener('click', function (e) {
                handleHighlightInteraction(e);
            }

            , true);

        // 2. Handle Long Press (Context Menu) - "Atomic" Behavior
        // This prevents the native Android selection handles from appearing inside the highlight
        document.addEventListener('contextmenu', function (e) {
                if (handleHighlightInteraction(e)) {
                    e.preventDefault(); // Ensure menu doesn't show
                    return false;
                }
            }

            , true);

        window.updateReaderStyles=function (fontSizeEm, lineHeight, fontFamily, textAlign) {
            var logTag="ReaderFontDiagnosis";
            console.log(logTag + ": updateReaderStyles called. Size: " + fontSizeEm + ", LineHeight: " + lineHeight + ", Font: '" + fontFamily + "', Align: '" + textAlign + "'");

            var dynamicStyleId='dynamicReaderStyles';
            var dynamicStyleElement=document.getElementById(dynamicStyleId);

            if ( !dynamicStyleElement) {
                dynamicStyleElement=document.createElement('style');
                dynamicStyleElement.setAttribute('id', dynamicStyleId);
                document.head.appendChild(dynamicStyleElement);
            }

            var newFontSize=parseFloat(fontSizeEm);
            var newLineHeight=parseFloat(lineHeight);

            if (isNaN(newFontSize) || newFontSize < 0.5 || newFontSize > 5.0) newFontSize=1.0;
            if (isNaN(newLineHeight) || newLineHeight < 1.0 || newLineHeight > 3.0) newLineHeight=1.6;

            var fontCss="";
            var selector="body";

            if (fontFamily && fontFamily !=="Original" && fontFamily !=="") {
                var fallback="sans-serif";

                if (fontFamily==="Merriweather" || fontFamily==="Lora") {
                    fallback="serif";
                }

                else if (fontFamily==="Roboto Mono") {
                    fallback="monospace";
                }

                selector="body, p, span, div, li, a, h1, h2, h3, h4, h5, h6, blockquote, td, th";
                fontCss="font-family: '" + fontFamily + "', " + fallback + " !important;";
            }

            // --- ALIGNMENT LOGIC ---
            var alignCss="";
            var alignSelector="body, p, li, div, h1, h2, h3, h4, h5, h6";

            if (textAlign==="left") {
                alignCss=` ` + alignSelector + ` {
                    text-align: left !important;
                }

                `;
            }

            else if (textAlign==="justify") {
                alignCss=` ` + alignSelector + ` {
                    text-align: justify !important;
                    -webkit-hyphens: auto !important;
                    hyphens: auto !important;
                }

                `;
            }

            dynamicStyleElement.innerHTML=` body {
                font-size: ` + newFontSize + `em !important;
                line-height: ` + newLineHeight + ` !important;
            }

            ` + selector + ` {
                ` + fontCss + `
            }

            ` + alignCss + ` `;

            setTimeout(function () {
                    var computedBody=window.getComputedStyle(document.body).fontFamily;
                    console.log(logTag + ": [BODY] Computed font-family: " + computedBody);

                    var firstPara=document.querySelector('p');

                    if (firstPara) {
                        var computedPara=window.getComputedStyle(firstPara).fontFamily;
                        var computedAlign=window.getComputedStyle(firstPara).textAlign;
                        console.log(logTag + ": [PARAGRAPH] Computed font-family: " + computedPara);
                        console.log(logTag + ": [PARAGRAPH] Inner Text Sample: " + firstPara.innerText.substring(0, 20));
                    }

                    else {
                        console.log(logTag + ": [PARAGRAPH] No <p> tag found to check.");
                    }

                    if (fontFamily && fontFamily !=="") {
                        var isCheckAvailable=(document.fonts && document.fonts.check);

                        if (isCheckAvailable) {
                            var loaded=document.fonts.check("12px '" + fontFamily + "'");
                            console.log(logTag + ": Font Loading Status -> document.fonts.check('" + fontFamily + "') = " + loaded);
                        }

                        else {
                            console.log(logTag + ": document.fonts API not available.");
                        }
                    }
                }

                , 300);

            if (window.reportScrollState) {
                setTimeout(window.reportScrollState, 60);
            }
        }

        ;

        window.TOC_FRAGMENTS=window.TOC_FRAGMENTS || [];

        window.setTocFragments=function (jsonArray) {
            console.log("FRAG_NAV_DEBUG: window.setTocFragments called with " + jsonArray.length + " items.");
            window.TOC_FRAGMENTS=jsonArray;
            // Immediate audit and report
            window.auditTocFragments();
            window.reportScrollState();
        }

        ;


        window.reportScrollState=function () {
            if (typeof PageInfoReporter !=='undefined' && PageInfoReporter.updateScrollState) {
                var scrollY=Math.round(window.scrollY || window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);
                var scrollHeight=Math.round(Math.max(document.body.scrollHeight, document.documentElement.scrollHeight));
                var clientHeight=Math.round(document.documentElement.clientHeight || window.innerHeight || 0);

                if (clientHeight===0) return;

                var activeFragment=null;
                var hasFoundAnyElementInDom=false;

                if (window.TOC_FRAGMENTS && window.TOC_FRAGMENTS.length > 0) {
                    // Adjust threshold to be slightly more forgiving (padding + 60px)
                    var threshold=window.VIEWPORT_PADDING_TOP + 60;

                    for (var i=0; i < window.TOC_FRAGMENTS.length; i++) {
                        var id=window.TOC_FRAGMENTS[i];
                        // FIX: Look for both 'id' and 'name' attributes
                        var el=document.getElementById(id) || document.querySelector('[name="' + id + '"]');

                        if (el) {
                            hasFoundVisible=true;
                            var rect=el.getBoundingClientRect();

                            // Log individual element positions so we can see them in your FRAG_NAV_DEBUG filter
                            console.log("FRAG_NAV_DEBUG: Checking #" + id + " | rect.top: " + Math.round(rect.top) + " | threshold: " + threshold);

                            if (rect.top <=threshold) {
                                activeFragment=id;
                            }

                            else {
                                break;
                            }
                        }

                        else {
                            if ( !hasFoundVisible) {
                                activeFragment=id;
                            }
                        }
                    }
                }

                // Fallback for the very start of the chapter
                if (activeFragment===null && scrollY < 50 && window.TOC_FRAGMENTS && window.TOC_FRAGMENTS.length > 0) {
                    activeFragment=window.TOC_FRAGMENTS[0];
                }

                PageInfoReporter.updateScrollState(scrollY, scrollHeight, clientHeight, activeFragment);
            }

            window.reportTopChunk();
        }

        ;

        // ADD THIS NEW DIAGNOSTIC FUNCTION
        window.auditTocFragments=function () {
            console.log("FRAG_NAV_DEBUG: --- Starting DOM Audit ---");

            if ( !window.TOC_FRAGMENTS || window.TOC_FRAGMENTS.length===0) {
                console.log("FRAG_NAV_DEBUG: Audit failed - window.TOC_FRAGMENTS is empty.");
                return;
            }

            var foundCount=0;

            window.TOC_FRAGMENTS.forEach(id=> {
                    var el=document.getElementById(id);

                    if (el) {
                        foundCount++;
                        console.log("FRAG_NAV_DEBUG: [FOUND] ID: " + id + " | Tag: " + el.tagName + " | OffsetTop: " + el.offsetTop);
                    }

                    else {
                        console.log("FRAG_NAV_DEBUG: [MISSING] ID: " + id + " - Not in DOM.");
                    }
                });
            console.log("FRAG_NAV_DEBUG: Audit complete. Found " + foundCount + "/" + window.TOC_FRAGMENTS.length);

            // Also log some random IDs from the DOM to see what's actually there
            var allWithId=document.querySelectorAll('[id]');
            console.log("FRAG_NAV_DEBUG: Sample IDs existing in DOM: " + Array.from(allWithId).slice(0, 5).map(el=> el.id).join(", "));
        }

        ;

        window.addEventListener('scroll', window.reportScrollState, {
            passive: true
        });
    window.addEventListener('resize', window.reportScrollState);

    window.triggerInitialScrollStateReport=function () {
        var attempts=0; var maxAttempts=7; var baseInterval=100;

        function tryReport() {
            attempts++; window.reportScrollState();
            var currentClientHeight=document.documentElement.clientHeight || window.innerHeight || 0;

            if (currentClientHeight > 0) {
                setTimeout(window.reportScrollState, 50); return;
            }

            if (attempts < maxAttempts) {
                var retryDelay=baseInterval + (attempts * 50); setTimeout(tryReport, retryDelay);
            }
        }

        setTimeout(tryReport, baseInterval);
    }

    ;

    window.scrollToChapterStart=function () {
        requestAnimationFrame(function () {
                window.scrollTo(0, 0);

                setTimeout(function () {
                        window.reportScrollState();
                    }

                    , 100);
            });
    }

    ;

    window.scrollToChapterEnd=function () {
        requestAnimationFrame(function () {
                var targetScrollY=(document.body.scrollHeight || document.documentElement.scrollHeight) - (window.innerHeight || document.documentElement.clientHeight);
                if (targetScrollY < 0) targetScrollY=0;
                window.scrollTo(0, targetScrollY);

                setTimeout(function () {
                        window.reportScrollState();
                    }

                    , 100);
            });
    }

    ;

    window.scrollToSpecificY=function (yPosition) {
        if (typeof yPosition==='number' && yPosition >=0) {
            window.scrollTo(0, yPosition);

            setTimeout(function () {
                    window.reportScrollState();
                }

                , 100);
        }

        else {
            setTimeout(function () {
                    window.reportScrollState();
                }

                , 100);
        }
    }

    ;

    function initializeReaderContent() {
        applyMobileOptimizationsAndSelection();
    }

    if (document.readyState==='complete' || document.readyState==='interactive') {
        initializeReaderContent();
    }

    else {
        document.addEventListener('DOMContentLoaded', initializeReaderContent);
    }

    window.clearSearchHighlights=function () {
        document.querySelectorAll('mark.search-highlight').forEach(function (el) {
                var parent=el.parentNode;

                if (parent) {
                    while (el.firstChild) {
                        parent.insertBefore(el.firstChild, el);
                    }

                    parent.removeChild(el);
                    parent.normalize();
                }
            });
        return "JS: Search highlights cleared.";
    }

    ;

    window.highlightAllOccurrences=function (query) {
        window.clearSearchHighlights();
        if ( !query || query.length < 2) return "JS: Query too short for highlighting.";

        var walker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        var nodesToModify=[];

        while (node=walker.nextNode()) {
            if (node.nodeValue.toLowerCase().includes(query.toLowerCase())) {
                nodesToModify.push(node);
            }
        }

       var regex = new RegExp('(' + query.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&') + ')', 'gi');

        nodesToModify.forEach(function (textNode) {
                if (textNode.parentNode && textNode.parentNode.tagName !=='SCRIPT' && textNode.parentNode.tagName !=='STYLE') {
                    var tempDiv=document.createElement('div');
                    tempDiv.innerHTML=textNode.nodeValue.replace(regex, '<mark class="search-highlight">$1</mark>');

                    var parent=textNode.parentNode;

                    while (tempDiv.firstChild) {
                        parent.insertBefore(tempDiv.firstChild, textNode);
                    }

                    parent.removeChild(textNode);
                }
            });

        return "JS: Highlighted " + document.querySelectorAll('mark.search-highlight').length + " occurrences.";
    }

    ;

    window.scrollToOccurrence=function (index) {
        var highlights=document.querySelectorAll('mark.search-highlight');

        if (highlights && index >=0 && index < highlights.length) {
            var element=highlights[index];

            element.scrollIntoView({
                behavior: 'auto', block: 'center', inline: 'nearest'
            });
        return "JS: Scrolled to occurrence " + index;
    }

    return "JS: Occurrence " + index + " not found.";
}

;

window.removeHighlight=function () {
    var highlightNode;
    var removedCount=0;

    while ((highlightNode=document.querySelector('.tts-highlight')) !==null) {
        var parent=highlightNode.parentNode;

        if (parent) {
            try {
                while (highlightNode.firstChild) {
                    parent.insertBefore(highlightNode.firstChild, highlightNode);
                }

                parent.removeChild(highlightNode);
                parent.normalize();
                removedCount++;
            }

            catch (e) {
                if (highlightNode.parentNode) {
                    // Check if it wasn't already removed by a concurrent process
                    highlightNode.remove(); // Fallback removal
                }

                break; // Exit loop on error to prevent infinite loop on a problematic node
            }
        }

        else {
            try {
                highlightNode.remove();
                removedCount++;
            }

            catch (e_orphan) {
                // console.error("JS: Error removing orphaned highlight node: " + e_orphan.message, highlightNode);
            }

            break;
        }
    }
}

;

const TTS_HIGHLIGHT_LOG_TAG="TTS_HIGHLIGHT_DIAGNOSIS";

window.highlightFromCfi=function (cfi, textToHighlight, startOffset) {
    console.log(`$ {
            TTS_HIGHLIGHT_LOG_TAG
        }

        : highlightFromCfi called. CFI='${cfi}', Offset=$ {
            startOffset
        }

        , Text='${textToHighlight.substring(0, 50)}...' `);
    window.removeHighlight();

    if ( !cfi || !textToHighlight) {
        const errorMsg="JS: CFI or text missing.";

        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : $ {
                errorMsg
            }

            `);
        return errorMsg;
    }

    try {
        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : Resolving CFI to node...`);
        const location=window.getNodeAndOffsetFromCfi(cfi);

        if ( !location || !location.node) {
            const errorMsg="JS: Could not find node for CFI.";

            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : $ {
                    errorMsg
                }

                `);
            return errorMsg;
        }

        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : Node found for CFI. Node type: $ {
                location.node.nodeName
            }

            , Text content: '${(location.node.textContent || "").substring(0, 50)}...' `);


        const baseNode=location.node;
        let remainingOffset=startOffset;

        const treeWalker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        treeWalker.currentNode=baseNode;

        let currentNode=(baseNode.nodeType===Node.TEXT_NODE) ? baseNode : treeWalker.nextNode();

        // 1. Find the starting text node and character position
        while (currentNode && remainingOffset >=currentNode.nodeValue.length) {
            remainingOffset -=currentNode.nodeValue.length;
            currentNode=treeWalker.nextNode();
        }

        if ( !currentNode) {
            const errorMsg="JS: Text offset is out of bounds for the CFI node.";

            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : $ {
                    errorMsg
                }

                `);
            return errorMsg;
        }

        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : Start node for range found. Node: '${currentNode.nodeValue.substring(0, 50)}...', calculated offset: $ {
                remainingOffset
            }

            `);

        const range=document.createRange();
        range.setStart(currentNode, remainingOffset);

        // 2. Find the ending text node and character position
        let remainingTextLength=textToHighlight.length;
        let endNode=currentNode;
        let endOffset=remainingOffset;
        let sanityCheck=0;

        while (remainingTextLength > 0 && endNode && sanityCheck < 50) {
            const availableLength=endNode.nodeValue.length - endOffset;

            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : Finding end range. Remaining text: $ {
                    remainingTextLength
                }

                , Current node: '${endNode.nodeValue.substring(0, 50)}...', available length: $ {
                    availableLength
                }

                `);

            if (availableLength >=remainingTextLength) {
                endOffset +=remainingTextLength;
                remainingTextLength=0;
            }

            else {
                remainingTextLength -=availableLength;
                // Important: We need a fresh walker starting from the endNode to find the *next* text node reliably
                const nextNodeWalker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                nextNodeWalker.currentNode=endNode;
                endNode=nextNodeWalker.nextNode();
                endOffset=0; // Start from the beginning of the next node
            }

            sanityCheck++;
        }

        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : End node for range found. End node: '${endNode ? endNode.nodeValue.substring(0, 50) : "null"}', End offset: $ {
                endOffset
            }

            `);


        if (remainingTextLength > 0) {
            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : Text to highlight was longer than found text nodes. Highlighting to end of last found node.`);

            if (endNode) {
                range.setEnd(endNode, endNode.nodeValue.length);
            }

            else {
                const lastKnownGoodNode=range.endContainer;
                range.setEnd(lastKnownGoodNode, lastKnownGoodNode.nodeValue.length);
            }
        }

        else {
            range.setEnd(endNode, endOffset);
        }

        const highlightSpan=document.createElement('span');
        highlightSpan.className='tts-highlight';

        try {
            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : Attempting to surround content with highlight span.`);
            range.surroundContents(highlightSpan);
        }

        catch (e) {
            console.log(`$ {
                    TTS_HIGHLIGHT_LOG_TAG
                }

                : surroundContents failed, using fallback. Error: $ {
                    e.message
                }

                `);
            const contents=range.extractContents();
            highlightSpan.appendChild(contents);
            range.insertNode(highlightSpan);
        }

        console.log(`$ {
                TTS_HIGHLIGHT_LOG_TAG
            }

            : Highlight successful. Scrolling into view.`);

        highlightSpan.scrollIntoView({
            behavior: 'smooth', block: 'center', inline: 'nearest'
        });
    return "JS: Highlight successful.";

}

catch (e) {
    const errorMsg="JS: Error during highlightFromCfi: " + e.message;

    console.log(`$ {
            TTS_HIGHLIGHT_LOG_TAG
        }

        : $ {
            errorMsg
        }

        `);
    return errorMsg;
}
}

;

window.extractTextWithCfiFromTop=function () {
    try {
        // 1. Find the element at the top of the viewport.
        const viewportX=window.innerWidth / 2;
        const viewportY=window.VIEWPORT_PADDING_TOP + 20; // A bit down from the very top edge
        let topElement=document.elementFromPoint(viewportX, viewportY);

        if ( !topElement) {
            // Fallback if nothing is found (e.g., blank space between elements)
            topElement=document.body.querySelector('p, h1, h2, h3, h4, img, svg, table, li');
            if ( !topElement) return "[]"; // Chapter seems empty
        }

        // 2. Find its containing block-level element that we use for TTS.
        const ttsNodeSelector='p, h1, h2, h3, h4, h5, h6, li, blockquote';
        let startBlock=topElement.closest(ttsNodeSelector);

        if ( !startBlock) {
            // If the element itself isn't in a TTS block, fall back to the start of the chapter.
            console.log("Could not find a starting TTS block. Falling back to full chapter.");
            return window.extractTextWithCfi();
        }

        // 3. Get all potential TTS nodes.
        const allContentNodes=Array.from(document.body.querySelectorAll(ttsNodeSelector));

        // 4. Find the index of our starting block.
        const startIndex=allContentNodes.findIndex(node=> node===startBlock);

        if (startIndex===-1) {
            // Should be rare if startBlock was found, but as a safeguard:
            console.log("Could not find the start block in the node list. Falling back to full chapter.");
            return window.extractTextWithCfi();
        }

        // 5. Slice the array and process it.
        const nodesToProcess=allContentNodes.slice(startIndex);
        const results=[];

        nodesToProcess.forEach(node=> {
                const text=node.innerText ? node.innerText.trim() : "";

                if (text.length > 0 && node.offsetParent !==null) {
                    try {
                        const cfi=getCfiPathForElement(node, 0);

                        if (cfi) {
                            results.push({
                                cfi: cfi, text: text
                            });
                    }
                }

                catch (e) {
                    // ignore CFI generation errors for a single node
                }
            }
        });

    return JSON.stringify(results);

}

catch (e) {
    // On any error, fall back to extracting everything to not break TTS completely.
    return window.extractTextWithCfi();
}
}

;

window.extractTextWithCfi=function () {
    const results=[];
    const contentNodes=document.body.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote');

    contentNodes.forEach(node=> {
            const text=node.innerText ? node.innerText.trim() : "";

            if (text.length > 0 && node.offsetParent !==null) {
                try {
                    const cfi=getCfiPathForElement(node, 0);

                    if (cfi) {
                        results.push({
                            cfi: cfi, text: text
                        });
                }
            }

            catch (e) {}
        }
    });
const jsonResult=JSON.stringify(results);
return jsonResult;
}

;

window.TtsBridgeHelper= {
    extractAndRelayText: function () {
        try {
            const structuredTextJson=window.extractTextWithCfiFromTop();

            if (typeof TtsBridge !=='undefined' && TtsBridge.onStructuredTextExtracted) {
                TtsBridge.onStructuredTextExtracted(structuredTextJson);
            }
        }

        catch (e) {
            if (typeof TtsBridge !=='undefined' && TtsBridge.onStructuredTextExtracted) {
                TtsBridge.onStructuredTextExtracted("[]");
            }
        }
    }
}

;

window.reportTopChunk=function () {
    if (typeof ProgressReporter==='undefined' || typeof ProgressReporter.updateTopChunk==='undefined') return;

    const topElement=document.elementFromPoint(window.innerWidth / 2, window.VIEWPORT_PADDING_TOP + 1);

    if (topElement) {
        const chunkContainer=topElement.closest('[data-chunk-index]');

        if (chunkContainer) {
            const chunkIndex=parseInt(chunkContainer.dataset.chunkIndex, 10);

            if ( !isNaN(chunkIndex)) {
                ProgressReporter.updateTopChunk(chunkIndex);
            }
        }

        else {
            ProgressReporter.updateTopChunk(0);
        }
    }
}

;

window.AiBridgeHelper= {
    extractAndRelayTextForSummarization: function () {
        var textContent="";

        try {
            var mainElement=document.querySelector('article, [role="main"], main, body');

            if (mainElement) {
                textContent=mainElement.innerText || mainElement.textContent || "";
            }

            else {
                textContent=document.body.innerText || document.body.textContent || "";
            }

            if (typeof AiBridge !=='undefined' && AiBridge.onContentExtractedForSummarization) {
                AiBridge.onContentExtractedForSummarization(textContent.trim());
            }
        }

        catch (e) {
            if (typeof AiBridge !=='undefined' && AiBridge.onContentExtractedForSummarization) {
                AiBridge.onContentExtractedForSummarization("");
            }
        }
    }
}

;

window.checkImagesForDiagnosis=function () {
    const images=document.querySelectorAll('img, image'); // 'image' for SVG images
    const logTag="ImageDiagnosis";
    console.log(logTag + ": JS checkImagesForDiagnosis called. Found " + images.length + " image elements.");

    images.forEach((img, index)=> {
            const src=img.src || img.getAttribute('xlink:href');
            console.log(logTag + ": Image #" + index + " | src: '" + src + "'");

            function processImage() {
                console.log(logTag + ": Processing Image #" + index + " (complete=" + img.complete + ")");
                console.log(logTag + ": Image #" + index + " | clientWidth: " + img.clientWidth + ", clientHeight: " + img.clientHeight);
                console.log(logTag + ": Image #" + index + " | naturalWidth: " + img.naturalWidth + ", naturalHeight: " + img.naturalHeight);
                console.log(logTag + ": Image #" + index + " | is visible (offsetParent): " + (img.offsetParent !==null));

                const style=window.getComputedStyle(img);
                console.log(logTag + ": Image #" + index + " | computed display: '" + style.display + "', visibility: '" + style.visibility + "', opacity: '" + style.opacity + "'");

                // FIX: If height has collapsed, manually calculate and set it forcefully.
                if (img.complete && img.naturalWidth > 0 && img.clientWidth > 0 && img.clientHeight===0) {
                    console.log(logTag + ": CORRECTING GEOMETRY for Image #" + index);
                    const parent=img.parentElement;

                    if (parent) {
                        const parentStyle=window.getComputedStyle(parent);
                        console.log(logTag + ": Parent <" + parent.tagName + "> computed height: " + parentStyle.height + ", overflow: " + parentStyle.overflow);
                        // Force the parent's height to be determined by its content. This is crucial.
                        parent.style.setProperty('height', 'auto', 'important');
                    }

                    const aspectRatio=img.naturalHeight / img.naturalWidth;
                    const correctHeight=img.clientWidth * aspectRatio;

                    // Remove the conflicting max-height property and then set the explicit height.
                    img.style.setProperty('max-height', 'none', 'important');
                    img.style.setProperty('height', correctHeight + 'px', 'important');

                    console.log(logTag + ": Corrective styles applied to Image #" + index + ". Verifying height after a short delay for reflow...");

                    // After applying styles, wait a moment for the browser to reflow the layout
                    // before reporting the new height and updating the scroll state.
                    setTimeout(function () {
                            console.log(logTag + ": Verified height for Image #" + index + ": " + img.clientHeight + "px");
                            window.reportScrollState(); // Update scroll metrics now that the image has height
                        }

                        , 150);
                }

                img.onerror=function () {
                    console.log(logTag + ": ERROR: Image #" + index + " FAILED to load. Src was: '" + src + "'");
                }

                ;

                if (img.complete && img.naturalWidth===0) {
                    console.log(logTag + ": WARNING: Image #" + index + " is complete but has 0 naturalWidth, may indicate loading error. Src: '" + src + "'");
                }
            }

            if (img.complete) {
                processImage();
            }

            else {
                img.onload=processImage;
            }
        });
}

;

const CFI_LOG_TAG="CFI_DIAGNOSIS";

function log(message) {
    console.log(`$ {
            CFI_LOG_TAG
        }

        : $ {
            message
        }

        `);
}

function resolveCfiPath(rootElement, path) {
    log(`Attempting to resolve path '${path}' from root <$ {
            rootElement.tagName
        }

        >`);
    let currentNode=rootElement;
    const steps=path.substring(1).split('/').map(Number);

    for (let i=0; i < steps.length; i++) {
        const cfiIndex=steps[i];

        if ( !currentNode) {
            log(`Traversal failed: currentNode became null before step $ {
                    i
                }

                (CFI index $ {
                        cfiIndex
                    }).`);
            return null;
        }

        const elementChildren=Array.from(currentNode.childNodes).filter(node=> node.nodeType===Node.ELEMENT_NODE);
        const childNodeIndex=(cfiIndex - 2) / 2;

        if (childNodeIndex >=0 && childNodeIndex < elementChildren.length) {
            currentNode=elementChildren[childNodeIndex];
        }

        else {
            const childrenTags=elementChildren.map(node=> `<$ {
                    node.tagName || "TEXT"
                }

                >`).join(', ');

            log(`Step $ {
                    i
                }

                : FAILED. CFI index $ {
                    cfiIndex
                }

                (child index $ {
                        childNodeIndex

                    }) is out of bounds for $ {
                    elementChildren.length
                }

                element children: [$ {
                    childrenTags
                }

                ]`);

            log(`Parent Node HTML at failure point (<$ {
                        currentNode.tagName
                    }

                    >): $ {
                    currentNode.innerHTML.substring(0, 300)
                }

                ...`); // ADD THIS LINE
            return null; // Path is invalid from this root
        }
    }

    return currentNode;
}

window.getNodeAndOffsetFromCfi=function (cfi) {
    log(`getNodeAndOffsetFromCfi called with: $ {
            cfi
        }

        `);

    try {
        var pathParts=cfi.split(':');
        var nodePath=pathParts[0];
        var charOffset=pathParts.length > 1 ? parseInt(pathParts[1], 10) : 0;

        log(`Parsed CFI: path=$ {
                nodePath
            }

            , offset=$ {
                charOffset
            }

            `);

        let cfiRoot=document.getElementById('content-container') || document.body;
        let pathToResolve=nodePath;

        const firstChunk=cfiRoot.querySelector('[data-chunk-index]');

        if (firstChunk && pathToResolve.startsWith ('/4/')) {
            log(`Paginator CFI detected. Adjusting root to first chunk and stripping '/4' from path.`);
            cfiRoot=firstChunk;
            pathToResolve='/' + pathToResolve.substring(3);
        }

        log(`CFI Root is <$ {
                cfiRoot.tagName
            }

            >, Path to resolve is $ {
                pathToResolve
            }

            `);

        let resolvedNode=resolveCfiPath(cfiRoot, pathToResolve);

        log(`Resolution attempt #1 (from CFI root) result: $ {
                resolvedNode ? resolvedNode.tagName : 'null'
            }

            `);

        if ( !resolvedNode) {
            log("Resolution failed from all roots.");
            return null;
        }

        let currentNode=resolvedNode;

        log(`Successfully resolved containing element: <$ {
                currentNode.tagName || 'TEXT_NODE'
            }

            >`);

        if (currentNode.nodeType===Node.ELEMENT_NODE) {
            const treeWalker=document.createTreeWalker(currentNode, NodeFilter.SHOW_TEXT, null, false);
            const firstTextNode=treeWalker.nextNode();

            if (firstTextNode) {
                log(`Found first text node inside element to apply offset.`);
                currentNode=firstTextNode;
            }

            else {
                log(`Could not find a text node inside the target element. Using the element itself.`);
            }
        }

        return {
            node: currentNode, offset: charOffset
        }

        ;
    }

    catch (e) {
        log(`ERROR in getNodeAndOffsetFromCfi: $ {
                e.message
            }

            `);
        return null;
    }
}

;

window.getCfiPathForElement=function (element, charOffset) {
    const logStack=[];

    try {
        var path=[];
        var currentNode=element;

        if (currentNode.nodeType===Node.TEXT_NODE) {
            logStack.push(`Initial node is a TEXT_NODE. Calculating cumulative offset.`);

            // --- FIX: Accumulate offsets from previous siblings ---
            var accumulatedOffset=charOffset || 0;
            var sibling=currentNode.previousSibling;

            while (sibling) {
                if (sibling.nodeType===Node.TEXT_NODE) {
                    accumulatedOffset +=sibling.nodeValue.length;
                }

                else if (sibling.nodeType===Node.ELEMENT_NODE) {
                    // Elements like <em>, <b> contribute text content to the flow
                    accumulatedOffset +=(sibling.textContent || "").length;
                }

                sibling=sibling.previousSibling;
            }

            logStack.push(`Original offset: $ {
                    charOffset
                }

                , Cumulative offset: $ {
                    accumulatedOffset
                }

                `);
            charOffset=accumulatedOffset;
            // -----------------------------------------------------

            logStack.push(`Using its parent <$ {
                    currentNode.parentNode.tagName
                }

                > for path generation.`);
            currentNode=currentNode.parentNode;
        }

        const root=document.getElementById('content-container') || document.body;

        logStack.push(`Using <$ {
                root.tagName
            }

            id='${root.id}' class='${root.className}' > as the consistent CFI root.`);

        while (currentNode && currentNode !==root && currentNode.parentNode) {
            const parentNode=currentNode.parentNode;
            const elementSiblings=Array.from(parentNode.childNodes).filter(node=> node.nodeType===Node.ELEMENT_NODE);
            const nodeIndex=elementSiblings.indexOf(currentNode);

            if (nodeIndex===-1) {
                currentNode=parentNode;
                continue;
            }

            const cfiIndex=(nodeIndex * 2) + 2;
            path.unshift(cfiIndex);

            const childrenTags=elementSiblings.map(node=> `<$ {
                    node.tagName || "TEXT"
                }

                >`).join(', ');

            logStack.push(`GENERATION: Parent <$ {
                    parentNode.tagName
                }

                > has $ {
                    elementSiblings.length
                }

                element children: [$ {
                    childrenTags
                }

                ]. Current node <$ {
                    currentNode.tagName
                }

                > is at index $ {
                    nodeIndex
                }

                , becoming CFI step $ {
                    cfiIndex
                }

                . Path: /$ {
                    path.join('/')
                }

                `);

            currentNode=parentNode;
        }

        var cfi=`/` + path.join('/');

        if (charOffset !==undefined && charOffset > 0) {
            cfi +=':' + charOffset;
        }

        logStack.push(`Final CFI generated: $ {
                cfi
            }

            `);

        return {
            cfi: cfi, log: logStack
        }

        ;
    }

    catch (e) {
        logStack.push(`ERROR in getCfiPathForElement: $ {
                e.message
            }

            `);

        return {
            cfi: `/2`, log: logStack
        }

        ;
    }
}

;

window.getCurrentCfi=function () {
    const debugLog=[];
    let finalCfi="/2";

    try {
        const viewportX=window.innerWidth / 2;
        const viewportY=window.VIEWPORT_PADDING_TOP + 5;

        debugLog.push(`Probing for CFI at coordinates: x=$ {
                viewportX
            }

            , y=$ {
                viewportY
            }

            (padding top: $ {
                    window.VIEWPORT_PADDING_TOP
                })`);

        let topElement=document.elementFromPoint(viewportX, viewportY);

        if ( !topElement) {
            debugLog.push("elementFromPoint returned null. Trying to find the first element in the body as a fallback.");
            topElement=document.body.querySelector('p, h1, h2, h3, h4, img, svg, table, li');

            if ( !topElement) {
                debugLog.push("No meaningful elements found in body. Aborting.");

                return JSON.stringify({
                    cfi: finalCfi, log: debugLog
                });
        }
    }

    debugLog.push(`Initial element found: <$ {
            topElement.tagName
        }

        id='${topElement.id}' class='${topElement.className}' >`);

    debugLog.push(`Element's text content (first 50 chars): '$ {
            (topElement.textContent || "").trim().substring(0, 50)
        }

        '`);

        // Ensure we are inside our content root, not on the body or html itself
        const contentRoot=document.getElementById('content-container') || document.body;

        if ( !contentRoot.contains(topElement)) {
            topElement=contentRoot.querySelector('p, h1, h2, h3, h4, img, svg, table, li') || contentRoot.firstElementChild;

            debugLog.push(`Initial element was outside content root. Switched to first meaningful child: <$ {
                    topElement ? topElement.tagName : 'null'
                }

                >`);
        }

        let range=document.caretRangeFromPoint ? document.caretRangeFromPoint(viewportX, viewportY) : null;
        let nodeForCfi;
        let offsetForCfi=0;

        if (range && range.startContainer && range.startContainer.nodeType===Node.TEXT_NODE && range.startContainer.textContent.trim().length > 0) {
            nodeForCfi=range.startContainer;
            offsetForCfi=range.startOffset;

            debugLog.push(`Success with caretRangeFromPoint. Node: "${nodeForCfi.nodeValue.substring(0, 30)}...", Offset: $ {
                    offsetForCfi
                }

                `);
        }

        else {
            debugLog.push("caretRangeFromPoint failed or found non-text/empty node. Using element-based CFI.");
            const treeWalker=document.createTreeWalker(topElement, NodeFilter.SHOW_TEXT, null, false);
            let firstTextNode=treeWalker.nextNode();
            nodeForCfi=(firstTextNode && firstTextNode.textContent.trim().length > 0) ? firstTextNode : topElement;
            offsetForCfi=0;

            debugLog.push(`Using fallback node for CFI generation: <$ {
                    nodeForCfi.tagName || 'TEXT_NODE'
                }

                >`);
        }

        const cfiResult=getCfiPathForElement(nodeForCfi, offsetForCfi);
        finalCfi=cfiResult.cfi;
        debugLog.push(...cfiResult.log);

    }

    catch (e) {
        debugLog.push(`FATAL ERROR in getCurrentCfi: $ {
                e.message
            }

            `);
    }

    return JSON.stringify({
        cfi: finalCfi, log: debugLog
    });
}

;

window.scrollToCfi=function (cfi) {
    log(`scrollToCfi called with: $ {
            cfi
        }

        `);
    let cleanCfi=cfi;

    if (cfi && cfi.includes('@')) {
        log("Old CFI format detected. Stripping prefix.");
        cleanCfi=cfi.substring(cfi.indexOf('@') + 1);

        log(`Cleaned CFI is now: $ {
                cleanCfi
            }

            `);
    }

    if ( !cleanCfi || !cleanCfi.startsWith ('/')) {
        log("CFI is null or has invalid format, not scrolling.");
        return;
    }

    setTimeout(()=> {
            log(`Executing scroll for CFI '${cleanCfi}' after delay.`);

            try {
                const location=window.getNodeAndOffsetFromCfi(cleanCfi);

                if (location && location.node) {
                    log(`Successfully found location for CFI. Node Type: $ {
                            location.node.nodeType
                        }

                        , Node Name: $ {
                            location.node.nodeName
                        }

                        `);

                    if (location.node.nodeType===Node.TEXT_NODE && location.offset > 0) {
                        try {
                            const range=document.createRange();
                            const validOffset=Math.min(location.offset, location.node.nodeValue.length);
                            range.setStart(location.node, validOffset);
                            range.collapse(true);

                            const rect=range.getBoundingClientRect();

                            if (rect.top !==0 || rect.left !==0) {
                                const currentScrollY=window.scrollY;
                                const targetScrollY=currentScrollY + rect.top - window.VIEWPORT_PADDING_TOP;

                                log(`Precise scroll calculated. Rect top: $ {
                                        rect.top
                                    }

                                    , Current scrollY: $ {
                                        currentScrollY
                                    }

                                    , Target scrollY: $ {
                                        targetScrollY
                                    }

                                    `);

                                window.scrollTo({
                                    top: targetScrollY, behavior: 'auto'
                                });

                            setTimeout(window.reportScrollState, 150);
                            return;
                        }

                        else {
                            log("Range rect.top was 0, indicating an issue or the element is already at the top. Falling back to element scroll.");
                        }
                    }

                    catch (rangeError) {
                        log(`Error during precise scroll calculation: $ {
                                rangeError.message
                            }

                            . Falling back.`);
                    }
                }

                const targetElement=(location.node.nodeType===Node.TEXT_NODE) ? location.node.parentNode : location.node;

                log(`Using fallback scrollIntoView for element: <$ {
                        targetElement.tagName
                    }

                    >`);

                targetElement.scrollIntoView({
                    behavior: 'auto', block: 'start', inline: 'nearest'
                });

            setTimeout(()=> {
                    const newScrollY=window.scrollY;

                    log(`ScrollY AFTER fallback scroll: $ {
                            newScrollY
                        }

                        `);
                    window.reportScrollState();
                }

                , 150);

        }

        else {
            log("FAILED to find the target node for CFI: " + cleanCfi);
        }
    }

    catch (e) {
        log("FATAL ERROR during scrollToCfi: " + e.message);
    }
}

, 250);
}

;

window.getElementByCfi=function (cfi) {
    var TAG_DIAG="BookmarkDiagnosis";
    console.log(TAG_DIAG + ": getElementByCfi called with CFI: " + cfi);

    try {
        var pathParts=cfi.split('!')[0].split(':')[0];
        var steps=pathParts.substring(1).split('/').map(Number);
        console.log(TAG_DIAG + ": Parsed steps: " + JSON.stringify(steps));
        var currentNode=document.body;

        for (var i=1; i < steps.length; i++) {
            if ( !currentNode) {
                console.log(TAG_DIAG + ": Traversal failed. currentNode became null at step " + i);
                return null;
            }

            var cfiIndex=steps[i];

            const children=Array.from(currentNode.childNodes).filter(node=> {
                    return node.nodeType===Node.ELEMENT_NODE || (node.nodeType===Node.TEXT_NODE && node.textContent.trim() !=='');
                });

            var childNodeIndex=(cfiIndex - 2) / 2;
            console.log(TAG_DIAG + ": Step " + i + " (CFI index " + cfiIndex + "): Looking for child at index " + childNodeIndex + " among " + children.length + " children.");


            if (childNodeIndex < 0 || childNodeIndex >=children.length) {
                console.log(TAG_DIAG + ": Path is invalid. Child index " + childNodeIndex + " is out of bounds.");
                return null; // Path is invalid for this document
            }

            currentNode=children[childNodeIndex];

            if (currentNode) {
                console.log(TAG_DIAG + ": Found node for step " + i + ": " + (currentNode.tagName || "TEXT_NODE"));
            }
        }

        if ( !currentNode) {
            console.log(TAG_DIAG + ": Final currentNode is null.");
            return null;
        }

        var resultNode=(currentNode.nodeType===Node.TEXT_NODE) ? currentNode.parentNode : currentNode;
        console.log(TAG_DIAG + ": Successfully found element: " + (resultNode ? resultNode.tagName : "null"));
        return resultNode;

    }

    catch (e) {
        console.log(TAG_DIAG + ": Error during getElementByCfi: " + e.message);
        return null;
    }
}

;

window.isElementInViewport=function (el) {
    if ( !el || typeof el.getBoundingClientRect !=='function') return false;
    const rect=el.getBoundingClientRect();
    const viewportHeight=window.innerHeight || document.documentElement.clientHeight;
    return (rect.top >=0 && rect.top <=viewportHeight);
}

;

window.getSnippetForCfi=function (cfi) {
    var TAG_DIAG="BookmarkDiagnosis";
    console.log(TAG_DIAG + ": getSnippetForCfi called with CFI: " + cfi);
    const location=window.getNodeAndOffsetFromCfi(cfi);

    if (location && location.node) {
        let textNode=location.node.nodeType===Node.TEXT_NODE ? location.node : null;
        let offset=location.offset;

        if ( !textNode) {
            const treeWalker=document.createTreeWalker(location.node, NodeFilter.SHOW_TEXT, null, false);
            textNode=treeWalker.nextNode();
            offset=0;
        }

        if (textNode) {
            const fullText=textNode.textContent;
            const lastSpace=fullText.lastIndexOf(' ', offset);
            const startIndex=(lastSpace===-1) ? 0 : lastSpace + 1;

            let snippet=fullText.substring(startIndex);

            const walker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            walker.currentNode=textNode;

            while (snippet.length < 160) {
                const nextNode=walker.nextNode();

                if (nextNode) {
                    snippet +=" " + nextNode.textContent;
                }

                else {
                    break;
                }
            }

            const finalSnippet=snippet.trim().replace(/\s+/g, ' ').substring(0, 150);
            console.log(TAG_DIAG + ": Snippet generated: '" + finalSnippet + "'");
            return finalSnippet;
        }
    }

    console.log(TAG_DIAG + ": No usable text found for CFI. Returning empty snippet.");
    return "";
}

;

window.findFirstVisibleCfi=function (cfiArray) {

    if ( !Array.isArray(cfiArray)) {
        return null;
    }

    const viewportHeight=window.innerHeight || document.documentElement.clientHeight;
    const activationZoneEnd=window.VIEWPORT_PADDING_TOP + ((viewportHeight - window.VIEWPORT_PADDING_TOP) * 0.6);

    for (const cfi of cfiArray) {
        const location=window.getNodeAndOffsetFromCfi(cfi);

        if (location && location.node) {
            try {
                const node=location.node;
                // Use the element's rect for stability, not a potentially zero-height range rect.
                const elementForRect=(node.nodeType===Node.TEXT_NODE) ? node.parentNode : node;
                const rect=elementForRect.getBoundingClientRect();
                const nodeName=elementForRect.nodeName;

                // The element is "active" if:
                // 1. It's positioned at the top of the content, even if under the padding.
                const isAtContentTop=rect.top >=0 && rect.top < window.VIEWPORT_PADDING_TOP;
                // 2. Any part of it overlaps with the main "reading zone" below the padding.
                const isInReadingZone=rect.bottom > window.VIEWPORT_PADDING_TOP && rect.top < activationZoneEnd;

                if ((isAtContentTop && rect.height > 0) || isInReadingZone) {
                    return cfi;
                }
            }

            catch (e) {
                console.log(TAG + ": Error processing CFI " + cfi + ": " + e.message);
            }
        }

        else {
            console.log(TAG + ": No node found for CFI: " + cfi);
        }
    }

    console.log(TAG + ": No visible bookmarked element found in viewport. Returning null.");
    return null;
}

;

})();

(function () {
        // --- VIRTUALIZATION LOGIC ---
        if (window.virtualization) return;

        let currentBottomChunkIndex=0;
        let totalChunks=0;
        let isLoading=false;
        let observer;

        window.virtualization= {
            totalChunks: 0,
            chunksData: [],
            chunkHeights: [],
            observer: null,

            init: function (initialChunkIndex, total) {
                console.log(`Virtualization: Init with $ {
                        total
                    }

                    chunks. Anchor: $ {
                        initialChunkIndex
                    }

                    `);
                this.totalChunks=total;
                this.chunksData=new Array(total).fill(null);
                this.chunkHeights=new Array(total).fill(0);

                const container=document.getElementById('content-container');

                if (container) {
                    container.querySelectorAll('.chunk-container').forEach(div=> {
                            let idx=parseInt(div.dataset.chunkIndex, 10);
                            let content=div.innerHTML.trim();

                            if (content.length > 0) {
                                this.chunksData=content;
                                this.chunkHeights=div.getBoundingClientRect().height;
                            }
                        });
                }

                this.setupObserver();
            }

            ,

            setupObserver: function () {
                if (this.observer) this.observer.disconnect();

                this.observer=new IntersectionObserver((entries)=> {
                        let scrollAdjust=0;

                        entries.forEach(entry=> {
                                let div=entry.target;
                                let idx=parseInt(div.dataset.chunkIndex, 10);

                                if (entry.isIntersecting) {
                                    if ( !this.chunksData) {
                                        if (window.ContentBridge && window.ContentBridge.requestChunk) {
                                            window.ContentBridge.requestChunk(idx);
                                        }
                                    }

                                    else if (div.innerHTML==='') {
                                        let oldHeight=div.getBoundingClientRect().height;
                                        div.innerHTML=this.chunksData;
                                        div.style.height='';
                                        let newHeight=div.getBoundingClientRect().height;
                                        this.chunkHeights=newHeight;

                                        if (div.getBoundingClientRect().top < 0) {
                                            scrollAdjust +=(newHeight - oldHeight);
                                        }
                                    }
                                }

                                else {
                                    if (div.innerHTML !=='') {
                                        let oldHeight=div.getBoundingClientRect().height;
                                        this.chunkHeights=oldHeight;
                                        div.style.height=oldHeight + 'px';
                                        div.innerHTML='';
                                    }
                                }
                            });

                        if (scrollAdjust !==0) {
                            window.scrollBy(0, scrollAdjust);
                        }

                    }

                    , {
                    rootMargin: '2500px 0px'
                });

            document.querySelectorAll('.chunk-container').forEach(div=> {
                    this.observer.observe(div);
                });
        }

        ,

        appendChunk: function (index, htmlContent) {
            console.log(`Virtualization: Receiving chunk $ {
                    index
                }

                from Kotlin`);
            this.chunksData=htmlContent;

            let div=document.querySelector(`.chunk-container`);

            if (div && div.innerHTML==='') {
                let oldHeight=div.getBoundingClientRect().height;
                div.innerHTML=htmlContent;
                div.style.height='';
                let newHeight=div.getBoundingClientRect().height;
                this.chunkHeights=newHeight;

                if (div.getBoundingClientRect().top < 0) {
                    window.scrollBy(0, newHeight - oldHeight);
                }
            }

            if (window.checkImagesForDiagnosis) {
                setTimeout(window.checkImagesForDiagnosis, 100);
            }
        }
    }

    ;

    const HL_LOG_TAG="HIGHLIGHT_DEBUG";

    window.HighlightBridgeHelper= {

        updateHighlightStyle: function (cfi, newColorClass, colorId) {
            console.log(`${HL_LOG_TAG}: updateHighlightStyle called. CFI: ${cfi}, Class: ${newColorClass}`);

            var allSpans = document.querySelectorAll('span[class*="user-highlight-"]');

            allSpans.forEach(span => {
                var currentCfiAttr = span.getAttribute('data-cfi') || "";
                var cfis = currentCfiAttr.split('|');

                if (cfis.includes(cfi)) {
                    var classesToRemove = [];
                    for (var i = 0; i < span.classList.length; i++) {
                        if (span.classList[i].startsWith('user-highlight-')) {
                            classesToRemove.push(span.classList[i]);
                        }
                    }

                    classesToRemove.forEach(cls => span.classList.remove(cls));

                    span.classList.add(newColorClass);
                }
            });

            if (window.HighlightBridge) {
                var sampleSpan = document.querySelector(`span[data-cfi*='${cfi}']`);
                var text = sampleSpan ? sampleSpan.textContent : "";
                window.HighlightBridge.onHighlightCreated(cfi, text, colorId);
            }
        },

        createUserHighlight: function (colorClass, colorId) {
            console.log(`$ {
                    HL_LOG_TAG
                }

                : createUserHighlight. Class: $ {
                    colorClass
                }

                `);
            var selection=window.getSelection();

            if ( !selection || selection.rangeCount===0 || selection.toString().trim()==="") {
                return;
            }

            try {
                var range=selection.getRangeAt(0);
                var text=range.toString();

                var safeStartNode=range.startContainer;
                var safeStartOffset=range.startOffset;
                var safeEndNode=range.endContainer;
                var safeEndOffset=range.endOffset;

                console.log(`$ {
                        HL_LOG_TAG
                    }

                    : [CREATE] Raw Selection - Text: '${text}' `);

                console.log(`$ {
                        HL_LOG_TAG
                    }

                    : [CREATE] StartContainer Type: $ {
                        safeStartNode.nodeType
                    }

                    , Name: $ {
                        safeStartNode.nodeName
                    }

                    `);

                var startResult=getCfiPathForElement(safeStartNode, safeStartOffset);
                var startCfi=startResult.cfi;

                var endResult=getCfiPathForElement(safeEndNode, safeEndOffset);
                var endCfi=endResult.cfi;

                var finalCfi=startCfi;

                if (startCfi !==endCfi) {
                    finalCfi=startCfi + "|" + endCfi;

                    console.log(`$ {
                            HL_LOG_TAG
                        }

                        : [CREATE] Range CFI Detected. Start: $ {
                            startCfi
                        }

                        , End: $ {
                            endCfi
                        }

                        `);
                }

                console.log(`$ {
                        HL_LOG_TAG
                    }

                    : [CREATE] Final CFI: $ {
                        finalCfi
                    }

                    `);

                range=this.normalizeRangeBoundaries(range);
                this.highlightRangeSafe(range, colorClass, finalCfi);

                selection.removeAllRanges();

                if (window.HighlightBridge) {
                    window.HighlightBridge.onHighlightCreated(finalCfi, text, colorId);
                }
            }

            catch (e) {
                console.log(`$ {
                        HL_LOG_TAG
                    }

                    : Create Error: ` + e.message);
            }
        }

        ,

        normalizeRangeBoundaries: function (range) {
            var startContainer=range.startContainer;
            var startOffset=range.startOffset;
            var endContainer=range.endContainer;
            var endOffset=range.endOffset;

            if (startContainer.nodeType===Node.TEXT_NODE && startOffset > 0 && startOffset < startContainer.length) {
                var newStartNode=startContainer.splitText(startOffset);
                range.setStart(newStartNode, 0);

                if (endContainer===startContainer) {
                    endContainer=newStartNode;
                    endOffset=endOffset - startOffset;
                }
            }

            if (endContainer.nodeType===Node.TEXT_NODE && endOffset > 0 && endOffset < endContainer.length) {
                endContainer.splitText(endOffset);
                range.setEnd(endContainer, endOffset);
            }

            return range;
        }

        ,

        highlightRangeSafe: function (range, className, newCfi) {
            var nodes=this.getTextNodesInRange(range);

            nodes.forEach(node=> {
                    var parent=node.parentNode;

                    if (parent && parent.tagName==='SPAN' && parent.classList.contains(className)) {
                        var currentCfi=parent.getAttribute('data-cfi') || "";
                        var cfiList=currentCfi.split('|');

                        if ( !cfiList.includes(newCfi)) {
                            parent.setAttribute('data-cfi', currentCfi + "|" + newCfi);
                        }
                    }

                    else {
                        if (node.nodeValue.trim().length===0) return;
                        var span=document.createElement('span');
                        span.className=className;
                        span.setAttribute('data-cfi', newCfi);
                        node.parentNode.insertBefore(span, node);
                        span.appendChild(node);
                    }
                });
        }

        ,

        getTextNodesInRange: function (range) {
            var textNodes=[];
            var container=range.commonAncestorContainer;
            var root=(container.nodeType===Node.TEXT_NODE) ? container.parentNode : container;

            var walker=document.createTreeWalker(root,
                NodeFilter.SHOW_TEXT,
                {
                acceptNode: function (node) {
                    return range.intersectsNode(node) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
                }
            }

            ,
            false);

        while (walker.nextNode()) {
            textNodes.push(walker.currentNode);
        }

        return textNodes;
    }

    ,

    removeHighlightByCfi: function (cfiToRemove, optionalCssClass) {
        console.log(`$ {
                HL_LOG_TAG
            }

            : removeHighlightByCfi called.`);

        console.log(`$ {
                HL_LOG_TAG
            }

            : -> Target CFI: '${cfiToRemove}' `);

        console.log(`$ {
                HL_LOG_TAG
            }

            : -> Optional Class: '${optionalCssClass}' `);

        // 1. Select all highlight spans manually to avoid selector syntax errors
        var allSpans=document.querySelectorAll('span[data-cfi]');

        console.log(`$ {
                HL_LOG_TAG
            }

            : Total highlight spans found in DOM: $ {
                allSpans.length
            }

            `);

        var foundCount=0;
        var removedCount=0;
        var updatedCount=0;

        allSpans.forEach(span=> {
                var currentCfiAttr=span.getAttribute('data-cfi') || "";
                var cfiList=currentCfiAttr.split('|');

                // Detailed check for match
                if (cfiList.includes(cfiToRemove)) {
                    foundCount++;

                    console.log(`$ {
                            HL_LOG_TAG
                        }

                        : Match found on span. Current CFIs: [$ {
                            currentCfiAttr
                        }

                        ]`);

                    var newCfiList=cfiList.filter(c=> c !==cfiToRemove);

                    if (newCfiList.length===0) {

                        // CASE 1: No other highlights on this span -> Remove entirely
                        console.log(`$ {
                                HL_LOG_TAG
                            }

                            : -> Removing span entirely (no remaining CFIs).`);
                        var parent=span.parentNode;
                        while (span.firstChild) parent.insertBefore(span.firstChild, span);
                        parent.removeChild(span);
                        parent.normalize();
                        removedCount++;
                    }

                    else {

                        // CASE 2: Overlapping highlight -> Update data-cfi
                        console.log(`$ {
                                HL_LOG_TAG
                            }

                            : -> Updating span (remaining CFIs: $ {
                                    newCfiList.join('|')
                                }).`);
                        span.setAttribute('data-cfi', newCfiList.join('|'));

                        if (optionalCssClass) {
                            console.log(`$ {
                                    HL_LOG_TAG
                                }

                                : -> Removing CSS class: $ {
                                    optionalCssClass
                                }

                                `);
                            span.classList.remove(optionalCssClass);
                        }

                        else {
                            console.log(`$ {
                                    HL_LOG_TAG
                                }

                                : -> Warning: No CSS class provided to remove. Visual style might persist if classes are mixed.`);
                        }

                        updatedCount++;
                    }
                }
            });

        console.log(`$ {
                HL_LOG_TAG
            }

            : Removal Summary -> Matched: $ {
                foundCount
            }

            , Removed: $ {
                removedCount
            }

            , Updated: $ {
                updatedCount
            }

            `);

        if (foundCount===0) {
            console.log(`$ {
                    HL_LOG_TAG
                }

                : No exact matches found. Attempting legacy fallback.`);
            this.removeHighlightByCfiLegacy(cfiToRemove);
        }
    }

    ,

    removeHighlightByCfiLegacy: function (cfi) {
        try {
            var location=window.getNodeAndOffsetFromCfi(cfi);

            if (location && location.node) {
                var target=location.node;
                if (target.nodeType===Node.TEXT_NODE) target=target.parentNode;

                if (target.tagName==='SPAN' && target.className.startsWith ('user-highlight-')) {
                    var parent=target.parentNode;
                    while (target.firstChild) parent.insertBefore(target.firstChild, target);
                    parent.removeChild(target);
                    parent.normalize();
                }
            }
        }

        catch (e) {}
    }

    ,

    restoreHighlights: function (jsonArrayString) {
        try {
            var highlights=JSON.parse(jsonArrayString);
            var self=this;

            highlights.forEach(function (h) {
                    self.applyHighlight(h.cfi, h.text, h.cssClass);
                });
        }

        catch (e) {
            console.log(`$ {
                    HL_LOG_TAG
                }

                : Error restoring: ` + e.message);
        }
    }

    ,

    applyHighlight: function (cfi, text, cssClass) {

        // "Healed" Apply Logic: Checks text equality before applying
        try {
            if (document.querySelector(`span[data-cfi='${cfi}']`)) return;

            const location=window.getNodeAndOffsetFromCfi(cfi);
            if ( !location || !location.node) return;

            let startNode=location.node;
            let startOffset=location.offset;

            if (startNode.nodeType===Node.TEXT_NODE) {
                const walker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                walker.currentNode=startNode;

                while (startNode && startOffset >=startNode.nodeValue.length) {
                    if (startOffset===startNode.nodeValue.length) {
                        const next=walker.nextNode();

                        if (next) {
                            startOffset -=startNode.nodeValue.length;
                            startNode=next;
                        }

                        else {
                            break;
                        }
                    }

                    else {
                        startOffset -=startNode.nodeValue.length;
                        startNode=walker.nextNode();
                    }
                }
            }

            // 1. Text Verification / Healing
            if (text && text.length > 0 && startNode && startNode.nodeType===Node.TEXT_NODE) {
                const nodeVal=startNode.nodeValue;
                // Check if text matches at exact offset
                const substring=nodeVal.substring(startOffset, startOffset + text.length);

                // Allow for some whitespace looseness (trim comparison)
                if (substring !==text && substring.trim() !==text.trim()) {
                    console.log(`$ {
                            HL_LOG_TAG
                        }

                        : Text mismatch at CFI. Searching nearby... Expected: '${text.substring(0, 10)}...', Found: '${substring.substring(0, 10)}...' `);

                    // Try finding the text in the whole node
                    const foundIndex=nodeVal.indexOf(text);

                    if (foundIndex !==-1) {
                        console.log(`$ {
                                HL_LOG_TAG
                            }

                            : Found text elsewhere in node. Adjusting offset from $ {
                                startOffset
                            }

                            to $ {
                                foundIndex
                            }

                            .`);
                        startOffset=foundIndex;
                    }

                    else {
                        // Simple fuzzy: Try finding first 20 chars
                        const partial=text.substring(0, Math.min(text.length, 20));
                        const partialIndex=nodeVal.indexOf(partial);

                        if (partialIndex !==-1) {
                            console.log(`$ {
                                    HL_LOG_TAG
                                }

                                : Found partial match. Adjusting offset.`);
                            startOffset=partialIndex;
                        }
                    }
                }
            }

            if ( !startNode) return;

            const range=document.createRange();

            // Set Start
            if (startNode.nodeType===Node.TEXT_NODE) {

                // Ensure offset is valid
                if (startOffset > startNode.nodeValue.length) {
                    startOffset=Math.max(0, startNode.nodeValue.length - 1);
                }
            }

            range.setStart(startNode, startOffset);

            const treeWalker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            treeWalker.currentNode=startNode;

            let currentNode=treeWalker.currentNode;
            let remainingOffset=startOffset;
            let remainingLen=text.length;
            let endNode=currentNode;
            let endOffset=startOffset;

            while (remainingLen > 0 && endNode) {
                let avail=endNode.nodeValue.length - endOffset;

                if (avail >=remainingLen) {
                    endOffset +=remainingLen;
                    remainingLen=0;
                }

                else {
                    remainingLen -=avail;
                    endNode=treeWalker.nextNode();
                    endOffset=0;
                }
            }

            if (endNode) {
                range.setEnd(endNode, endOffset);
                var normalizedRange=this.normalizeRangeBoundaries(range);
                this.highlightRangeSafe(normalizedRange, cssClass, cfi);
            }
        }

        catch (e) {
            console.log(e);
        }
    }
}

;
})();

(function () {
        const TAG_AUTO_SCROLL="AutoScrollDiagnosis";

        window.autoScroll= {
            active: false,
            speed: 1.0,
            accumulator: 0.0,
            animationId: null,

            start: function (speed) {
                console.log(`$ {
                        TAG_AUTO_SCROLL
                    }

                    : Starting auto-scroll. Speed: $ {
                        speed
                    }

                    `);
                this.active=true;
                this.speed=speed || this.speed;
                this.accumulator=0.0;
                if (this.animationId) cancelAnimationFrame(this.animationId);
                this.loop();
            },

            stop: function () {
                this.active = false;
                if (this.animationId) {
                    cancelAnimationFrame(this.animationId);
                    this.animationId = null;
                }

                const container = document.getElementById('content-container') || document.body;
                if (container) {
                    container.style.transform = 'none';
                    window.scrollBy(0, 0);
                }
            },

            updateSpeed: function (newSpeed) {
                console.log(`$ {
                        TAG_AUTO_SCROLL
                    }

                    : Speed updated to $ {
                        newSpeed
                    }

                    `);
                this.speed=newSpeed;
            }

            ,

            loop: function () {
                if (!this.active) return;

                this.accumulator += this.speed;

                const totalPixelsToScroll = Math.floor(this.accumulator);

                if (totalPixelsToScroll >= 1) {
                    const prevScrollY = window.scrollY;
                    window.scrollBy(0, totalPixelsToScroll);

                    this.accumulator -= totalPixelsToScroll;

                    const scrollY = window.scrollY;
                    const docHeight = document.documentElement.scrollHeight;
                    const innerH = window.innerHeight;
                    const isAtBottom = (scrollY + innerH) >= (docHeight - 3);
                    const isStuck = (totalPixelsToScroll > 0 && scrollY === prevScrollY && prevScrollY > 0);

                    if (isAtBottom || isStuck) {
                        this.stop();
                        if (window.AutoScrollBridge && window.AutoScrollBridge.onChapterEnd) {
                            window.AutoScrollBridge.onChapterEnd();
                        }
                        return;
                    }
                }

                const container = document.getElementById('content-container') || document.body;
                if (container) {
                    container.style.transform = `translate3d(0, -${this.accumulator}px, 0)`;
                }

                this.animationId = requestAnimationFrame(this.loop.bind(this));
            },
        };
    })();