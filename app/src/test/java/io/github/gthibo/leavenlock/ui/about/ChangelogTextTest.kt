package io.github.gthibo.leavenlock.ui.about

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogTextTest {

    @Test
    fun wrappedBulletBecomesOneLine() {
        val lines = listOf(
            "- **The widget's button follows the lock state.** When the car is saved as locked, the",
            "  widget offers \"Unlock…\" instead of \"Lock now\".",
        )
        assertEquals(
            listOf(
                "- **The widget's button follows the lock state.** When the car is saved as locked, " +
                    "the widget offers \"Unlock…\" instead of \"Lock now\".",
            ),
            unwrapChangelog(lines),
        )
    }

    @Test
    fun boldSpanningAWrapIsRejoined() {
        val lines = listOf("- **New look and", "  name.** Done.")
        assertEquals(listOf("- **New look and name.** Done."), unwrapChangelog(lines))
    }

    @Test
    fun headingsBlanksRulesAndNewBulletsStaySeparate() {
        val lines = listOf(
            "# Changelog",
            "",
            "## [Unreleased]",
            "",
            "- First item, wrapped",
            "  over two lines.",
            "- Second item.",
            "",
            "---",
            "A paragraph",
            "that wraps.",
            "## [0.1.0] - 2026-11-04",
        )
        assertEquals(
            listOf(
                "# Changelog",
                "",
                "## [Unreleased]",
                "",
                "- First item, wrapped over two lines.",
                "- Second item.",
                "",
                "---",
                "A paragraph that wraps.",
                "## [0.1.0] - 2026-11-04",
            ),
            unwrapChangelog(lines),
        )
    }

    @Test
    fun lineAfterHeadingIsNotJoinedToIt() {
        assertEquals(
            listOf("## [0.1.0]", "First release text."),
            unwrapChangelog(listOf("## [0.1.0]", "First release text.")),
        )
    }
}
