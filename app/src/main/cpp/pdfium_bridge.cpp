#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "PdfiumBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef double (*FPDFText_GetFontSize_t)(void* text_page, int index);
typedef int (*FPDFText_GetFontWeight_t)(void* text_page, int index);

static void* pdfium_handle = nullptr;
static FPDFText_GetFontSize_t get_font_size_func = nullptr;
static FPDFText_GetFontWeight_t get_font_weight_func = nullptr;

static bool init_pdfium() {
    if (pdfium_handle) return true;

    pdfium_handle = dlopen("libpdfium.so", RTLD_LAZY);
    if (!pdfium_handle) {
        LOGE("Failed to hook into libpdfium.so: %s", dlerror());
        return false;
    }

    get_font_size_func = (FPDFText_GetFontSize_t) dlsym(pdfium_handle, "FPDFText_GetFontSize");
    get_font_weight_func = (FPDFText_GetFontWeight_t) dlsym(pdfium_handle, "FPDFText_GetFontWeight");

    return get_font_size_func != nullptr && get_font_weight_func != nullptr;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getFontSize(JNIEnv *env, jclass clazz, jlong textPagePtr, jint index) {
    if (!init_pdfium() || !get_font_size_func) return 0.0;
    return get_font_size_func(reinterpret_cast<void*>(textPagePtr), index);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getFontWeight(JNIEnv *env, jclass clazz, jlong textPagePtr, jint index) {
    if (!init_pdfium() || !get_font_weight_func) return 0;
    return get_font_weight_func(reinterpret_cast<void*>(textPagePtr), index);
}

// Bulk extraction for blazing fast formatting processing
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageFontSizes(JNIEnv *env, jclass clazz, jlong textPagePtr, jint count) {
    if (!init_pdfium() || !get_font_size_func || count <= 0) return nullptr;

    jfloatArray result = env->NewFloatArray(count);
    jfloat *fill = new jfloat[count];
    for(int i = 0; i < count; i++) {
        fill[i] = (jfloat)get_font_size_func(reinterpret_cast<void*>(textPagePtr), i);
    }
    env->SetFloatArrayRegion(result, 0, count, fill);
    delete[] fill;
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageFontWeights(JNIEnv *env, jclass clazz, jlong textPagePtr, jint count) {
    if (!init_pdfium() || !get_font_weight_func || count <= 0) return nullptr;

    jintArray result = env->NewIntArray(count);
    jint *fill = new jint[count];
    for(int i = 0; i < count; i++) {
        fill[i] = (jint)get_font_weight_func(reinterpret_cast<void*>(textPagePtr), i);
    }
    env->SetIntArrayRegion(result, 0, count, fill);
    delete[] fill;
    return result;
}