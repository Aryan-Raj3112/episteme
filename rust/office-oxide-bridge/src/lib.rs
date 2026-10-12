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
use std::io::Cursor;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::slice;

use office_oxide::ir_render::{HtmlOptions, ImageEmbed, MarkdownOptions};
use office_oxide::{Document, DocumentFormat, OfficeError};

/// ABI revision. Bump when an existing function changes its signature or
/// meaning; never when merely adding a function.
pub const OFFICE_BRIDGE_ABI_VERSION: u32 = 1;

/// Input cap, matching `SingleFileImporter.MAX_DOCX_ARCHIVE_BYTES` (64 MiB) so
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
