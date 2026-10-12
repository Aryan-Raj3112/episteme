//! Stable C ABI over [`office_oxide`], consumed by the Reader app.
//!
//! # Why a bridge instead of calling office_oxide's own FFI from Kotlin
//!
//! office_oxide ships a 45-function C header, but we deliberately do not call
//! it directly. This crate owns the ABI the app depends on, for four reasons:
//!
//! 1. **Stability.** office_oxide is pre-1.0 and its FFI has churned across
//!    releases. Our surface is 6 functions; upstream's is 45 and may change.
//! 2. **One call per operation.** Upstream's API is an open/extract/free dance
//!    across several calls, each needing an `int* error_code` out-parameter.
//!    Kotlin-side that is a lot of ceremony for `docx -> html`.
//! 3. **One allocator, one free.** Every string we return is allocated here and
//!    must be released with [`office_bridge_free`]. Kotlin never has to match
//!    `free_string` against `free_bytes`.
//! 4. **Invariants we control.** Entry-point validation, an input size cap,
//!    `catch_unwind` so a library panic becomes an error code, NUL sanitising,
//!    and a thread-local error code for diagnostics.
//!
//! # Memory contract
//!
//! [`office_bridge_render`] returns either NULL (failure; read
//! [`office_bridge_last_error`]) or a NUL-terminated buffer whose content length
//! is written to `out_len`. The buffer is allocated by this library and must be
//! released exactly once with [`office_bridge_free`]. [`office_bridge_version`]
//! returns a `'static` string that must **not** be freed.
//!
//! # Threading
//!
//! Every function is reentrant. The last error code is thread-local, so call it
//! on the same thread that made the failing call.

#![allow(clippy::missing_safety_doc)]

use std::cell::Cell;
use std::ffi::{c_char, CStr, CString};
use std::io::{Cursor, Read, Seek, SeekFrom, Write};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::slice;

use office_oxide::ir_render::{HtmlOptions, ImageEmbed, MarkdownOptions};
use office_oxide::{Document, DocumentFormat, OfficeError};

/// ABI revision. Bump when an existing function changes its signature or
/// meaning; never when merely adding a function.
pub const OFFICE_BRIDGE_ABI_VERSION: u32 = 1;

/// Input cap, matching `SingleFileImporter.MAX_OFFICE_ARCHIVE_BYTES` (64 MiB) so
/// the native layer enforces the same policy as the existing DOCX import path.
const MAX_INPUT_BYTES: usize = 64 * 1024 * 1024;

// --- Error codes -----------------------------------------------------------
// Our vocabulary, mirroring the C FFI codes so the Kotlin side has one mapping.
// Do not renumber: these are an interface.
pub const OFFICE_BRIDGE_OK: i32 = 0;
/// Null pointer or otherwise invalid argument from the caller.
pub const OFFICE_BRIDGE_ERR_ARGUMENT: i32 = 1;
/// Extension does not map to a supported Office format, or the document is of a
/// kind office_oxide refuses.
pub const OFFICE_BRIDGE_ERR_UNSUPPORTED: i32 = 2;
/// Input exceeds MAX_INPUT_BYTES.
pub const OFFICE_BRIDGE_ERR_TOO_LARGE: i32 = 3;
/// The document could not be opened or read (corrupt, encrypted, wrong type).
pub const OFFICE_BRIDGE_ERR_READ: i32 = 4;
/// A panic, an allocation failure, or a bug in this crate.
pub const OFFICE_BRIDGE_ERR_INTERNAL: i32 = 5;

// --- Render modes ----------------------------------------------------------
pub const OFFICE_BRIDGE_TEXT: i32 = 0;
pub const OFFICE_BRIDGE_MARKDOWN: i32 = 1;
pub const OFFICE_BRIDGE_HTML: i32 = 2;
/// HTML with images inline as `data:` URIs, so the fragment is self-contained.
pub const OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES: i32 = 3;
/// Markdown with images inline as base64, for TTS and search indexing.
pub const OFFICE_BRIDGE_MARKDOWN_WITH_IMAGES: i32 = 4;

thread_local! {
    static LAST_ERROR: Cell<i32> = const { Cell::new(OFFICE_BRIDGE_OK) };
}

fn set_last_error(code: i32) {
    LAST_ERROR.with(|cell| cell.set(code));
}

fn map_error(error: OfficeError) -> i32 {
    match error {
        OfficeError::UnsupportedFormat(_) => OFFICE_BRIDGE_ERR_UNSUPPORTED,
        // office_oxide catches parser panics and reports them honestly rather
        // than as "unsupported format" - keep that distinction.
        OfficeError::Panic(_) => OFFICE_BRIDGE_ERR_INTERNAL,
        _ => OFFICE_BRIDGE_ERR_READ,
    }
}

/// Accepts `docx`, `.docx`, `DOCX` and `.DOCX`.
fn parse_extension(ext: &str) -> Option<DocumentFormat> {
    let normalised = ext.trim().trim_start_matches('.').to_ascii_lowercase();
    if normalised.is_empty() {
        return None;
    }
    DocumentFormat::from_extension(&normalised)
}

unsafe fn string_argument<'a>(ptr: *const c_char) -> Option<&'a str> {
    if ptr.is_null() {
        return None;
    }
    CStr::from_ptr(ptr).to_str().ok()
}

/// Renders a document held in memory.
///
/// On success returns a NUL-terminated buffer and writes its content length
/// (excluding the terminator) to `out_len`. On failure returns NULL and sets
/// the thread-local error code.
///
/// # Safety
/// `data` must point to `len` readable bytes; `ext` must be a NUL-terminated
/// string; `out_len` must be a valid writable pointer.
#[no_mangle]
pub unsafe extern "C" fn office_bridge_render(
    data: *const u8,
    len: usize,
    ext: *const c_char,
    mode: i32,
    out_len: *mut usize,
) -> *mut c_char {
    set_last_error(OFFICE_BRIDGE_OK);

    if data.is_null() || ext.is_null() || out_len.is_null() {
        set_last_error(OFFICE_BRIDGE_ERR_ARGUMENT);
        return std::ptr::null_mut();
    }
    if len > MAX_INPUT_BYTES {
        set_last_error(OFFICE_BRIDGE_ERR_TOO_LARGE);
        return std::ptr::null_mut();
    }
    let ext = match string_argument(ext) {
        Some(ext) => ext,
        None => {
            set_last_error(OFFICE_BRIDGE_ERR_ARGUMENT);
            return std::ptr::null_mut();
        }
    };
    let format = match parse_extension(ext) {
        Some(format) => format,
        None => {
            set_last_error(OFFICE_BRIDGE_ERR_UNSUPPORTED);
            return std::ptr::null_mut();
        }
    };
    // Reject an unknown mode before doing any work.
    if !matches!(
        mode,
        OFFICE_BRIDGE_TEXT
            | OFFICE_BRIDGE_MARKDOWN
            | OFFICE_BRIDGE_HTML
            | OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES
    ) {
        set_last_error(OFFICE_BRIDGE_ERR_ARGUMENT);
        return std::ptr::null_mut();
    }

    // `from_reader` wants an owned `Read + Seek + Send + 'static`, so copy the
    // caller's buffer once here rather than holding a raw pointer across the
    // unwind boundary below.
    let bytes = slice::from_raw_parts(data, len).to_vec();
    // Compatibility shims for office_oxide 0.1.13 parsing gaps (see below).
    // DOCX: DrawingML anchors. DOC: pictures outside the Data stream.
    let bytes = match format {
        DocumentFormat::Docx => shim_align_positioned_anchors(&bytes),
        DocumentFormat::Doc => shim_doc_images_to_data_stream(&bytes),
        _ => bytes,
    };

    let rendered = catch_unwind(AssertUnwindSafe(|| render(&bytes, format, mode)));
    let text = match rendered {
        Ok(Ok(text)) => text,
        Ok(Err(code)) => {
            set_last_error(code);
            return std::ptr::null_mut();
        }
        Err(_) => {
            set_last_error(OFFICE_BRIDGE_ERR_INTERNAL);
            return std::ptr::null_mut();
        }
    };
    // Legacy DOC charts backed by an embedded ODF chart document surface as
    // data paragraphs (below); every other format renders unchanged.
    let text = if format == DocumentFormat::Doc {
        splice_doc_chart_text(&text, &bytes)
    } else {
        text
    };

    // Legacy containers can legitimately carry NUL bytes in their text tables.
    // They have no meaning in a text/HTML fragment, so replace rather than
    // fail the whole document.
    let text = text.replace('\0', "\u{FFFD}");
    match CString::new(text) {
        Ok(cstring) => {
            let content_len = cstring.as_bytes().len();
            *out_len = content_len;
            cstring.into_raw()
        }
        Err(_) => {
            set_last_error(OFFICE_BRIDGE_ERR_INTERNAL);
            std::ptr::null_mut()
        }
    }
}

/// Compatibility shim for an office_oxide 0.1.13 parsing gap: a floating image
/// positioned with `<wp:align>` instead of `<wp:posOffset>` parses to
/// `anchor_position = None`, and `drawing_to_float_element` then discards the
/// image bytes entirely (the `images` map *is* populated — the drawing is
/// dropped). Word center-aligns pictures this way by default, so this is the
/// common case, not an edge.
///
/// Rewrites `<wp:align>…</wp:align>` to `<wp:posOffset>0</wp:posOffset>` in the
/// DOCX content parts before parsing. HTML output ignores positioning, so the
/// zero offset changes nothing visible; it only makes the image reachable.
/// Returns the input unchanged when there is nothing to rewrite (including when
/// the bytes are not a readable zip — the parse below then fails cleanly as
/// before). Remove when upstream emits images for align-positioned anchors.
fn shim_align_positioned_anchors(bytes: &[u8]) -> Vec<u8> {
    const ALIGN_OPEN: &[u8] = b"<wp:align>";
    const ALIGN_CLOSE: &[u8] = b"</wp:align>";
    const POS_OFFSET: &[u8] = b"<wp:posOffset>0</wp:posOffset>";

    /// Content parts that may carry anchored drawings. Styles, numbering,
    /// charts and embeddings cannot hold `wp:align` and are left alone.
    fn is_content_part(name: &str) -> bool {
        name == "word/document.xml"
            || name == "word/footnotes.xml"
            || name == "word/endnotes.xml"
            || name == "word/comments.xml"
            || (name.starts_with("word/header") || name.starts_with("word/footer"))
                && name.ends_with(".xml")
    }

    fn rewrite_xml(xml: &[u8]) -> Option<Vec<u8>> {
        if !xml.windows(ALIGN_OPEN.len()).any(|w| w == ALIGN_OPEN) {
            return None;
        }
        let mut out = Vec::with_capacity(xml.len());
        let mut rest = xml;
        let mut rewritten = false;
        while let Some(start) = rest.windows(ALIGN_OPEN.len()).position(|w| w == ALIGN_OPEN) {
            let after_open = start + ALIGN_OPEN.len();
            let Some(end) = rest[after_open..]
                .windows(ALIGN_CLOSE.len())
                .position(|w| w == ALIGN_CLOSE)
            else {
                break;
            };
            out.extend_from_slice(&rest[..start]);
            out.extend_from_slice(POS_OFFSET);
            rest = &rest[after_open + end + ALIGN_CLOSE.len()..];
            rewritten = true;
        }
        out.extend_from_slice(rest);
        rewritten.then_some(out)
    }

    let mut archive = match zip::ZipArchive::new(Cursor::new(bytes)) {
        Ok(archive) => archive,
        Err(_) => return bytes.to_vec(),
    };
    // Read first: ZipArchive borrows `bytes`, ZipWriter needs its own buffer.
    let mut entries: Vec<(String, Vec<u8>)> = Vec::new();
    let mut touched = false;
    for index in 0..archive.len() {
        let mut file = match archive.by_index(index) {
            Ok(file) => file,
            Err(_) => return bytes.to_vec(),
        };
        let name = file.name().to_string();
        let mut contents = Vec::new();
        if file.read_to_end(&mut contents).is_err() {
            return bytes.to_vec();
        }
        if is_content_part(&name) {
            if let Some(rewritten) = rewrite_xml(&contents) {
                contents = rewritten;
                touched = true;
            }
        }
        entries.push((name, contents));
    }
    if !touched {
        return bytes.to_vec();
    }
    let mut buffer = Vec::new();
    {
        let mut writer = zip::ZipWriter::new(Cursor::new(&mut buffer));
        for (name, contents) in &entries {
            // Store without recompressing: the bytes were already compressed
            // once, and import latency matters more than a few saved bytes.
            let options = zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Stored);
            if writer.start_file(name, options).is_err() {
                return bytes.to_vec();
            }
            if writer.write_all(contents).is_err() {
                return bytes.to_vec();
            }
        }
        if writer.finish().is_err() {
            return bytes.to_vec();
        }
    }
    buffer
}

/// Compatibility shim for an office_oxide 0.1.13 coverage gap: its DOC reader
/// extracts pictures by scanning only the `Data` stream for OfficeArt BLIP
/// records (`doc/images.rs::extract_images`), but some writers — LibreOffice
/// conversions included — store the BLIPs in the main `WordDocument` stream
/// instead. This repo's `file-sample_1MB.doc` carries its 1 MB JPEG there
/// while `Data` is 191 bytes, so every picture was silently dropped.
///
/// When the `Data` scan finds nothing but the main stream holds decodable
/// pictures, the main stream's bytes are appended to `Data` so the stock scan
/// finds them. Appends only grow the stream, so existing FIB offsets into
/// `Data` are preserved; conversion then proceeds untouched and emits the
/// pictures as end-of-document images, which is upstream's designed placement
/// for legacy DOC pictures (`convert_doc.rs::append_legacy_images`).
///
/// The gate is office_oxide's own scanner, so the shim only fires when the
/// current behavior drops everything — otherwise the bytes pass through
/// unchanged (including when they are not a readable CFB, in which case the
/// parse below fails cleanly as before). Remove when upstream scans the main
/// stream itself.
fn shim_doc_images_to_data_stream(bytes: &[u8]) -> Vec<u8> {
    use office_oxide::cfb::CfbReader;
    use office_oxide::doc::images::extract_images;

    let mut archive = match CfbReader::new(Cursor::new(bytes)) {
        Ok(archive) => archive,
        Err(_) => return bytes.to_vec(),
    };
    let data = archive.open_stream("Data").unwrap_or_default();
    if !extract_images(&data).is_empty() {
        return bytes.to_vec();
    }
    let word_document = match archive.open_stream("WordDocument") {
        Ok(stream) => stream,
        Err(_) => return bytes.to_vec(),
    };
    if extract_images(&word_document).is_empty() {
        return bytes.to_vec();
    }

    let mut compound = match cfb::CompoundFile::open(Cursor::new(bytes.to_vec())) {
        Ok(compound) => compound,
        Err(_) => return bytes.to_vec(),
    };
    let appended = (|| -> std::io::Result<()> {
        if !compound.exists("Data") {
            compound.create_stream("Data")?;
        }
        let mut stream = compound.open_stream("Data")?;
        stream.seek(SeekFrom::End(0))?;
        stream.write_all(&word_document)?;
        stream.flush()?;
        Ok(())
    })();
    if appended.is_err() {
        return bytes.to_vec();
    }
    compound.into_inner().into_inner()
}

/// Placeholder HTML office_oxide emits for an embedded OLE object it
/// identifies but cannot render (`convert_doc.rs`: a data-less `Image` with
/// the object's description as alt text).
const OLE_PLACEHOLDER_HTML: &str = "<figure><figcaption>Embedded OLE Object</figcaption></figure>";

/// Enrichment for legacy DOC charts backed by an embedded ODF chart document:
/// LibreOffice writes `ProgID LibreOffice.ChartDocument.1` plus a
/// `package_stream` holding an ODF chart package whose data table carries the
/// categories, series and values. That table is rendered as plain paragraphs
/// in the same shape office_oxide renders DrawingML charts for DOCX
/// (`Categories: …` + one line per series), replacing the generic OLE
/// placeholder one-for-one; leftovers (more charts than placeholders, or no
/// placeholder at all) are appended at the end, where legacy pictures land.
///
/// Anything unrecognized — not a CFB, no chart ProgID, payload not a zip,
/// table missing or empty — yields no paragraphs and the HTML passes through
/// untouched, so enrichment can never fail an import the plain render would
/// have survived. MSGraph (`MSGraph.Chart.8`) objects are binary MS-OGRAPH,
/// not ODF, and stay placeholders. Remove if upstream ever renders them.
fn splice_doc_chart_text(html: &str, bytes: &[u8]) -> String {
    let charts = doc_chart_paragraphs(bytes);
    if charts.is_empty() {
        return html.to_string();
    }
    let blocks: Vec<String> = charts
        .iter()
        .map(|paragraphs| {
            paragraphs
                .iter()
                .map(|text| format!("<p>{}</p>", escape_html_text(text)))
                .collect::<Vec<_>>()
                .join("\n")
        })
        .collect();
    let mut html = html.to_string();
    for block in &blocks {
        if html.contains(OLE_PLACEHOLDER_HTML) {
            html = html.replacen(OLE_PLACEHOLDER_HTML, block, 1);
        } else {
            html.push_str("\n<hr />\n");
            html.push_str(block);
        }
    }
    html
}

/// Chart data paragraphs (one list per chart object) for a legacy DOC, or
/// empty when the file holds no ODF-backed chart.
fn doc_chart_paragraphs(bytes: &[u8]) -> Vec<Vec<String>> {
    let mut compound = match cfb::CompoundFile::open(Cursor::new(bytes)) {
        Ok(compound) => compound,
        Err(_) => return Vec::new(),
    };
    // Direct children of the ObjectPool storage only, mirroring upstream's
    // own OLE walk: deeper nesting is another object's business.
    let objects: Vec<String> = compound
        .walk()
        .filter(|entry| entry.is_storage())
        .filter_map(|entry| {
            let path = entry.path();
            if !is_direct_child(path, "ObjectPool") {
                return None;
            }
            path.components()
                .next_back()?
                .as_os_str()
                .to_str()
                .map(str::to_string)
        })
        .collect();
    let mut charts = Vec::new();
    for object in objects {
        let base = format!("ObjectPool/{object}");
        let comp_obj = read_cfb_stream(&mut compound, &format!("{base}/\u{1}CompObj"));
        // Gate on the chart ProgID so Excel/Equation/Word objects keep their
        // placeholders: only chart objects can carry an ODF chart package.
        let is_chart = comp_obj
            .map(|data| data.windows(5).any(|w| w.eq_ignore_ascii_case(b"chart")))
            .unwrap_or(false);
        if !is_chart {
            continue;
        }
        let Some(package) = read_cfb_stream(&mut compound, &format!("{base}/package_stream"))
        else {
            continue;
        };
        if let Some(paragraphs) = odf_chart_paragraphs(&package) {
            charts.push(paragraphs);
        }
    }
    charts
}

/// True when `path` is exactly `parent` + one component (walk paths carry a
/// leading root, so this compares names, not raw components).
fn is_direct_child(path: &std::path::Path, parent: &str) -> bool {
    let names: Vec<_> = path
        .components()
        .filter_map(|component| match component {
            std::path::Component::Normal(name) => name.to_str(),
            _ => None,
        })
        .collect();
    names.len() == 2 && names[0] == parent
}

fn read_cfb_stream<F: std::io::Read + std::io::Seek>(
    compound: &mut cfb::CompoundFile<F>,
    path: &str,
) -> Option<Vec<u8>> {
    use std::io::Read;
    let mut stream = compound.open_stream(path).ok()?;
    let mut data = Vec::new();
    stream.read_to_end(&mut data).ok()?;
    Some(data)
}

/// Paragraphs for an ODF chart package (`package_stream` bytes): the first
/// table's header row names the series (past the corner cell), its first
/// column the categories, and the body the values — the same convention the
/// DOCX chart extractor emits. `None` when the payload is not a chart table.
fn odf_chart_paragraphs(package: &[u8]) -> Option<Vec<String>> {
    if package.len() < 2 || &package[0..2] != b"PK" {
        return None;
    }
    let mut archive = zip::ZipArchive::new(Cursor::new(package)).ok()?;
    let mut content = String::new();
    archive
        .by_name("content.xml")
        .ok()?
        .read_to_string(&mut content)
        .ok()?;
    let table = first_odf_table(&content)?;
    if table.len() < 2 || table[0].len() < 2 {
        return None;
    }
    let categories: Vec<&str> = table[1..].iter().map(|row| row[0].as_str()).collect();
    if categories.iter().all(|c| c.is_empty()) {
        return None;
    }
    let mut paragraphs = vec![format!("Categories: {}", categories.join(", "))];
    for (index, series) in table[0][1..].iter().enumerate() {
        if series.is_empty() {
            continue;
        }
        let values: Vec<&str> = table[1..]
            .iter()
            .map(|row| row.get(index + 1).map(String::as_str).unwrap_or(""))
            .collect();
        paragraphs.push(format!("{series}: {}", values.join(", ")));
    }
    if paragraphs.len() < 2 {
        return None;
    }
    Some(paragraphs)
}

/// Rows of the first `<table:table>` in ODF `content.xml`: each cell is its
/// `office:value` when present, else its text content. `table:covered-table-cell`
/// contributes an empty cell so columns stay aligned; `number-columns-repeated`
/// repeats the cell. Local tag names only, so writer prefix choices don't matter.
fn first_odf_table(xml: &str) -> Option<Vec<Vec<String>>> {
    use quick_xml::events::Event;
    let mut reader = quick_xml::Reader::from_str(xml);
    let mut table: Vec<Vec<String>> = Vec::new();
    let mut row: Vec<String> = Vec::new();
    let mut in_table = false;
    let mut table_done = false;
    let mut in_row = false;
    let mut cell_value: Option<String> = None;
    let mut cell_repeated = 1usize;
    let mut cell_text = String::new();
    let mut in_cell = false;
    loop {
        match reader.read_event() {
            Err(_) => return None,
            Ok(Event::Eof) => break,
            Ok(Event::Start(element)) => match element.local_name().as_ref() {
                "table" if !table_done => in_table = true,
                "table-row" if in_table => {
                    in_row = true;
                    row = Vec::new();
                }
                "table-cell" if in_table && in_row => {
                    in_cell = true;
                    let (value, repeated) = read_cell_attrs(&element);
                    cell_value = value;
                    cell_repeated = repeated;
                    cell_text.clear();
                }
                _ => {}
            },
            Ok(Event::Empty(element)) => match element.local_name().as_ref() {
                "covered-table-cell" if in_table && in_row => row.push(String::new()),
                "table-cell" if in_table && in_row => {
                    // Self-closed cells (`<table:table-cell office:value="9.1"/>`)
                    // carry everything in attributes; LibreOffice emits both forms.
                    let (value, repeated) = read_cell_attrs(&element);
                    let cell = value.unwrap_or_default();
                    for _ in 0..repeated {
                        row.push(cell.clone());
                    }
                }
                _ => {}
            },
            Ok(Event::Text(text)) => {
                if in_cell {
                    cell_text.push_str(&text.xml10_content());
                }
            }
            // Entity references arrive as their own events (`R`, `&amp;`, `D`
            // come through separately), so resolve them here or text loses
            // every `&`.
            Ok(Event::GeneralRef(reference)) => {
                if in_cell {
                    cell_text.push_str(&resolve_entity_ref(&reference));
                }
            }
            Ok(Event::End(element)) => match element.local_name().as_ref() {
                "table-cell" if in_cell => {
                    in_cell = false;
                    let cell = cell_value
                        .take()
                        .unwrap_or_else(|| unescape_xml_attr(cell_text.trim()));
                    for _ in 0..cell_repeated {
                        row.push(cell.clone());
                    }
                    cell_repeated = 1;
                }
                "table-row" if in_row => {
                    in_row = false;
                    table.push(std::mem::take(&mut row));
                }
                "table" if in_table => {
                    in_table = false;
                    table_done = true;
                }
                _ => {}
            },
            _ => {}
        }
    }
    if table.is_empty() {
        return None;
    }
    Some(table)
}

/// Cell attributes for `<table:table-cell>` in either Start or Empty form:
/// its `office:value` when present and its `number-columns-repeated` span.
fn read_cell_attrs(element: &quick_xml::events::BytesStart) -> (Option<String>, usize) {
    let mut value = None;
    let mut repeated = 1usize;
    for attribute in element.attributes().flatten() {
        let name = attribute.key.local_name();
        // Attribute values arrive unresolved, so unescape here; the
        // pass-through fallback keeps anything already plain.
        let text = unescape_xml_attr(&attribute.value);
        match name.as_ref() {
            "value" => value = Some(text),
            "number-columns-repeated" => repeated = text.parse().unwrap_or(1).max(1),
            _ => {}
        }
    }
    (value, repeated)
}

/// Resolves one `&name;` reference to its character; unknown names pass
/// through literally so the cell keeps them instead of losing content.
fn resolve_entity_ref(reference: &str) -> String {
    match reference {
        "amp" => "&".to_string(),
        "lt" => "<".to_string(),
        "gt" => ">".to_string(),
        "quot" => "\"".to_string(),
        "apos" => "'".to_string(),
        _ => {
            let code = reference.strip_prefix('#').and_then(|number| {
                if let Some(hex) = number
                    .strip_prefix('x')
                    .or_else(|| number.strip_prefix('X'))
                {
                    u32::from_str_radix(hex, 16).ok()
                } else {
                    number.parse().ok()
                }
            });
            code.and_then(char::from_u32)
                .map(|char| char.to_string())
                .unwrap_or_else(|| format!("&{reference};"))
        }
    }
}

/// Entity-unescape for ODF attribute/text content (`&amp;` → `&`);
/// pass-through on anything malformed rather than dropping the cell.
fn unescape_xml_attr(raw: &str) -> String {
    quick_xml::escape::unescape(raw)
        .map(|text| text.into_owned())
        .unwrap_or_else(|_| raw.to_string())
}

/// Minimal `& < > "` escaping for text injected into the HTML fragment.
fn escape_html_text(text: &str) -> String {
    let mut escaped = String::with_capacity(text.len());
    for char in text.chars() {
        match char {
            '&' => escaped.push_str("&amp;"),
            '<' => escaped.push_str("&lt;"),
            '>' => escaped.push_str("&gt;"),
            '"' => escaped.push_str("&quot;"),
            _ => escaped.push(char),
        }
    }
    escaped
}

fn render(bytes: &[u8], format: DocumentFormat, mode: i32) -> Result<String, i32> {
    let document = Document::from_reader(Cursor::new(bytes.to_vec()), format).map_err(map_error)?;
    let text = match mode {
        OFFICE_BRIDGE_TEXT => document.plain_text(),
        OFFICE_BRIDGE_MARKDOWN => document.to_markdown(),
        OFFICE_BRIDGE_HTML => document.to_html(),
        OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES => document.to_html_with(HtmlOptions {
            image_embed: ImageEmbed::Base64,
        }),
        OFFICE_BRIDGE_MARKDOWN_WITH_IMAGES => document.to_markdown_with(MarkdownOptions {
            image_embed: ImageEmbed::Base64,
        }),
        _ => return Err(OFFICE_BRIDGE_ERR_ARGUMENT),
    };
    Ok(text)
}

/// Returns 1 when `ext` maps to a format this bridge can read, 0 otherwise.
/// Lets the app drive `FileCapabilities` from the library instead of a
/// hardcoded table.
///
/// # Safety
/// `ext` must be a NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn office_bridge_supports(ext: *const c_char) -> i32 {
    match string_argument(ext) {
        Some(ext) if parse_extension(ext).is_some() => 1,
        _ => 0,
    }
}

/// Returns the error code set by the last failing call on this thread.
#[no_mangle]
pub extern "C" fn office_bridge_last_error() -> i32 {
    LAST_ERROR.with(Cell::get)
}

/// Returns a `'static` build identifier for diagnostics. Do not free.
#[no_mangle]
pub extern "C" fn office_bridge_version() -> *const c_char {
    concat!(
        "office-oxide-bridge/",
        env!("CARGO_PKG_VERSION"),
        " office_oxide=0.1.13"
    )
    .as_ptr()
    .cast::<c_char>()
}

/// Releases a buffer returned by [`office_bridge_render`]. Passing NULL is a
/// no-op. Passing any other pointer is undefined behaviour.
///
/// # Safety
/// `ptr` must come from a successful [`office_bridge_render`] call and must not
/// have been freed already.
#[no_mangle]
pub unsafe extern "C" fn office_bridge_free(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    drop(CString::from_raw(ptr));
}

#[cfg(test)]
mod tests;
