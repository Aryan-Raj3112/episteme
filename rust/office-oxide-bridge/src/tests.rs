/// Guards the C ABI contract end-to-end: a real DOCX built in memory, rendered
/// through the exported `extern "C"` functions, plus every failure path.
///
/// These call the functions exactly as Kotlin does (same signatures, same
/// pointer semantics), so a signature or ownership regression fails here rather
/// than on a device.
use super::*;
use std::ffi::{CStr, CString};
use std::io::{Cursor, Read, Write};
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

const CONTENT_TYPES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"#;

const RELS: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"#;

const DOCUMENT: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
<w:p><w:r><w:t xml:space="preserve">Plain </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:t xml:space="preserve"> and </w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t>italic</w:t></w:r><w:r><w:t>.</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>CellA</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>CellB</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
</w:body></w:document>"#;

fn minimal_docx() -> Vec<u8> {
    let mut buffer = Vec::new();
    {
        let mut zip = ZipWriter::new(Cursor::new(&mut buffer));
        let options = SimpleFileOptions::default();
        for (name, contents) in [
            ("[Content_Types].xml", CONTENT_TYPES),
            ("_rels/.rels", RELS),
            ("word/document.xml", DOCUMENT),
        ] {
            zip.start_file(name, options).expect("start zip entry");
            zip.write_all(contents.as_bytes()).expect("write zip entry");
        }
        zip.finish().expect("finish zip");
    }
    buffer
}

/// Mirrors the Kotlin call: pass bytes, get a NUL-terminated buffer plus its
/// content length, then free it.
unsafe fn render(bytes: &[u8], ext: &str, mode: i32) -> Option<(String, usize)> {
    let ext = CString::new(ext).ok()?;
    let mut len = 0usize;
    let ptr = office_bridge_render(bytes.as_ptr(), bytes.len(), ext.as_ptr(), mode, &mut len);
    if ptr.is_null() {
        return None;
    }
    let text = CStr::from_ptr(ptr).to_string_lossy().into_owned();
    office_bridge_free(ptr);
    Some((text, len))
}

unsafe fn last_error() -> i32 {
    office_bridge_last_error()
}

#[test]
fn renders_every_mode_with_formatting_preserved() {
    let docx = minimal_docx();

    let (text, _) = unsafe { render(&docx, "docx", OFFICE_BRIDGE_TEXT) }.expect("text render");
    assert!(text.contains("Plain bold and italic."), "got: {text}");
    assert!(text.contains("CellA\tCellB"), "table cells missing: {text}");

    let (markdown, _) =
        unsafe { render(&docx, "docx", OFFICE_BRIDGE_MARKDOWN) }.expect("md render");
    assert!(markdown.contains("**bold**"), "got: {markdown}");
    assert!(markdown.contains("*italic*"), "got: {markdown}");
    assert!(
        markdown.contains("| CellA | CellB |"),
        "md table missing: {markdown}"
    );

    let (html, _) = unsafe { render(&docx, "docx", OFFICE_BRIDGE_HTML) }.expect("html render");
    assert!(html.contains("<strong>bold</strong>"), "got: {html}");
    assert!(html.contains("<em>italic</em>"), "got: {html}");
    assert!(
        html.contains("<td><p>CellA</p></td>"),
        "html table missing: {html}"
    );

    // The embedded-image mode must stay valid HTML and inline any media.
    let (embedded, _) = unsafe { render(&docx, "docx", OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES) }
        .expect("embedded html render");
    assert!(
        embedded.contains("<strong>bold</strong>"),
        "got: {embedded}"
    );
}

#[test]
fn extension_is_accepted_with_or_without_a_dot_in_any_case() {
    let docx = minimal_docx();
    for ext in ["docx", ".docx", "DOCX", ".DoCx"] {
        assert!(
            unsafe { render(&docx, ext, OFFICE_BRIDGE_HTML) }.is_some(),
            "{ext} should render"
        );
    }
}

#[test]
fn supports_reports_the_formats_the_library_can_read() {
    for ext in ["docx", ".docx", "DOCX", "ppt", "xls", "doc"] {
        assert_eq!(
            unsafe { office_bridge_supports(CString::new(ext).unwrap().as_ptr()) },
            1
        );
    }
    // ODF and PDF are deliberately not office_oxide formats.
    for ext in ["odt", "pdf", "fodt", "epub", "zip"] {
        assert_eq!(
            unsafe { office_bridge_supports(CString::new(ext).unwrap().as_ptr()) },
            0
        );
    }
    assert_eq!(unsafe { office_bridge_supports(std::ptr::null()) }, 0);
}

#[test]
fn failure_paths_return_null_with_a_specific_code() {
    let docx = minimal_docx();

    // Unsupported extension: rejected before any parsing happens.
    assert!(unsafe { render(&docx, "odt", OFFICE_BRIDGE_HTML) }.is_none());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_UNSUPPORTED);

    // Unknown mode: rejected before any parsing happens.
    assert!(unsafe { render(&docx, "docx", 99) }.is_none());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_ARGUMENT);

    // NULL data pointer.
    let ext = CString::new("docx").unwrap();
    let mut len = 0usize;
    assert!(unsafe {
        office_bridge_render(
            std::ptr::null(),
            docx.len(),
            ext.as_ptr(),
            OFFICE_BRIDGE_HTML,
            &mut len,
        )
    }
    .is_null());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_ARGUMENT);

    // NULL out_len pointer.
    assert!(unsafe {
        office_bridge_render(
            docx.as_ptr(),
            docx.len(),
            ext.as_ptr(),
            OFFICE_BRIDGE_HTML,
            std::ptr::null_mut(),
        )
    }
    .is_null());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_ARGUMENT);

    // Bytes that are not a ZIP container at all.
    let garbage = b"this is definitely not an office document";
    assert!(unsafe { render(garbage, "docx", OFFICE_BRIDGE_HTML) }.is_none());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_READ);

    // Empty input.
    assert!(unsafe { render(&[], "docx", OFFICE_BRIDGE_HTML) }.is_none());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_READ);
}

#[test]
fn free_is_a_no_op_for_null_and_the_last_error_resets_per_call() {
    unsafe { office_bridge_free(std::ptr::null_mut()) };

    let docx = minimal_docx();
    // A failing call sets the code; the next successful call clears it.
    assert!(unsafe { render(&docx, "odt", OFFICE_BRIDGE_HTML) }.is_none());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_ERR_UNSUPPORTED);

    assert!(unsafe { render(&docx, "docx", OFFICE_BRIDGE_TEXT) }.is_some());
    assert_eq!(unsafe { last_error() }, OFFICE_BRIDGE_OK);
}

#[test]
fn reports_a_version_string_for_diagnostics() {
    let version = unsafe { CStr::from_ptr(office_bridge_version()) };
    let version = version.to_string_lossy();
    assert!(version.contains("office-oxide-bridge/"), "got: {version}");
    assert!(version.contains("office_oxide=0.1.13"), "got: {version}");
}

/// NUL bytes are legal in legacy containers' text tables and must be sanitised
/// rather than failing the whole document.
#[test]
fn nul_bytes_in_output_are_sanitised_not_fatal() {
    // A tiny DOCX whose body text is a lone NUL, delivered via a numeric
    // character reference (XML forbids a literal NUL).
    let document = DOCUMENT.replace("Plain ", "\u{0}");
    let docx = minimal_docx_with_document(&document);
    let rendered = unsafe { render(&docx, "docx", OFFICE_BRIDGE_HTML) };
    assert!(rendered.is_some(), "a NUL must not fail the render");
}

fn minimal_docx_with_document(document: &str) -> Vec<u8> {
    let mut buffer = Vec::new();
    {
        let mut zip = ZipWriter::new(Cursor::new(&mut buffer));
        let options = SimpleFileOptions::default();
        for (name, contents) in [
            ("[Content_Types].xml", CONTENT_TYPES),
            ("_rels/.rels", RELS),
            ("word/document.xml", document),
        ] {
            zip.start_file(name, options).unwrap();
            zip.write_all(contents.as_bytes()).unwrap();
        }
        zip.finish().unwrap();
    }
    buffer
}

/// The office_oxide 0.1.13 gap the bridge shims: a floating image positioned
/// with `<wp:align>` (Word's default for centered pictures) must still reach
/// the HTML as an embedded `<img>`, not be silently dropped.
#[test]
fn align_positioned_floating_image_is_embedded_not_dropped() {
    let docx = docx_with_align_positioned_image();
    let (html, _) = unsafe { render(&docx, "docx", OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES) }
        .expect("align-positioned image must render");
    assert!(
        html.contains("<img src=\"data:image/png;base64,"),
        "image bytes missing from HTML, got: {html}"
    );
    assert!(html.contains("Before"), "body text missing, got: {html}");
}

#[test]
fn shim_rewrites_only_align_anchors() {
    let docx = docx_with_align_positioned_image();
    let shimmed = shim_align_positioned_anchors(&docx);

    let read = |bytes: &[u8], name: &str| {
        let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).unwrap();
        let mut out = Vec::new();
        archive
            .by_name(name)
            .unwrap()
            .read_to_end(&mut out)
            .unwrap();
        String::from_utf8(out).unwrap()
    };
    let document = read(&shimmed, "word/document.xml");
    assert!(
        !document.contains("<wp:align>"),
        "align survived: {document}"
    );
    assert!(
        document.contains("<wp:posOffset>0</wp:posOffset>"),
        "no posOffset written: {document}"
    );
    // Untouched parts stay byte-identical.
    assert_eq!(
        read(&shimmed, "[Content_Types].xml"),
        read(&docx, "[Content_Types].xml")
    );

    // No align anywhere: byte-identical output, no re-zip churn.
    let plain = minimal_docx();
    assert_eq!(shim_align_positioned_anchors(&plain), plain);

    // Not a zip at all: returned unchanged for the parse below to reject.
    assert_eq!(shim_align_positioned_anchors(b"not a zip"), b"not a zip");
}

/// Minimal DOCX whose only image is an align-positioned floating anchor —
/// the exact shape Word writes for a centered picture.
fn docx_with_align_positioned_image() -> Vec<u8> {
    const TYPES: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"#;
    const DOC_RELS: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
</Relationships>"#;
    const ANCHORED_DOCUMENT: &str = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><w:body>
<w:p><w:r><w:t>Before</w:t></w:r></w:p>
<w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" locked="0" layoutInCell="1" allowOverlap="1" relativeHeight="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="column"><wp:align>center</wp:align></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:align>top</wp:align></wp:positionV><wp:extent cx="9525" cy="9525"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:wrapSquare wrapText="largest"/><wp:docPr id="1" name="Picture 1" descr=""/><wp:cNvGraphicFramePr/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="1" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="9525" cy="9525"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>
</w:body></w:document>"#;
    let mut buffer = Vec::new();
    {
        let mut zip = ZipWriter::new(Cursor::new(&mut buffer));
        let options = SimpleFileOptions::default();
        for (name, contents) in [
            ("[Content_Types].xml", TYPES),
            ("_rels/.rels", RELS),
            ("word/document.xml", ANCHORED_DOCUMENT),
            ("word/_rels/document.xml.rels", DOC_RELS),
        ] {
            zip.start_file(name, options).unwrap();
            zip.write_all(contents.as_bytes()).unwrap();
        }
        // Content is irrelevant (raw bytes are embedded, never decoded).
        zip.start_file("word/media/image1.png", options).unwrap();
        zip.write_all(b"fakepngbytes").unwrap();
        zip.finish().unwrap();
    }
    buffer
}

/// A minimal OfficeArt JPEG BLIP: record header + 17 UID bytes + a payload
/// starting with the JPEG SOI marker — exactly what `extract_images` needs to
/// accept it (it signature-checks the payload, never decodes it).
fn jpeg_blip() -> Vec<u8> {
    let payload = [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10];
    let mut blip = Vec::new();
    blip.extend_from_slice(&0u16.to_le_bytes()); // ver + inst 0
    blip.extend_from_slice(&0xF01Du16.to_le_bytes()); // msofbtBlipJPEG
    blip.extend_from_slice(&(17u32 + payload.len() as u32).to_le_bytes());
    blip.extend_from_slice(&[0u8; 17]); // UID + tag
    blip.extend_from_slice(&payload);
    blip
}

/// A minimal CFB with just the two streams the DOC shim cares about.
fn minimal_doc_cfb(data_stream: &[u8], word_stream: &[u8]) -> Vec<u8> {
    let mut compound = cfb::CompoundFile::create(Cursor::new(Vec::new())).expect("create cfb");
    for (name, contents) in [("Data", data_stream), ("WordDocument", word_stream)] {
        compound
            .create_stream(name)
            .expect("create stream")
            .write_all(contents)
            .expect("write stream");
    }
    compound.into_inner().into_inner()
}

/// The legacy-DOC gap the bridge shims: pictures stored in the main
/// `WordDocument` stream (LibreOffice conversions do this) must still be
/// recovered when the `Data` scan comes up empty — not silently dropped.
#[test]
fn doc_pictures_outside_data_stream_are_recovered() {
    let cfb = minimal_doc_cfb(&[0u8; 64], &jpeg_blip());
    let shimmed = shim_doc_images_to_data_stream(&cfb);
    assert_ne!(
        shimmed, cfb,
        "shim must fire: Data scan is empty, main stream has a picture"
    );

    // The rewritten container still opens, and its Data stream now yields
    // the picture through office_oxide's own scanner.
    let mut archive = office_oxide::cfb::CfbReader::new(Cursor::new(&shimmed)).expect("re-open");
    let data = archive.open_stream("Data").expect("Data stream");
    let found = office_oxide::doc::images::extract_images(&data);
    assert_eq!(found.len(), 1, "expected the recovered picture");
    assert!(
        found[0].data.starts_with(&[0xFF, 0xD8]),
        "payload must be the JPEG"
    );
    // The main stream itself is untouched by the rewrite.
    let word = archive.open_stream("WordDocument").expect("main stream");
    assert_eq!(word, jpeg_blip());
}

#[test]
fn doc_shim_is_byte_identical_when_nothing_needs_recovery() {
    // Pictures already in Data: untouched.
    let blip = jpeg_blip();
    let cfb = minimal_doc_cfb(&blip, &[0u8; 64]);
    assert_eq!(shim_doc_images_to_data_stream(&cfb), cfb);

    // No pictures anywhere: untouched.
    let bare = minimal_doc_cfb(&[0u8; 64], &[0u8; 64]);
    assert_eq!(shim_doc_images_to_data_stream(&bare), bare);

    // Not a CFB at all: untouched, for the parse below to reject.
    let garbage = b"this is definitely not a compound document".to_vec();
    assert_eq!(shim_doc_images_to_data_stream(&garbage), garbage);
}

/// Minimal ODF chart `content.xml`: corner + series header row, one row per
/// category, values as `office:value`, names as text (one entity-escaped).
fn chart_content_xml() -> String {
    let mut xml = String::from(
        r#"<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"><office:body><table:table table:name="local-table">"#,
    );
    xml.push_str("<table:table-row><table:table-cell/>");
    for series in ["Column 1", "R&amp;D"] {
        xml.push_str(&format!(
            "<table:table-cell><text:p>{series}</text:p></table:table-cell>"
        ));
    }
    xml.push_str("</table:table-row>");
    for (category, values) in [("Row 1", ["9.1", "3.2"]), ("Row 2", ["2.4", "8.8"])] {
        xml.push_str(&format!(
            "<table:table-row><table:table-cell><text:p>{category}</text:p></table:table-cell>"
        ));
        for value in values {
            xml.push_str(&format!(
                r#"<table:table-cell office:value-type="float" office:value="{value}"/>"#
            ));
        }
        xml.push_str("</table:table-row>");
    }
    xml.push_str("</table:table></office:body></office:document-content>");
    xml
}

/// A zip holding `content.xml`, like an OLE `package_stream` wraps an ODF chart.
fn chart_package(content: &str) -> Vec<u8> {
    let mut buffer = Vec::new();
    {
        let mut zip = ZipWriter::new(Cursor::new(&mut buffer));
        let options = SimpleFileOptions::default();
        zip.start_file("content.xml", options).unwrap();
        zip.write_all(content.as_bytes()).unwrap();
        zip.finish().unwrap();
    }
    buffer
}

/// A CFB whose ObjectPool holds one object with the given CompObj bytes and
/// package payload.
fn doc_cfb_with_object(comp_obj: &[u8], package: &[u8]) -> Vec<u8> {
    let mut compound = cfb::CompoundFile::create(Cursor::new(Vec::new())).expect("create cfb");
    compound.create_storage("ObjectPool").expect("pool");
    compound.create_storage("ObjectPool/obj1").expect("object");
    for (name, contents) in [
        ("ObjectPool/obj1/\u{1}CompObj", comp_obj),
        ("ObjectPool/obj1/package_stream", package),
    ] {
        compound
            .create_stream(name)
            .expect("create stream")
            .write_all(contents)
            .expect("write stream");
    }
    compound.into_inner().into_inner()
}

const CHART_COMP_OBJ: &[u8] = b"\x01\x00\xfe\xffLibreOffice.ChartDocument.1\x00Embedded Object\x00";

/// An ODF-backed chart object surfaces its data table as data paragraphs in
/// the DOCX chart-text shape — with XML entities resolved exactly once.
#[test]
fn doc_chart_package_renders_as_data_paragraphs() {
    let cfb = doc_cfb_with_object(CHART_COMP_OBJ, &chart_package(&chart_content_xml()));
    let charts = doc_chart_paragraphs(&cfb);
    assert_eq!(
        charts,
        vec![vec![
            "Categories: Row 1, Row 2".to_string(),
            "Column 1: 9.1, 2.4".to_string(),
            "R&D: 3.2, 8.8".to_string(),
        ]],
        "chart data wrong: {charts:?}"
    );

    let html = splice_doc_chart_text(
        "<p>Body</p><figure><figcaption>Embedded OLE Object</figcaption></figure>",
        &cfb,
    );
    assert!(
        html.contains("<p>Categories: Row 1, Row 2</p>"),
        "chart text missing: {html}"
    );
    assert!(
        html.contains("<p>R&amp;D: 3.2, 8.8</p>"),
        "entity must resolve once then re-escape: {html}"
    );
    assert!(
        !html.contains("Embedded OLE Object"),
        "placeholder must be replaced: {html}"
    );
}

#[test]
fn doc_chart_splice_keeps_unmatched_placeholders_and_appends_extras() {
    let cfb = doc_cfb_with_object(CHART_COMP_OBJ, &chart_package(&chart_content_xml()));
    // Two placeholders, one chart: first replaced, second kept.
    let html = splice_doc_chart_text(
        "<figure><figcaption>Embedded OLE Object</figcaption></figure><figure><figcaption>Embedded OLE Object</figcaption></figure>",
        &cfb,
    );
    assert_eq!(html.matches("Categories:").count(), 1);
    assert!(html.contains("Embedded OLE Object"));
    // No placeholder at all: appended after a rule, like legacy pictures.
    let appended = splice_doc_chart_text("<p>Body</p>", &cfb);
    assert!(appended.contains("<hr />\n<p>Categories: Row 1, Row 2</p>"));
}

#[test]
fn doc_chart_ignores_anything_that_is_not_an_odf_chart() {
    let package = chart_package(&chart_content_xml());
    // Excel objects keep their placeholders even with a chart-shaped payload.
    let excel = doc_cfb_with_object(b"Excel.Sheet.8\x00", &package);
    assert!(doc_chart_paragraphs(&excel).is_empty());
    assert!(!splice_doc_chart_text("<p>x</p>", &excel).contains("Categories:"));

    // Chart ProgID but garbage payload, zip without content, or no table.
    let garbage = doc_cfb_with_object(CHART_COMP_OBJ, b"not a zip");
    assert!(doc_chart_paragraphs(&garbage).is_empty());
    let no_content = doc_cfb_with_object(CHART_COMP_OBJ, &chart_package("<a/>"));
    assert!(doc_chart_paragraphs(&no_content).is_empty());

    // Not a CFB at all.
    assert!(doc_chart_paragraphs(b"garbage").is_empty());
    assert_eq!(splice_doc_chart_text("<p>x</p>", b"garbage"), "<p>x</p>");
}
