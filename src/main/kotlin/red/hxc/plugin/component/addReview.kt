package red.hxc.plugin.component

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.NotNull
import red.hxc.plugin.*
import java.io.File
import javax.swing.JComponent


class AddReviewDialog(
    private val editor: Editor,
) : DialogWrapper(editor.project), DataProvider {
    private val authors = dataPersistent.getMembers().associateBy { it.fullName }
    var memberId: String? = null
    private var comment: String? = null

    init {
        title = "Add a Review Comment"
        setOKButtonText("Submit")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val psiFile = editor.project?.let { PsiDocumentManager.getInstance(it).getPsiFile(editor.document) }
        val file = editor.document.file
//        val authors = if (file != null) {
//            val annotate = editor.project?.gitAnnotationProvider?.annotate(file)
//            annotate?.authorsMappingProvider?.authors
//        } else mapOf()
        val authorComBox = ComboBox(authors.keys.toTypedArray(), 200)
        authorComBox.addItemListener {
            memberId = it.item.toString()
        }
        return panel {
            row("Who:") {
                cell {
                    authorComBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
                }
            }
            row("Comment: ") {
                textField({ "" }, { comment = it }).focused().withValidationOnApply {
                    if (it.text == null)
                        ValidationInfo("Please input comment!")
                    else null
                }
            }
            row {
                button("test") {
                    val logicalPosition = editor.caretModel.logicalPosition
                    val start = editor.selectionModel.selectionStartPosition
                    val end = editor.selectionModel.selectionEndPosition
                    val virtualFile = (LocalFileSystem.getInstance()
                        .findFileByIoFile(File(PROJECT_PATH + editor.document.file?.uri))
                        ?: return@button)
                    OpenFileDescriptor(
                        editor.project ?: return@button,
                        virtualFile,
                        end?.line ?: 0,
                        end?.column ?: 0
                    ).navigate(true)
                    doOKAction()
                    print(Gson().toJson(it))
                }
            }
            row {
                val preview = genPreviewEditor(psiFile)
                preview?.component?.apply {
                    setSize(400, 200)
                }?.let<@NotNull JComponent, Unit> { scrollPane(it) }
            }
        }.apply {
            withMaximumWidth(600)
            withMaximumHeight(800)
        }
    }

    override fun doOKAction() {
        doValidateAll()
        super.doOKAction()
        val sm = editor.selectionModel
        editor.project?.editorService?.addComment(
            Review(
                memberId!!,
                comment!!,
                Code(
                    editor.document.file!!.uri!!,
                    Position(sm.selectionStartPosition?.line ?: 0, sm.selectionStartPosition?.column ?: 0),
                    Position(sm.selectionEndPosition?.line ?: 0, sm.selectionEndPosition?.column ?: 0)
                )
            )
        )
    }

    private fun genPreviewEditor(psiFile: PsiFile?): Editor? {
        val preview = psiFile?.virtualFile?.let {
            EditorFactory.getInstance()
                .createEditor(
                    EditorFactory.getInstance().createDocument(editor.selectionModel.selectedText ?: ""),
                    editor.project,
                    it,
                    true,
                    EditorKind.PREVIEW
                )
        }
        preview?.settings?.apply<@NotNull EditorSettings> {
            isLineMarkerAreaShown = true
            isFoldingOutlineShown = false
            additionalLinesCount = 3
            additionalColumnsCount = 3
            isAnimatedScrolling = false
            isAutoCodeFoldingEnabled = false
            setGutterIconsShown(false)
        }
        return preview
    }

    override fun getData(dataId: String): Any? {
        if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
            val psiFile = editor.project?.let { PsiDocumentManager.getInstance(it).getPsiFile(editor.document) }
            val logicalPosition = editor.caretModel.logicalPosition
            return arrayOf<Navigatable>(
                OpenFileDescriptor(
                    editor.project ?: return null,
                    psiFile?.virtualFile ?: return null,
                    logicalPosition.line,
                    logicalPosition.column
                )
            )
        }
        return null
    }
}
