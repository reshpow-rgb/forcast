package com.forcast.asxvalue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the small Markdown subset used by research notes: headings, bullets, numbered lists,
 * paragraphs, horizontal rules, **bold**, _italic_ / *italic* and [links](https://…).
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val paragraph = StringBuilder()
        val blocks = mutableListOf<Pair<String, String>>() // kind to text
        fun flush() {
            if (paragraph.isNotBlank()) blocks += "p" to paragraph.toString().trim()
            paragraph.clear()
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val t = line.trimStart()
            when {
                t.isEmpty() -> flush()
                t.startsWith("### ") -> { flush(); blocks += "h3" to t.removePrefix("### ") }
                t.startsWith("## ") -> { flush(); blocks += "h2" to t.removePrefix("## ") }
                t.startsWith("# ") -> { flush(); blocks += "h1" to t.removePrefix("# ") }
                t == "---" || t == "***" -> { flush(); blocks += "hr" to "" }
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ") -> {
                    flush(); blocks += (if (line.length - t.length >= 2) "li2" else "li") to t.substring(2)
                }
                Regex("^\\d+[.)] ").containsMatchIn(t) -> {
                    flush(); blocks += "ol" to t
                }
                else -> paragraph.append(if (paragraph.isEmpty()) t else " $t")
            }
        }
        flush()

        for ((kind, text) in blocks) {
            when (kind) {
                "h1" -> Text(inline(text, linkColor), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                "h2" -> Text(inline(text, linkColor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                "h3" -> Text(inline(text, linkColor), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                "hr" -> HorizontalDivider()
                "li", "li2", "ol" -> Row(Modifier.padding(start = if (kind == "li2") 20.dp else 4.dp)) {
                    if (kind != "ol") {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    }
                    Text(inline(text, linkColor), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(inline(text, linkColor), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\((https?://[^)\s]+)\)|(?<![\w*])[*_](?![\s*_])(.+?)(?<![\s*_])[*_](?![\w*])""")

private fun inline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(i, m.range.first))
        val (bold, linkText, url, italic) = m.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            url.isNotEmpty() -> withLink(
                LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))),
            ) { append(linkText) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        i = m.range.last + 1
    }
    append(text.substring(i))
}
