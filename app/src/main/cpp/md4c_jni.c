/*
 * JNI bridge exposing md4c (vendored under app/src/main/cpp/md4c, MIT license)
 * to the shared Kotlin markdown pipeline. Attached to the existing `native-lib`
 * shared library, which the app process loads at startup.
 */
#include <jni.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "md4c/md4c-html.h"

typedef struct {
    char* data;
    size_t size;
    size_t capacity;
} HtmlBuffer;

static int html_buffer_append(HtmlBuffer* buffer, const char* text, MD_SIZE size) {
    if (buffer->size + (size_t) size + 1 > buffer->capacity) {
        size_t newCapacity = buffer->capacity == 0 ? 4096 : buffer->capacity;
        while (buffer->size + (size_t) size + 1 > newCapacity) {
            newCapacity *= 2;
        }
        char* grown = realloc(buffer->data, newCapacity);
        if (grown == NULL) {
            return -1;
        }
        buffer->data = grown;
        buffer->capacity = newCapacity;
    }
    memcpy(buffer->data + buffer->size, text, size);
    buffer->size += size;
    return 0;
}

static void html_buffer_chunk(const MD_CHAR* text, MD_SIZE size, void* userdata) {
    html_buffer_append((HtmlBuffer*) userdata, text, size);
}

JNIEXPORT jstring JNICALL
Java_com_aryan_reader_shared_docparse_Md4cJni_markdownToHtml(
        JNIEnv* env, jobject thiz, jstring markdown, jint flags) {
    (void) thiz;
    if (markdown == NULL) {
        return NULL;
    }

    const char* utf8 = (*env)->GetStringUTFChars(env, markdown, NULL);
    if (utf8 == NULL) {
        return NULL;
    }
    MD_SIZE byteLength = (MD_SIZE) (*env)->GetStringUTFLength(env, markdown);

    HtmlBuffer buffer = {NULL, 0, 0};
    int status = md_html(utf8, byteLength, html_buffer_chunk, &buffer, (unsigned) flags, 0);
    (*env)->ReleaseStringUTFChars(env, markdown, utf8);

    if (status != 0) {
        free(buffer.data);
        return NULL;
    }

    jstring result;
    if (buffer.data == NULL) {
        result = (*env)->NewStringUTF(env, "");
    } else {
        buffer.data[buffer.size] = '\0';
        result = (*env)->NewStringUTF(env, buffer.data);
    }
    free(buffer.data);
    return result;
}
