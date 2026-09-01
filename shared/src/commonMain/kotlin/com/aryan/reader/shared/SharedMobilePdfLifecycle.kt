package com.aryan.reader.shared

/** Lifecycle boundary shared by mobile PDF hosts. */
enum class MobilePdfLifecycleAction {
    NORMAL_SAVE,
    FINAL_FLUSH,
}

fun mobilePdfLifecycleAction(isActive: Boolean): MobilePdfLifecycleAction =
    if (isActive) MobilePdfLifecycleAction.NORMAL_SAVE else MobilePdfLifecycleAction.FINAL_FLUSH
