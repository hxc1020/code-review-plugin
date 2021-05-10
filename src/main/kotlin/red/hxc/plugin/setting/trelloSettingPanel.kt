package red.hxc.plugin.setting

import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.trello.TrelloRepository
import com.intellij.tasks.trello.TrelloRepositoryType
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import red.hxc.plugin.dataPersistent
import red.hxc.plugin.repository.Trello

val trelloName = TrelloRepositoryType().name

var trelloRepository: TrelloRepository? = getRepositoryNameMap()[trelloName]?.let { it as TrelloRepository }

val trello = Trello()

val trelloBoardsPanel = panel {
    row("Trello Boards: ") {
        val userBoards = trelloRepository?.fetchUserBoards()
        val userBoardsNamesMap = userBoards?.associateBy { it.name }
        val userBoardsIdsMap = userBoards?.associateBy { it.id }
        val comboBox = ComboBox(userBoardsNamesMap?.keys?.toTypedArray() ?: arrayOf<String>(), 200)
        comboBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
        comboBox.addItemListener { event ->
            dataPersistent.setTrelloBoardId(userBoardsNamesMap?.get(event.item.toString())?.id)
            dataPersistent.setMembers(trello.queryBoardMembers())
            trello.refreshList()
        }
        dataPersistent.getTrelloBoardId()?.let {
            comboBox.item = userBoardsIdsMap?.get(it)?.name
        }
    }
}


