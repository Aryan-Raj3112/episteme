package com.aryan.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FishVoiceParsingTest {

    @Test
    fun `parses ids titles and descriptions`() {
        val voice = fishVoiceFromModel(
            JSONObject("""{"_id":"abc-123","title":"Narrator","description":"Warm"}""")
        )

        assertNotNull(voice)
        assertEquals("abc-123", voice!!.referenceId)
        assertEquals("Narrator", voice.title)
        assertEquals("Warm", voice.description)
    }

    @Test
    fun `falls back to id and name`() {
        val voice = fishVoiceFromModel(JSONObject("""{"id":"raw-id","name":"Fallback"}"""))

        assertNotNull(voice)
        assertEquals("raw-id", voice!!.referenceId)
        assertEquals("Fallback", voice.title)
    }

    @Test
    fun `keeps trained and stateless entries`() {
        assertNotNull(fishVoiceFromModel(JSONObject("""{"_id":"t1","state":"trained"}""")))
        assertNotNull(fishVoiceFromModel(JSONObject("""{"_id":"t2"}""")))
    }

    @Test
    fun `parses language codes`() {
        val voice = fishVoiceFromModel(
            JSONObject("""{"_id":"l1","languages":["en","hi",""]}""")
        )

        assertNotNull(voice)
        assertEquals(listOf("en", "hi"), voice!!.languages)
    }

    @Test
    fun `skips non tts model types`() {
        assertNull(fishVoiceFromModel(JSONObject("""{"_id":"s1","type":"svc"}""")))
        assertNotNull(fishVoiceFromModel(JSONObject("""{"_id":"t3","type":"tts"}""")))
    }

    @Test
    fun `skips unsynthesizable voices and blank ids`() {
        assertNull(fishVoiceFromModel(JSONObject("""{"_id":"x","state":"training"}""")))
        assertNull(fishVoiceFromModel(JSONObject("""{"_id":"x","state":"failed"}""")))
        assertNull(fishVoiceFromModel(JSONObject("""{"title":"No id"}""")))
        assertNull(fishVoiceFromModel(null))
    }
}
