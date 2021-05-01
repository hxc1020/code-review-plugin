import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.DocumentUtil
import java.awt.Color
import java.awt.Container
import java.awt.Font
import java.awt.Point
import java.net.URL
import kotlin.math.roundToInt

val Project.editorService: EditorService?
    get() = getServiceIfNotDisposed(EditorService::class.java)

val Project.codeReview: CodeReviewComponent?
    get() =
        if (!isDisposed) getComponent(CodeReviewComponent::class.java)
        else null

fun <T : Any> Project.getServiceIfNotDisposed(serviceClass: Class<T>): T? =
    if (!isDisposed) ServiceManager.getService(this, serviceClass)
    else null

fun Document.position(offset: Int): Position {
    val line = getLineNumber(offset)
    val lineStart = getLineStartOffset(line)
    val lineTextBeforeOffset = getText(TextRange.create(lineStart, offset))
    val column = lineTextBeforeOffset.length
    return Position(line, column)
}

val FoldRegion.start get() = document.position(startOffset)
val FoldRegion.end get() = document.position(endOffset)

val Document.uri: String?
    get() {
        val file = FileDocumentManager.getInstance().getFile(this)
        return file?.uri
    }

//val Document.textDocumentIdentifier: TextDocumentIdentifier?
//    get() = uri?.let { TextDocumentIdentifier(uri) }

val Document.file: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

val Editor.displayPath: String?
    get() = FileDocumentManager.getInstance().getFile(document)?.name

fun Editor.getOffset(position: Position): Int {
    val line = position.line
    if (document.lineCount == 0) return 0
    if (line >= document.lineCount) {
        return document.getLineEndOffset(document.lineCount - 1)
    }
    val lineText = document.getText(DocumentUtil.getLineTextRange(document, line))
    val endIndex = Math.min(lineText.length, position.character)
    val lineTextForPosition = lineText.substring(0, endIndex)
    val tabs = StringUtil.countChars(lineTextForPosition, '\t')
    val tabSize = settings.getTabSize(project)
    val column = tabs * tabSize + lineTextForPosition.length - tabs
    val offset = logicalPositionToOffset(LogicalPosition(line, column))
    if (position.character >= lineText.length) {
        // println("LSPPOS outofbounds : $pos line : $lineText column : $column offset : $offset")
    }
    val docLength = document.textLength
    if (offset > docLength) {
        println("Offset greater than text length : $offset > $docLength")
    }
    return offset.coerceIn(0, docLength)
}

val Editor.margins: EditorMargins
    get() {
        var withTabHeaders = this.component as Container?
        while (withTabHeaders != null && withTabHeaders !is JBTabsImpl) {
            withTabHeaders = withTabHeaders.parent
        }

        var withBreadcrumbs = this.component as Container?
        while (withBreadcrumbs != null && withBreadcrumbs !is EditorWindowHolder) {
            withBreadcrumbs = withBreadcrumbs.parent
        }

        val height = this.component.height
        val heightWithBreadcrumbs = withBreadcrumbs?.height ?: height
        val heightWithTabHeaders = withTabHeaders?.height ?: height
        val tabRowHeight = ((withTabHeaders as? JBTabsImpl)?.myInfo2Label?.values?.first()?.height ?: 27) + 1

        var bottom: Int
        var top: Int
        if (this.document.file is ReviewDiffVirtualFile) {
            top = (heightWithTabHeaders - tabRowHeight - height).coerceAtLeast(0)
            bottom = 0
        } else {
            top = (heightWithTabHeaders - heightWithBreadcrumbs - tabRowHeight).coerceAtLeast(0)
            bottom = (heightWithBreadcrumbs - height).coerceAtLeast(0)
        }

        return EditorMargins(top, 0, bottom, 0)
    }

val Editor.selections: List<EditorSelection>
    get() {
        return listOf(
            EditorSelection(
                document.position(selectionModel.selectionStart),
                document.position(selectionModel.selectionEnd),
                document.position(caretModel.offset)
            )
        )
    }

val Editor.highlightTextAttributes: TextAttributes
    get() {
        val bg = colorsScheme.defaultBackground
        val highlight =
            if (ColorUtil.isDark(bg)) bg.lighten(7)
            else bg.darken(7)
        return TextAttributes(
            null,
            highlight,
            null,
            null,
            Font.PLAIN
        )
    }

val Editor.selectionOrCurrentLine: Range
    get() = if (selectionModel.hasSelection()) {
        Range(document.position(selectionModel.selectionStart), document.position(selectionModel.selectionEnd))
    } else {
        val logicalPos = caretModel.currentCaret.logicalPosition
        val startOffset = logicalPositionToOffset(LogicalPosition(logicalPos.line, 0))
        val endOffset = logicalPositionToOffset(LogicalPosition(logicalPos.line, Int.MAX_VALUE))
        Range(document.position(startOffset), document.position(endOffset))
    }

val Editor.visibleRanges: List<Range>
    get() {
        val visibleArea = scrollingModel.visibleArea

        val viewportStartPoint = visibleArea.location
        val startLogicalPos = xyToLogicalPosition(viewportStartPoint)
        val startOffset = logicalPositionToOffset(startLogicalPos)
        val startLspPos = document.position(startOffset)

        val viewportEndPoint = Point(
            visibleArea.location.x + visibleArea.width,
            visibleArea.location.y + visibleArea.height
        )
        val endLogicalPos = xyToLogicalPosition(viewportEndPoint)

        // getOffset will coerce it within document length
        val endLspPos = document.position(getOffset(Position(endLogicalPos.line, endLogicalPos.column)))

        val fullRange = Range(
            startLspPos,
            endLspPos
        )

        val ranges = mutableListOf<Range>()
        var range = Range(fullRange.start, fullRange.end)

        val collapsedRegions = foldingModel.allFoldRegions.filter { !it.isExpanded }
        for (collapsed in collapsedRegions) {
            if (collapsed.end < fullRange.start) {
                continue
            }
            if (collapsed.start > fullRange.end) {
                break
            }

            val previousExpandedOffset = getOffset(collapsed.start) - 1
            val nextExpandedOffset = getOffset(collapsed.end) + 1
            if (previousExpandedOffset >= 0) {
                range.end = document.position(previousExpandedOffset)
                if (range.start < range.end) {
                    ranges += range
                }

                range = Range(document.position(nextExpandedOffset), fullRange.end)
            } else {
                ranges += Range(range.start, range.start)
                range.start = document.position(nextExpandedOffset)
            }
        }

        ranges += range

        return ranges
    }

fun Editor.isRangeVisible(range: Range): Boolean {
    val ranges = this.visibleRanges
    val firstRange = ranges.first()
    val lastRange = ranges.last()
    return range.start.line >= firstRange.start.line && range.end.line <= lastRange.end.line
}

fun Color.darken(percentage: Int): Color {
    return darken(percentage.toFloat())
}

fun Color.darken(percentage: Float): Color {
    return lighten(-percentage)
}

fun Color.lighten(percentage: Int): Color {
    return lighten(percentage.toFloat())
}

fun Color.lighten(percentage: Float): Color {
    val amount = (255 * percentage) / 100
    return Color(
        adjustLight(red, amount),
        adjustLight(green, amount),
        adjustLight(blue, amount),
        alpha
    )
}

fun Color.opacity(percentage: Int): Color {
    return Color(
        red,
        green,
        blue,
        (255 * ((alpha / 255) * percentage.toFloat() / 100)).roundToInt()
    )
}

private fun adjustLight(light: Int, amount: Float): Int {
    val cc = light + amount
    val c = cc.coerceIn(0F, 255F)
    return c.roundToInt()
}

val VirtualFile.uri: String?
    get() {
        return try {
            sanitizeURI(
                URL(
                    url
                        .replace(" ", SPACE_ENCODED)
                        .replace("#", HASH_ENCODED)
                ).toURI().toString()
            )
        } catch (e: Exception) {
            // LOG.warn(e)
            null
        }
    }

operator fun Position.compareTo(another: Position): Int {
    return when {
        line < another.line -> -1
        line > another.line -> 1
        else -> when {
            character < another.character -> -1
            character > another.character -> 1
            else -> 0
        }
    }
}

val SelectionEvent.editorSelections: List<EditorSelection>
    get() = newRanges.map {
        EditorSelection(
            editor.document.position(it.startOffset),
            editor.document.position(it.endOffset),
            editor.document.position(editor.caretModel.offset)
        )
    }

val SelectionEvent.lspRange: Range
    get() = Range(
        editor.document.position(newRange.startOffset),
        editor.document.position(newRange.endOffset)
    )
