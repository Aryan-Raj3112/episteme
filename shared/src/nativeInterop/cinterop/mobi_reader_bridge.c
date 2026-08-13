#include "mobi_reader_bridge.h"

#include <stdlib.h>
#include <string.h>

#include "index.h"
#include "util.h"

static MOBIIndx *reader_mobi_toc(const MOBIRawml *rawml) {
    if (!rawml) {
        return NULL;
    }
    if (rawml->ncx && rawml->ncx->entries_count > 0) {
        return rawml->ncx;
    }
    if (rawml->guide && rawml->guide->entries_count > 0) {
        return rawml->guide;
    }
    return NULL;
}

size_t reader_mobi_toc_count(const MOBIRawml *rawml) {
    MOBIIndx *toc = reader_mobi_toc(rawml);
    if (!toc) {
        return 0;
    }
    size_t count = 0;
    for (size_t index = 0; index < toc->entries_count; index++) {
        uint32_t file_position = 0;
        if (mobi_get_indxentry_tagvalue(
                &file_position,
                &toc->entries[index],
                INDX_TAG_NCX_FILEPOS
            ) == MOBI_SUCCESS) {
            count++;
        }
    }
    return count;
}

int reader_mobi_toc_entry(
    const MOBIRawml *rawml,
    size_t visible_index,
    uint32_t *file_position,
    char *title,
    size_t title_capacity
) {
    MOBIIndx *toc = reader_mobi_toc(rawml);
    if (!toc || !file_position || !title || title_capacity == 0) {
        return 0;
    }
    size_t current_visible_index = 0;
    for (size_t index = 0; index < toc->entries_count; index++) {
        MOBIIndexEntry *entry = &toc->entries[index];
        uint32_t position = 0;
        if (mobi_get_indxentry_tagvalue(
                &position,
                entry,
                INDX_TAG_NCX_FILEPOS
            ) != MOBI_SUCCESS) {
            continue;
        }
        if (current_visible_index++ != visible_index) {
            continue;
        }

        char *cncx_title = NULL;
        uint32_t cncx_offset = 0;
        if (toc->cncx_record &&
            mobi_get_indxentry_tagvalue(
                &cncx_offset,
                entry,
                INDX_TAG_NCX_TEXT_CNCX
            ) == MOBI_SUCCESS) {
            cncx_title = mobi_get_cncx_string(toc->cncx_record, cncx_offset);
        }
        const char *source_title =
            (cncx_title && cncx_title[0] != '\0') ? cncx_title :
            (entry->label ? entry->label : "");
        *file_position = position;
        snprintf(title, title_capacity, "%s", source_title);
        free(cncx_title);
        return 1;
    }
    return 0;
}

MOBIFiletype reader_mobi_flow_type(const MOBIRawml *rawml, size_t flow_index) {
    return mobi_determine_flowpart_type(rawml, flow_index);
}

int reader_mobi_cover_resource(
    const MOBIData *mobi,
    uint32_t *uid,
    const unsigned char **data,
    size_t *size,
    MOBIFiletype *type
) {
    if (!mobi || !uid || !data || !size || !type) {
        return 0;
    }
    MOBIExthHeader *cover = mobi_get_exthrecord_by_tag(mobi, EXTH_COVEROFFSET);
    if (!cover || !mobi->mh || !mobi->mh->image_index) {
        return 0;
    }
    uint32_t cover_offset = mobi_decode_exthvalue(cover->data, cover->size);
    size_t first_image_sequence = *mobi->mh->image_index;
    MOBIPdbRecord *record =
        mobi_get_record_by_seqnumber(mobi, first_image_sequence + cover_offset);
    if (!record || !record->data || record->size == 0) {
        return 0;
    }
    *uid = record->uid;
    *data = record->data;
    *size = record->size;
    *type = mobi_determine_resource_type(record);
    return 1;
}

size_t reader_mobi_cover_size(const MOBIData *mobi) {
    uint32_t uid = 0;
    const unsigned char *data = NULL;
    size_t size = 0;
    MOBIFiletype type = T_UNKNOWN;
    return reader_mobi_cover_resource(mobi, &uid, &data, &size, &type) ? size : 0;
}

size_t reader_mobi_copy_cover(const MOBIData *mobi, unsigned char *output, size_t capacity) {
    uint32_t uid = 0;
    const unsigned char *data = NULL;
    size_t size = 0;
    MOBIFiletype type = T_UNKNOWN;
    if (!output ||
        !reader_mobi_cover_resource(mobi, &uid, &data, &size, &type) ||
        size > capacity) {
        return 0;
    }
    memcpy(output, data, size);
    return size;
}
