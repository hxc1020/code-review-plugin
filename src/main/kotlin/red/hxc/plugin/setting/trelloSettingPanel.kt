package red.hxc.plugin.setting

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.trello.TrelloRepository
import com.intellij.tasks.trello.TrelloRepositoryType
import com.intellij.tasks.trello.model.TrelloBoard
import com.intellij.ui.ClickListener
import com.intellij.ui.LayeredIcon
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import red.hxc.plugin.Board
import red.hxc.plugin.dataPersistent
import red.hxc.plugin.repository.Trello
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel

val trelloName = TrelloRepositoryType().name

var trelloRepository: TrelloRepository? = getRepositoryNameMap()[trelloName]?.let { it as TrelloRepository }

val trello = Trello()

fun TrelloBoard.toMyBoard(): Board {
    return Board(id, name)
}

val trelloBoardsPanel = panel {
    fun userBoards(): List<Board>? {
        return trelloRepository?.fetchUserBoards()?.map { it.toMyBoard() }
    }

    val userBoards = userBoards()
    val boardsComboBox = ComboBox(userBoards?.toTypedArray(), 200)

    row("Trello Boards: ") {
        boardsComboBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
        boardsComboBox.addItemListener { event ->
            dataPersistent.setTrelloBoard(event.item as Board)
            dataPersistent.setMembers(trello.queryBoardMembers())
            trello.refreshList()
        }
        dataPersistent.getTrelloBoard()?.let {
            boardsComboBox.item = it
        }
        val refreshButton = JLabel(LayeredIcon(AllIcons.Actions.Refresh))
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                boardsComboBox.removeAllItems()
                boardsComboBox.model = DefaultComboBoxModel(userBoards()?.toTypedArray())
                boardsComboBox.repaint()
                return true
            }
        }.installOn(refreshButton)
        component(refreshButton)
    }
}
