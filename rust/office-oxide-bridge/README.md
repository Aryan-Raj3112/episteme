# office-oxide-bridge

Stable C ABI over [`office_oxide`](https://crates.io/crates/office_oxide), consumed by the
Reader app on Android (JNI), iOS (cinterop) and desktop. Plan and rationale:
[`docs/office-oxide-integration-plan.md`](../../docs/office-oxide-integration-plan.md).

This crate exists so the app depends on **our** six functions rather than upstream's 45. See
the module docs in `src/lib.rs` for why, and for the memory contract.

## Files

| File | Purpose |
|---|---|
| `Cargo.toml` | Deps (exact pin) + the size-critical release profile |
| `rust-toolchain.toml` | Pins rustc 1.99.0 and the target list, so every machine and CI builds identical code |
| `src/lib.rs` | The ABI |
| `src/tests.rs` | Builds a minimal DOCX in memory and asserts every mode and failure path |
| `.cargo/config.toml.example` | Copy to `.cargo/config.toml` for local cross-builds (see below) |

## The ABI

```c
// Render a document held in memory. Returns NULL on failure; read
// office_bridge_last_error() on the same thread.
char*       office_bridge_render(const uint8_t* data, size_t len,
                                 const char* ext, int mode, size_t* out_len);
int         office_bridge_supports(const char* ext);   // 1 = we can read it
int         office_bridge_last_error(void);            // thread-local
const char* office_bridge_version(void);                // 'static, do not free
void        office_bridge_free(char* ptr);              // NULL-safe
```

`out_len` receives the content length excluding the NUL terminator. The returned buffer is
allocated here and must be released exactly once with `office_bridge_free`.

| `mode` | Output |
|---|---|
| `0` (`OFFICE_BRIDGE_TEXT`) | Plain text — search, TTS, library indexing |
| `1` (`OFFICE_BRIDGE_MARKDOWN`) | Markdown |
| `2` (`OFFICE_BRIDGE_HTML`) | HTML fragment — the DOCX import path |
| `3` (`OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES`) | HTML with images inline as `data:` URIs, self-contained |
| `4` (`OFFICE_BRIDGE_MARKDOWN_WITH_IMAGES`) | Markdown with images inline as base64 |

Error codes (never renumber — this is an interface): `0` OK, `1` argument, `2` unsupported,
`3` too large, `4` read, `5` internal.

`ext` accepts `docx`, `.docx`, `DOCX`, `.DOCX` and likewise for `xlsx`, `pptx`, `doc`, `xls`,
`ppt`. Input is capped at 64 MiB, matching `SingleFileImporter.MAX_DOCX_ARCHIVE_BYTES`.

## Build

```sh
# Tests, lint, format
cargo test
cargo clippy --all-targets -- -D warnings
cargo fmt -- --check

# One target per shipped ABI. Add the linker config first, or Gradle does it for you.
cp .cargo/config.toml.example .cargo/config.toml    # then edit NDK path
cargo build --release --target aarch64-linux-android      # Android arm64-v8a
cargo build --release --target armv7-linux-androideabi    # Android armeabi-v7a
cargo build --release --target aarch64-apple-ios          # iOS device
cargo build --release --target aarch64-apple-ios-sim      # iOS simulator
cargo build --release --target aarch64-apple-darwin       # desktop
```

Outputs: `target/<triple>/release/liboffice_oxide_bridge.{so,dylib}` (cdylib) and
`liboffice_oxide_bridge.a` (staticlib).

## Compatibility shims

`render()` pre-processes the input for known upstream parsing gaps before handing it
to office_oxide. Each shim is byte-identical when inapplicable and documented for
removal once upstream fixes the gap:

- **Align-positioned floating images** (`shim_align_positioned_anchors`, DOCX only):
  office_oxide 0.1.13 discards `wp:anchor` drawings positioned with `<wp:align>`
  instead of `<wp:posOffset>` (Word's default for centered pictures), losing the
  image bytes. The shim rewrites them to `<wp:posOffset>0</wp:posOffset>` in the
  content parts; HTML output ignores positioning, so only reachability changes.
  Covered by `align_positioned_floating_image_is_embedded_not_dropped`.

Cross-compiling without Gradle needs the linker set, otherwise cargo uses the host `cc`:

```sh
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi26-clang"
```

## Size

Measured with the profile in `Cargo.toml` (`opt-level = "s"`, LTO, `codegen-units = 1`,
`panic = "unwind"`, stripped):

| Target | cdylib | staticlib |
|---|---:|---:|
| `aarch64-linux-android` | **2.76 MB** | 14.8 MB |
| `armv7-linux-androideabi` | **2.11 MB** | 11.9 MB |
| `aarch64-apple-ios` | 2.75 MB | 9.8 MB |
| `aarch64-apple-ios-sim` | 2.75 MB | 9.8 MB |
| `aarch64-apple-darwin` | 2.77 MB | 9.8 MB |

The `.a` figures are **not** shipped size: an archive holds every object plus its LTO bitcode
(375 objects here), and the final link dead-strips to roughly the cdylib number. iOS will pull
only what `ReaderShared` references.

`panic = "unwind"` rather than `abort` is deliberate — the entry points `catch_unwind`, so a
library panic becomes error code 5 instead of killing the app.
