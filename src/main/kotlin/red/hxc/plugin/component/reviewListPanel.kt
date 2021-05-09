package red.hxc.plugin.component

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.layout.selected
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.UsagePreviewPanel
import red.hxc.plugin.PROJECT_PATH
import red.hxc.plugin.repository.CheckItem
import red.hxc.plugin.repository.ItemState
import red.hxc.plugin.repository.cards
import red.hxc.plugin.repository.fileRelationMap
import red.hxc.plugin.setting.trello
import java.io.File
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel


class ReviewListPanel(
    private val project: Project
) : SimpleToolWindowPanel(false, true) {
    private val root = CheckedTreeNode()
    private val usagePreviewPanel =
        UsagePreviewPanel(project, FindInProjectUtil.setupViewPresentation(false, FindModel()), true)
    private val tree = CheckboxTree(MyCheckboxTreeCellRender(), root)

    init {
        val splitter = OnePixelSplitter(false).apply {
            firstComponent = tree.apply {
                isRootVisible = false
                addTreeSelectionListener {
                    val node = (it?.newLeadSelectionPath?.lastPathComponent
                        ?: return@addTreeSelectionListener) as CheckedTreeNode
                    val userObject = node.userObject
                    if (userObject !is CheckItem) return@addTreeSelectionListener
                    val code = fileRelationMap[userObject.id]
                    if (code != null) {
                        val psiFile = PsiManager.getInstance(project).findFile(
                            (LocalFileSystem.getInstance()
                                .findFileByIoFile(File("$PROJECT_PATH${code.file}"))
                                ?: return@addTreeSelectionListener)
                        ) ?: return@addTreeSelectionListener
                        usagePreviewPanel.updateLayoutLater(listOf(UsageInfo(psiFile, code.start, code.end)))
                        usagePreviewPanel.apply {
                            (getData(CommonDataKeys.EDITOR.name) as Editor).apply {
                                settings.setGutterIconsShown(false)
                            }
                        }
                    }
                }
                updateUI()
                (model as DefaultTreeModel).reload()
            }
            secondComponent = usagePreviewPanel
        }
        add(splitter)
    }

    fun reload(records: Map<String, List<CheckItem>>) {
        if (cards.isEmpty()) trello.refreshAll()
        root.removeAllChildren()
        records.forEach { entry ->
            val node = CheckedTreeNode(entry.key)
            entry.value.forEach {
                node.add(CheckedTreeNode(it))
            }
            root.add(node)
        }
        tree.updateUI()
        (tree.model as DefaultTreeModel).reload()
    }

    class MyCheckboxTreeCellRender : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            if (value !is CheckedTreeNode) return
            when (val item = value.userObject) {
                is CheckItem -> {
                    textRenderer.append(item.name)
                    myCheckbox.isSelected = item.state == ItemState.COMPLETE.value
                    myCheckbox.selected.addListener {
//                        if (it) trello.refreshCards() //todo update state done
                    }
                }
                is String -> {
                    textRenderer.append(item)
                    myCheckbox.isVisible = false
                }
            }
        }
    }
}
