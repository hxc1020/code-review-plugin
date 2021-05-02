package red.hxc.plugin.setting

import com.google.gson.Gson
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.config.RecentTaskRepositories
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selectedValueIs
import red.hxc.plugin.dataPersistent
import red.hxc.plugin.repository.cards
import red.hxc.plugin.repository.historyRecords
import javax.swing.JComponent

const val CODE_REVIEW_SETTING_ID = "code.review.setting.id"
val repositoryNameMap = RecentTaskRepositories.getInstance().repositories.associateBy { it.repositoryType.name }

class CodeReviewSettingConfigurable : SearchableConfigurable {
    val repositoryComBox = ComboBox(listOf("").plus(repositoryNameMap.keys).toTypedArray(), 200)
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
            button("test") {
                val gson = Gson()
//                val json = trello.createCard()
//                println(json)
                println(gson.toJson(dataPersistent.state))
                println(gson.toJson(cards))
                println(gson.toJson(historyRecords))
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
