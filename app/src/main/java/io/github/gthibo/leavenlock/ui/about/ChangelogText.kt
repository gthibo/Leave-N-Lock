package io.github.gthibo.leavenlock.ui.about

/**
 * Joins the hard-wrapped lines of CHANGELOG.md back into whole bullets and paragraphs, the way
 * Markdown reads them. The file is wrapped at ~95 characters for editing; shown line by line,
 * sentences would break mid-way on the phone and bold spans across a wrap would be lost.
 *
 * A line continues the one before it unless either is blank, a heading (`#`), a rule
 * (`---`, `***`, `___`), or the line starts a new bullet (`- ` / `* `).
 */
internal fun unwrapChangelog(lines: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (line in lines) {
        val prev = out.lastOrNull()
        if (prev != null && isFlowText(prev) && isContinuation(line)) {
            out[out.lastIndex] = prev.trimEnd() + " " + line.trim()
        } else {
            out += line
        }
    }
    return out
}

private fun isRule(trimmed: String) = trimmed == "---" || trimmed == "***" || trimmed == "___"

private fun isBullet(trimmed: String) = trimmed.startsWith("- ") || trimmed.startsWith("* ")

/** A bullet or paragraph line that a following line may continue. */
private fun isFlowText(line: String): Boolean {
    val t = line.trim()
    return t.isNotEmpty() && !t.startsWith("#") && !isRule(t)
}

/** A line that carries on the previous bullet or paragraph rather than starting something new. */
private fun isContinuation(line: String): Boolean {
    val t = line.trim()
    return t.isNotEmpty() && !t.startsWith("#") && !isRule(t) && !isBullet(t)
}
