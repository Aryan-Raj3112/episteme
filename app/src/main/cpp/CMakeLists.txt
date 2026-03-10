# CMakeLists.txt
# Sets the minimum version of CMake required.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("reader-native")

add_compile_options(
    -ffile-prefix-map=${CMAKE_SOURCE_DIR}=.
    -ffile-prefix-map=${CMAKE_BINARY_DIR}=.
)

set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

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
# Revert back to building static libs for any subsequent dependencies.
set(BUILD_SHARED_LIBS OFF)

# Force all symbols in the mobi shared library to be visible.
# This is necessary because some internal functions we use (like mobi_determine_flowpart_type)
# are not explicitly exported by the library's public API for shared builds.
set_target_properties(mobi PROPERTIES C_VISIBILITY_PRESET default)


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
)

# 7. Tell our library where to find all necessary header files.
target_include_directories(native-lib
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/woff2/include
        ${CMAKE_CURRENT_SOURCE_DIR}/libmobi/src
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
        ${log-lib}
        ${z-lib}
        dl
)