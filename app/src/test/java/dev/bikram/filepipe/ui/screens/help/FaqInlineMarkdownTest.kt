package dev.bikram.filepipe.ui.screens.help

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaqInlineMarkdownTest {
    private val codeBackground = Color.Gray

    @Test
    fun codeSpansDropTheirBackticksAndGetMonospace() {
        val text = parseFaqInlineMarkdown("Type `cache/` on its own line.", codeBackground)

        assertEquals("Type cache/ on its own line.", text.text)
        val code = text.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("cache/", text.text.substring(code.start, code.end))
        assertEquals(codeBackground, code.item.background)
    }

    @Test
    fun boldStillWorksAlongsideCode() {
        val text = parseFaqInlineMarkdown("Tap **RegEx Off** next to `^cache$`.", codeBackground)

        assertEquals("Tap RegEx Off next to ^cache$.", text.text)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("RegEx Off", text.text.substring(bold.start, bold.end))
        assertTrue(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun asterisksInsideCodeAreLiteral() {
        val text = parseFaqInlineMarkdown("`**` is not bold here", codeBackground)

        assertEquals("** is not bold here", text.text)
        assertTrue(text.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun aBacktickWithoutAPartnerIsShownAsTyped() {
        assertEquals("a ` b", parseFaqInlineMarkdown("a ` b", codeBackground).text)
    }
}
