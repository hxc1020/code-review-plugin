package red.hxc.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import red.hxc.plugin.component.ReviewListPanel
import red.hxc.plugin.setting.trello
import red.hxc.plugin.setting.trelloName

const val CODE_REVIEW_ID = "CodeReview"
var PROJECT_PATH: String? = null

class CodeReviewComponent(private val project: Project) : Disposable {
    private val toolWindow
        get() = if (project.isDisposed) null else ToolWindowManager.getInstance(project).getToolWindow(CODE_REVIEW_ID)

    override fun dispose() = Unit

    init {
        PROJECT_PATH = project.basePath
        initEditorFactoryListener()
        initRepository()
    }

    private fun initRepository() {
        if (dataPersistent.getRepository() == trelloName) {
            val trelloBoardId = dataPersistent.getTrelloBoardId()
            trelloBoardId ?: JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                "Please config first! </br> Preferences -> Tools -> Code Review",
                MessageType.ERROR,
                null
            )
        }
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

class CodeReviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                ReviewListPanel(project),
                "Today",
                false
            ).apply {
                isCloseable = false
            }
        )
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                ReviewListPanel(project),
                "Me",
                false
            ).apply {
                isCloseable = false
            }
        )
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(
                ReviewListPanel(project),
                "History",
                false
            ).apply {
                isCloseable = false
            }
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

class EditorService {

    fun add(editor: Editor) {
        val file = editor.document.file as? ReviewDiffVirtualFile
        file?.let {
            println("!it.canCreateMarker || it.side == red.hxc.plugin.ReviewDiffSide.LEFT ${!it.canCreateMarker || it.side == ReviewDiffSide.LEFT}")
            if (!it.canCreateMarker || it.side == ReviewDiffSide.LEFT) return
        }
        GutterIconManager(editor)
    }

    fun remove(editor: Editor) {
    }

    fun addComment(review: Review) {
        trello.createReview(review)
    }
}
