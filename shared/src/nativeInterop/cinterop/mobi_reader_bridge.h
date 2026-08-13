#ifndef READER_MOBI_BRIDGE_H
#define READER_MOBI_BRIDGE_H

#include <stddef.h>
#include <stdint.h>
#include "mobi.h"

#ifdef __cplusplus
extern "C" {
#endif

size_t reader_mobi_toc_count(const MOBIRawml *rawml);
int reader_mobi_toc_entry(
    const MOBIRawml *rawml,
    size_t visible_index,
    uint32_t *file_position,
    char *title,
    size_t title_capacity
);
MOBIFiletype reader_mobi_flow_type(const MOBIRawml *rawml, size_t flow_index);
int reader_mobi_cover_resource(
    const MOBIData *mobi,
    uint32_t *uid,
    const unsigned char **data,
    size_t *size,
    MOBIFiletype *type
);

size_t reader_mobi_cover_size(const MOBIData *mobi);
size_t reader_mobi_copy_cover(const MOBIData *mobi, unsigned char *output, size_t capacity);

#ifdef __cplusplus
}
#endif

#endif
