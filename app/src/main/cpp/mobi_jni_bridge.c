/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <malloc.h>
#include "mobi.h"
#include "index.h" // Needed for INDX_TAG_NCX_FILEPOS
#include "util.h"  // Needed for mobi_determine_resource_type

#define TAG "MobiJNI"
#define AZW3_DEBUG_TAG "AZW3_DEBUG" // Common tag for diagnosis
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOG_AZW3(...) __android_log_print(ANDROID_LOG_DEBUG, AZW3_DEBUG_TAG, __VA_ARGS__)


// Helper function to find a class and handle exceptions
jclass find_class(JNIEnv *env, const char *name) {
    jclass clazz = (*env)->FindClass(env, name);
    if (clazz == NULL) {
        (*env)->ExceptionClear(env);
        LOGE("Failed to find class: %s", name);
    }
    return clazz;
}

// Helper function to create a Java String from a C string (UTF-8)
jstring new_string_utf(JNIEnv *env, const char *str) {
    if (str == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, str);
}

// Main JNI function
JNIEXPORT jobject JNICALL
Java_com_aryan_reader_epub_MobiParser_parseMobiFile(JNIEnv *env, jobject thiz, jstring file_path) {
    const char *native_file_path = (*env)->GetStringUTFChars(env, file_path, 0);
    LOGD("Starting to parse file: %s", native_file_path);

    MOBIData *m = mobi_init();
    if (m == NULL) {
        LOGE("mobi_init() failed");
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    FILE *file = fopen(native_file_path, "rb");
    if (file == NULL) {
        LOGE("Failed to open file: %s", native_file_path);
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    MOBI_RET mobi_ret = mobi_load_file(m, file);
    fclose(file);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGE("mobi_load_file() failed with error: %d", mobi_ret);
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    if (m->rh && m->rh->encryption_type != MOBI_ENCRYPTION_NONE) {
        LOGE("File is protected by DRM (encryption type: %d) and cannot be opened.", m->rh->encryption_type);
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    // Add diagnostic logs
    if (m->mh) {
        LOG_AZW3("MOBI Version: %u", (m->mh->version) ? *m->mh->version : 0);
        LOG_AZW3("Is KF8: %s", mobi_is_kf8(m) ? "yes" : "no");
        LOG_AZW3("Is Hybrid: %s", mobi_is_hybrid(m) ? "yes" : "no");
    }


    MOBIRawml *rawml = mobi_init_rawml(m);
    if (rawml == NULL) {
        LOGE("mobi_init_rawml() failed");
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    // Use the _opt version to explicitly enable parsing of all TOC types (NCX, Guide, etc.)
    mobi_ret = mobi_parse_rawml_opt(rawml, m, true, false, true);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGE("mobi_parse_rawml_opt() failed with error: %d", mobi_ret);
        mobi_free_rawml(rawml);
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    LOGD("Successfully parsed MOBI file.");

    // Find Kotlin data classes
    jclass parsed_data_class = find_class(env, "com/aryan/reader/epub/MobiParser$ParsedMobiData");
    jclass resource_class = find_class(env, "com/aryan/reader/epub/MobiParser$ParsedMobiResource");
    jclass toc_entry_class = find_class(env, "com/aryan/reader/epub/MobiParser$ParsedMobiTocEntry");


    if (!parsed_data_class || !resource_class || !toc_entry_class) {
        LOGE("Could not find one or more required Kotlin data classes.");
        mobi_free_rawml(rawml);
        mobi_free(m);
        (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);
        return NULL;
    }

    // Get constructors for data classes (NOTE THE NEW SIGNATURE for ParsedMobiData)
    jmethodID parsed_data_ctor = (*env)->GetMethodID(env, parsed_data_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Lcom/aryan/reader/epub/MobiParser$ParsedMobiResource;[Lcom/aryan/reader/epub/MobiParser$ParsedMobiTocEntry;I)V");
    jmethodID resource_ctor = (*env)->GetMethodID(env, resource_class, "<init>", "(ILjava/lang/String;[BLjava/lang/String;)V");
    jmethodID toc_entry_ctor = (*env)->GetMethodID(env, toc_entry_class, "<init>", "(Ljava/lang/String;I)V");

    // --- Manually concatenate flow parts to get the full raw HTML content ---
    size_t total_html_size = 0;
    size_t num_flow_parts = 0;
    MOBIPart *curr_part = rawml->flow;
    while (curr_part) {
        if (curr_part->data) {
            total_html_size += curr_part->size;
        }
        num_flow_parts++;
        curr_part = curr_part->next;
    }

    jstring raw_html_string = NULL;
    if (total_html_size > 0) {
        char *full_html = malloc(total_html_size + 1);
        if (full_html) {
            size_t current_offset = 0;
            curr_part = rawml->flow;
            while (curr_part) {
                if (curr_part->data) {
                    memcpy(full_html + current_offset, curr_part->data, curr_part->size);
                    current_offset += curr_part->size;
                }
                curr_part = curr_part->next;
            }
            full_html[total_html_size] = '\0'; // Null-terminate the final string

            raw_html_string = new_string_utf(env, full_html);
            free(full_html);
            LOGD("Manually concatenated %zu flow parts into single string (%zu bytes).", num_flow_parts, total_html_size);
        } else {
            LOGE("Failed to allocate memory for full HTML string.");
            raw_html_string = NULL;
        }
    } else {
        LOGE("No data found in rawml->flow parts.");
    }


    // --- Create Resource Array ---

    // 1. Find the cover record first.
    jint cover_uid = -1;
    MOBIPdbRecord *cover_image_rec = NULL;
    MOBIExthHeader *cover_exth = mobi_get_exthrecord_by_tag(m, EXTH_COVEROFFSET);
    if (cover_exth) {
        uint32_t cover_offset = mobi_decode_exthvalue(cover_exth->data, cover_exth->size);
        if (m->mh && m->mh->image_index) {
            size_t first_image_seq = *m->mh->image_index;
            cover_image_rec = mobi_get_record_by_seqnumber(m, first_image_seq + cover_offset);
            if (cover_image_rec) {
                cover_uid = (jint)cover_image_rec->uid;
            }
        }
    } else {
        LOGD("JNI: No cover metadata found (EXTH 201).");
    }

    // 2. Count resources from rawml (binary resources AND flow-based CSS)
    size_t num_binary_resources = 0;
    bool cover_in_resources = false;
    curr_part = rawml->resources;
    while (curr_part) {
        num_binary_resources++;
        if (cover_image_rec && curr_part->uid == cover_image_rec->uid) {
            cover_in_resources = true;
        }
        curr_part = curr_part->next;
    }

    // NEW: Count flow parts that are CSS
    size_t num_css_flow_parts = 0;
    size_t flow_part_index = 0;
    curr_part = rawml->flow;
    while (curr_part) {
        // mobi_determine_flowpart_type is key here
        if (mobi_determine_flowpart_type(rawml, flow_part_index) == T_CSS) {
            num_css_flow_parts++;
        }
        flow_part_index++;
        curr_part = curr_part->next;
    }
    LOG_AZW3("Found %zu binary resources and %zu CSS resources in flow.", num_binary_resources, num_css_flow_parts);


    size_t total_resources = num_binary_resources + num_css_flow_parts;
    if (cover_image_rec && !cover_in_resources) {
        total_resources++;
    }

    // 3. Create the array and populate it.
    jobjectArray resource_array = (*env)->NewObjectArray(env, total_resources, resource_class, NULL);
    size_t i = 0; // This is now the main index for the resource_array

    // Add binary resources first
    curr_part = rawml->resources;
    while (curr_part) {
        if (curr_part->data) {
            MOBIFileMeta meta = mobi_get_filemeta_by_type(curr_part->type);
            char filename[64];
            snprintf(filename, sizeof(filename), "res_%05zu.%s", curr_part->uid, meta.extension);
            LOG_AZW3("Binary Resource -> UID: %zu, Type: %s, Mime: %s, Filename: %s", curr_part->uid, meta.extension, meta.mime_type, filename);

            jstring path = new_string_utf(env, filename);
            jstring mimetype = new_string_utf(env, meta.mime_type);
            jbyteArray data_array = (*env)->NewByteArray(env, curr_part->size);
            (*env)->SetByteArrayRegion(env, data_array, 0, curr_part->size, (jbyte *)curr_part->data);

            jobject resource_obj = (*env)->NewObject(env, resource_class, resource_ctor, (jint)curr_part->uid, path, data_array, mimetype);
            (*env)->SetObjectArrayElement(env, resource_array, i++, resource_obj);

            (*env)->DeleteLocalRef(env, path);
            (*env)->DeleteLocalRef(env, mimetype);
            (*env)->DeleteLocalRef(env, data_array);
            (*env)->DeleteLocalRef(env, resource_obj);
        }
        curr_part = curr_part->next;
    }

    // NEW: Add flow-based CSS resources
    flow_part_index = 0;
    curr_part = rawml->flow;
    while (curr_part) {
        if (mobi_determine_flowpart_type(rawml, flow_part_index) == T_CSS) {
            MOBIFileMeta meta = mobi_get_filemeta_by_type(T_CSS);
            char filename[64];
            // We use the flow part index for a unique filename, since flow parts don't have UIDs
            snprintf(filename, sizeof(filename), "flow_%05zu.css", flow_part_index);
            LOG_AZW3("Flow Resource (CSS) -> Index: %zu, Mime: %s, Filename: %s", flow_part_index, meta.mime_type, filename);

            jstring path = new_string_utf(env, filename);
            jstring mimetype = new_string_utf(env, meta.mime_type);
            jbyteArray data_array = (*env)->NewByteArray(env, curr_part->size);
            (*env)->SetByteArrayRegion(env, data_array, 0, curr_part->size, (jbyte *)curr_part->data);

            // Use flow_part_index for UID as it's unique in this context. Add a large offset to avoid collisions with real UIDs.
            jobject resource_obj = (*env)->NewObject(env, resource_class, resource_ctor, (jint)(100000 + flow_part_index), path, data_array, mimetype);
            (*env)->SetObjectArrayElement(env, resource_array, i++, resource_obj);

            (*env)->DeleteLocalRef(env, path);
            (*env)->DeleteLocalRef(env, mimetype);
            (*env)->DeleteLocalRef(env, data_array);
            (*env)->DeleteLocalRef(env, resource_obj);
        }
        flow_part_index++;
        curr_part = curr_part->next;
    }

    // 4. If we need to add the cover manually, do it now.
    if (cover_image_rec && !cover_in_resources) {
        MOBIFiletype cover_type = mobi_determine_resource_type(cover_image_rec);
        MOBIFileMeta meta = mobi_get_filemeta_by_type(cover_type);
        char filename[64];
        snprintf(filename, sizeof(filename), "res_%05zu.%s", cover_image_rec->uid, meta.extension);

        LOG_AZW3("Manually adding cover resource -> UID: %zu, Type: %s, Mime: %s", cover_image_rec->uid, meta.extension, meta.mime_type);

        jstring path = new_string_utf(env, filename);
        jstring mimetype = new_string_utf(env, meta.mime_type);
        jbyteArray data_array = (*env)->NewByteArray(env, cover_image_rec->size);
        (*env)->SetByteArrayRegion(env, data_array, 0, cover_image_rec->size, (jbyte *)cover_image_rec->data);

        jobject resource_obj = (*env)->NewObject(env, resource_class, resource_ctor, (jint)cover_image_rec->uid, path, data_array, mimetype);
        (*env)->SetObjectArrayElement(env, resource_array, i, resource_obj); // Use i, not i++

        (*env)->DeleteLocalRef(env, path);
        (*env)->DeleteLocalRef(env, mimetype);
        (*env)->DeleteLocalRef(env, data_array);
        (*env)->DeleteLocalRef(env, resource_obj);
    }
    LOGD("Processed %zu total resources.", total_resources);


    // --- Create TOC Array (from NCX index or Guide index) ---
    jobjectArray toc_array = NULL;
    MOBIIndx *toc_indx = NULL;
    const char *toc_type_str = "None";

    if (rawml->ncx && rawml->ncx->entries_count > 0) {
        toc_indx = rawml->ncx;
        toc_type_str = "NCX";
    } else if (rawml->guide && rawml->guide->entries_count > 0) {
        toc_indx = rawml->guide;
        toc_type_str = "Guide";
    }
    LOG_AZW3("TOC type detected: %s", toc_type_str);

    if (toc_indx) {
        size_t valid_entries_count = 0;
        // First, count valid entries to allocate the exact array size.
        for (size_t i = 0; i < toc_indx->entries_count; ++i) {
            MOBIIndexEntry entry = toc_indx->entries[i];
            uint32_t file_pos = 0;
            if (mobi_get_indxentry_tagvalue(&file_pos, &entry, INDX_TAG_NCX_FILEPOS) == MOBI_SUCCESS) {
                valid_entries_count++;
            }
        }

        if (valid_entries_count > 0) {
            toc_array = (*env)->NewObjectArray(env, valid_entries_count, toc_entry_class, NULL);
            size_t current_valid_index = 0;
            for (size_t i = 0; i < toc_indx->entries_count; ++i) {
                MOBIIndexEntry entry = toc_indx->entries[i];
                uint32_t file_pos = 0;

                // We check again, this time processing the valid entries.
                if (mobi_get_indxentry_tagvalue(&file_pos, &entry, INDX_TAG_NCX_FILEPOS) == MOBI_SUCCESS) {
                    char *chapter_title_str = NULL;
                    uint32_t cncx_text_offset = 0;

                    // Try to get the title from the CNCX text tag first. This is the most reliable.
                    if (toc_indx->cncx_record &&
                        mobi_get_indxentry_tagvalue(&cncx_text_offset, &entry, INDX_TAG_NCX_TEXT_CNCX) == MOBI_SUCCESS) {
                        chapter_title_str = mobi_get_cncx_string(toc_indx->cncx_record, cncx_text_offset);
                    }

                    jstring title;
                    if (chapter_title_str != NULL && strlen(chapter_title_str) > 0) {
                        // Success! Use the title from CNCX.
                        title = new_string_utf(env, chapter_title_str);
                        free(chapter_title_str); // mobi_get_cncx_string allocates memory that we must free.
                    } else {
                        // Fallback to using the entry label if CNCX fails or is empty.
                        title = new_string_utf(env, entry.label);
                        LOGD("%s: TOC Entry [%zu]: Title from Label: '%s'", TAG, current_valid_index, entry.label);
                    }

                    jobject toc_entry_obj = (*env)->NewObject(env, toc_entry_class, toc_entry_ctor, title, (jint) file_pos);
                    (*env)->SetObjectArrayElement(env, toc_array, current_valid_index, toc_entry_obj);
                    current_valid_index++;

                    (*env)->DeleteLocalRef(env, title);
                    (*env)->DeleteLocalRef(env, toc_entry_obj);
                }
            }
        }
    } else {
        LOGD("%s: No NCX or Guide TOC found in the document.", TAG);
    }

    // --- Create Final Data Object ---
    jstring title = new_string_utf(env, mobi_meta_get_title(m));
    jstring author = new_string_utf(env, mobi_meta_get_author(m));
    jstring publisher = new_string_utf(env, mobi_meta_get_publisher(m));

    jobject result = (*env)->NewObject(env, parsed_data_class, parsed_data_ctor,
                                       title, author, publisher,
                                       raw_html_string, // Pass the single HTML string
                                       resource_array, toc_array, cover_uid);

    // --- Cleanup ---
    (*env)->DeleteLocalRef(env, title);
    (*env)->DeleteLocalRef(env, author);
    (*env)->DeleteLocalRef(env, publisher);
    if (raw_html_string) {
        (*env)->DeleteLocalRef(env, raw_html_string);
    }
    (*env)->DeleteLocalRef(env, resource_array);
    if (toc_array) {
        (*env)->DeleteLocalRef(env, toc_array);
    }
    mobi_free_rawml(rawml);
    mobi_free(m);
    (*env)->ReleaseStringUTFChars(env, file_path, native_file_path);

    return result;
}