#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#include <mutex>
#include <regex>

#define LOG_TAG "PdfiumAnnotation"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct FS_RECTF_BRIDGE {
    float left;
    float top;
    float right;
    float bottom;
};

struct FS_POINTF_BRIDGE {
    float x;
    float y;
};

struct FS_QUADPOINTSF_BRIDGE {
    float x1;
    float y1;
    float x2;
    float y2;
    float x3;
    float y3;
    float x4;
    float y4;
};

struct FPDF_FILEWRITE_BRIDGE {
    int version;
    int (*WriteBlock)(FPDF_FILEWRITE_BRIDGE* self, const void* data, unsigned long size);
};

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
typedef void* (*FPDFBitmap_CreateEx_t)(int width, int height, int format, void* first_scan, int stride);
typedef int (*FPDFBitmap_GetWidth_t)(void* bitmap);
typedef int (*FPDFBitmap_GetHeight_t)(void* bitmap);
typedef int (*FPDFBitmap_GetStride_t)(void* bitmap);
typedef void* (*FPDFBitmap_GetBuffer_t)(void* bitmap);
typedef void (*FPDFBitmap_Destroy_t)(void* bitmap);
typedef int (*FPDFPageObj_GetBounds_t)(void* page_object, float* left, float* bottom, float* right, float* top);
typedef int (*FPDF_DoAnnotAction_t)(void* annot, int action_type);
typedef void* (*FPDFAnnot_GetWidgetAtPoint_t)(void* page, double page_x, double page_y);
typedef void* (*FPDFLink_GetAction_t)(void* link);
typedef unsigned long (*FPDFAction_GetType_t)(void* action);
typedef void* (*FPDFLink_GetAnnot_t)(void* link);
typedef int (*FPDFAnnot_GetFlags_t)(void* annot);
typedef int (*FPDFAnnot_SetFlags_t)(void* annot, int flags);
typedef unsigned long (*FPDFAnnot_GetFormFieldName_t)(void* hFPDFTextPage, void* annot, void* buffer, unsigned long buflen);
typedef void* (*FPDFLink_GetLinkAtPoint_t)(void* page, double x, double y);
typedef unsigned long (*FPDFAction_GetURIPath_t)(void* document, void* action, void* buffer, unsigned long buflen);
typedef void* (*FPDFLink_GetDest_t)(void* document, void* link);
typedef void* (*FPDFAction_GetDest_t)(void* document, void* action);
typedef int (*FPDFDest_GetDestPageIndex_t)(void* document, void* dest);
typedef unsigned long (*FPDFAction_GetFilePath_t)(void* action, void* buffer, unsigned long buflen);
typedef void* (*FPDF_LoadDocument_t)(const char* file_path, const char* password);
typedef void (*FPDF_CloseDocument_t)(void* document);
typedef int (*FPDF_GetPageCount_t)(void* document);
typedef void* (*FPDF_LoadPage_t)(void* document, int page_index);
typedef void (*FPDF_ClosePage_t)(void* page);
typedef float (*FPDF_GetPageWidthF_t)(void* page);
typedef float (*FPDF_GetPageHeightF_t)(void* page);
typedef double (*FPDF_GetPageWidth_t)(void* page);
typedef double (*FPDF_GetPageHeight_t)(void* page);
typedef void* (*FPDFPage_CreateAnnot_t)(void* page, int subtype);
typedef int (*FPDFAnnot_SetRect_t)(void* annot, const FS_RECTF_BRIDGE* rect);
typedef int (*FPDFAnnot_SetColor_t)(void* annot, int type, unsigned int R, unsigned int G, unsigned int B, unsigned int A);
typedef int (*FPDFAnnot_SetBorder_t)(void* annot, float horizontal_radius, float vertical_radius, float border_width);
typedef int (*FPDFAnnot_SetStringValue_t)(void* annot, const char* key, const unsigned short* value);
typedef int (*FPDFAnnot_AddInkStroke_t)(void* annot, const FS_POINTF_BRIDGE* points, size_t point_count);
typedef int (*FPDFAnnot_AppendAttachmentPoints_t)(void* annot, const FS_QUADPOINTSF_BRIDGE* quad_points);
typedef void (*FPDFPage_InsertObject_t)(void* page, void* page_object);
typedef void* (*FPDFPageObj_NewImageObj_t)(void* document);
typedef int (*FPDFImageObj_SetMatrix_t)(void* image_object, double a, double b, double c, double d, double e, double f);
typedef int (*FPDFImageObj_SetBitmap_t)(void** pages, int nCount, void* image_object, void* bitmap);
typedef void* (*FPDFPageObj_NewTextObj_t)(void* document, const char* font, float font_size);
typedef void* (*FPDFPageObj_CreateTextObj_t)(void* document, void* font, float font_size);
typedef void* (*FPDFText_LoadFont_t)(void* document, const unsigned char* data, unsigned int size, int font_type, int cid);
typedef void* (*FPDFText_LoadStandardFont_t)(void* document, const char* font);
typedef int (*FPDFText_SetText_t)(void* text_object, const unsigned short* text);
typedef int (*FPDFPageObj_SetFillColor_t)(void* page_object, unsigned int R, unsigned int G, unsigned int B, unsigned int A);
typedef int (*FPDFPageObj_SetStrokeColor_t)(void* page_object, unsigned int R, unsigned int G, unsigned int B, unsigned int A);
typedef int (*FPDFPageObj_SetStrokeWidth_t)(void* page_object, float width);
typedef void (*FPDFPageObj_Transform_t)(void* page_object, double a, double b, double c, double d, double e, double f);
typedef void* (*FPDFPageObj_CreateNewRect_t)(float x, float y, float w, float h);
typedef void* (*FPDFPageObj_CreateNewPath_t)(float x, float y);
typedef int (*FPDFPath_LineTo_t)(void* path, float x, float y);
typedef int (*FPDFPath_SetDrawMode_t)(void* path, int fillmode, int stroke);
typedef void (*FPDFPageObj_Destroy_t)(void* page_object);
typedef int (*FPDFPage_GenerateContent_t)(void* page);
typedef int (*FPDF_SaveAsCopy_t)(void* document, FPDF_FILEWRITE_BRIDGE* file_write, unsigned long flags);

static FPDFLink_GetLinkAtPoint_t get_link_at_point_func = nullptr;
static FPDFAction_GetURIPath_t get_uri_path_func = nullptr;
static FPDFLink_GetDest_t get_dest_func = nullptr;
static FPDFAction_GetDest_t get_action_dest_func = nullptr;
static FPDFDest_GetDestPageIndex_t get_dest_page_index_func = nullptr;
static FPDFAction_GetFilePath_t get_file_path_func = nullptr;
static std::recursive_mutex g_pdfium_mutex;
static FPDFLink_GetAnnot_t get_link_annot_func = nullptr;
static FPDFLink_GetAction_t get_link_action_func = nullptr;
static FPDFAction_GetType_t get_action_type_func = nullptr;
static FPDF_DoAnnotAction_t do_annot_action_func = nullptr;
static FPDFAnnot_GetWidgetAtPoint_t get_widget_at_point_func = nullptr;
static FPDFPage_CountObjects_t count_objects_func = nullptr;
static FPDFPage_GetObject_t get_object_func = nullptr;
static FPDFPageObj_GetType_t get_object_type_func = nullptr;
static FPDFImageObj_GetBitmap_t get_image_bitmap_func = nullptr;
static FPDFBitmap_CreateEx_t bitmap_create_ex_func = nullptr;
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
static FPDFAnnot_GetFlags_t get_annot_flags_func = nullptr;
static FPDFAnnot_SetFlags_t set_annot_flags_func = nullptr;
static FPDFAnnot_GetFormFieldName_t get_form_field_name_func = nullptr;

typedef void* (*FPDFAnnot_GetLinkedAnnot_t)(void* annot, const char* key);
typedef void (*FPDFPage_CloseAnnot_t)(void* annot);

static FPDFAnnot_GetLinkedAnnot_t get_linked_annot_func = nullptr;
static FPDFPage_CloseAnnot_t close_annot_func = nullptr;
static FPDF_LoadDocument_t load_document_func = nullptr;
static FPDF_CloseDocument_t close_document_func = nullptr;
static FPDF_GetPageCount_t get_page_count_func = nullptr;
static FPDF_LoadPage_t load_page_func = nullptr;
static FPDF_ClosePage_t close_page_func = nullptr;
static FPDF_GetPageWidthF_t get_page_width_func = nullptr;
static FPDF_GetPageHeightF_t get_page_height_func = nullptr;
static FPDF_GetPageWidth_t get_page_width_double_func = nullptr;
static FPDF_GetPageHeight_t get_page_height_double_func = nullptr;
static FPDFPage_CreateAnnot_t create_annot_func = nullptr;
static FPDFAnnot_SetRect_t set_annot_rect_func = nullptr;
static FPDFAnnot_SetColor_t set_annot_color_func = nullptr;
static FPDFAnnot_SetBorder_t set_annot_border_func = nullptr;
static FPDFAnnot_SetStringValue_t set_annot_string_value_func = nullptr;
static FPDFAnnot_AddInkStroke_t add_ink_stroke_func = nullptr;
static FPDFAnnot_AppendAttachmentPoints_t append_attachment_points_func = nullptr;
static FPDFPage_InsertObject_t insert_page_object_func = nullptr;
static FPDFPageObj_NewImageObj_t new_image_object_func = nullptr;
static FPDFImageObj_SetMatrix_t set_image_matrix_func = nullptr;
static FPDFImageObj_SetBitmap_t set_image_bitmap_func = nullptr;
static FPDFPageObj_NewTextObj_t new_text_object_func = nullptr;
static FPDFPageObj_CreateTextObj_t create_text_object_func = nullptr;
static FPDFText_LoadFont_t load_font_func = nullptr;
static FPDFText_LoadStandardFont_t load_standard_font_func = nullptr;
static FPDFText_SetText_t set_text_object_text_func = nullptr;
static FPDFPageObj_SetFillColor_t set_page_object_fill_color_func = nullptr;
static FPDFPageObj_SetStrokeColor_t set_page_object_stroke_color_func = nullptr;
static FPDFPageObj_SetStrokeWidth_t set_page_object_stroke_width_func = nullptr;
static FPDFPageObj_Transform_t transform_page_object_func = nullptr;
static FPDFPageObj_CreateNewRect_t create_rect_object_func = nullptr;
static FPDFPageObj_CreateNewPath_t create_path_object_func = nullptr;
static FPDFPath_LineTo_t path_line_to_func = nullptr;
static FPDFPath_SetDrawMode_t path_set_draw_mode_func = nullptr;
static FPDFPageObj_Destroy_t destroy_page_object_func = nullptr;
static FPDFPage_GenerateContent_t generate_content_func = nullptr;
static FPDF_SaveAsCopy_t save_as_copy_func = nullptr;

static bool init_pdfium() {
    if (pdfium_handle) return true;

    pdfium_handle = dlopen("libpdfium.so", RTLD_LAZY);
    if (!pdfium_handle) {
        LOGE("Failed to hook into libpdfium.so: %s", dlerror());
        return false;
    }

    // --- Text Functions ---
    get_font_size_func   = (FPDFText_GetFontSize_t)   dlsym(pdfium_handle, "FPDFText_GetFontSize");
    get_font_weight_func = (FPDFText_GetFontWeight_t) dlsym(pdfium_handle, "FPDFText_GetFontWeight");
    get_font_info_func   = (FPDFText_GetFontInfo_t)   dlsym(pdfium_handle, "FPDFText_GetFontInfo");
    get_char_box_func    = (FPDFText_GetCharBox_t)    dlsym(pdfium_handle, "FPDFText_GetCharBox");

    // --- Annotation Functions ---
    get_annot_count_func   = (FPDFPage_GetAnnotCount_t)   dlsym(pdfium_handle, "FPDFPage_GetAnnotCount");
    get_annot_func         = (FPDFPage_GetAnnot_t)        dlsym(pdfium_handle, "FPDFPage_GetAnnot");
    get_annot_subtype_func = (FPDFAnnot_GetSubtype_t)     dlsym(pdfium_handle, "FPDFAnnot_GetSubtype");
    get_annot_rect_func    = (FPDFAnnot_GetRect_t)        dlsym(pdfium_handle, "FPDFAnnot_GetRect");
    get_annot_string_func  = (FPDFAnnot_GetStringValue_t) dlsym(pdfium_handle, "FPDFAnnot_GetStringValue");
    get_annot_color_func   = (FPDFAnnot_GetColor_t)       dlsym(pdfium_handle, "FPDFAnnot_GetColor");
    get_linked_annot_func  = (FPDFAnnot_GetLinkedAnnot_t) dlsym(pdfium_handle, "FPDFAnnot_GetLinkedAnnot");
    close_annot_func       = (FPDFPage_CloseAnnot_t)      dlsym(pdfium_handle, "FPDFPage_CloseAnnot");
    get_annot_flags_func   = (FPDFAnnot_GetFlags_t)       dlsym(pdfium_handle, "FPDFAnnot_GetFlags");
    set_annot_flags_func   = (FPDFAnnot_SetFlags_t)       dlsym(pdfium_handle, "FPDFAnnot_SetFlags");
    load_document_func     = (FPDF_LoadDocument_t)        dlsym(pdfium_handle, "FPDF_LoadDocument");
    close_document_func    = (FPDF_CloseDocument_t)       dlsym(pdfium_handle, "FPDF_CloseDocument");
    get_page_count_func    = (FPDF_GetPageCount_t)        dlsym(pdfium_handle, "FPDF_GetPageCount");
    load_page_func         = (FPDF_LoadPage_t)            dlsym(pdfium_handle, "FPDF_LoadPage");
    close_page_func        = (FPDF_ClosePage_t)           dlsym(pdfium_handle, "FPDF_ClosePage");
    get_page_width_func    = (FPDF_GetPageWidthF_t)       dlsym(pdfium_handle, "FPDF_GetPageWidthF");
    get_page_height_func   = (FPDF_GetPageHeightF_t)      dlsym(pdfium_handle, "FPDF_GetPageHeightF");
    get_page_width_double_func = (FPDF_GetPageWidth_t)    dlsym(pdfium_handle, "FPDF_GetPageWidth");
    get_page_height_double_func = (FPDF_GetPageHeight_t)  dlsym(pdfium_handle, "FPDF_GetPageHeight");
    create_annot_func      = (FPDFPage_CreateAnnot_t)     dlsym(pdfium_handle, "FPDFPage_CreateAnnot");
    set_annot_rect_func    = (FPDFAnnot_SetRect_t)        dlsym(pdfium_handle, "FPDFAnnot_SetRect");
    set_annot_color_func   = (FPDFAnnot_SetColor_t)       dlsym(pdfium_handle, "FPDFAnnot_SetColor");
    set_annot_border_func  = (FPDFAnnot_SetBorder_t)      dlsym(pdfium_handle, "FPDFAnnot_SetBorder");
    set_annot_string_value_func = (FPDFAnnot_SetStringValue_t) dlsym(pdfium_handle, "FPDFAnnot_SetStringValue");
    add_ink_stroke_func    = (FPDFAnnot_AddInkStroke_t)   dlsym(pdfium_handle, "FPDFAnnot_AddInkStroke");
    append_attachment_points_func = (FPDFAnnot_AppendAttachmentPoints_t) dlsym(pdfium_handle, "FPDFAnnot_AppendAttachmentPoints");
    insert_page_object_func = (FPDFPage_InsertObject_t)   dlsym(pdfium_handle, "FPDFPage_InsertObject");
    new_image_object_func = (FPDFPageObj_NewImageObj_t)   dlsym(pdfium_handle, "FPDFPageObj_NewImageObj");
    set_image_matrix_func = (FPDFImageObj_SetMatrix_t)    dlsym(pdfium_handle, "FPDFImageObj_SetMatrix");
    set_image_bitmap_func = (FPDFImageObj_SetBitmap_t)    dlsym(pdfium_handle, "FPDFImageObj_SetBitmap");
    new_text_object_func   = (FPDFPageObj_NewTextObj_t)   dlsym(pdfium_handle, "FPDFPageObj_NewTextObj");
    create_text_object_func = (FPDFPageObj_CreateTextObj_t) dlsym(pdfium_handle, "FPDFPageObj_CreateTextObj");
    load_font_func         = (FPDFText_LoadFont_t)        dlsym(pdfium_handle, "FPDFText_LoadFont");
    load_standard_font_func = (FPDFText_LoadStandardFont_t) dlsym(pdfium_handle, "FPDFText_LoadStandardFont");
    set_text_object_text_func = (FPDFText_SetText_t)      dlsym(pdfium_handle, "FPDFText_SetText");
    set_page_object_fill_color_func = (FPDFPageObj_SetFillColor_t) dlsym(pdfium_handle, "FPDFPageObj_SetFillColor");
    set_page_object_stroke_color_func = (FPDFPageObj_SetStrokeColor_t) dlsym(pdfium_handle, "FPDFPageObj_SetStrokeColor");
    set_page_object_stroke_width_func = (FPDFPageObj_SetStrokeWidth_t) dlsym(pdfium_handle, "FPDFPageObj_SetStrokeWidth");
    transform_page_object_func = (FPDFPageObj_Transform_t) dlsym(pdfium_handle, "FPDFPageObj_Transform");
    create_rect_object_func = (FPDFPageObj_CreateNewRect_t) dlsym(pdfium_handle, "FPDFPageObj_CreateNewRect");
    create_path_object_func = (FPDFPageObj_CreateNewPath_t) dlsym(pdfium_handle, "FPDFPageObj_CreateNewPath");
    path_line_to_func      = (FPDFPath_LineTo_t)          dlsym(pdfium_handle, "FPDFPath_LineTo");
    path_set_draw_mode_func = (FPDFPath_SetDrawMode_t)    dlsym(pdfium_handle, "FPDFPath_SetDrawMode");
    destroy_page_object_func = (FPDFPageObj_Destroy_t)    dlsym(pdfium_handle, "FPDFPageObj_Destroy");
    generate_content_func  = (FPDFPage_GenerateContent_t) dlsym(pdfium_handle, "FPDFPage_GenerateContent");
    save_as_copy_func      = (FPDF_SaveAsCopy_t)          dlsym(pdfium_handle, "FPDF_SaveAsCopy");

    // --- Object & Bitmap Functions ---
    count_objects_func     = (FPDFPage_CountObjects_t)  dlsym(pdfium_handle, "FPDFPage_CountObjects");
    get_object_func        = (FPDFPage_GetObject_t)     dlsym(pdfium_handle, "FPDFPage_GetObject");
    get_object_type_func   = (FPDFPageObj_GetType_t)    dlsym(pdfium_handle, "FPDFPageObj_GetType");
    get_object_bounds_func = (FPDFPageObj_GetBounds_t)  dlsym(pdfium_handle, "FPDFPageObj_GetBounds");
    get_image_bitmap_func  = (FPDFImageObj_GetBitmap_t) dlsym(pdfium_handle, "FPDFImageObj_GetBitmap");
    bitmap_create_ex_func  = (FPDFBitmap_CreateEx_t)    dlsym(pdfium_handle, "FPDFBitmap_CreateEx");
    bitmap_get_width_func  = (FPDFBitmap_GetWidth_t)    dlsym(pdfium_handle, "FPDFBitmap_GetWidth");
    bitmap_get_height_func = (FPDFBitmap_GetHeight_t)   dlsym(pdfium_handle, "FPDFBitmap_GetHeight");
    bitmap_get_stride_func = (FPDFBitmap_GetStride_t)   dlsym(pdfium_handle, "FPDFBitmap_GetStride");
    bitmap_get_buffer_func = (FPDFBitmap_GetBuffer_t)   dlsym(pdfium_handle, "FPDFBitmap_GetBuffer");
    bitmap_destroy_func    = (FPDFBitmap_Destroy_t)     dlsym(pdfium_handle, "FPDFBitmap_Destroy");

    // --- Interaction, Links & Form Functions ---
    do_annot_action_func     = (FPDF_DoAnnotAction_t)       dlsym(pdfium_handle, "FPDF_DoAnnotAction");
    get_widget_at_point_func = (FPDFAnnot_GetWidgetAtPoint_t) dlsym(pdfium_handle, "FPDFAnnot_GetWidgetAtPoint");
    get_link_action_func     = (FPDFLink_GetAction_t)       dlsym(pdfium_handle, "FPDFLink_GetAction");
    get_action_type_func     = (FPDFAction_GetType_t)       dlsym(pdfium_handle, "FPDFAction_GetType");
    get_link_annot_func      = (FPDFLink_GetAnnot_t)        dlsym(pdfium_handle, "FPDFLink_GetAnnot");
    get_form_field_name_func = (FPDFAnnot_GetFormFieldName_t) dlsym(pdfium_handle, "FPDFAnnot_GetFormFieldName");

    get_link_at_point_func = (FPDFLink_GetLinkAtPoint_t) dlsym(pdfium_handle, "FPDFLink_GetLinkAtPoint");
    get_uri_path_func = (FPDFAction_GetURIPath_t) dlsym(pdfium_handle, "FPDFAction_GetURIPath");
    get_dest_func = (FPDFLink_GetDest_t) dlsym(pdfium_handle, "FPDFLink_GetDest");
    get_action_dest_func = (FPDFAction_GetDest_t) dlsym(pdfium_handle, "FPDFAction_GetDest");
    get_dest_page_index_func = (FPDFDest_GetDestPageIndex_t) dlsym(pdfium_handle, "FPDFDest_GetDestPageIndex");
    get_file_path_func = (FPDFAction_GetFilePath_t) dlsym(pdfium_handle, "FPDFAction_GetFilePath");

    // --- Validation & Logging ---
    bool success = get_annot_count_func && get_annot_func && get_annot_subtype_func &&
                   get_annot_rect_func && get_annot_string_func;

    if (!success) {
        LOGE("Failed to find one or more core annotation functions in libpdfium.so");
    } else {
        LOGI("Pdfium Annotation Bridge initialized successfully.");
    }

    LOGD("PdfInteraction: Flags -> Get:%p Set:%p, FormField -> %p",
         get_annot_flags_func, set_annot_flags_func, get_form_field_name_func);

    if (!get_link_action_func || !do_annot_action_func || !get_widget_at_point_func) {
        LOGE("PdfInteraction: Missing one or more action/widget functions. LinkAction=%p, DoAction=%p, GetWidget=%p",
             get_link_action_func, do_annot_action_func, get_widget_at_point_func);
    } else {
        LOGI("PdfInteraction: Initialization complete. Summary: LinkAction=%p, DoAction=%p, GetWidget=%p",
             get_link_action_func, do_annot_action_func, get_widget_at_point_func);
    }

    if (!load_document_func || !load_page_func || !create_annot_func || !save_as_copy_func) {
        LOGE("PdfiumExport: Missing export functions. LoadDoc=%p LoadPage=%p CreateAnnot=%p Save=%p",
             load_document_func, load_page_func, create_annot_func, save_as_copy_func);
    }

    if (!insert_page_object_func || !set_text_object_text_func ||
        !set_page_object_fill_color_func || !transform_page_object_func || !generate_content_func) {
        LOGE("PdfiumExport: Missing text object functions. InsertObj=%p NewText=%p CreateText=%p SetText=%p Fill=%p Transform=%p Generate=%p",
             insert_page_object_func, new_text_object_func, create_text_object_func,
             set_text_object_text_func, set_page_object_fill_color_func,
             transform_page_object_func, generate_content_func);
    }

    if (!insert_page_object_func || !new_image_object_func || !set_image_bitmap_func ||
        !bitmap_create_ex_func || !bitmap_destroy_func || (!set_image_matrix_func && !transform_page_object_func) ||
        !generate_content_func) {
        LOGE("PdfiumExport: Missing raster image functions. InsertObj=%p NewImage=%p SetBitmap=%p SetMatrix=%p CreateBitmap=%p DestroyBitmap=%p Transform=%p Generate=%p",
             insert_page_object_func, new_image_object_func, set_image_bitmap_func,
             set_image_matrix_func, bitmap_create_ex_func, bitmap_destroy_func,
             transform_page_object_func, generate_content_func);
    }

    return get_annot_count_func != nullptr;
}

static constexpr int kMaxSafeAnnotCount = 100000;

static int get_safe_annot_count(void* page) {
    if (!get_annot_count_func || page == nullptr) return 0;
    int count = get_annot_count_func(page);
    if (count < 0 || count > kMaxSafeAnnotCount) {
        LOGE("Ignoring invalid annotation count: %d", count);
        return 0;
    }
    return count;
}

class ScopedPdfAnnot {
public:
    explicit ScopedPdfAnnot(void* annot) : annot_(annot) {}
    ~ScopedPdfAnnot() {
        if (annot_ && close_annot_func) {
            close_annot_func(annot_);
        }
    }

    ScopedPdfAnnot(const ScopedPdfAnnot&) = delete;
    ScopedPdfAnnot& operator=(const ScopedPdfAnnot&) = delete;

    ScopedPdfAnnot(ScopedPdfAnnot&& other) noexcept : annot_(other.annot_) {
        other.annot_ = nullptr;
    }

    ScopedPdfAnnot& operator=(ScopedPdfAnnot&& other) noexcept {
        if (this != &other) {
            if (annot_ && close_annot_func) {
                close_annot_func(annot_);
            }
            annot_ = other.annot_;
            other.annot_ = nullptr;
        }
        return *this;
    }

    void* get() const { return annot_; }

private:
    void* annot_;
};

static ScopedPdfAnnot get_annot_checked(void* page, jint index) {
    if (!get_annot_func || page == nullptr || index < 0) return ScopedPdfAnnot(nullptr);
    int count = get_safe_annot_count(page);
    if (index >= count) return ScopedPdfAnnot(nullptr);
    return ScopedPdfAnnot(get_annot_func(page, index));
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getFontSize(JNIEnv *env, jclass clazz, jlong textPagePtr, jint index) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_font_size_func || textPagePtr == 0 || index < 0) return 0.0;
    return get_font_size_func(reinterpret_cast<void*>(textPagePtr), index);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getFontWeight(JNIEnv *env, jclass clazz, jlong textPagePtr, jint index) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_font_weight_func || textPagePtr == 0 || index < 0) return 0;
    return get_font_weight_func(reinterpret_cast<void*>(textPagePtr), index);
}

// Bulk extraction for blazing fast formatting processing
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageFontSizes(JNIEnv *env, jclass clazz, jlong textPagePtr, jint count) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_font_size_func || textPagePtr == 0 || count <= 0) return nullptr;

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
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_font_weight_func || textPagePtr == 0 || count <= 0) return nullptr;

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
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_font_info_func || textPagePtr == 0 || count <= 0) return nullptr;

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
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_char_box_func || textPagePtr == 0 || count <= 0) return nullptr;

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotString(JNIEnv *env, jclass clazz, jlong pagePtr, jint index, jstring key) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_string_func || pagePtr == 0 || key == nullptr) return nullptr;

    void* page = reinterpret_cast<void*>(pagePtr);
    ScopedPdfAnnot annot = get_annot_checked(page, index);
    if (!annot.get()) return nullptr;

    const char* nativeKey = env->GetStringUTFChars(key, nullptr);
    if (!nativeKey) return nullptr;

    if (strcmp(nativeKey, "IRT") == 0) {
        if (get_linked_annot_func && close_annot_func) {
            void* parentAnnot = get_linked_annot_func(annot.get(), "IRT");
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
        env->ReleaseStringUTFChars(key, nativeKey);
        return nullptr;
    }

    unsigned long len = get_annot_string_func(annot.get(), nativeKey, nullptr, 0);

    if (len <= 2) {
        env->ReleaseStringUTFChars(key, nativeKey);
        return nullptr;
    }

    std::vector<unsigned short> buffer(len / 2);
    get_annot_string_func(annot.get(), nativeKey, buffer.data(), len);

    jstring result = env->NewString(reinterpret_cast<const jchar*>(buffer.data()), (jsize)(buffer.size() - 1));

    env->ReleaseStringUTFChars(key, nativeKey);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectCount(JNIEnv *env, jclass clazz, jlong pagePtr) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !count_objects_func || pagePtr == 0) return 0;
    return count_objects_func(reinterpret_cast<void*>(pagePtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectType(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_object_func || !get_object_type_func || pagePtr == 0 || index < 0) return 0;
    void* obj = get_object_func(reinterpret_cast<void*>(pagePtr), index);
    return obj ? get_object_type_func(obj) : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getPageObjectBoundingBox(JNIEnv *env, jclass clazz, jlong pagePtr, jint index, jfloatArray outRect) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_object_func || !get_object_bounds_func || pagePtr == 0 || index < 0 || outRect == nullptr) return JNI_FALSE;
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
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_object_func || !get_object_type_func || !get_image_bitmap_func ||
        !bitmap_get_width_func || !bitmap_get_height_func || !bitmap_get_stride_func ||
        !bitmap_get_buffer_func || !bitmap_destroy_func ||
        pagePtr == 0 || index < 0 || dimens == nullptr) {
        return nullptr;
    }

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

static constexpr int kPdfAnnotHighlight = 9;
static constexpr int kPdfAnnotInk = 15;
static constexpr int kAnnotColor = 0;
static constexpr int kAnnotFlagPrint = 1 << 2;
static constexpr unsigned long kPdfNoIncremental = 1 << 1;
static constexpr int kTextFlagBold = 1;
static constexpr int kTextFlagItalic = 1 << 1;
static constexpr int kTextFlagUnderline = 1 << 2;
static constexpr int kTextFlagStrikeThrough = 1 << 3;
static constexpr int kTextFlagAbsoluteLine = 1 << 4;
static constexpr int kPdfFontTrueType = 2;
static constexpr int kPdfBitmapBgra = 4;

struct PdfiumFileWriter {
    FPDF_FILEWRITE_BRIDGE base;
    FILE* file;
};

static int write_pdf_block(FPDF_FILEWRITE_BRIDGE* self, const void* data, unsigned long size) {
    auto* writer = reinterpret_cast<PdfiumFileWriter*>(self);
    if (!writer || !writer->file || !data) return 0;
    return fwrite(data, 1, size, writer->file) == size ? 1 : 0;
}

static std::vector<jint> read_int_array(JNIEnv* env, jintArray array) {
    std::vector<jint> values;
    if (!array) return values;
    jsize length = env->GetArrayLength(array);
    values.resize(length);
    if (length > 0) env->GetIntArrayRegion(array, 0, length, values.data());
    return values;
}

static std::vector<jfloat> read_float_array(JNIEnv* env, jfloatArray array) {
    std::vector<jfloat> values;
    if (!array) return values;
    jsize length = env->GetArrayLength(array);
    values.resize(length);
    if (length > 0) env->GetFloatArrayRegion(array, 0, length, values.data());
    return values;
}

static std::string jstring_to_utf8(JNIEnv* env, jstring value) {
    if (!value) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static std::vector<std::string> read_string_array(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> values;
    if (!array) return values;
    jsize length = env->GetArrayLength(array);
    values.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; i++) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        values.push_back(jstring_to_utf8(env, value));
        if (value) env->DeleteLocalRef(value);
    }
    return values;
}

static bool set_annot_string_from_jstring(JNIEnv* env, void* annot, const char* key, jstring value) {
    if (!set_annot_string_value_func || !annot || !key || !value) return false;
    jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) return false;

    std::vector<unsigned short> wide(static_cast<size_t>(length) + 1);
    for (jsize i = 0; i < length; i++) {
        wide[static_cast<size_t>(i)] = static_cast<unsigned short>(chars[i]);
    }
    wide[static_cast<size_t>(length)] = 0;
    env->ReleaseStringChars(value, chars);

    return set_annot_string_value_func(annot, key, wide.data()) != 0;
}

static bool set_annot_string_from_ascii(void* annot, const char* key, const std::string& value) {
    if (!set_annot_string_value_func || !annot || !key) return false;
    std::vector<unsigned short> wide(value.size() + 1);
    for (size_t i = 0; i < value.size(); i++) {
        wide[i] = static_cast<unsigned short>(static_cast<unsigned char>(value[i]));
    }
    wide[value.size()] = 0;
    return set_annot_string_value_func(annot, key, wide.data()) != 0;
}

static void argb_to_rgba(jint color, unsigned int* r, unsigned int* g, unsigned int* b, unsigned int* a) {
    unsigned int argb = static_cast<unsigned int>(color);
    *a = (argb >> 24) & 0xFF;
    *r = (argb >> 16) & 0xFF;
    *g = (argb >> 8) & 0xFF;
    *b = argb & 0xFF;
}

static float clamp_unit(float value) {
    if (!std::isfinite(value)) return 0.0f;
    if (value < 0.0f) return 0.0f;
    if (value > 1.0f) return 1.0f;
    return value;
}

static FS_RECTF_BRIDGE make_pdf_rect(float left, float top, float right, float bottom, float padding) {
    float l = std::min(left, right) - padding;
    float r = std::max(left, right) + padding;
    float t = std::max(top, bottom) + padding;
    float b = std::min(top, bottom) - padding;
    return FS_RECTF_BRIDGE{l, t, r, b};
}

static float get_page_width_bridge(void* page) {
    if (get_page_width_func) return get_page_width_func(page);
    if (get_page_width_double_func) return static_cast<float>(get_page_width_double_func(page));
    return 0.0f;
}

static float get_page_height_bridge(void* page) {
    if (get_page_height_func) return get_page_height_func(page);
    if (get_page_height_double_func) return static_cast<float>(get_page_height_double_func(page));
    return 0.0f;
}

static bool validate_export_functions() {
    return load_document_func &&
           close_document_func &&
           get_page_count_func &&
           load_page_func &&
           close_page_func &&
           (get_page_width_func || get_page_width_double_func) &&
           (get_page_height_func || get_page_height_double_func) &&
           create_annot_func &&
           close_annot_func &&
           set_annot_rect_func &&
           set_annot_color_func &&
           set_annot_border_func &&
           set_annot_string_value_func &&
           add_ink_stroke_func &&
           append_attachment_points_func &&
           save_as_copy_func;
}

static bool validate_text_object_functions() {
    return insert_page_object_func &&
           (new_text_object_func || create_text_object_func) &&
           set_text_object_text_func &&
           set_page_object_fill_color_func &&
           transform_page_object_func &&
           generate_content_func;
}

static bool validate_raster_image_functions() {
    return insert_page_object_func &&
           new_image_object_func &&
           set_image_bitmap_func &&
           bitmap_create_ex_func &&
           bitmap_destroy_func &&
           (set_image_matrix_func || transform_page_object_func) &&
           generate_content_func;
}

static std::vector<std::vector<unsigned short>> split_jstring_lines_wide(JNIEnv* env, jstring value) {
    std::vector<std::vector<unsigned short>> lines;
    lines.emplace_back();
    if (!value) {
        lines.back().push_back(0);
        return lines;
    }

    jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) {
        lines.back().push_back(0);
        return lines;
    }

    for (jsize i = 0; i < length; i++) {
        jchar ch = chars[i];
        if (ch == '\n') {
            lines.emplace_back();
        } else if (ch != '\r') {
            lines.back().push_back(static_cast<unsigned short>(ch));
        }
    }
    env->ReleaseStringChars(value, chars);

    for (auto& line : lines) {
        line.push_back(0);
    }
    return lines;
}

static bool is_wide_space(unsigned short value) {
    return value == static_cast<unsigned short>(' ') ||
           value == static_cast<unsigned short>('\t') ||
           value == static_cast<unsigned short>('\v') ||
           value == static_cast<unsigned short>('\f');
}

static void push_wide_slice(
        std::vector<std::vector<unsigned short>>& lines,
        const std::vector<unsigned short>& source,
        size_t start,
        size_t end) {
    std::vector<unsigned short> line;
    if (start < end && start < source.size()) {
        end = std::min(end, source.size());
        line.insert(line.end(), source.begin() + static_cast<long>(start), source.begin() + static_cast<long>(end));
    }
    line.push_back(0);
    lines.push_back(std::move(line));
}

static std::vector<std::vector<unsigned short>> wrap_wide_lines(
        const std::vector<std::vector<unsigned short>>& source_lines,
        float max_width,
        float font_size,
        bool preserve_lines) {
    if (preserve_lines || max_width <= 1.0f || font_size <= 0.0f) {
        return source_lines;
    }

    int max_chars = static_cast<int>(std::floor(max_width / std::max(1.0f, font_size * 0.55f)));
    max_chars = std::max(1, max_chars);

    std::vector<std::vector<unsigned short>> wrapped;
    for (const auto& source_line : source_lines) {
        if (source_line.size() <= 1) {
            wrapped.push_back(source_line);
            continue;
        }

        size_t length = source_line.size() - 1;
        size_t start = 0;
        while (start < length) {
            size_t end = std::min(length, start + static_cast<size_t>(max_chars));
            if (end < length) {
                size_t break_at = end;
                for (size_t pos = end; pos > start; pos--) {
                    if (is_wide_space(source_line[pos - 1])) {
                        break_at = pos;
                        break;
                    }
                }
                end = break_at;
            }

            if (end <= start) end = std::min(length, start + static_cast<size_t>(max_chars));
            push_wide_slice(wrapped, source_line, start, end);
            start = end;
        }
    }

    if (wrapped.empty()) {
        wrapped.push_back(std::vector<unsigned short>{0});
    }
    return wrapped;
}

static bool insert_page_object_or_destroy(void* page, void* object) {
    if (!page || !object || !insert_page_object_func) {
        if (object && destroy_page_object_func) destroy_page_object_func(object);
        return false;
    }
    insert_page_object_func(page, object);
    return true;
}

static std::vector<unsigned char> read_file_bytes(const std::string& path) {
    std::vector<unsigned char> bytes;
    if (path.empty()) return bytes;

    FILE* file = fopen(path.c_str(), "rb");
    if (!file) return bytes;
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return bytes;
    }
    long size = ftell(file);
    if (size <= 0) {
        fclose(file);
        return bytes;
    }
    rewind(file);

    bytes.resize(static_cast<size_t>(size));
    size_t read = fread(bytes.data(), 1, bytes.size(), file);
    fclose(file);
    if (read != bytes.size()) bytes.clear();
    return bytes;
}

static const char* standard_font_name(const std::string& font_name, int flags) {
    bool bold = (flags & kTextFlagBold) != 0;
    bool italic = (flags & kTextFlagItalic) != 0;

    if (font_name == "Serif") {
        if (bold && italic) return "Times-BoldItalic";
        if (bold) return "Times-Bold";
        if (italic) return "Times-Italic";
        return "Times-Roman";
    }
    if (font_name == "Monospace") {
        if (bold && italic) return "Courier-BoldOblique";
        if (bold) return "Courier-Bold";
        if (italic) return "Courier-Oblique";
        return "Courier";
    }
    if (bold && italic) return "Helvetica-BoldOblique";
    if (bold) return "Helvetica-Bold";
    if (italic || font_name == "Cursive") return "Helvetica-Oblique";
    return "Helvetica";
}

static void* create_pdfium_text_object(
        void* document,
        const std::string& font_path,
        const std::string& font_name,
        float font_size,
        int flags) {
    if (create_text_object_func && load_font_func && !font_path.empty()) {
        std::vector<unsigned char> font_bytes = read_file_bytes(font_path);
        if (!font_bytes.empty()) {
            void* font = load_font_func(
                    document,
                    font_bytes.data(),
                    static_cast<unsigned int>(font_bytes.size()),
                    kPdfFontTrueType,
                    1
            );
            if (font) {
                void* text_object = create_text_object_func(document, font, font_size);
                if (text_object) return text_object;
            }
        }
    }

    const char* standard_name = standard_font_name(font_name, flags);
    if (create_text_object_func && load_standard_font_func) {
        void* font = load_standard_font_func(document, standard_name);
        if (font) {
            void* text_object = create_text_object_func(document, font, font_size);
            if (text_object) return text_object;
        }
    }

    if (new_text_object_func) {
        return new_text_object_func(document, standard_name, font_size);
    }
    return nullptr;
}

static bool insert_background_rect_object(
        void* page,
        float left,
        float bottom,
        float width,
        float height,
        unsigned int r,
        unsigned int g,
        unsigned int b,
        unsigned int a) {
    if (!create_rect_object_func || !set_page_object_fill_color_func || !path_set_draw_mode_func) {
        return false;
    }
    if (width <= 0.0f || height <= 0.0f || a == 0) return false;

    void* background = create_rect_object_func(left, bottom, width, height);
    if (!background) return false;
    set_page_object_fill_color_func(background, r, g, b, a);
    path_set_draw_mode_func(background, 1, 0);
    return insert_page_object_or_destroy(page, background);
}

static bool insert_decoration_line_object(
        void* page,
        float x1,
        float y,
        float x2,
        unsigned int r,
        unsigned int g,
        unsigned int b,
        unsigned int a,
        float stroke_width) {
    if (!create_path_object_func || !path_line_to_func || !path_set_draw_mode_func ||
        !set_page_object_stroke_color_func || !set_page_object_stroke_width_func) {
        return false;
    }

    void* path = create_path_object_func(x1, y);
    if (!path) return false;
    path_line_to_func(path, x2, y);
    set_page_object_stroke_color_func(path, r, g, b, a);
    set_page_object_stroke_width_func(path, stroke_width);
    path_set_draw_mode_func(path, 0, 1);
    return insert_page_object_or_destroy(page, path);
}

static bool insert_text_line_object(
        void* document,
        void* page,
        const std::vector<unsigned short>& wide_line,
        float x,
        float y,
        float font_size,
        unsigned int r,
        unsigned int g,
        unsigned int b,
        unsigned int a,
        int flags,
        const std::string& font_path,
        const std::string& font_name) {
    if (wide_line.size() <= 1) return true;

    void* text_object = create_pdfium_text_object(document, font_path, font_name, font_size, flags);
    if (!text_object) {
        LOGE("PdfiumExport: Failed to create text object fontPath=%s fontName=%s size=%.2f",
             font_path.c_str(), font_name.c_str(), font_size);
        return false;
    }
    if (!set_text_object_text_func(text_object, wide_line.data())) {
        LOGE("PdfiumExport: Failed to set text object text fontPath=%s fontName=%s chars=%zu",
             font_path.c_str(), font_name.c_str(), wide_line.size() > 0 ? wide_line.size() - 1 : 0);
        if (destroy_page_object_func) destroy_page_object_func(text_object);
        return false;
    }

    set_page_object_fill_color_func(text_object, r, g, b, a);
    float italicSkew = (flags & kTextFlagItalic) ? 0.22f : 0.0f;
    transform_page_object_func(text_object, 1.0, 0.0, italicSkew, 1.0, x, y);

    bool inserted = insert_page_object_or_destroy(page, text_object);
    if (inserted && (flags & kTextFlagBold)) {
        void* bold_object = create_pdfium_text_object(document, font_path, font_name, font_size, flags);
        if (bold_object && set_text_object_text_func(bold_object, wide_line.data())) {
            set_page_object_fill_color_func(bold_object, r, g, b, a);
            transform_page_object_func(bold_object, 1.0, 0.0, italicSkew, 1.0, x + std::max(0.35f, font_size * 0.035f), y);
            insert_page_object_or_destroy(page, bold_object);
        } else if (bold_object && destroy_page_object_func) {
            destroy_page_object_func(bold_object);
        }
    }

    return inserted;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_exportAnnotatedPdf(
        JNIEnv *env,
        jclass clazz,
        jstring sourcePath,
        jstring destPath,
        jintArray inkPageIndicesArray,
        jintArray inkTypesArray,
        jintArray inkColorsArray,
        jfloatArray inkStrokeWidthsArray,
        jintArray inkPointOffsetsArray,
        jintArray inkPointCountsArray,
        jfloatArray inkPointsArray,
        jintArray textPageIndicesArray,
        jfloatArray textBoundsArray,
        jintArray textColorsArray,
        jintArray textBackgroundColorsArray,
        jfloatArray textFontSizesArray,
        jintArray textFlagsArray,
        jobjectArray textValuesArray,
        jobjectArray textFontPathsArray,
        jobjectArray textFontNamesArray,
        jintArray rasterPageIndicesArray,
        jfloatArray rasterBoundsArray,
        jintArray rasterWidthsArray,
        jintArray rasterHeightsArray,
        jintArray rasterPixelOffsetsArray,
        jintArray rasterPixelsArray,
        jintArray highlightPageIndicesArray,
        jintArray highlightColorsArray,
        jintArray highlightRectOffsetsArray,
        jintArray highlightRectCountsArray,
        jfloatArray highlightRectsArray,
        jobjectArray highlightContentsArray) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);

    if (!init_pdfium() || !validate_export_functions()) {
        LOGE("PdfiumExport: PDFium export functions are unavailable.");
        return JNI_FALSE;
    }

    std::string source = jstring_to_utf8(env, sourcePath);
    std::string dest = jstring_to_utf8(env, destPath);
    if (source.empty() || dest.empty()) {
        LOGE("PdfiumExport: Missing source or destination path.");
        return JNI_FALSE;
    }

    std::vector<jint> inkPageIndices = read_int_array(env, inkPageIndicesArray);
    std::vector<jint> inkTypes = read_int_array(env, inkTypesArray);
    std::vector<jint> inkColors = read_int_array(env, inkColorsArray);
    std::vector<jfloat> inkStrokeWidths = read_float_array(env, inkStrokeWidthsArray);
    std::vector<jint> inkPointOffsets = read_int_array(env, inkPointOffsetsArray);
    std::vector<jint> inkPointCounts = read_int_array(env, inkPointCountsArray);
    std::vector<jfloat> inkPoints = read_float_array(env, inkPointsArray);

    std::vector<jint> textPageIndices = read_int_array(env, textPageIndicesArray);
    std::vector<jfloat> textBounds = read_float_array(env, textBoundsArray);
    std::vector<jint> textColors = read_int_array(env, textColorsArray);
    std::vector<jint> textBackgroundColors = read_int_array(env, textBackgroundColorsArray);
    std::vector<jfloat> textFontSizes = read_float_array(env, textFontSizesArray);
    std::vector<jint> textFlags = read_int_array(env, textFlagsArray);
    std::vector<std::string> textFontPaths = read_string_array(env, textFontPathsArray);
    std::vector<std::string> textFontNames = read_string_array(env, textFontNamesArray);
    if (!textPageIndices.empty() && !validate_text_object_functions()) {
        LOGE("PdfiumExport: Text export functions are unavailable.");
        return JNI_FALSE;
    }

    std::vector<jint> rasterPageIndices = read_int_array(env, rasterPageIndicesArray);
    std::vector<jfloat> rasterBounds = read_float_array(env, rasterBoundsArray);
    std::vector<jint> rasterWidths = read_int_array(env, rasterWidthsArray);
    std::vector<jint> rasterHeights = read_int_array(env, rasterHeightsArray);
    std::vector<jint> rasterPixelOffsets = read_int_array(env, rasterPixelOffsetsArray);
    jsize rasterPixelsLength = rasterPixelsArray ? env->GetArrayLength(rasterPixelsArray) : 0;
    if (!rasterPageIndices.empty() && !validate_raster_image_functions()) {
        LOGE("PdfiumExport: Raster image export functions are unavailable.");
        return JNI_FALSE;
    }
    if (!rasterPageIndices.empty() && rasterPixelsLength <= 0) {
        LOGE("PdfiumExport: Raster image payload is missing pixels.");
        return JNI_FALSE;
    }

    std::vector<jint> highlightPageIndices = read_int_array(env, highlightPageIndicesArray);
    std::vector<jint> highlightColors = read_int_array(env, highlightColorsArray);
    std::vector<jint> highlightRectOffsets = read_int_array(env, highlightRectOffsetsArray);
    std::vector<jint> highlightRectCounts = read_int_array(env, highlightRectCountsArray);
    std::vector<jfloat> highlightRects = read_float_array(env, highlightRectsArray);

    void* document = load_document_func(source.c_str(), nullptr);
    if (!document) {
        LOGE("PdfiumExport: Failed to load source PDF.");
        return JNI_FALSE;
    }

    int pageCount = get_page_count_func(document);
    bool hadFailure = false;
    jint* rasterPixels = nullptr;
    std::vector<void*> rasterBitmapsToDestroy;
    auto releaseRasterResources = [&]() {
        for (void* bitmap : rasterBitmapsToDestroy) {
            if (bitmap && bitmap_destroy_func) {
                bitmap_destroy_func(bitmap);
            }
        }
        rasterBitmapsToDestroy.clear();
        if (rasterPixels) {
            env->ReleaseIntArrayElements(rasterPixelsArray, rasterPixels, JNI_ABORT);
            rasterPixels = nullptr;
        }
    };

    if (!rasterPageIndices.empty()) {
        rasterPixels = env->GetIntArrayElements(rasterPixelsArray, nullptr);
        if (!rasterPixels) {
            LOGE("PdfiumExport: Unable to access raster image pixels.");
            close_document_func(document);
            return JNI_FALSE;
        }
    }

    for (size_t i = 0; i < inkPageIndices.size(); i++) {
        if (i >= inkTypes.size() || i >= inkColors.size() || i >= inkStrokeWidths.size() ||
            i >= inkPointOffsets.size() || i >= inkPointCounts.size()) {
            hadFailure = true;
            break;
        }

        int pageIndex = inkPageIndices[i];
        int pointOffset = inkPointOffsets[i];
        int pointCount = inkPointCounts[i];
        if (pageIndex < 0 || pageIndex >= pageCount || pointOffset < 0 || pointCount < 2 ||
            (pointOffset + pointCount) * 2 > static_cast<int>(inkPoints.size())) {
            hadFailure = true;
            continue;
        }

        void* page = load_page_func(document, pageIndex);
        if (!page) {
            hadFailure = true;
            continue;
        }

        float pageWidth = get_page_width_bridge(page);
        float pageHeight = get_page_height_bridge(page);
        if (pageWidth <= 0.0f || pageHeight <= 0.0f) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        void* annot = create_annot_func(page, kPdfAnnotInk);
        if (!annot) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        std::vector<FS_POINTF_BRIDGE> points;
        points.reserve(static_cast<size_t>(pointCount));
        float minX = pageWidth;
        float maxX = 0.0f;
        float minY = pageHeight;
        float maxY = 0.0f;

        for (int j = 0; j < pointCount; j++) {
            int sourceIndex = (pointOffset + j) * 2;
            float x = clamp_unit(inkPoints[sourceIndex]) * pageWidth;
            float y = (1.0f - clamp_unit(inkPoints[sourceIndex + 1])) * pageHeight;
            points.push_back(FS_POINTF_BRIDGE{x, y});
            minX = std::min(minX, x);
            maxX = std::max(maxX, x);
            minY = std::min(minY, y);
            maxY = std::max(maxY, y);
        }

        float strokeWidth = std::max(0.25f, inkStrokeWidths[i] * pageWidth);
        FS_RECTF_BRIDGE rect = make_pdf_rect(minX, maxY, maxX, minY, strokeWidth * 1.5f);
        set_annot_rect_func(annot, &rect);

        unsigned int r, g, b, a;
        argb_to_rgba(inkColors[i], &r, &g, &b, &a);
        if ((inkTypes[i] == 1 || inkTypes[i] == 2) && a == 255) {
            a = 102;
        }
        set_annot_color_func(annot, kAnnotColor, r, g, b, a);
        set_annot_border_func(annot, 0.0f, 0.0f, strokeWidth);
        if (set_annot_flags_func) set_annot_flags_func(annot, kAnnotFlagPrint);

        if (add_ink_stroke_func(annot, points.data(), points.size()) < 0) {
            hadFailure = true;
        }

        set_annot_string_from_ascii(annot, "Contents", "Ink");
        if (generate_content_func) generate_content_func(page);
        close_annot_func(annot);
        close_page_func(page);
    }

    for (size_t i = 0; i < highlightPageIndices.size(); i++) {
        if (i >= highlightColors.size() || i >= highlightRectOffsets.size() || i >= highlightRectCounts.size()) {
            hadFailure = true;
            break;
        }

        int pageIndex = highlightPageIndices[i];
        int rectOffset = highlightRectOffsets[i];
        int rectCount = highlightRectCounts[i];
        if (pageIndex < 0 || pageIndex >= pageCount || rectOffset < 0 || rectCount <= 0 ||
            (rectOffset + rectCount) * 4 > static_cast<int>(highlightRects.size())) {
            hadFailure = true;
            continue;
        }

        std::vector<FS_QUADPOINTSF_BRIDGE> quads;
        quads.reserve(static_cast<size_t>(rectCount));
        float unionLeft = 0.0f;
        float unionRight = 0.0f;
        float unionTop = 0.0f;
        float unionBottom = 0.0f;

        for (int j = 0; j < rectCount; j++) {
            int sourceIndex = (rectOffset + j) * 4;
            float left = std::min(highlightRects[sourceIndex], highlightRects[sourceIndex + 2]);
            float right = std::max(highlightRects[sourceIndex], highlightRects[sourceIndex + 2]);
            float top = std::max(highlightRects[sourceIndex + 1], highlightRects[sourceIndex + 3]);
            float bottom = std::min(highlightRects[sourceIndex + 1], highlightRects[sourceIndex + 3]);
            if (right <= left || top <= bottom) continue;

            quads.push_back(FS_QUADPOINTSF_BRIDGE{left, top, right, top, left, bottom, right, bottom});
            if (quads.size() == 1) {
                unionLeft = left;
                unionRight = right;
                unionTop = top;
                unionBottom = bottom;
            } else {
                unionLeft = std::min(unionLeft, left);
                unionRight = std::max(unionRight, right);
                unionTop = std::max(unionTop, top);
                unionBottom = std::min(unionBottom, bottom);
            }
        }

        if (quads.empty()) {
            hadFailure = true;
            continue;
        }

        void* page = load_page_func(document, pageIndex);
        if (!page) {
            hadFailure = true;
            continue;
        }

        void* annot = create_annot_func(page, kPdfAnnotHighlight);
        if (!annot) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        for (const FS_QUADPOINTSF_BRIDGE& quad : quads) {
            if (!append_attachment_points_func(annot, &quad)) {
                hadFailure = true;
            }
        }
        FS_RECTF_BRIDGE rect = make_pdf_rect(unionLeft, unionTop, unionRight, unionBottom, 1.0f);
        set_annot_rect_func(annot, &rect);

        unsigned int r, g, b, a;
        argb_to_rgba(highlightColors[i], &r, &g, &b, &a);
        if (a == 255) a = 102;
        set_annot_color_func(annot, kAnnotColor, r, g, b, a);
        if (set_annot_flags_func) set_annot_flags_func(annot, kAnnotFlagPrint);

        if (highlightContentsArray && i < static_cast<size_t>(env->GetArrayLength(highlightContentsArray))) {
            auto content = static_cast<jstring>(env->GetObjectArrayElement(highlightContentsArray, static_cast<jsize>(i)));
            if (content) {
                set_annot_string_from_jstring(env, annot, "Contents", content);
                env->DeleteLocalRef(content);
            }
        }

        if (generate_content_func) generate_content_func(page);
        close_annot_func(annot);
        close_page_func(page);
    }

    for (size_t i = 0; i < rasterPageIndices.size(); i++) {
        if (i >= rasterWidths.size() || i >= rasterHeights.size() || i >= rasterPixelOffsets.size() ||
            (i + 1) * 4 > rasterBounds.size()) {
            hadFailure = true;
            break;
        }

        int pageIndex = rasterPageIndices[i];
        int imageWidth = rasterWidths[i];
        int imageHeight = rasterHeights[i];
        int pixelOffset = rasterPixelOffsets[i];
        long long pixelCount = static_cast<long long>(imageWidth) * static_cast<long long>(imageHeight);
        if (pageIndex < 0 || pageIndex >= pageCount || imageWidth <= 0 || imageHeight <= 0 ||
            pixelOffset < 0 || pixelCount <= 0 ||
            static_cast<long long>(pixelOffset) + pixelCount > static_cast<long long>(rasterPixelsLength)) {
            LOGE("PdfiumExport: Invalid raster image payload index=%zu page=%d size=%dx%d offset=%d pixels=%d",
                 i, pageIndex, imageWidth, imageHeight, pixelOffset, rasterPixelsLength);
            hadFailure = true;
            continue;
        }

        void* page = load_page_func(document, pageIndex);
        if (!page) {
            hadFailure = true;
            continue;
        }

        float pageWidth = get_page_width_bridge(page);
        float pageHeight = get_page_height_bridge(page);
        if (pageWidth <= 0.0f || pageHeight <= 0.0f) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        float left = clamp_unit(rasterBounds[i * 4]) * pageWidth;
        float top = (1.0f - clamp_unit(rasterBounds[i * 4 + 1])) * pageHeight;
        float right = clamp_unit(rasterBounds[i * 4 + 2]) * pageWidth;
        float bottom = (1.0f - clamp_unit(rasterBounds[i * 4 + 3])) * pageHeight;
        FS_RECTF_BRIDGE rect = make_pdf_rect(left, top, right, bottom, 0.0f);
        float rectWidth = rect.right - rect.left;
        float rectHeight = rect.top - rect.bottom;
        if (rectWidth <= 0.5f || rectHeight <= 0.5f) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        void* imageObject = new_image_object_func(document);
        if (!imageObject) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        void* bitmap = bitmap_create_ex_func(
                imageWidth,
                imageHeight,
                kPdfBitmapBgra,
                reinterpret_cast<void*>(rasterPixels + pixelOffset),
                imageWidth * 4
        );
        if (!bitmap) {
            if (destroy_page_object_func) destroy_page_object_func(imageObject);
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        void* pages[] = {page};
        if (!set_image_bitmap_func(pages, 1, imageObject, bitmap)) {
            bitmap_destroy_func(bitmap);
            if (destroy_page_object_func) destroy_page_object_func(imageObject);
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        bool positioned = true;
        if (set_image_matrix_func) {
            positioned = set_image_matrix_func(imageObject, rectWidth, 0.0, 0.0, rectHeight, rect.left, rect.bottom) != 0;
        } else {
            transform_page_object_func(imageObject, rectWidth, 0.0, 0.0, rectHeight, rect.left, rect.bottom);
        }
        if (!positioned) {
            bitmap_destroy_func(bitmap);
            if (destroy_page_object_func) destroy_page_object_func(imageObject);
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        if (!insert_page_object_or_destroy(page, imageObject)) {
            bitmap_destroy_func(bitmap);
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        rasterBitmapsToDestroy.push_back(bitmap);
        if (!generate_content_func(page)) {
            hadFailure = true;
        }
        close_page_func(page);
    }

    for (size_t i = 0; i < textPageIndices.size(); i++) {
        if (i >= textColors.size() || i >= textBackgroundColors.size() || i >= textFontSizes.size() ||
            i >= textFlags.size() || i >= textFontPaths.size() || i >= textFontNames.size() ||
            (i + 1) * 4 > textBounds.size()) {
            hadFailure = true;
            break;
        }

        int pageIndex = textPageIndices[i];
        if (pageIndex < 0 || pageIndex >= pageCount) {
            hadFailure = true;
            continue;
        }

        void* page = load_page_func(document, pageIndex);
        if (!page) {
            hadFailure = true;
            continue;
        }

        float pageWidth = get_page_width_bridge(page);
        float pageHeight = get_page_height_bridge(page);
        if (pageWidth <= 0.0f || pageHeight <= 0.0f) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }

        float left = clamp_unit(textBounds[i * 4]) * pageWidth;
        float top = (1.0f - clamp_unit(textBounds[i * 4 + 1])) * pageHeight;
        float right = clamp_unit(textBounds[i * 4 + 2]) * pageWidth;
        float bottom = (1.0f - clamp_unit(textBounds[i * 4 + 3])) * pageHeight;
        FS_RECTF_BRIDGE rect = make_pdf_rect(left, top, right, bottom, 0.0f);
        if (rect.right - rect.left <= 1.0f || rect.top - rect.bottom <= 1.0f) {
            close_page_func(page);
            hadFailure = true;
            continue;
        }
        float rectWidth = std::max(1.0f, rect.right - rect.left);
        bool preserveLines = (textFlags[i] & kTextFlagAbsoluteLine) != 0;

        unsigned int textR, textG, textB, textA;
        argb_to_rgba(textColors[i], &textR, &textG, &textB, &textA);
        unsigned int bgR, bgG, bgB, bgA;
        argb_to_rgba(textBackgroundColors[i], &bgR, &bgG, &bgB, &bgA);

        float fontSize = textFontSizes[i] > 1.0f ? textFontSizes[i] : textFontSizes[i] * pageHeight;
        if (fontSize <= 0.0f) fontSize = 12.0f;

        if (textValuesArray && i < static_cast<size_t>(env->GetArrayLength(textValuesArray))) {
            auto content = static_cast<jstring>(env->GetObjectArrayElement(textValuesArray, static_cast<jsize>(i)));
            if (content) {
                auto lines = wrap_wide_lines(
                        split_jstring_lines_wide(env, content),
                        preserveLines ? rectWidth : std::max(1.0f, rectWidth - 4.0f),
                        fontSize,
                        preserveLines
                );
                float lineHeight = std::max(fontSize * 1.18f, fontSize + 2.0f);
                float baseline = preserveLines ? top : rect.top - (fontSize * 0.85f);
                float textX = preserveLines ? rect.left : rect.left + 2.0f;
                bool insertedAnyText = false;
                float decorationStroke = std::max(0.35f, fontSize * 0.035f);

                for (const auto& line : lines) {
                    if (!preserveLines && baseline < rect.bottom + 1.0f) break;
                    float lineVisualWidth = line.size() > 1
                                            ? std::min(
                                                    std::max(1.0f, rectWidth),
                                                    std::max(1.0f, static_cast<float>(line.size() - 1) * fontSize * 0.55f))
                                            : 0.0f;
                    if (preserveLines) {
                        lineVisualWidth = std::max(1.0f, rectWidth);
                    }
                    if (line.size() > 1 && bgA > 0) {
                        insert_background_rect_object(
                                page,
                                textX,
                                baseline - (fontSize * 0.95f),
                                lineVisualWidth + (fontSize * 0.2f),
                                fontSize * 1.2f,
                                bgR,
                                bgG,
                                bgB,
                                bgA
                        );
                    }
                    if (insert_text_line_object(
                            document,
                            page,
                            line,
                            textX,
                            baseline,
                            fontSize,
                            textR,
                            textG,
                            textB,
                            textA,
                            textFlags[i],
                            textFontPaths[i],
                            textFontNames[i])) {
                        if (line.size() > 1) insertedAnyText = true;
                    }

                    if (line.size() > 1 && (textFlags[i] & (kTextFlagUnderline | kTextFlagStrikeThrough))) {
                        if (textFlags[i] & kTextFlagUnderline) {
                            insert_decoration_line_object(
                                    page,
                                    textX,
                                    baseline - 2.0f,
                                    textX + lineVisualWidth,
                                    textR,
                                    textG,
                                    textB,
                                    textA,
                                    decorationStroke
                            );
                        }
                        if (textFlags[i] & kTextFlagStrikeThrough) {
                            insert_decoration_line_object(
                                    page,
                                    textX,
                                    baseline + fontSize * 0.35f,
                                    textX + lineVisualWidth,
                                    textR,
                                    textG,
                                    textB,
                                    textA,
                                    decorationStroke
                            );
                        }
                    }

                    baseline -= lineHeight;
                }

                if (!insertedAnyText) {
                    LOGE("PdfiumExport: No text inserted for text item index=%zu page=%d fontPath=%s fontName=%s textChars=%d rect=(%.2f,%.2f,%.2f,%.2f)",
                         i,
                         pageIndex,
                         textFontPaths[i].c_str(),
                         textFontNames[i].c_str(),
                         content ? env->GetStringLength(content) : 0,
                         rect.left,
                         rect.top,
                         rect.right,
                         rect.bottom);
                    hadFailure = true;
                }
                env->DeleteLocalRef(content);
            }
        } else {
            hadFailure = true;
        }

        if (generate_content_func) generate_content_func(page);
        close_page_func(page);
    }

    FILE* output = fopen(dest.c_str(), "wb");
    if (!output) {
        LOGE("PdfiumExport: Failed to open destination PDF.");
        releaseRasterResources();
        close_document_func(document);
        return JNI_FALSE;
    }

    PdfiumFileWriter writer{{1, write_pdf_block}, output};
    int saved = save_as_copy_func(document, &writer.base, kPdfNoIncremental);
    fclose(output);
    close_document_func(document);
    releaseRasterResources();

    if (!saved) {
        LOGE("PdfiumExport: Save result=%d hadFailure=%d", saved, hadFailure ? 1 : 0);
        remove(dest.c_str());
        return JNI_FALSE;
    }

    if (hadFailure) {
        LOGE("PdfiumExport: Saved PDF with partial annotation/text failures.");
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_checkActionSupport(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    init_pdfium();
    // Return true if we have ANY way to handle actions
    return (do_annot_action_func || get_link_action_func) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotSubtypeAtPoint(JNIEnv *env, jclass clazz, jlong pagePtr, jdouble x, jdouble y) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_func || !get_annot_rect_func || !get_annot_subtype_func || pagePtr == 0) return -1;

    void* page = reinterpret_cast<void*>(pagePtr);
    int count = get_safe_annot_count(page);

    for (int i = 0; i < count; i++) {
        ScopedPdfAnnot annot = get_annot_checked(page, i);
        if (!annot.get()) continue;

        float r[4]; // L, B, R, T
        if (get_annot_rect_func(annot.get(), r)) {
            // FIX: Use min/max to handle inverted PDF rectangles
            float minX = fmin(r[0], r[2]);
            float maxX = fmax(r[0], r[2]);
            float minY = fmin(r[1], r[3]);
            float maxY = fmax(r[1], r[3]);

            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                int subtype = get_annot_subtype_func(annot.get());
                LOGI("PdfInteraction: MATCH FOUND! Index=%d, Type=%d", i, subtype);
                return subtype;
            }
        }
    }
    return -1;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotRectAtPoint(JNIEnv *env, jclass clazz, jlong pagePtr, jdouble x, jdouble y) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_func || !get_annot_rect_func || pagePtr == 0) return nullptr;

    void* page = reinterpret_cast<void*>(pagePtr);
    int count = get_safe_annot_count(page);
    for (int i = 0; i < count; i++) {
        ScopedPdfAnnot annot = get_annot_checked(page, i);
        if (!annot.get()) continue;

        float rect[4];
        if (get_annot_rect_func(annot.get(), rect)) {
            float minX = fminf(rect[0], rect[2]);
            float maxX = fmaxf(rect[0], rect[2]);
            float minY = fminf(rect[1], rect[3]);
            float maxY = fmaxf(rect[1], rect[3]);

            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                jfloatArray result = env->NewFloatArray(4);
                env->SetFloatArrayRegion(result, 0, 4, rect);
                return result;
            }
        }
    }
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotCount(JNIEnv *env, jclass clazz, jlong pagePtr) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_count_func || pagePtr == 0) return 0;
    return get_safe_annot_count(reinterpret_cast<void*>(pagePtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotSubtype(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_subtype_func || pagePtr == 0) return 0;
    ScopedPdfAnnot annot = get_annot_checked(reinterpret_cast<void*>(pagePtr), index);
    return annot.get() ? get_annot_subtype_func(annot.get()) : 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getAnnotRect(JNIEnv *env, jclass clazz, jlong pagePtr, jint index) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_rect_func || pagePtr == 0) return nullptr;
    ScopedPdfAnnot annot = get_annot_checked(reinterpret_cast<void*>(pagePtr), index);
    if (!annot.get()) return nullptr;

    float rect[4];
    if (!get_annot_rect_func(annot.get(), rect)) return nullptr;

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, rect);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_performClick(JNIEnv *env, jclass clazz, jlong pagePtr, jdouble x, jdouble y) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium() || !get_annot_func || !get_annot_rect_func || !get_annot_subtype_func || pagePtr == 0) return JNI_FALSE;

    void* page = reinterpret_cast<void*>(pagePtr);
    int count = get_safe_annot_count(page);
    int hitSubtype = 0;

    LOGI("PdfLinkDiagnostic: [C++] performClick at x=%f, y=%f (Total annots: %d)", x, y, count);

    for (int i = 0; i < count; i++) {
        ScopedPdfAnnot annot = get_annot_checked(page, i);
        if (!annot.get()) continue;

        float r[4];
        if (get_annot_rect_func(annot.get(), r)) {
            float minX = fminf(r[0], r[2]);
            float maxX = fmaxf(r[0], r[2]);
            float minY = fminf(r[1], r[3]);
            float maxY = fmaxf(r[1], r[3]);

            if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                hitSubtype = get_annot_subtype_func(annot.get());
                LOGI("PdfLinkDiagnostic: [C++] HIT! Annot Index %d, Subtype %d", i, hitSubtype);
                if (get_annot_flags_func) {
                    int flags = get_annot_flags_func(annot.get());
                    LOGI("PdfLinkDiagnostic: [C++] Flags for hit annot: %d", flags);
                }
                break;
            }
        }
    }

    if (hitSubtype != 0) {
        if (hitSubtype == 19 || hitSubtype == 20) {
            LOGI("PdfInteraction: Button clicked (Subtype %d). Performing Blanket Reveal.", hitSubtype);
            bool anyChanged = false;

            for (int j = 0; j < count; j++) {
                ScopedPdfAnnot target = get_annot_checked(page, j);
                if (!target.get() || !get_annot_flags_func || !set_annot_flags_func) continue;

                int flags = get_annot_flags_func(target.get());

                // We check for: Invisible (1), Hidden (2), or NoView (32)
                if (flags & (1 | 2 | 32)) {
                    LOGD("PdfInteraction: Unhiding element at index %d (Flags were 0x%X)", j, flags);

                    // Clear bits 1, 2, and 6 (1 + 2 + 32 = 35)
                    set_annot_flags_func(target.get(), flags & ~35);
                    anyChanged = true;
                }
            }

            if (anyChanged) {
                return JNI_TRUE;
            }
        }
    }

    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aryan_reader_pdf_NativePdfiumBridge_getLinkInfoAtPoint(JNIEnv *env, jclass clazz, jlong docPtr, jlong pagePtr, jdouble x, jdouble y) {
    std::lock_guard<std::recursive_mutex> lock(g_pdfium_mutex);
    if (!init_pdfium()) {
        LOGE("PdfLinkDiagnostic: init_pdfium failed.");
        return nullptr;
    }
    if (!get_link_at_point_func) {
        LOGE("PdfLinkDiagnostic: get_link_at_point_func is null.");
        return nullptr;
    }
    if (pagePtr == 0 || docPtr == 0) {
        LOGE("PdfLinkDiagnostic: Missing Pointers -> pagePtr=%ld, docPtr=%ld", (long)pagePtr, (long)docPtr);
        return nullptr;
    }

    void* page = reinterpret_cast<void*>(pagePtr);
    void* wrapperDoc = reinterpret_cast<void*>(docPtr);

    void* doc = wrapperDoc ? *(void**)wrapperDoc : nullptr;

    if (!doc) {
        LOGE("PdfLinkDiagnostic: Dereferenced doc pointer is null!");
        return nullptr;
    }

    LOGI("PdfLinkDiagnostic: Checking native link at x=%f, y=%f", x, y);

    void* link = get_link_at_point_func(page, x, y);
    if (!link) {
        LOGI("PdfLinkDiagnostic: No FPDF_LINK found at point.");
        return nullptr;
    }

    LOGI("PdfLinkDiagnostic: FPDF_LINK found!");

    if (get_link_action_func && get_action_type_func) {
        void* action = get_link_action_func(link);
        if (action) {
            unsigned long type = get_action_type_func(action);
            LOGI("PdfLinkDiagnostic: Action Type = %lu", type);

            if (type == 3 && get_uri_path_func) { // 3 = URI
                unsigned long len = get_uri_path_func(doc, action, nullptr, 0);
                if (len > 0) {
                    std::vector<char> buffer(len);
                    get_uri_path_func(doc, action, buffer.data(), len);
                    std::string uri(buffer.data());
                    LOGI("PdfLinkDiagnostic: Extracted Action URI = %s", uri.c_str());
                    std::string result = "URI:" + uri;
                    return env->NewStringUTF(result.c_str());
                }
            } else if (type == 1 && get_action_dest_func && get_dest_page_index_func) { // 1 = GoTo
                void* dest = get_action_dest_func(doc, action);
                if (dest) {
                    int pageIndex = get_dest_page_index_func(doc, dest);
                    LOGI("PdfLinkDiagnostic: Extracted Action GoTo Page = %d", pageIndex);
                    std::string result = "PAGE:" + std::to_string(pageIndex);
                    return env->NewStringUTF(result.c_str());
                }
            } else if ((type == 2 || type == 4) && get_file_path_func) { // 2 = RemoteGoTo, 4 = Launch
                unsigned long len = get_file_path_func(action, nullptr, 0);
                if (len > 0) {
                    std::vector<char> buffer(len);
                    get_file_path_func(action, buffer.data(), len);
                    std::string path(buffer.data());
                    LOGI("PdfLinkDiagnostic: Extracted File Path = %s", path.c_str());
                    std::string result = "URI:" + path;
                    return env->NewStringUTF(result.c_str());
                }
            }
        }
    }

    if (get_dest_func && get_dest_page_index_func) {
        void* dest = get_dest_func(doc, link);
        if (dest) {
            int pageIndex = get_dest_page_index_func(doc, dest);
            LOGI("PdfLinkDiagnostic: Extracted Direct Dest Page = %d", pageIndex);
            std::string result = "PAGE:" + std::to_string(pageIndex);
            return env->NewStringUTF(result.c_str());
        }
    }

    LOGI("PdfLinkDiagnostic: Link found but payload was empty or unsupported.");
    return nullptr;
}
