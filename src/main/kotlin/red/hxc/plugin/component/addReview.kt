package red.hxc.plugin.component

import com.intellij.openapi.editor.*
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.NotNull
import red.hxc.plugin.*
import javax.swing.JComponent


class AddReviewDialog(
    private val editor: Editor,
) : DialogWrapper(editor.project) {
    private val authors = dataPersistent.getMembers().associateBy { it.fullName }
    val authorComBox = ComboBox(authors.keys.toTypedArray(), 200)
    var memberId: String? = null
    private var comment: String? = null

    init {
        title = "Add a Review Comment"
        setOKButtonText("Submit")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val psiFile = editor.project?.let { PsiDocumentManager.getInstance(it).getPsiFile(editor.document) }
        return panel {
            row("Member:") {
                cell {
                    authorComBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
                }
            }
            row("Comment: ") {
                val commentField = textField({ "" }, { comment = it })
                commentField.focused().withValidationOnApply {
                    if (it.text.isBlank())
                        ValidationInfo("Please input comment!", commentField.component)
                    else null
                }
            }
            row {
                button("test") {
                    showStartupNotification(editor.project ?: return@button)
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

    override fun doValidate(): ValidationInfo? {
        memberId = authorComBox.item
        return if (memberId?.isBlank() == true) ValidationInfo("Please select one member!", authorComBox) else null
    }

    override fun doOKAction() {
        super.doOKAction()
        editor.project?.editorService?.addComment(
            Review(
                memberId!!,
                comment!!,
                Code(
                    editor.document.file!!.uri!!,
                    getOffset(editor.selectionModel.selectionStartPosition),
                    getOffset(editor.selectionModel.selectionEndPosition)
                )
            )
        )
        refreshReviewContent()
        showNotification(editor.project, CodeReviewBundle.message("code.review.add.review.success"))
    }

    private fun getOffset(position: VisualPosition?) = position?.let { editor.visualPositionToOffset(it) } ?: 0

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

}

//        val authors = if (file != null) {
//            val annotate = editor.project?.gitAnnotationProvider?.annotate(file)
//            annotate?.authorsMappingProvider?.authors
//        } else mapOf()
