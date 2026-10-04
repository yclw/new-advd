package com.aeibi.avd.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private const val FADE_MS = 120
private val flavour = GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)

private data class ParsedMarkdown(val source: String, val root: ASTNode)

private fun parse(source: String) =
    ParsedMarkdown(source, MarkdownParser(flavour).buildMarkdownTreeFromString(source))

/** A snapshot is parsed before it becomes visible. New tokens are conflated while the previous
 * snapshot fades, so Markdown layout never flickers through half-parsed intermediate states. */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
internal fun StreamingMarkdown(text: String, streaming: Boolean, modifier: Modifier = Modifier) {
    var base by remember { mutableStateOf<ParsedMarkdown?>(null) }
    var overlay by remember { mutableStateOf<ParsedMarkdown?>(null) }
    val alpha = remember { Animatable(0f) }
    val latestText by rememberUpdatedState(text)

    LaunchedEffect(Unit) {
        snapshotFlow { latestText }
            .conflate()
            .mapLatest { source -> withContext(Dispatchers.Default) { parse(source) } }
            .conflate()
            .collect { next ->
                if (base?.source == next.source) return@collect
                if (base == null) {
                    base = next
                } else {
                    overlay = next
                    alpha.snapTo(0f)
                    alpha.animateTo(1f, tween(FADE_MS, easing = LinearOutSlowInEasing))
                    base = next
                    awaitFrame()
                    alpha.snapTo(0f)
                    overlay = null
                }
            }
    }

    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val baseSnapshot = base
        if (baseSnapshot != null) {
            if (streaming || overlay != null) {
                MarkdownContent(
                    baseSnapshot,
                    Modifier.graphicsLayer {
                        this.alpha = 1f - alpha.value
                    }
                )
            } else {
                SelectionContainer {
                    MarkdownContent(
                        baseSnapshot,
                        Modifier.graphicsLayer {
                            this.alpha =
                                1f - alpha.value
                        }
                    )
                }
            }
        } else {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        overlay?.let { next ->
            MarkdownContent(
                next,
                Modifier.clearAndSetSemantics {}.graphicsLayer {
                    this.alpha = alpha.value
                    blendMode = BlendMode.Plus
                }
            )
        }
    }
}

@Composable
private fun MarkdownContent(parsed: ParsedMarkdown, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parsed.root.children.forEach { node -> MarkdownBlock(node, parsed.source) }
    }
}

@Composable
private fun MarkdownBlock(node: ASTNode, source: String) {
    when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> Text(
            inline(node, source),
            style = MaterialTheme.typography.bodyLarge
        )
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2 -> Text(
            inline(
                node.children.firstOrNull {
                    it.type == MarkdownTokenTypes.ATX_CONTENT
                } ?: node,
                source
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> Text(
            inline(
                node.children.firstOrNull {
                    it.type == MarkdownTokenTypes.ATX_CONTENT
                } ?: node,
                source
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children.filter {
                    it.type == MarkdownElementTypes.LIST_ITEM
                }.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (node.type ==
                                MarkdownElementTypes.ORDERED_LIST
                            ) {
                                "${index + 1}."
                            } else {
                                "•"
                            }
                        )
                        Text(inline(item, source), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK -> {
            val raw = slice(node, source)
            val code = if (node.type == MarkdownElementTypes.CODE_FENCE) {
                raw.lineSequence().drop(1).filterNot {
                    it.trim().startsWith("```")
                }.joinToString("\n")
            } else {
                raw
            }
            Text(
                code.trimEnd(),
                modifier = Modifier.fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(10.dp)
                    )
                    .horizontalScroll(rememberScrollState()).padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(fontFamily = FontFamily.Monospace)
                )
            )
        }
        MarkdownElementTypes.BLOCK_QUOTE -> Text(
            "▏ " +
                slice(node, source).lineSequence().joinToString("\n") {
                    it.trimStart().removePrefix("> ")
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        GFMElementTypes.TABLE -> {
            val rows = node.children.filter {
                it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW
            }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(10.dp)
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.children.filter { it.type == GFMTokenTypes.CELL }.forEach { cell ->
                            Text(
                                slice(cell, source).trim().trim('|'),
                                modifier = Modifier.width(120.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (row.type == GFMElementTypes.HEADER) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            }
        }
        else -> {
            val raw = slice(node, source).trim()
            if (raw.isNotEmpty()) Text(raw, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun slice(node: ASTNode, source: String): String = source.substring(
    node.startOffset.coerceIn(0, source.length),
    node.endOffset.coerceIn(0, source.length)
)

private fun inline(node: ASTNode, source: String): AnnotatedString = buildAnnotatedString {
    appendInline(node, source)
}

private fun AnnotatedString.Builder.appendInline(node: ASTNode, source: String) {
    when (node.type) {
        MarkdownElementTypes.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children.filterNot {
                it.type == MarkdownTokenTypes.EMPH
            }.forEach { appendInline(it, source) }
        }
        MarkdownElementTypes.EMPH -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.filterNot {
                it.type == MarkdownTokenTypes.EMPH
            }.forEach { appendInline(it, source) }
        }
        GFMElementTypes.STRIKETHROUGH -> withStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ) {
            node.children.filterNot {
                it.type == GFMTokenTypes.TILDE
            }.forEach { appendInline(it, source) }
        }
        MarkdownElementTypes.CODE_SPAN -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(slice(node, source).trim('`'))
        }
        MarkdownElementTypes.INLINE_LINK -> {
            val url = node.descendants().firstOrNull {
                it.type ==
                    MarkdownElementTypes.LINK_DESTINATION
            }?.let { slice(it, source) }.orEmpty()
            val label = node.descendants().firstOrNull {
                it.type == MarkdownElementTypes.LINK_TEXT
            }?.let { slice(it, source).trim('[', ']') }.orEmpty()
            if (url.startsWith("https://") || url.startsWith("http://")) {
                withLink(LinkAnnotation.Url(url)) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(label.ifEmpty { url })
                    }
                }
            } else {
                append(label.ifEmpty { url })
            }
        }
        MarkdownTokenTypes.EMPH, MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.LIST_BULLET,
        MarkdownTokenTypes.LIST_NUMBER -> Unit
        else -> if (node.children.isEmpty()) {
            append(slice(node, source))
        } else {
            node.children.forEach { appendInline(it, source) }
        }
    }
}

private fun ASTNode.descendants(): Sequence<ASTNode> = sequence {
    yield(this@descendants)
    children.forEach { yieldAll(it.descendants()) }
}
