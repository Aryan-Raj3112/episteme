package com.aryan.reader.data

import android.content.Intent

/** Every persisted folder grant acquired by the app is released on disconnect. */
internal val PERSISTED_URI_GRANT_FLAGS: Int =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
