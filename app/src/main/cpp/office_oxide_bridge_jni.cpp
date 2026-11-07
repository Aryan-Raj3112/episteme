/*
 * JNI bridge for office_oxide_bridge.
 *
 * Kotlin side: com.aryan.reader.shared.office.SharedOfficeOxideJni in
 * shared/src/androidMain. The native implementation of the bridge itself lives
 * in rust/office-oxide-bridge and is linked here as a shared library.
 *
 * Keep the function names below in sync with that class, and keep the ProGuard
 * rule in app/proguard-rules.pro up to date: R8 obfuscation would otherwise
 * rename the Kotlin members and break the JNI symbol lookup.
 */

#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <vector>

#include "office_oxide_bridge.h"

namespace {

jbyteArray toJavaBytes(JNIEnv* env, const char* data, size_t len) {
    if (data == nullptr) {
        return nullptr;
    }
    // Cap the conversion at what a jbyteArray can hold.
    if (len > static_cast<size_t>(INT32_MAX)) {
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(len));
    if (out == nullptr || len == 0) {
        // NewByteArray leaves a pending OutOfMemoryError when it returns null;
        // the caller propagates it by returning null itself.
        return out;
    }
    env->SetByteArrayRegion(
            out, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
    return out;
}

}  // namespace

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_aryan_reader_shared_office_SharedOfficeOxideJni_render(
        JNIEnv* env, jobject /*thiz*/, jbyteArray data, jstring ext, jint mode) {
    if (data == nullptr || ext == nullptr) {
        return nullptr;
    }

    const jsize length = env->GetArrayLength(data);
    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) {
            return nullptr;
        }
    }

    const char* extChars = env->GetStringUTFChars(ext, nullptr);
    if (extChars == nullptr) {
        return nullptr;
    }
    size_t outLength = 0;
    char* rendered = office_bridge_render(bytes.data(), bytes.size(), extChars, mode, &outLength);
    env->ReleaseStringUTFChars(ext, extChars);
    if (rendered == nullptr) {
        return nullptr;
    }
    jbyteArray result = toJavaBytes(env, rendered, outLength);
    office_bridge_free(rendered);
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_shared_office_SharedOfficeOxideJni_supports(
        JNIEnv* env, jobject /*thiz*/, jstring ext) {
    if (ext == nullptr) {
        return JNI_FALSE;
    }
    const char* extChars = env->GetStringUTFChars(ext, nullptr);
    if (extChars == nullptr) {
        return JNI_FALSE;
    }
    const jboolean supported = office_bridge_supports(extChars) ? JNI_TRUE : JNI_FALSE;
    env->ReleaseStringUTFChars(ext, extChars);
    return supported;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aryan_reader_shared_office_SharedOfficeOxideJni_lastError(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(office_bridge_last_error());
}
