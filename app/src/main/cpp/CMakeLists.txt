# CMakeLists.txt
# Sets the minimum version of CMake required.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("reader-native")

add_compile_options(
        "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=."
        "-ffile-prefix-map=${CMAKE_BINARY_DIR}=."
        -fno-ident
)

if(DEFINED CMAKE_ANDROID_NDK)
    add_compile_options("-ffile-prefix-map=${CMAKE_ANDROID_NDK}=/ndk")
endif()
if(DEFINED CMAKE_SYSROOT)
    add_compile_options("-ffile-prefix-map=${CMAKE_SYSROOT}=/sysroot")
endif()

add_compile_options(
        -Wno-builtin-macro-redefined
        -D__DATE__="\"1970-01-01\""
        -D__TIME__="\"00:00:00\""
)

set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--build-id=none")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id=none")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

function(strip_reproducibility_metadata target_name)
    add_custom_command(TARGET ${target_name} POST_BUILD
            COMMAND ${CMAKE_OBJCOPY}
            --remove-section=.comment
            --remove-section=.note.gnu.build-id
            $<TARGET_FILE:${target_name}>
            VERBATIM
    )
endfunction()

function(add_reproducibility_metadata_strip_target strip_target_name target_name)
    add_custom_target(${strip_target_name} ALL
            COMMAND ${CMAKE_OBJCOPY}
            --remove-section=.comment
            --remove-section=.note.gnu.build-id
            $<TARGET_FILE:${target_name}>
            DEPENDS ${target_name}
            VERBATIM
    )
endfunction()

# --- CONFIGURE SUBPROJECTS ---
# Force subprojects to build as static libraries. This is critical for Android.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
set(BROTLI_DISABLE_TESTS ON CACHE BOOL "Disable Brotli tests" FORCE)


# ===================================================================
# WOFF2 DEPENDENCY SETUP
# ===================================================================
# 1. Add the brotli project. This defines the `brotlidec-static` target.
add_subdirectory(woff2/brotli)

# 2. Manually define the variables that the woff2/CMakeLists.txt script expects.
set(BROTLIDEC_FOUND TRUE)
set(BROTLIENC_FOUND TRUE)
set(BROTLIDEC_INCLUDE_DIRS ${CMAKE_CURRENT_SOURCE_DIR}/woff2/brotli/c/include)
set(BROTLIENC_INCLUDE_DIRS ${CMAKE_CURRENT_SOURCE_DIR}/woff2/brotli/c/include)
set(BROTLIDEC_LIBRARIES brotlidec-static)
set(BROTLIENC_LIBRARIES brotlienc-static)

# 3. Add the woff2 project. This defines the `woff2dec` target.
add_subdirectory(woff2)


# ===================================================================
# LIBMOBI DEPENDENCY SETUP
# ===================================================================
# 4. Set libmobi options before adding it.
#    We disable libxml2 to use the internal writer, simplifying dependencies.
#    We also disable encryption for now for the same reason.
set(USE_LIBXML2 OFF CACHE BOOL "Use libxml2" FORCE)
set(USE_ENCRYPTION OFF CACHE BOOL "Enable encryption" FORCE)

# Temporarily enable shared libs to build libmobi as a .so file for LGPL compliance.
set(BUILD_SHARED_LIBS ON)
# 5. Add the libmobi project. This will define the `mobi` target as a shared library.
add_subdirectory(libmobi)

set_target_properties(mobi PROPERTIES VERSION "" SOVERSION "")
add_reproducibility_metadata_strip_target(strip_mobi_reproducibility_metadata mobi)
# Revert back to building static libs for any subsequent dependencies.
set(BUILD_SHARED_LIBS OFF)

# Force all symbols in the mobi shared library to be visible.
# This is necessary because some internal functions we use (like mobi_determine_flowpart_type)
# are not explicitly exported by the library's public API for shared builds.
set_target_properties(mobi PROPERTIES C_VISIBILITY_PRESET default)


# ===================================================================
# MD4C MARKDOWN PARSER (vendored, MIT license: see md4c/LICENSE.md)
# ===================================================================
add_library(md4c STATIC
        md4c/md4c.c
        md4c/md4c-html.c
        md4c/entity.c
)
target_include_directories(md4c PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/md4c)

# ===================================================================
# OFFICE_OXIDE_BRIDGE (Rust cdylib, see rust/office-oxide-bridge)
# ===================================================================
# Path to the Rust crate. Overridable with -DOFFICE_OXIDE_BRIDGE_ROOT=...;
# defaults to this repo's rust/office-oxide-bridge next to the app module.
if(NOT DEFINED OFFICE_OXIDE_BRIDGE_ROOT)
    get_filename_component(
            OFFICE_OXIDE_BRIDGE_ROOT
            "${CMAKE_CURRENT_SOURCE_DIR}/../../../../rust/office-oxide-bridge"
            ABSOLUTE)
endif()

# Cargo target triple per Android ABI.
set(OFFICE_OXIDE_ABI_TRIPLES
        "arm64-v8a=aarch64-linux-android"
        "armeabi-v7a=armv7-linux-androideabi"
        "x86=i686-linux-android"
        "x86_64=x86_64-linux-android")
set(OFFICE_OXIDE_TRIPLE "")
foreach(ENTRY ${OFFICE_OXIDE_ABI_TRIPLES})
    string(REPLACE "=" ";" PARTS ${ENTRY})
    list(GET PARTS 0 ABI)
    list(GET PARTS 1 TRIPLE)
    if(ABI STREQUAL "${ANDROID_ABI}")
        set(OFFICE_OXIDE_TRIPLE "${TRIPLE}")
    endif()
endforeach()

if(NOT OFFICE_OXIDE_TRIPLE)
    message(FATAL_ERROR "No cargo target triple configured for ANDROID_ABI=${ANDROID_ABI}")
endif()

set(OFFICE_OXIDE_LIB "${OFFICE_OXIDE_BRIDGE_ROOT}/target/${OFFICE_OXIDE_TRIPLE}/release/liboffice_oxide_bridge.so")
if(NOT EXISTS "${OFFICE_OXIDE_LIB}")
    message(FATAL_ERROR
            "Missing liboffice_oxide_bridge.so for ${ANDROID_ABI}.\n"
            "Expected at: ${OFFICE_OXIDE_LIB}\n"
            "Build it first:  ./gradlew :app:buildOfficeOxideLibraries\n"
            "Requires a Rust toolchain (https://rustup.rs) with the "
            "'${OFFICE_OXIDE_TRIPLE}' target installed.")
endif()

add_library(office_oxide_bridge SHARED IMPORTED)
set_target_properties(office_oxide_bridge PROPERTIES IMPORTED_LOCATION "${OFFICE_OXIDE_LIB}")
# The cargo-built .so carries no SONAME, so without this the linker records the
# absolute host path as DT_NEEDED and dlopen fails on device with ".../target/
# .../liboffice_oxide_bridge.so not found". With it, only the basename is
# recorded and the loader resolves it from the app's own lib/<abi> directory,
# where the .so is packaged next to native-lib.
set_target_properties(office_oxide_bridge PROPERTIES IMPORTED_NO_SONAME TRUE)

# ===================================================================
# FINAL NATIVE LIBRARY FOR THE APP
# ===================================================================
# 6. Define our final JNI wrapper library.
add_library(
        native-lib
        SHARED
        Woff2Converter.cpp
        mobi_jni_bridge.c
        pdfium_bridge.cpp  # Add this new file
        md4c_jni.c
        office_oxide_bridge_jni.cpp
)

# 7. Tell our library where to find all necessary header files.
target_include_directories(native-lib
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/woff2/include
        ${CMAKE_CURRENT_SOURCE_DIR}/libmobi/src
        ${OFFICE_OXIDE_BRIDGE_ROOT}/include
)

# 8. Find the Android logging and zlib libraries.
find_library(log-lib log)
find_library(z-lib z)

# 9. Link our final library against all the static libraries and Android libraries.
target_link_libraries(
        native-lib
        PRIVATE
        woff2dec
        mobi
        md4c
        office_oxide_bridge
        ${log-lib}
        ${z-lib}
        dl
)

add_dependencies(native-lib strip_mobi_reproducibility_metadata)
strip_reproducibility_metadata(native-lib)
