package com.aryan.reader.data

import android.net.Uri
import androidx.core.net.toUri

fun RecentFileItem.getUri(): Uri? = uriString?.toUri()
