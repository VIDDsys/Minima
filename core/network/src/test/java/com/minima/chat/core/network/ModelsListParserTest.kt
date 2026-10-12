package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.ModelsListParser
import com.minima.chat.core.network.api.SecretRedact
import org.junit.Test

class ModelsListParserTest {
    @Test
    fun openaiShapeExtractsIdsInOrder() {
        val raw = """
            {
              "object": "list",
              "data": [
                {"id": "gpt-4o-mini", "object": "model", "owned_by": "openai"},
                {"id": "gpt-4o", "object": "model"},
                {"id": "o1-mini"}
              ]
            }
        """.trimIndent()
        val r = ModelsListParser.parse(raw)
        assertThat(r.error).isNull()
        assertThat(r.ids).containsExactly("gpt-4o-mini", "gpt-4o", "o1-mini").inOrder()
    }

    @Test
    fun dedupesBlankAndDuplicateIds() {
        val raw = """
            {"data":[
              {"id":"a"},
              {"id":"  "},
              {"id":"a"},
              {"id":"b"},
              {"object":"model"},
              "bare-string-id"
            ]}
        """.trimIndent()
        val r = ModelsListParser.parse(raw)
        assertThat(r.error).isNull()
        assertThat(r.ids).containsExactly("a", "b", "bare-string-id").inOrder()
    }

    @Test
    fun rootArrayAccepted() {
        val r = ModelsListParser.parse("""[{"id":"x"},{"id":"y"}]""")
        assertThat(r.ids).containsExactly("x", "y").inOrder()
    }

    @Test
    fun emptyDataIsError() {
        val r = ModelsListParser.parse("""{"object":"list","data":[]}""")
        assertThat(r.ids).isEmpty()
        assertThat(r.error).contains("No model ids")
    }

    @Test
    fun missingDataIsError() {
        val r = ModelsListParser.parse("""{"object":"list"}""")
        assertThat(r.ids).isEmpty()
        assertThat(r.error).isNotNull()
    }

    @Test
    fun blankRawIsError() {
        assertThat(ModelsListParser.parse(null).error).contains("Empty")
        assertThat(ModelsListParser.parse("   ").error).contains("Empty")
    }

    @Test
    fun invalidJsonIsErrorWithoutLeakingKey() {
        val r = ModelsListParser.parse("""{"data":[{"id":"sk-abcdefghijklmnopqrstuv"}""")
        // truncated JSON → parse error; if somehow message echoed key, redact
        assertThat(r.ids).isEmpty()
        assertThat(r.error).isNotNull()
        val redacted = SecretRedact.redact(r.error)
        assertThat(redacted).doesNotContain("sk-abcdefghijklmnopqrstuv")
    }

    @Test
    fun nestedResultData() {
        val raw = """{"result":{"data":[{"id":"proxy-model"}]}}"""
        val r = ModelsListParser.parse(raw)
        assertThat(r.ids).containsExactly("proxy-model")
    }

    @Test
    fun ignoresNonObjectNoise() {
        val raw = """{"data":[1, true, null, {"id":"ok"}]}"""
        val r = ModelsListParser.parse(raw)
        assertThat(r.ids).containsExactly("ok")
    }
}
