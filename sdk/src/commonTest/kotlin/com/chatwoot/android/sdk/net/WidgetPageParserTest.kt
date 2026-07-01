package com.chatwoot.android.sdk.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetPageParserTest {

    @Test
    fun extractsTokensFromWidgetHtml() {
        // Mirrors the real script block served by GET /widget on app.chatwoot.com.
        val html = """
            <script>
              window.chatwootWebChannel = { websiteToken: 'abc' }
              window.authToken = 'eyJhbGciOiJIUzI1NiJ9.payload.sig'
              window.chatwootPubsubToken = 'czoAyeaR79j2BQdaNqy9ajFJ'
            </script>
        """.trimIndent()

        val session = WidgetPageParser.parse(html)
        assertEquals("eyJhbGciOiJIUzI1NiJ9.payload.sig", session?.authToken)
        assertEquals("czoAyeaR79j2BQdaNqy9ajFJ", session?.pubsubToken)
    }

    @Test
    fun returnsNullWhenTokensMissing() {
        assertNull(WidgetPageParser.parse("<html><body>404</body></html>"))
        assertNull(WidgetPageParser.parse("window.authToken = 'only-auth'"))
    }
}
