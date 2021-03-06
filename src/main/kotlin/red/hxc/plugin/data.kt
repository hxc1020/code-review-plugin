package red.hxc.plugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.xmlb.XmlSerializerUtil

data class Review(val userId: String, val comment: String, val code: Code)
data class Code(val project: String, val file: String, val start: Int, val end: Int)

data class MyPersistentData(
    var repository: String? = null,
    var trelloBoard: Board? = null,
    var cardList: CardList? = null,
    var members: List<Member> = listOf()
)

data class Member(var id: String? = null, var username: String? = null, var fullName: String? = null)

data class CardList(var id: String? = null, var name: String? = null)

data class Board(var id: String? = null, var name: String? = null) {
    override fun toString(): String {
        return name ?: ""
    }
}

@State(name = "codeReviewData", storages = [Storage("code-review.xml")])
class DataPersistentService :
    PersistentStateComponent<MyPersistentData> {
    private var data: MyPersistentData = MyPersistentData()

    fun getRepository() = data.repository
    fun setRepository(value: String) {
        data.repository = value
    }

    fun getTrelloBoard() = data.trelloBoard
    fun setTrelloBoard(value: Board?) {
        data.trelloBoard = value
    }

    fun getCardList() = data.cardList
    fun setCardList(value: CardList?) {
        data.cardList = value
    }

    fun getMembers() = data.members
    fun setMembers(value: List<Member>?) {
        value?.let { data.members = it }
    }

    override fun getState(): MyPersistentData {
        return data
    }

    override fun loadState(state: MyPersistentData) {
        XmlSerializerUtil.copyBean(state, data)
    }

    fun clear() {
        this.data = MyPersistentData()
    }
}

val dataPersistent: DataPersistentService = ServiceManager.getService(DataPersistentService::class.java)

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

    companion object

    val side: ReviewDiffSide get() = mySide

    val canCreateMarker: Boolean get() = myCanCreateMarker

    override fun getFileSystem(): VirtualFileSystem {
        return ReviewDiffFileSystem
    }

    override fun getPath(): String {
        return myFullPath
    }

    override fun toString(): String {
        return "red.hxc.plugin.ReviewDiffVirtualFile: $name"
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
