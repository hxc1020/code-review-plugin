import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.visualPaddingsPanel

const val CODE_REVIEW_ID = "CodeReview"

class CodeReviewComponent(private val project: Project) : Disposable {
    private val toolWindow
        get() = if (project.isDisposed) null else ToolWindowManager.getInstance(project).getToolWindow(CODE_REVIEW_ID)

    override fun dispose() = Unit

    init {
        initEditorFactoryListener()
    }

    private fun initEditorFactoryListener() {
        println("initEditorFactoryListener $project.isDisposed")
        if (project.isDisposed) return
        EditorFactory.getInstance().addEditorFactoryListener(
            EditorFactoryListenerImpl(project), this
        )
    }

    fun show(afterShow: (() -> Unit)? = null) = toolWindow?.show {
        afterShow?.invoke()
    }

    fun hide() = toolWindow?.hide()

    fun repaint() {
        toolWindow?.component?.repaint()
    }
}

val panel = panel(LCFlags.flowY, title = "Code Review") {

}

val codeViewPanel = panel(LCFlags.flowY, title = "Code Review Panel") {}

class CodeReviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        panel.add(codeViewPanel)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                panel,
                "",
                false
            )
        )
    }

}

class EditorFactoryListenerImpl(private val project: Project) : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        try {
            if (event.editor.project == project) {
                project.editorService?.add(event.editor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        try {
            if (event.editor.project == project) {
                project.editorService?.remove(event.editor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SelectionListenerImpl(private val project: Project?) : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
        try {
            val reviewFile = e.editor.document.file as? ReviewDiffVirtualFile
            if (reviewFile?.side == ReviewDiffSide.LEFT) return

        } catch (e: Exception) {
        }
    }
}

class EditorService {
    private val comments = mutableListOf<Comment>()
    private val editorFactory: EditorFactory = EditorFactory.getInstance()

    fun add(editor: Editor) {
        val file = editor.document.file as? ReviewDiffVirtualFile
        println("add editor $file")
        file?.let {
            println("!it.canCreateMarker || it.side == ReviewDiffSide.LEFT ${!it.canCreateMarker || it.side == ReviewDiffSide.LEFT}")
            if (!it.canCreateMarker || it.side == ReviewDiffSide.LEFT) return
        }
        println("$file add editor")

//        editor.selectionModel.addSelectionListener(SelectionListenerImpl(editor.project))
//        editor.scrollingModel.addVisibleAreaListener(VisibleAreaListenerImpl(project))
        GutterIconManager(editor)
    }

    fun remove(editor: Editor) {
        TODO("Not yet implemented")
    }

    fun addComment(new: Comment) {
        print("addComment")
        comments.add(new)
        codeViewPanel.add(
            editorFactory.createEditor(
                editorFactory.createDocument(new.code),
                null,
                new.fileType ?: FileTypeManager.getInstance().getFileTypeByExtension("java"),
                true
            ).component
        )
        codeViewPanel.revalidate()
        codeViewPanel.repaint()
        println(comments.toString())
    }
}
