package dev.bikram.filepipe.ui.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight markdown for in-app copy (e.g. changelogs): headings, lists, code fences,
 * inline bold/italic/code/links. Images become tappable links (no image loading dependency).
 * Approach aligned with a small custom parser rather than a full markdown engine.
 */
@Composable
fun SimpleMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val blocks = remember(content) { parseMarkdownToBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val style =
                        when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            4 -> MaterialTheme.typography.titleMedium
                            5 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.titleSmall
                        }
                    HeaderLine(block.text, style, block.isCentered)
                }

                is MarkdownBlock.Text -> {
                    MarkdownText(block.text, block.isCentered)
                }

                is MarkdownBlock.BulletPoint -> {
                    BulletPointLine(block.text, block.isCentered)
                }

                is MarkdownBlock.OrderedList -> {
                    OrderedListLine(
                        block.index,
                        block.text,
                        block.isCentered,
                    )
                }

                is MarkdownBlock.ImageGroup -> {
                    ImageBlock(block.images, block.isCentered)
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlock(block.code)
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalRule()
                }

                is MarkdownBlock.Spacer -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Header(
        val text: String,
        val level: Int,
        val isCentered: Boolean,
    ) : MarkdownBlock()

    data class Text(
        val text: String,
        val isCentered: Boolean,
    ) : MarkdownBlock()

    data class BulletPoint(
        val text: String,
        val isCentered: Boolean,
    ) : MarkdownBlock()

    data class OrderedList(
        val index: String,
        val text: String,
        val isCentered: Boolean,
    ) : MarkdownBlock()

    data class ImageGroup(
        val images: List<ImageData>,
        val isCentered: Boolean,
    ) : MarkdownBlock()

    data class CodeBlock(
        val code: String,
    ) : MarkdownBlock()

    data object HorizontalRule : MarkdownBlock()

    data object Spacer : MarkdownBlock()
}

private data class ImageData(
    val url: String,
    val alt: String,
    val widthFraction: Float? = null,
)

private fun parseMarkdownToBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var isInsideCenter = false
    var isInsideCodeBlock = false
    val codeBlockLines = mutableListOf<String>()
    val imageBuffer = mutableListOf<ImageData>()

    fun flushImages() {
        if (imageBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.ImageGroup(imageBuffer.toList(), isInsideCenter))
            imageBuffer.clear()
        }
    }

    lines.forEach { line ->
        val trimmedLine = line.trim()
        val imagesOnLine = parseImagesFromLine(line)

        if (imagesOnLine.isNotEmpty()) {
            imageBuffer.addAll(imagesOnLine)
            return@forEach
        }

        flushImages()

        when {
            trimmedLine.startsWith("```") -> {
                if (isInsideCodeBlock) {
                    blocks.add(MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n")))
                    codeBlockLines.clear()
                    isInsideCodeBlock = false
                } else {
                    isInsideCodeBlock = true
                }
            }

            isInsideCodeBlock -> {
                codeBlockLines.add(line)
            }

            trimmedLine == "<center>" || trimmedLine == "<p align=\"center\">" -> {
                isInsideCenter = true
            }

            trimmedLine == "</center>" || trimmedLine == "</p>" -> {
                isInsideCenter = false
            }

            trimmedLine.isEmpty() -> {
                if (!isInsideCodeBlock) {
                    blocks.add(MarkdownBlock.Spacer)
                }
            }

            trimmedLine == "---" -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }

            trimmedLine.startsWith("######") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("######").trim(),
                        6,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("#####") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("#####").trim(),
                        5,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("####") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("####").trim(),
                        4,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("###") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("###").trim(),
                        3,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("##") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("##").trim(),
                        2,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("#") -> {
                blocks.add(
                    MarkdownBlock.Header(
                        trimmedLine.substringAfter("#").trim(),
                        1,
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.startsWith("-") || (trimmedLine.startsWith("*") && trimmedLine.getOrNull(1) == ' ') -> {
                blocks.add(
                    MarkdownBlock.BulletPoint(
                        trimmedLine.substring(1).trim(),
                        isInsideCenter,
                    ),
                )
            }

            trimmedLine.firstOrNull()?.isDigit() == true && trimmedLine.contains(". ") -> {
                val index = trimmedLine.substringBefore(". ")
                if (index.all { it.isDigit() }) {
                    blocks.add(
                        MarkdownBlock.OrderedList(
                            index,
                            trimmedLine.substringAfter(". ").trim(),
                            isInsideCenter,
                        ),
                    )
                } else {
                    blocks.add(MarkdownBlock.Text(line, isInsideCenter))
                }
            }

            else -> {
                blocks.add(MarkdownBlock.Text(line, isInsideCenter))
            }
        }
    }
    flushImages()
    return blocks
}

private fun parseImagesFromLine(line: String): List<ImageData> {
    val images = mutableListOf<ImageData>()

    val mdRegex = Regex("!\\[(.*?)]\\((.*?)\\)")
    mdRegex.findAll(line).forEach { match ->
        images.add(ImageData(url = match.groupValues[2], alt = match.groupValues[1]))
    }

    val htmlRegex = Regex("<img.*?>")
    val srcRegex = Regex("src=\"(.*?)\"")
    val altRegex = Regex("alt=\"(.*?)\"")
    val widthRegex = Regex("width=\"(.*?)\"")

    htmlRegex.findAll(line).forEach { match ->
        val tag = match.value
        val src = srcRegex.find(tag)?.groupValues?.get(1)
        if (src != null) {
            val alt = altRegex.find(tag)?.groupValues?.get(1) ?: "image"
            val widthStr = widthRegex.find(tag)?.groupValues?.get(1)
            val widthFraction =
                if (widthStr?.endsWith("%") == true) {
                    widthStr.substringBefore("%").toFloatOrNull()?.let { fraction -> fraction / 100f }
                } else if (widthStr?.any { it.isDigit() } == true && !widthStr.contains("%")) {
                    null
                } else {
                    null
                }

            images.add(ImageData(url = src, alt = alt, widthFraction = widthFraction))
        }
    }

    return images
}

@Composable
private fun HorizontalRule() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderedListLine(
    index: String,
    content: String,
    isCentered: Boolean,
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        MarkdownText(content, isCentered)
    }
}

@Composable
private fun HeaderLine(
    text: String,
    style: TextStyle,
    isCentered: Boolean,
) {
    Text(
        text = parseMarkdownInline(text),
        style = style,
        color = MaterialTheme.colorScheme.primary,
        textAlign = if (isCentered) TextAlign.Center else TextAlign.Start,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun BulletPointLine(
    content: String,
    isCentered: Boolean,
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        MarkdownText(content, isCentered)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageBlock(
    images: List<ImageData>,
    isCentered: Boolean,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = if (isCentered) Arrangement.Center else Arrangement.Start,
    ) {
        images.forEach { image ->
            val label = image.alt.ifBlank { "Image" }
            val syntheticLink = "[$label](${image.url})"
            Text(
                text = parseMarkdownInline(syntheticLink),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun MarkdownText(
    text: String,
    isCentered: Boolean,
) {
    val cleanText = text.replace(Regex("<.*?>"), "").trim()
    if (cleanText.isNotBlank()) {
        Text(
            text = parseMarkdownInline(cleanText),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (isCentered) TextAlign.Center else TextAlign.Start,
            modifier = if (isCentered) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

@Composable
private fun parseMarkdownInline(text: String): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0

        val regex =
            Regex("(\\*\\*.*?\\*\\*)|(\\*.*?\\*)|(_.*?_)|(<u>.*?</u>)|(\\[.*?]\\(.*?\\))|(`.*?`)")
        val matches = regex.findAll(text)

        matches.forEach { match ->
            val matchValue = match.value
            val start = match.range.first

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            when {
                matchValue.startsWith("**") && matchValue.endsWith("**") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(matchValue.substring(2, matchValue.length - 2))
                    }
                }

                (matchValue.startsWith("*") && matchValue.endsWith("*") && !matchValue.startsWith("**")) ||
                    (matchValue.startsWith("_") && matchValue.endsWith("_")) -> {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(matchValue.substring(1, matchValue.length - 1))
                    }
                }

                matchValue.startsWith("<u>") && matchValue.endsWith("</u>") -> {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(matchValue.substring(3, matchValue.length - 4))
                    }
                }

                matchValue.startsWith("`") && matchValue.endsWith("`") -> {
                    withStyle(
                        style =
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        append(matchValue.substring(1, matchValue.length - 1))
                    }
                }

                matchValue.startsWith("[") && matchValue.contains("](") -> {
                    val title = matchValue.substringAfter("[").substringBefore("](")
                    val url = matchValue.substringAfter("](").substringBefore(")")
                    withLink(
                        link =
                            LinkAnnotation.Url(
                                url = url,
                                styles =
                                    TextLinkStyles(
                                        style =
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                                textDecoration = TextDecoration.Underline,
                                            ),
                                    ),
                            ),
                    ) {
                        append(title)
                    }
                }

                else -> {
                    append(matchValue)
                }
            }

            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
