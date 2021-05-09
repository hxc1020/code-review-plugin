package red.hxc.plugin.setting

import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.trello.TrelloRepository
import com.intellij.tasks.trello.TrelloRepositoryType
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import red.hxc.plugin.dataPersistent
import red.hxc.plugin.repository.Trello
import java.util.concurrent.CompletableFuture

val trelloName = TrelloRepositoryType().name

val trelloRepository = repositoryNameMap[trelloName] as TrelloRepository

val trello = Trello(trelloRepository)

val trelloBoardsPanel = panel {
    row("Trello Boards: ") {
        val userBoards = trelloRepository.fetchUserBoards()
        val userBoardsNamesMap = userBoards.associateBy { it.name }
        val userBoardsIdsMap = userBoards.associateBy { it.id }
        val comboBox = ComboBox(userBoardsNamesMap.keys.toTypedArray(), 200)
        comboBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
        comboBox.addItemListener { event ->
            dataPersistent.setTrelloBoardId(userBoardsNamesMap[event.item.toString()]?.id)
            CompletableFuture.runAsync {
                dataPersistent.setMembers(trello.queryBoardMembers())
                trello.refreshList()
            }
        }
        dataPersistent.getTrelloBoardId()?.let {
            comboBox.item = userBoardsIdsMap[it]?.name
        }
    }
}


