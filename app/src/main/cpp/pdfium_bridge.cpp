#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <vector>
#include <string>

#define LOG_TAG "PdfiumAnnotation"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

typedef double (*FPDFText_GetFontSize_t)(void* text_page, int index);
typedef int (*FPDFText_GetFontWeight_t)(void* text_page, int index);
typedef int (*FPDFText_GetFontInfo_t)(void* text_page, int index, void* buffer, unsigned long buflen, int* flags);
typedef int (*FPDFPage_GetAnnotCount_t)(void* page);
typedef void* (*FPDFPage_GetAnnot_t)(void* page, int index);
typedef int (*FPDFAnnot_GetSubtype_t)(void* annot);
typedef int (*FPDFAnnot_GetRect_t)(void* annot, void* rect);
typedef unsigned long (*FPDFAnnot_GetStringValue_t)(void* annot, const char* key, void* buffer, unsigned long buflen);
typedef int (*FPDFAnnot_GetColor_t)(void* annot, int type, unsigned int* R, unsigned int* G, unsigned int* B, unsigned int* A);

static FPDFPage_GetAnnotCount_t get_annot_count_func = nullptr;
static FPDFPage_GetAnnot_t get_annot_func = nullptr;
static FPDFAnnot_GetSubtype_t get_annot_subtype_func = nullptr;
static FPDFAnnot_GetRect_t get_annot_rect_func = nullptr;
static FPDFAnnot_GetStringValue_t get_annot_string_func = nullptr;
static FPDFAnnot_GetColor_t get_annot_color_func = nullptr;
static void* pdfium_handle = nullptr;
static FPDFText_GetFontSize_t get_font_size_func = nullptr;
static FPDFText_GetFontWeight_t get_font_weight_func = nullptr;
static FPDFText_GetFontInfo_t get_font_info_func = nullptr;

static bool init_pdfium() {
    if (pdfium_handle) return true;

    pdfium_handle = dlopen("libpdfium.so", RTLD_LAZY);
    if (!pdfium_handle) {
        LOGE("Failed to hook into libpdfium.so: %s", dlerror());
        return false;
    }

    get_font_size_func = (FPDFText_GetFontSize_t) dlsym(pdfium_handle, "FPDFText_GetFontSize");
    get_font_weight_func = (FPDFText_GetFontWeight_t) dlsym(pdfium_handle, "FPDFText_GetFontWeight");
    get_font_info_func = (FPDFText_GetFontInfo_t) dlsym(pdfium_handle, "FPDFText_GetFontInfo");

    get_annot_count_func = (FPDFPage_GetAnnotCount_t) dlsym(pdfium_handle, "FPDFPage_GetAnnotCount");
    get_annot_func = (FPDFPage_GetAnnot_t) dlsym(pdfium_handle, "FPDFPage_GetAnnot");
    get_annot_subtype_func = (FPDFAnnot_GetSubtype_t) dlsym(pdfium_handle, "FPDFAnnot_GetSubtype");
    get_annot_rect_func = (FPDFAnnot_GetRect_t) dlsym(pdfium_handle, "FPDFAnnot_GetRect");
    get_annot_string_func = (FPDFAnnot_GetStringValue_t) dlsym(pdfium_handle, "FPDFAnnot_GetStringValue");
    get_annot_color_func = (FPDFAnnot_GetColor_t) dlsym(pdfium_handle, "FPDFAnnot_GetColor");

    bool success = get_annot_count_func && get_annot_func && get_annot_subtype_func &&
                   get_annot_rect_func && get_annot_string_func;

    if (!success) {
        LOGE("Failed to find one or more annotation functions in libpdfium.so");
    } else {
        LOGI("Pdfium Annotation Bridge initialized successfully.");
    }

    return success;
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

extern "C" JNIEXPORT jintArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageFontFlags(JNIEnv *env, jclass clazz, jlong textPagePtr, jint count) {
    if (!init_pdfium() || !get_font_info_func || count <= 0) return nullptr;

    jintArray result = env->NewIntArray(count);
    jint *fill = new jint[count];
    for(int i = 0; i < count; i++) {
        int flags = 0;
        get_font_info_func(reinterpret_cast<void*>(textPagePtr), i, nullptr, 0, &flags);
        fill[i] = (jint)flags;
    }
    env->SetIntArrayRegion(result, 0, count, fill);
    delete[] fill;
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotCount(JNIEnv *env, jclass clazz, jlong pagePtr) {
    if (!init_pdfium() || !get_annot_count_func) return 0;
    return get_annot_count_func(reinterpret_cast<void*>(pagePtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotSubtype(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    if (!init_pdfium() || !get_annot_func || !get_annot_subtype_func) return 0;
    void* annot = get_annot_func(reinterpret_cast<void*>(pagePtr), index);
    return annot ? get_annot_subtype_func(annot) : 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotRect(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    if (!init_pdfium() || !get_annot_func || !get_annot_rect_func) return nullptr;
    void* annot = get_annot_func(reinterpret_cast<void*>(pagePtr), index);
    if (!annot) return nullptr;

    float rect[4];
    if (!get_annot_rect_func(annot, rect)) return nullptr;

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, rect);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotString(JNIEnv *env, jclass clazz, jlong pagePtr, jint index, jstring key) {
    if (!init_pdfium() || !get_annot_func || !get_annot_string_func) return nullptr;
    void* annot = get_annot_func(reinterpret_cast<void*>(pagePtr), index);
    if (!annot) return nullptr;

    const char* nativeKey = env->GetStringUTFChars(key, nullptr);
    unsigned long len = get_annot_string_func(annot, nativeKey, nullptr, 0);

    if (len <= 2) {
        env->ReleaseStringUTFChars(key, nativeKey);
        return nullptr;
    }

    std::vector<unsigned short> buffer(len / 2);
    get_annot_string_func(annot, nativeKey, buffer.data(), len);

    jstring result = env->NewString(reinterpret_cast<const jchar*>(buffer.data()), (jsize)(buffer.size() - 1));

    // Only log if we found actual data for "Contents"
    if (strcmp(nativeKey, "Contents") == 0) {
        LOGI("getAnnotString: Found Contents for index %d", index);
    }

    env->ReleaseStringUTFChars(key, nativeKey);
    return result;
}