package io.github.gthibo.leavenlock.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.gthibo.leavenlock.BuildConfig
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.Hairline
import io.github.gthibo.leavenlock.ui.components.HeroBanner
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PgTopBar
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText

private const val PROJECT_URL = "https://github.com/gthibo/Leave-N-Lock"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changelog = remember { readChangelog(context) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Porcelain.colors.surface,
        topBar = { PgTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = PorcelainSpace.screenMargin, end = PorcelainSpace.screenMargin, top = PorcelainSpace.s1, bottom = PorcelainSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
        ) {
            HeroBanner(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.about_hero_subtitle),
                icon = null,
                eyebrow = stringResource(R.string.about_hero_eyebrow),
            )

            PgCard(title = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
                Text(stringResource(R.string.about_open_source_body), style = PorcelainText.body)
                PgButton(
                    text = stringResource(R.string.about_view_project),
                    onClick = { openUrl(context, PROJECT_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Secondary,
                    icon = R.drawable.ic_pg_arrow_right,
                )
            }

            PgCard(title = stringResource(R.string.about_whats_new), spacing = PorcelainSpace.s2) {
                if (changelog.isEmpty()) {
                    Text(stringResource(R.string.about_changelog_unavailable), style = PorcelainText.body, color = mutedText)
                } else {
                    changelog.forEach { line -> ChangelogLine(line) }
                }
            }
        }
    }
}

/** Renders one Markdown line: headings, bullets, horizontal rules + inline **bold**, *italic*, `code`, links. */
@Composable
private fun ChangelogLine(line: String) {
    val linkColor = Porcelain.colors.link
    val codeColor = Porcelain.colors.inkMuted
    val trimmed = line.trimStart()
    when {
        line.startsWith("# ") -> Unit // top-level title, skip
        line.startsWith("### ") -> Text(
            inlineMarkdown(line.removePrefix("### ").trim(), linkColor, codeColor),
            style = PorcelainText.heading3,
        )
        line.startsWith("## ") -> Text(
            inlineMarkdown(line.removePrefix("## ").trim(), linkColor, codeColor),
            style = PorcelainText.heading2,
        )
        trimmed == "---" || trimmed == "***" || trimmed == "___" -> Hairline()
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
            buildAnnotatedString {
                append("•  ")
                append(inlineMarkdown(trimmed.drop(2).trim(), linkColor, codeColor))
            },
            style = PorcelainText.body,
        )
        line.isBlank() -> Spacer(Modifier.height(2.dp))
        else -> Text(
            inlineMarkdown(line.trim(), linkColor, codeColor),
            style = PorcelainText.body,
        )
    }
}

/** Parses inline Markdown (bold, italic, code, links) into a styled, clickable [AnnotatedString]. */
@Composable
private fun inlineMarkdown(text: String, linkColor: Color, codeColor: Color): AnnotatedString =
    remember(text, linkColor, codeColor) { parseInline(text, linkColor, codeColor) }

private fun parseInline(text: String, linkColor: Color, codeColor: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            val rest = text.substring(i)
            when {
                // [label](url) → clickable link
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val openParen = if (closeBracket != -1) closeBracket + 1 else -1
                    if (closeBracket != -1 && openParen < n && text[openParen] == '(') {
                        val closeParen = text.indexOf(')', openParen + 1)
                        if (closeParen != -1) {
                            val label = text.substring(i + 1, closeBracket)
                            val url = text.substring(openParen + 1, closeParen)
                            withLink(
                                LinkAnnotation.Url(
                                    url,
                                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                                ),
                            ) { append(label) }
                            i = closeParen + 1
                            continue
                        }
                    }
                    append(text[i]); i++
                }
                // **bold** or __bold__
                rest.startsWith("**") || rest.startsWith("__") -> {
                    val delim = rest.substring(0, 2)
                    val close = text.indexOf(delim, i + 2)
                    if (close != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, close)) }
                        i = close + 2
                    } else { append(text[i]); i++ }
                }
                // `code`
                text[i] == '`' -> {
                    val close = text.indexOf('`', i + 1)
                    if (close != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else { append(text[i]); i++ }
                }
                // *italic* or _italic_
                text[i] == '*' || text[i] == '_' -> {
                    val delim = text[i]
                    val close = text.indexOf(delim, i + 1)
                    if (close != -1 && close > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, close)) }
                        i = close + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }

/** Reads the bundled CHANGELOG.md, keeping only the human-readable body (no link refs). */
private fun readChangelog(context: android.content.Context): List<String> = runCatching {
    context.assets.open("CHANGELOG.md").bufferedReader().use { it.readLines() }
        .filterNot { it.startsWith("[") && it.contains("]: ") } // drop markdown link references
        .let(::unwrapChangelog) // one line per bullet/paragraph, not per wrapped source line
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
}.getOrDefault(emptyList())

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
