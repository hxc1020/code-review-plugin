package red.hxc.plugin.component

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.UsagePreviewPanel
import red.hxc.plugin.*
import red.hxc.plugin.repository.CheckItem
import red.hxc.plugin.repository.ItemState
import red.hxc.plugin.repository.fileRelationMap
import red.hxc.plugin.setting.trello
import java.awt.event.ItemEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javax.swing.JLabel


class ReviewListPanel(
    private val project: Project,
    private val tab: ContentTab
) : SimpleToolWindowPanel(false, true) {
    private val root = CheckedTreeNode()
    private val usagePreviewPanel =
        UsagePreviewPanel(project, FindInProjectUtil.setupViewPresentation(false, FindModel()), true)

    private var panel = panel {}
    private val splitter = OnePixelSplitter(false)

    init {
        splitter.apply {
            firstComponent = panel
            secondComponent = usagePreviewPanel
        }
        add(splitter)
    }

    fun reload(records: Map<String, List<CheckItem>>) {
        splitter.firstComponent = panel() {
            records.forEach { (name, items) ->
                val initRow: Row.() -> Unit = {
                    items.forEach { item ->
                        row {
                            panel(LCFlags.noGrid) {
                                row {
                                    val jbCheckBox = JBCheckBox("", item.state == ItemState.COMPLETE.value)
                                    jbCheckBox.addItemListener {
                                        if (it.stateChange == ItemEvent.SELECTED) {
                                            trello.updateItemState(item.id)
                                            showNotification(
                                                globalProject,
                                                CodeReviewBundle.message("c.r.notification.done.check.item")
                                            )
                                            refreshReviewContent()
                                        }
                                    }
                                    jbCheckBox()
                                    val code = fileRelationMap[item.id]
                                    val label: CellBuilder<JLabel> =
                                        label("${item.name}  [${code?.project ?: "unknown"}]")
                                    label.component.addMouseListener(onLabelClick(item))
                                }
                            }()
                        }
                    }
                }
                when (tab) {
                    ContentTab.Today -> row(name, init = initRow)
                    ContentTab.Mine -> row(init = initRow)
                    ContentTab.History -> hideableRow(name, init = initRow)
                }

            }
        }
        this.splitter.validate()
        this.splitter.repaint()
    }

    private fun onLabelClick(it: CheckItem) = object : MouseListener {
        override fun mouseClicked(e: MouseEvent?) {
            val code = fileRelationMap[it.id]
            if (code != null) {
                val findFileByIoFile = LocalFileSystem.getInstance()
                    .findFileByIoFile(File("$PROJECT_PATH${code.file}"))
                if (findFileByIoFile == null) {
                    showNotification(
                        globalProject,
                        CodeReviewBundle.message(
                            "c.r.notification.project.error",
                            globalProject?.name ?: "unknown",
                            code.project
                        )
                    )
                    return
                }
                val psiFile = PsiManager.getInstance(project).findFile(findFileByIoFile) ?: return
                usagePreviewPanel.updateLayoutLater(
                    listOf(
                        UsageInfo(psiFile, code.start, code.end)
                    )
                )
                usagePreviewPanel.apply {
                    (getData(CommonDataKeys.EDITOR.name) as Editor).apply {
                        settings.setGutterIconsShown(false)
                    }
                }

            }
        }

        override fun mousePressed(e: MouseEvent?) {
        }

        override fun mouseReleased(e: MouseEvent?) {
        }

        override fun mouseEntered(e: MouseEvent?) {
        }

        override fun mouseExited(e: MouseEvent?) {
        }
    }
}
