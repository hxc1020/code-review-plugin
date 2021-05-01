import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile

data class Position(val line: Int, val character: Int)

open class Range(var start: Position, var end: Position)

data class Comment(val uri: String?, val range: Range, val fileType: FileType?, val code: String)

class EditorMargins(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int
)

class EditorSelection(
    start: Position,
    end: Position,
    val cursor: Position
) : Range(start, end)

class ReviewDiffVirtualFile private constructor(
    fullPath: String,
    side: ReviewDiffSide,
    path: String,
    fileType: FileType,
    content: String,
    canCreateMarker: Boolean
) : LightVirtualFile(path, fileType, content) {
    private val myFullPath: String = fullPath
    private val mySide: ReviewDiffSide = side
    private val myCanCreateMarker: Boolean = canCreateMarker

    companion object {
        fun create(
            fullPath: String,
            side: ReviewDiffSide,
            path: String,
            content: String,
            fileType: FileType,
            canCreateMarker: Boolean
        ): ReviewDiffVirtualFile {
            return ReviewDiffVirtualFile(fullPath, side, path, fileType, content, canCreateMarker)
        }
    }

    val side: ReviewDiffSide get() = mySide

    val canCreateMarker: Boolean get() = myCanCreateMarker

    override fun getFileSystem(): VirtualFileSystem {
        return ReviewDiffFileSystem
    }

    override fun getPath(): String {
        return myFullPath
    }

    override fun toString(): String {
        return "ReviewDiffVirtualFile: $name"
    }
}

enum class ReviewDiffSide {
    LEFT,
    RIGHT
}

object ReviewDiffFileSystem : DeprecatedVirtualFileSystem() {
    override fun getProtocol(): String = "codestream-diff"

    override fun findFileByPath(path: String): VirtualFile? = null

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = null

    override fun refresh(asynchronous: Boolean) {}
}
