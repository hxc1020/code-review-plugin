package red.hxc.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.annotate.GitAnnotationProvider
import org.intellij.plugins.markdown.structureView.MarkdownStructureViewFactory

val Project.editorService: EditorService?
    get() = getServiceIfNotDisposed(EditorService::class.java)

val Project.codeReview: CodeReviewComponent?
    get() =
        if (!isDisposed) getComponent(CodeReviewComponent::class.java)
        else null

val Project.gitAnnotationProvider: GitAnnotationProvider?
    get() =
        if (!isDisposed) getServiceIfNotDisposed(GitAnnotationProvider::class.java)
        else null

val Project.markdown: MarkdownStructureViewFactory?
    get() =
        if (!isDisposed) getServiceIfNotDisposed(MarkdownStructureViewFactory::class.java)
        else null


fun <T : Any> Project.getServiceIfNotDisposed(serviceClass: Class<T>): T? =
    if (!isDisposed) ServiceManager.getService(this, serviceClass)
    else null

val Document.file: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

val Document.uri: String?
    get() {
        val file = FileDocumentManager.getInstance().getFile(this)
        return file?.uri
    }
const val SPACE_ENCODED: String = "%20"
const val COLON_ENCODED: String = "%3A"
const val HASH_ENCODED: String = "%23"
const val URI_FILE_BEGIN = "file:"
const val WINDOWS_NETWORK_FILE_BEGIN = "file:////"
const val URI_PATH_SEP: Char = '/'
const val URI_VALID_FILE_BEGIN: String = "file:///"

val VirtualFile.uri: String?
    get() {
        return canonicalPath?.replace(PROJECT_PATH ?: "", "")
    }
