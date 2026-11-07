/**
 * office_oxide_bridge C API.
 *
 * Defined by rust/office-oxide-bridge (src/lib.rs) and consumed by:
 *   - Android: app/src/main/cpp/office_oxide_bridge_jni.cpp
 *   - iOS:     shared/src/nativeInterop/cinterop/office_oxide.def (cinterop)
 *
 * ABI rules:
 *   - Append new functions freely. Never change or reorder an existing one.
 *   - `office_bridge_render` returns either NULL (failure) or a NUL-terminated
 *     buffer. On success `*out_len` receives the content length, excluding the
 *     terminator.
 *   - The caller must release the buffer with `office_bridge_free`, exactly once.
 *   - `office_bridge_version` returns a 'static string. Never free it.
 *   - Every function is reentrant. `office_bridge_last_error` is thread-local:
 *     call it on the same thread that made the failing call.
 */

#ifndef OFFICE_OXIDE_BRIDGE_H
#define OFFICE_OXIDE_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Error codes. These are an interface: never renumber. */
#define OFFICE_BRIDGE_OK              0
#define OFFICE_BRIDGE_ERR_ARGUMENT    1  /* null pointer / invalid mode */
#define OFFICE_BRIDGE_ERR_UNSUPPORTED 2  /* extension or format not readable */
#define OFFICE_BRIDGE_ERR_TOO_LARGE   3  /* input exceeds 64 MiB */
#define OFFICE_BRIDGE_ERR_READ        4  /* corrupt, encrypted or wrong type */
#define OFFICE_BRIDGE_ERR_INTERNAL    5  /* panic, allocation failure, bug */

/* Render modes. */
#define OFFICE_BRIDGE_TEXT                  0
#define OFFICE_BRIDGE_MARKDOWN              1
#define OFFICE_BRIDGE_HTML                  2
#define OFFICE_BRIDGE_HTML_EMBEDDED_IMAGES  3
#define OFFICE_BRIDGE_MARKDOWN_WITH_IMAGES  4

/**
 * Render a document held in memory.
 *
 * @param data     document bytes
 * @param len      length of @p data
 * @param ext      extension, with or without a leading dot, any case
 *                 ("docx", ".docX", "xlsx", "pptx", "doc", "xls", "ppt")
 * @param mode     one of the OFFICE_BRIDGE_* render modes
 * @param out_len  receives the content length on success; may be NULL only if
 *                 the caller passes a non-NULL @p out_len that it then ignores
 * @return NUL-terminated buffer, or NULL on failure
 */
char* office_bridge_render(const uint8_t* data,
                           size_t len,
                           const char* ext,
                           int mode,
                           size_t* out_len);

/** 1 when @p ext maps to a readable Office format, 0 otherwise. */
int office_bridge_supports(const char* ext);

/** Error code from the last failing call on this thread. */
int office_bridge_last_error(void);

/** 'static build identifier for diagnostics. Do not free. */
const char* office_bridge_version(void);

/** Release a buffer from office_bridge_render. NULL is a no-op. */
void office_bridge_free(char* ptr);

#ifdef __cplusplus
}
#endif

#endif /* OFFICE_OXIDE_BRIDGE_H */
