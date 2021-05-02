package red.hxc.plugin.component

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.largeFilesEditor.GuiUtils
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.ui.addScrollPaneIfNecessary
import com.intellij.psi.PsiManager
import com.intellij.tasks.trello.model.TrelloCard
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.UsagePreviewPanel
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.jetbrains.annotations.NotNull
import red.hxc.plugin.PROJECT_PATH
import red.hxc.plugin.file
import red.hxc.plugin.repository.*
import red.hxc.plugin.setting.trello
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.tree.DefaultTreeModel


class ReviewListPanel(
    private val project: Project
) : SimpleToolWindowPanel(false, true) {
    private val root = CheckedTreeNode()
    val usagePreviewPanel = UsagePreviewPanel(project, FindInProjectUtil.setupViewPresentation(false, FindModel()))

    init {
        if (cards.isEmpty()) trello.refreshCards()

        val splitter: Splitter = OnePixelSplitter(false).apply {
            firstComponent = CheckboxTree(MyCheckboxTreeCellRender(), root).apply {
                historyRecords.forEach { entry ->
                    val node = CheckedTreeNode(entry.key)
                    entry.value.forEach {
                        node.add(CheckedTreeNode(it))
                    }
                    root.add(node)
                }
                isRootVisible = false
                addTreeSelectionListener {
                    val userObject = (it.newLeadSelectionPath.lastPathComponent as CheckedTreeNode).userObject
                    if (userObject !is CheckItem) return@addTreeSelectionListener
                    val psiFile = PsiManager.getInstance(project).findFile(
                        (LocalFileSystem.getInstance()
                            .findFileByIoFile(File("$PROJECT_PATH/src/main/java/com/emtpy/Main.java"))
                            ?: return@addTreeSelectionListener)
                    ) ?: return@addTreeSelectionListener
                    usagePreviewPanel.updateLayoutLater(listOf(UsageInfo(psiFile, 10, 20)))
                }
                updateUI()
                (model as DefaultTreeModel).reload()
            }
            secondComponent = usagePreviewPanel
        }
        add(splitter)
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

    fun myInit() {
        if (cards.isEmpty()) trello.refreshCards()

        val disposable = Disposer.newDisposable()
        val panel = panel(LCFlags.fill) {}.apply {
            withMaximumWidth(400)
        }
        val refreshButton = JButton("refresh")
        refreshButton.addActionListener {
            trello.refreshCards()
            panel.repaint()
        }
        panel.add(refreshButton)
        val tabs = JBTabsFactory.createEditorTabs(project, disposable)
        tabs.presentation.setSupportsCompression(false)
        tabs.presentation.setAlphabeticalMode(true)
        val historyPanel = addScrollPaneIfNecessary(panel {
            historyRecords.forEach {
                hideableRow(it.key) {
                    it.value.forEach { item ->
                        row {
                            val todo = CheckBox(item.name, selected = item.state == ItemState.COMPLETE.value)
                            // todo add listener
                            todo()
                            ActionLink("detail") {
                            }()
                        }
                    }
                }
            }
        })
        val mePanel = addScrollPaneIfNecessary(panel {
            meRecords.forEach { item ->
                row {
                    val todo = CheckBox(item.name, selected = item.state == ItemState.COMPLETE.value)
                    // todo add listener
                    todo()
                    ActionLink("detail") {
                    }()
                }
            }
        })
        val todayPanel = addScrollPaneIfNecessary(panel {
            row {
                button("Start today") {
                    trello.initTodayCard()
                }
            }
        })
        tabs.addTab(TabInfo(mePanel).setText("Me"))
        tabs.addTab(TabInfo(todayPanel).setText("Today"))
        tabs.addTab(TabInfo(historyPanel).setText("History"))
        val detail = EditorFactory.getInstance()
            .createViewer(EditorFactory.getInstance().createDocument("public void main(){}")).component

        val detailPanel = panel(LCFlags.fill) {
        }.apply {
            add(detail)
            setSize(600, 400)
            border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
            repaint()
        }
        val tabsComponent = tabs.component.apply { }
        val tabPanel = panel(LCFlags.fill) {
            row {
                grow
                tabsComponent()
            }
        }
        val panelHeader = JPanel().apply {
            val panelHeaderFlowLayout = FlowLayout().apply {
                alignment = FlowLayout.LEFT
                hgap = 0
            }
            layout = panelHeaderFlowLayout
            val lblSearchStatusLeft = SimpleColoredComponent()
            lblSearchStatusLeft.border = JBUI.Borders.emptyLeft(5)
            val lblSearchStatusCenter = SimpleColoredComponent()
            add(lblSearchStatusLeft)
            add(lblSearchStatusCenter)
        }

        panel.add(panelHeader, BorderLayout.NORTH)
        GuiUtils.setStandardSizeForPanel(panelHeader, true)
        GuiUtils.setStandardLineBorderToPanel(panelHeader, 0, 0, 1, 0)

        panel.add(tabPanel, 0)
        panel.add(detailPanel, 1)
        setContent(panel)
    }

    private fun detailDialog(card: TrelloCard?): DialogWrapper {
        val panel = panel {
            row {
                if (card?.description != null) {
                    val markdownPreviewFileEditor =
                        EditorFactory.getInstance().createDocument(card.description).file?.let { file ->
                            MarkdownPreviewFileEditor(
                                project,
                                file
                            )
                        }
                    markdownPreviewFileEditor?.component<@NotNull JComponent>()
                }
            }
            noteRow("url: ${card?.url}") {}
            noteRow("due date: ${card?.dateLastActivity}") { }
            noteRow("labels: ${card?.labels?.joinToString(", ") { label -> label.name }}") { }
            noteRow("closed: ${card?.isClosed}") { }
            noteRow("members: ${card?.idMembers?.joinToString(", ")}") { }
        }.apply {
            withMaximumWidth(400)
            withMaximumHeight(500)
        }
        return dialog(
            card?.name ?: "detail",
            panel, resizable = true
        )
    }
}
