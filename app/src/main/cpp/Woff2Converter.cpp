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
#include <string>
#include <vector>

// This is the correct header for the public API
#include <woff2/decode.h>

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_aryan_reader_paginatedreader_Woff2Converter_convertWoff2ToTtf(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray woff2_data) {

    // Get the input WOFF2 data from the jbyteArray
    jbyte* woff2_bytes = env->GetByteArrayElements(woff2_data, nullptr);
    jsize woff2_size = env->GetArrayLength(woff2_data);
    const uint8_t* woff2_input = reinterpret_cast<const uint8_t*>(woff2_bytes);

    // Calculate the required size using the correct function name from your header
    size_t ttf_size = woff2::ComputeWOFF2FinalSize(woff2_input, woff2_size);
    if (ttf_size == 0) {
        // This indicates an error in the input font data
        env->ReleaseByteArrayElements(woff2_data, woff2_bytes, JNI_ABORT);
        return nullptr;
    }

    // Create the output buffer
    std::vector<uint8_t> ttf_output(ttf_size);

    // Perform the conversion using the deprecated function signature that matches your header
    bool success = woff2::ConvertWOFF2ToTTF(
            ttf_output.data(), ttf_size,
            woff2_input, woff2_size
    );

    // Release the input byte array
    env->ReleaseByteArrayElements(woff2_data, woff2_bytes, JNI_ABORT);

    // If conversion failed, return null
    if (!success) {
        return nullptr;
    }

    // Create a new Java byte array for the result
    jbyteArray ttf_data = env->NewByteArray(ttf_size);
    if (ttf_data == nullptr) {
        // Out of memory error
        return nullptr;
    }

    // Copy the converted data to the Java byte array
    env->SetByteArrayRegion(ttf_data, 0, ttf_size,
                            reinterpret_cast<const jbyte*>(ttf_output.data()));

    return ttf_data;
}