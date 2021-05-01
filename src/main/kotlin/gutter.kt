import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.Processor
import javax.swing.Icon
import kotlin.math.min

class GutterIconManager(private val editor: Editor) : EditorMouseMotionListener, SelectionListener {

    private val isCSDiff = editor.document.file is ReviewDiffVirtualFile

    init {
        editor.selectionModel.addSelectionListener(this)
        editor.addEditorMouseMotionListener(this)
        if (isCSDiff) {
            (editor as EditorEx).gutterComponentEx.setInitialIconAreaWidth(20)
        }
    }

    private var lastHighlightedLine: Int? = null
    private val lineHighlighters = mutableMapOf<Int, RangeHighlighter>()
    private var isDragging = false
    private val renderer = NewCodeMarkGutterIconRenderer(
        editor,
        1,
    ) { disableCurrentRenderer() }

    override fun mouseMoved(e: EditorMouseEvent) {
        if (e.area == EditorMouseEventArea.LINE_MARKERS_AREA) {
            val line = editor.xyToLogicalPosition(e.mouseEvent.point).line
            if (line != lastHighlightedLine && !editor.selectionModel.hasSelection() && line < editor.document.lineCount) {
                disableCurrentRenderer()
                if (isCSDiff) enableRenderer(line)
            }
        } else if (!editor.selectionModel.hasSelection()) {
            disableCurrentRenderer()
        }
    }

    override fun selectionChanged(e: SelectionEvent) {
        if (isDragging) return

        disableCurrentRenderer()
        if (!e.newRange.isEmpty) {
            val offset = min(e.newRange.startOffset, e.newRange.endOffset)
            val line = editor.document.getLineNumber(offset)
            enableRenderer(line)
        }
    }

    private fun disableCurrentRenderer() {
        lastHighlightedLine?.let {
            lineHighlighters[it]?.updateRenderer(null)
            lastHighlightedLine = null
        }
    }

    private val highlighterProcessor = HighlighterProcessor()

    private fun enableRenderer(line: Int) {
        val startOffset = editor.document.getLineStartOffset(line)
        val endOffset = editor.document.getLineEndOffset(line)
        highlighterProcessor.startOffset = startOffset
        highlighterProcessor.endOffset = endOffset
        val canAddHighlighter = (editor.markupModel as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(
            startOffset, endOffset, highlighterProcessor
        ) ?: false
        if (!canAddHighlighter) return

        lineHighlighters.getOrPut(line) {
            editor.markupModel.addLineHighlighter(line, HighlighterLayer.LAST, null)
        }.updateRenderer(renderer.also { it.line = line })
        lastHighlightedLine = line
    }

    private fun RangeHighlighter.updateRenderer(renderer: GutterIconRenderer?) {
        this.gutterIconRenderer = renderer
    }
}

val HIGHLIGHTER = KeyWithDefaultValue.create("HIGHLIGHTER", false)
val GUTTER_ICON = if (ColorUtil.isDark(JBColor.background())) {
    IconLoader.getIcon("/images/gutter_icon_dark.svg", HighlighterProcessor::class.java)
} else {
    IconLoader.getIcon("/images/gutter_icon.svg", HighlighterProcessor::class.java)
}

class HighlighterProcessor : Processor<RangeHighlighter> {
    var startOffset: Int = 0
    var endOffset: Int = 0

    override fun process(highlighter: RangeHighlighter?): Boolean {
        return highlighter?.let {
            val minOffset = min(it.startOffset, it.endOffset)
            it.getUserData(HIGHLIGHTER) != true || minOffset < startOffset || minOffset > endOffset
        } ?: true
    }
}

class NewCodeMarkGutterIconRenderer(
    private val editor: Editor,
    var line: Int,
    private val onClick: () -> Unit,
) : GutterIconRenderer() {

    override fun getIcon(): Icon {
        return GUTTER_ICON
    }

    override fun equals(other: Any?): Boolean {
        val otherRenderer = other as? NewCodeMarkGutterIconRenderer ?: return false
        return line == otherRenderer.line
    }

    override fun hashCode(): Int {
        return line.hashCode()
    }

    override fun getClickAction(): AnAction {
        return NewCodeMarkGutterIconRendererClickAction(editor, line, onClick)
    }

    override fun getAlignment() = Alignment.LEFT
}

class NewCodeMarkGutterIconRendererClickAction(
    private val editor: Editor,
    private val line: Int,
    val onClick: () -> Unit
) :
    DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            val project = editor.project
            if (!editor.selectionModel.hasSelection()) {
                val startOffset = editor.document.getLineStartOffset(line)
                val endOffset = editor.document.getLineEndOffset(line)
                editor.selectionModel.setSelection(startOffset, endOffset)
            }
            project?.codeReview?.show {
                project.editorService?.addComment(
                    Comment(
                        editor.document.uri,
                        editor.selectionOrCurrentLine,
                        editor.document.file?.fileType,
                        editor.selectionModel.selectedText ?: ""
                    )
                )
                onClick()
            }
            project?.codeReview?.repaint()
        }
    }
}

