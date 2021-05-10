package red.hxc.plugin.setting

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.TaskRepository
import com.intellij.tasks.config.RecentTaskRepositories
import com.intellij.tasks.trello.TrelloRepository
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selectedValueIs
import red.hxc.plugin.dataPersistent
import javax.swing.JComponent

const val CODE_REVIEW_SETTING_ID = "code.review.setting.id"
fun getRepositoryNameMap(): Map<String, TaskRepository> {
    return RecentTaskRepositories.getInstance().repositories.associateBy { it.repositoryType.name }
}

class CodeReviewSettingConfigurable : SearchableConfigurable {
    var repositoryComBox = getRepositoryComboBox()

    private fun getRepositoryComboBox() = ComboBox(listOf("").plus(getRepositoryNameMap().keys).toTypedArray(), 200)
    private val repositoryPanel = panel {
        row("Repository:") {
            cell {
                repositoryComBox(growPolicy = GrowPolicy.MEDIUM_TEXT)
                repositoryComBox.addItemListener {
                    dataPersistent.setRepository(it.item.toString())
                }
                dataPersistent.getRepository()?.let {
                    repositoryComBox.selectedItem = it
                }
            }
        }
    }
    private val settingPanel = panel(LCFlags.flowY) {
        row {
            button("Refresh") {
                repositoryComBox = getRepositoryComboBox()
                repositoryPanel.validate()
                repositoryPanel.repaint()
            }
        }
    }

    override fun createComponent(): JComponent {
        settingPanel.add(repositoryPanel)
        if (repositoryComBox.selectedItem?.toString() == trelloName) {
            settingPanel.add(trelloBoardsPanel)
        }

        repositoryComBox.selectedValueIs(trelloName).addListener { selected ->
            if (selected) {
                trelloRepository = getRepositoryNameMap()[trelloName] as TrelloRepository
                trelloBoardsPanel.validate()
                trelloBoardsPanel.repaint()
                settingPanel.add(trelloBoardsPanel)
                settingPanel.repaint()
            } else {
                settingPanel.remove(trelloBoardsPanel)
                settingPanel.repaint()
            }
        }

        return settingPanel
    }

    override fun isModified(): Boolean = settingPanel.isModified()

    override fun apply() = settingPanel.apply()

    override fun getDisplayName(): String = "Code Review"

    override fun getId(): String = CODE_REVIEW_SETTING_ID

}
