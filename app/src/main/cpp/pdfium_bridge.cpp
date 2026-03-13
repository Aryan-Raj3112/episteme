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
typedef int (*FPDFText_GetCharBox_t)(void* text_page, int index, double* left, double* right, double* bottom, double* top);
typedef int (*FPDFPage_GetAnnotCount_t)(void* page);
typedef void* (*FPDFPage_GetAnnot_t)(void* page, int index);
typedef int (*FPDFAnnot_GetSubtype_t)(void* annot);
typedef int (*FPDFAnnot_GetRect_t)(void* annot, void* rect);
typedef unsigned long (*FPDFAnnot_GetStringValue_t)(void* annot, const char* key, void* buffer, unsigned long buflen);
typedef int (*FPDFAnnot_GetColor_t)(void* annot, int type, unsigned int* R, unsigned int* G, unsigned int* B, unsigned int* A);
typedef int (*FPDFPage_CountObjects_t)(void* page);
typedef void* (*FPDFPage_GetObject_t)(void* page, int index);
typedef int (*FPDFPageObj_GetType_t)(void* page_object);
typedef void* (*FPDFImageObj_GetBitmap_t)(void* image_object);
typedef int (*FPDFBitmap_GetWidth_t)(void* bitmap);
typedef int (*FPDFBitmap_GetHeight_t)(void* bitmap);
typedef int (*FPDFBitmap_GetStride_t)(void* bitmap);
typedef void* (*FPDFBitmap_GetBuffer_t)(void* bitmap);
typedef void (*FPDFBitmap_Destroy_t)(void* bitmap);
typedef int (*FPDFPageObj_GetBounds_t)(void* page_object, float* left, float* bottom, float* right, float* top);

static FPDFPage_CountObjects_t count_objects_func = nullptr;
static FPDFPage_GetObject_t get_object_func = nullptr;
static FPDFPageObj_GetType_t get_object_type_func = nullptr;
static FPDFImageObj_GetBitmap_t get_image_bitmap_func = nullptr;
static FPDFBitmap_GetWidth_t bitmap_get_width_func = nullptr;
static FPDFBitmap_GetHeight_t bitmap_get_height_func = nullptr;
static FPDFBitmap_GetStride_t bitmap_get_stride_func = nullptr;
static FPDFBitmap_GetBuffer_t bitmap_get_buffer_func = nullptr;
static FPDFBitmap_Destroy_t bitmap_destroy_func = nullptr;
static FPDFPageObj_GetBounds_t get_object_bounds_func = nullptr;
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
static FPDFText_GetCharBox_t get_char_box_func = nullptr;

typedef void* (*FPDFAnnot_GetLinkedAnnot_t)(void* annot, const char* key);
typedef void (*FPDFPage_CloseAnnot_t)(void* annot);

static FPDFAnnot_GetLinkedAnnot_t get_linked_annot_func = nullptr;
static FPDFPage_CloseAnnot_t close_annot_func = nullptr;

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
    get_char_box_func = (FPDFText_GetCharBox_t) dlsym(pdfium_handle, "FPDFText_GetCharBox");

    get_annot_count_func = (FPDFPage_GetAnnotCount_t) dlsym(pdfium_handle, "FPDFPage_GetAnnotCount");
    get_annot_func = (FPDFPage_GetAnnot_t) dlsym(pdfium_handle, "FPDFPage_GetAnnot");
    get_annot_subtype_func = (FPDFAnnot_GetSubtype_t) dlsym(pdfium_handle, "FPDFAnnot_GetSubtype");
    get_annot_rect_func = (FPDFAnnot_GetRect_t) dlsym(pdfium_handle, "FPDFAnnot_GetRect");
    get_annot_string_func = (FPDFAnnot_GetStringValue_t) dlsym(pdfium_handle, "FPDFAnnot_GetStringValue");
    get_annot_color_func = (FPDFAnnot_GetColor_t) dlsym(pdfium_handle, "FPDFAnnot_GetColor");
    get_linked_annot_func = (FPDFAnnot_GetLinkedAnnot_t) dlsym(pdfium_handle, "FPDFAnnot_GetLinkedAnnot");
    close_annot_func = (FPDFPage_CloseAnnot_t) dlsym(pdfium_handle, "FPDFPage_CloseAnnot");

    count_objects_func = (FPDFPage_CountObjects_t) dlsym(pdfium_handle, "FPDFPage_CountObjects");
    get_object_func = (FPDFPage_GetObject_t) dlsym(pdfium_handle, "FPDFPage_GetObject");
    get_object_type_func = (FPDFPageObj_GetType_t) dlsym(pdfium_handle, "FPDFPageObj_GetType");
    get_image_bitmap_func = (FPDFImageObj_GetBitmap_t) dlsym(pdfium_handle, "FPDFImageObj_GetBitmap");
    bitmap_get_width_func = (FPDFBitmap_GetWidth_t) dlsym(pdfium_handle, "FPDFBitmap_GetWidth");
    bitmap_get_height_func = (FPDFBitmap_GetHeight_t) dlsym(pdfium_handle, "FPDFBitmap_GetHeight");
    bitmap_get_stride_func = (FPDFBitmap_GetStride_t) dlsym(pdfium_handle, "FPDFBitmap_GetStride");
    bitmap_get_buffer_func = (FPDFBitmap_GetBuffer_t) dlsym(pdfium_handle, "FPDFBitmap_GetBuffer");
    bitmap_destroy_func = (FPDFBitmap_Destroy_t) dlsym(pdfium_handle, "FPDFBitmap_Destroy");
    get_object_bounds_func = (FPDFPageObj_GetBounds_t) dlsym(pdfium_handle, "FPDFPageObj_GetBounds");

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

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageCharBoxes(JNIEnv *env, jclass clazz, jlong textPagePtr, jint count) {
    if (!init_pdfium() || !get_char_box_func || count <= 0) return nullptr;

    const int stride = 4;
    jfloatArray result = env->NewFloatArray(count * stride);
    jfloat *fill = new jfloat[count * stride];
    void* tp = reinterpret_cast<void*>(textPagePtr);
    for (int i = 0; i < count; i++) {
        double left = 0, right = 0, bottom = 0, top = 0;
        get_char_box_func(tp, i, &left, &right, &bottom, &top);
        fill[i * stride + 0] = (jfloat)left;
        fill[i * stride + 1] = (jfloat)bottom;
        fill[i * stride + 2] = (jfloat)right;
        fill[i * stride + 3] = (jfloat)top;
    }
    env->SetFloatArrayRegion(result, 0, count * stride, fill);
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

    if (strcmp(nativeKey, "IRT") == 0 && get_linked_annot_func && close_annot_func) {
        void* parentAnnot = get_linked_annot_func(annot, "IRT");
        if (parentAnnot) {
            unsigned long len = get_annot_string_func(parentAnnot, "NM", nullptr, 0);
            jstring result = nullptr;
            if (len > 2) {
                std::vector<unsigned short> buffer(len / 2);
                get_annot_string_func(parentAnnot, "NM", buffer.data(), len);
                result = env->NewString(reinterpret_cast<const jchar*>(buffer.data()), (jsize)(buffer.size() - 1));
            }
            close_annot_func(parentAnnot);
            env->ReleaseStringUTFChars(key, nativeKey);
            return result;
        }
    }

    unsigned long len = get_annot_string_func(annot, nativeKey, nullptr, 0);

    if (len <= 2) {
        env->ReleaseStringUTFChars(key, nativeKey);
        return nullptr;
    }

    std::vector<unsigned short> buffer(len / 2);
    get_annot_string_func(annot, nativeKey, buffer.data(), len);

    jstring result = env->NewString(reinterpret_cast<const jchar*>(buffer.data()), (jsize)(buffer.size() - 1));

    env->ReleaseStringUTFChars(key, nativeKey);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectCount(JNIEnv *env, jclass clazz, jlong pagePtr) {
    if (!init_pdfium() || !count_objects_func) return 0;
    return count_objects_func(reinterpret_cast<void*>(pagePtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectType(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    if (!init_pdfium() || !get_object_func || !get_object_type_func) return 0;
    void* obj = get_object_func(reinterpret_cast<void*>(pagePtr), index);
    return obj ? get_object_type_func(obj) : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectBoundingBox(JNIEnv *env, jclass clazz, jlong pagePtr, jint index, jfloatArray outRect) {
    if (!init_pdfium() || !get_object_func || !get_object_bounds_func) return JNI_FALSE;
    void* obj = get_object_func(reinterpret_cast<void*>(pagePtr), index);
    if (!obj) return JNI_FALSE;

    float left = 0, bottom = 0, right = 0, top = 0;
    if (get_object_bounds_func(obj, &left, &bottom, &right, &top)) {
        jfloat rect[4] = {left, bottom, right, top};
        env->SetFloatArrayRegion(outRect, 0, 4, rect);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_extractImagePixels(JNIEnv *env, jclass clazz, jlong pagePtr, jint index, jintArray dimens) {
    if (!init_pdfium() || !get_object_func || !get_image_bitmap_func || !bitmap_get_buffer_func) return nullptr;

    void* obj = get_object_func(reinterpret_cast<void*>(pagePtr), index);
    if (!obj || get_object_type_func(obj) != 3) return nullptr; // 3 = FPDF_PAGEOBJ_IMAGE

    void* bmp = get_image_bitmap_func(obj);
    if (!bmp) return nullptr;

    int w = bitmap_get_width_func(bmp);
    int h = bitmap_get_height_func(bmp);
    int stride = bitmap_get_stride_func(bmp);
    uint8_t* buffer = (uint8_t*)bitmap_get_buffer_func(bmp);

    if (!buffer || w <= 0 || h <= 0) {
        bitmap_destroy_func(bmp);
        return nullptr;
    }

    jintArray result = env->NewIntArray(w * h);
    jint* pixels = new jint[w * h];
    int bpp = stride / w;

    for (int y = 0; y < h; y++) {
        uint8_t* row = buffer + y * stride;
        for (int x = 0; x < w; x++) {
            uint8_t r = 0, g = 0, b = 0, a = 255;
            if (bpp >= 3) {
                b = row[x * bpp + 0];
                g = row[x * bpp + 1];
                r = row[x * bpp + 2];
                if (bpp >= 4) a = row[x * bpp + 3];
            } else if (bpp == 1) {
                r = g = b = row[x];
            }
            // Pack pixels for Android ARGB_8888
            pixels[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->SetIntArrayRegion(result, 0, w * h, pixels);
    delete[] pixels;
    bitmap_destroy_func(bmp);

    jint dims[2] = {w, h};
    env->SetIntArrayRegion(dimens, 0, 2, dims);

    return result;
}