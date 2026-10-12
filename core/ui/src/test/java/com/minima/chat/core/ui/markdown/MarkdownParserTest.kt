package com.minima.chat.core.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parsesCodeWithLanguage() {
        val blocks = MarkdownParser.parseBlocks("```kotlin\nval x = 1\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun parsesBulletsAndNumbers() {
        val src = """
            - one
            - two
            1. first
            2. second
        """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(src)
        assertTrue(blocks.any { it is MdBlock.Bullet })
        assertTrue(blocks.any { it is MdBlock.Numbered })
        val numbered = blocks.filterIsInstance<MdBlock.Numbered>()
        assertEquals(1, numbered[0].number)
        assertEquals("first", numbered[0].text)
    }

    @Test
    fun parsesHeading() {
        val blocks = MarkdownParser.parseBlocks("## Title\n\nBody")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(2, (blocks[0] as MdBlock.Heading).level)
    }

    @Test
    fun unclosedFenceBecomesCode() {
        val blocks = MarkdownParser.parseBlocks("```\nno close")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Code)
        assertEquals("no close", (blocks[0] as MdBlock.Code).code)
    }

    @Test
    fun emptyYieldsParagraph() {
        val blocks = MarkdownParser.parseBlocks("")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun inlineBoldAndCode() {
        val ann = MarkdownParser.inlineMarkdown("say **hi** and `x`")
        assertTrue(ann.text.contains("hi"))
        assertTrue(ann.text.contains("x"))
    }

    @Test
    fun mixedBlocksOrder() {
        val src = "# H\n\npara\n\n- a\n\n```\nz\n```"
        val blocks = MarkdownParser.parseBlocks(src)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks.any { it is MdBlock.Paragraph })
        assertTrue(blocks.any { it is MdBlock.Bullet })
        assertTrue(blocks.any { it is MdBlock.Code })
    }

    @Test
    fun parsesGfmTable() {
        val src = """
            | Name | Qty |
            | --- | ---: |
            | apples | 3 |
            | **pears** | `2` |
        """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(src)
        assertEquals(1, blocks.size)
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("Name", "Qty"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("apples", "3"), table.rows[0])
        assertEquals(listOf("**pears**", "`2`"), table.rows[1])
        assertEquals(2, table.colCount)
    }

    @Test
    fun tableWithSurroundingParagraphs() {
        val src = """
            Before

            | A | B |
            |---|---|
            | 1 | 2 |

            After
        """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(src)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Table)
        assertTrue(blocks[2] is MdBlock.Paragraph)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("A", "B"), table.headers)
        assertEquals(listOf("1", "2"), table.rows.single())
    }

    @Test
    fun shortRowPaddedToHeaderWidth() {
        val src = """
            | A | B | C |
            |---|---|---|
            | only |
        """.trimIndent()
        val table = MarkdownParser.parseBlocks(src).single() as MdBlock.Table
        assertEquals(listOf("only", "", ""), table.rows.single())
    }

    @Test
    fun pipeWithoutSeparatorStaysParagraph() {
        val blocks = MarkdownParser.parseBlocks("a | b | c")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun splitTableRowTrimsOuterPipes() {
        assertEquals(listOf("a", "b"), MarkdownParser.splitTableRow("| a | b |"))
        assertEquals(listOf("a", "b"), MarkdownParser.splitTableRow("a | b"))
    }

    @Test
    fun separatorDetection() {
        assertTrue(MarkdownParser.looksLikeTableSeparator("| --- | ---: | :--- |"))
        assertTrue(MarkdownParser.looksLikeTableSeparator("|---|---|"))
        assertFalse(MarkdownParser.looksLikeTableSeparator("| Name | Qty |"))
        assertFalse(MarkdownParser.looksLikeTableSeparator("just text"))
    }

    @Test
    fun alignmentColonsAllowedInSeparator() {
        assertTrue(
            MarkdownParser.looksLikeTableHeader(
                "| left | center | right |",
                "| :--- | :---: | ---: |",
            ),
        )
    }

    @Test
    fun mismatchedSeparatorColsRejected() {
        assertFalse(
            MarkdownParser.looksLikeTableHeader(
                "| A | B |",
                "|---|",
            ),
        )
    }

    @Test
    fun plusBulletAndBlankSeparatedLists() {
        val src = """
            + alpha
            + beta

            - gamma
            1. one

            2. two
        """.trimIndent()
        val blocks = MarkdownParser.parseBlocks(src)
        val bullets = blocks.filterIsInstance<MdBlock.Bullet>()
        val nums = blocks.filterIsInstance<MdBlock.Numbered>()
        assertEquals(3, bullets.size)
        assertEquals("alpha", bullets[0].text)
        assertEquals("gamma", bullets[2].text)
        assertEquals(2, nums.size)
        assertEquals(1, nums[0].number)
        assertEquals(2, nums[1].number)
    }
}
