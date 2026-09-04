package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCharsetPlausibilityTest {

    // ---- CJK script gates ----

    @Test
    fun cjkGateAcceptsKanaEvidenceForJapaneseCandidates() {
        val decoded = "日本語のテキストです。文字コードの判定を確認するための、やや長い文章を収めています。"
        val profile = SharedCharsetPlausibility.scriptProfile(decoded)
        assertTrue(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.SHIFT_JIS, profile))
        assertTrue(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.EUC_JP, profile))
    }

    @Test
    fun cjkGateRejectsHanOnlyDecodeForJapaneseCandidates() {
        // GB18030 decoding of Japanese Shift-JIS text produces Han only (no kana) —
        // offline-verified EUC-JP decode of the GB fixture below.
        val profile = SharedCharsetPlausibility.scriptProfile("嶄猟霞編宸頁匯粁熟海議酒悶嶄猟猟云喘噐忖憲鹿殊霞響慕聞繁序化")
        assertFalse(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.EUC_JP, profile))
    }

    @Test
    fun cjkGateAcceptsHanForBig5AndGb18030() {
        val profile = SharedCharsetPlausibility.scriptProfile("中文测试这是一段较长的简体中文文本用于字符集检测读书使人进步")
        assertTrue(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.GB18030, profile))
        assertTrue(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.BIG5, profile))
    }

    @Test
    fun cjkGateRejectsKanaContaminationForHanFamilies() {
        // Big5 decode of Simplified GBK text leaks kana/PUA characters.
        val profile = SharedCharsetPlausibility.scriptProfile("羉砰いゅ代刚硂琌")
        assertFalse(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.GB18030, profile))
        assertFalse(SharedCharsetPlausibility.cjkAccepts(SharedCjkCandidate.BIG5, profile))
    }

    @Test
    fun cjkGateRequiresHangulForEucKr() {
        val decoded = "한국어 텍스트입니다. 문자 집합 판별을 확인하기 위한 다소 긴 문장을 담고 있습니다."
        assertTrue(
            SharedCharsetPlausibility.cjkAccepts(
                SharedCjkCandidate.EUC_KR,
                SharedCharsetPlausibility.scriptProfile(decoded)
            )
        )
        // EUC-KR decode of Chinese GB text produces Han, not Hangul.
        assertFalse(
            SharedCharsetPlausibility.cjkAccepts(
                SharedCjkCandidate.EUC_KR,
                SharedCharsetPlausibility.scriptProfile("櫓匡꿎桿侶角寧뙈싹낀돨숌")
            )
        )
    }

    // ---- C1 control rejection ----

    @Test
    fun rejectsControlCharactersInDecodedText() {
        val withControls = "some text \u0081 more text \u009D"
        assertTrue(SharedCharsetPlausibility.hasControlCharacters(withControls))
        val clean = "Café Münchén – déjà vu, naïve façade über alle Maßen gerettet."
        assertFalse(SharedCharsetPlausibility.hasControlCharacters(clean))
        assertFalse(SharedCharsetPlausibility.hasControlCharacters("word\tline\n\r\nend\u000C"))
        // DEL (0x7F) is also rejected as a control character.
        assertTrue(SharedCharsetPlausibility.hasControlCharacters("a\u007Fb"))
    }

    // ---- Single-byte frequency models ----

    @Test
    fun cyrillicModelRanksWindows1251OverWesternMojibake() {
        val cyrillicText = "Привет мир! Это тестовая строка на русском языке для проверки определения кодировки символов."
        // A wrong-codepage decode yields accented Latin mojibake (Ïðèâåò...), scoring near zero.
        val latinMojibake = "Ïðèâåò ìèð! Ýòî òåñòîâàÿ ñòðîêà íà ðóññêîì ÿçûêå."
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(cyrillicText, SharedCyrillicSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(latinMojibake, SharedCyrillicSingleByteModel)
        )
    }

    @Test
    fun cyrillicModelRanksKoi8DecodeOverWindows1251Mojibake() {
        // KOI8 and windows-1251 permute the Cyrillic block differently; decoding
        // KOI8 bytes as windows-1251 yields "рТПЧЕТЛБ ЛПДЙТПЧЛЙ..." mojibake that
        // lands on rare letters, while the correct KOI8 decode lands on frequent ones.
        val koi8Decoded = "Проверка кодировки КОИ-8 для русского текста с достаточным количеством символов."
        val win1251Mojibake = "рТПЧЕТЛБ ЛПДЙТПЧЛЙ лпй-8 ДМС ТХУУЛПЗП ФЕЛУФБ У ДПУСТАТОЧНЫМ КОЛИЧЕСТВОМ СИМВОЛОВ."
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(koi8Decoded, SharedCyrillicSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(win1251Mojibake, SharedCyrillicSingleByteModel)
        )
    }

    @Test
    fun latinModelRanksAccentedWesternTextOverCyrillicMojibake() {
        val westernText = "Café Münchén – déjà vu, naïve façade über alle Maßen gerettet. Diese Zeile wiederholt sich, damit genug Daten vorhanden sind."
        // Same bytes decoded as windows-1251 give Cyrillic letters (wrong language).
        val cyrillicMojibake = "юafэ юпnchыn – фейа иu, натіие фаыade юber alle юaюen гereттет."
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(westernText, SharedLatinSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(cyrillicMojibake, SharedLatinSingleByteModel)
        )
    }

    @Test
    fun greekModelRanksGreekTextOverCyrillicMojibake() {
        val greekText = "Ελληνικό κείμενο δοκιμής για τον εντοπισμό κωδικοποίησης χαρακτήρων με αρκετό μήκος."
        val cyrillicMojibake = "Улленпто кбйжено фоппшэб фбл лон энтопыфм кпо пкопыуернщ"
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(greekText, SharedGreekSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(cyrillicMojibake, SharedGreekSingleByteModel)
        )
    }

    @Test
    fun hebrewModelRanksHebrewTextOverLatinMojibake() {
        val hebrewText = "קובץ טקסט בעברית לבדיקת זיהוי קידוד תווים עם מספיק תוכן לצורך הבדיקה."
        val latinMojibake = "lbeС ytСsЛ бvСrСfl ЖlфСЛВl жСfvСl иСfвС lттСm рзпЛlЛ рттЛn рС зтрЛ бСфСЛВb."
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(hebrewText, SharedHebrewSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(latinMojibake, SharedHebrewSingleByteModel)
        )
    }

    @Test
    fun arabicModelRanksArabicTextOverCyrillicMojibake() {
        val arabicText = "ملف نصي باللغة العربية لاختبار اكتشاف ترميز الأحرف مع محتوى كافٍ لإجراء الاختبار."
        // Windows-1251 decode of Arabic bytes produces Cyrillic letters scoring ~0 on the Arabic model.
        val cyrillicMojibake = "уьу №ЛЙ итгум фпЯСфЬ фпЯ уЯуеуп ЯШърЯ урЯщУ уф ЯиЯкЯ тЯуСпЯ юрг фЯу уьЯЛрЯ бЯуспЯ фЯуррЯспЯ."
        assertTrue(
            SharedCharsetPlausibility.scoreSingleByteDecode(arabicText, SharedArabicSingleByteModel) >
                SharedCharsetPlausibility.scoreSingleByteDecode(cyrillicMojibake, SharedArabicSingleByteModel)
        )
    }

    // ---- ISO-2022 escape detection ----

    @Test
    fun detectsIso2022EscapeSequences() {
        val payload = byteArrayOf(0x41, 0x1B, 0x24, 0x42, 0x46, 0x7C, 0x41)
        assertTrue(SharedLegacyCharsetFamilies.containsIso2022Escape(payload))
        val plain = "Plain ASCII and some 0x1B-free text.".encodeToByteArray()
        assertFalse(SharedLegacyCharsetFamilies.containsIso2022Escape(plain))
        // ESC only counts within the leading window.
        val lateEscape = ByteArray(2048)
        lateEscape[1024] = 0x1B
        assertFalse(SharedLegacyCharsetFamilies.containsIso2022Escape(lateEscape))
    }
}
