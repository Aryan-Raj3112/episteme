package com.aryan.reader.pdf

object NativePdfiumBridge {
    init {
        System.loadLibrary("native-lib")
    }

    @JvmStatic external fun getFontSize(textPagePtr: Long, index: Int): Double
    @JvmStatic external fun getFontWeight(textPagePtr: Long, index: Int): Int

    @JvmStatic external fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray?
    @JvmStatic external fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray?
    @JvmStatic external fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray?
}