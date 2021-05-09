package red.hxc.plugin.repository

import com.intellij.tasks.trello.TrelloRepository
import com.intellij.tasks.trello.TrelloRepositoryType
import io.joshworks.restclient.http.HttpResponse
import io.joshworks.restclient.http.Json
import io.joshworks.restclient.http.Unirest
import io.joshworks.restclient.request.HttpRequestWithBody
import red.hxc.plugin.*
import red.hxc.plugin.repository.TrelloApi.*
import red.hxc.plugin.setting.trello
import java.time.LocalDate

data class SimpleBoard(val id: String, val name: String, val memberships: List<Membership>)
data class Membership(
    val unconfirmed: Boolean,
    val idMember: String,
    val id: String,
    val memberType: String,
    val deactivated: Boolean
)

data class Card(
    val id: String,
    val idMembers: List<String>,
    val idBoard: String,
    val idList: String,
    val due: String,
    val name: String,
    val checklists: List<CheckList>,
    val shortUrl: String,
    val actions: List<Action>
)

data class CheckList(
    val name: String,
    val id: String,
    val checkItems: List<CheckItem>
)

data class CheckItem(
    val id: String,
    val name: String,
    var state: String,
    val idChecklist: String
) {
    override fun toString(): String {
        return name
    }
}

data class Action(val data: ActionData)

data class ActionData(val text: String)

enum class ItemState(val value: String) {
    INCOMPLETE("incomplete"),
    COMPLETE("complete")
}

data class BoardMembers(val id: String, val members: List<Member>)

var todayCard: Card? = null
var cards: List<Card> = emptyList()
var historyRecords: Map<String, List<CheckItem>> = emptyMap()
var meRecords: List<CheckItem> = emptyList()
var todayRecords: Map<String, List<CheckItem>> = emptyMap()
var fileRelationMap: Map<String, Code> = emptyMap()
var memberMap: Map<String, Member> = dataPersistent.getMembers().filter { it.id != null }.associateBy { it.id!! }

const val FILE_RELATION_SPLITTER = "|"

class Trello(
    private val baseRepository: TrelloRepository
) {
    fun queryBoardsForMe(): List<SimpleBoard>? {
        return query(
            BOARDS_FOR_ME,
            mapOf("id" to (baseRepository.currentUser ?: return emptyList()).id)
        )?.asListOf(SimpleBoard::class.java)
    }

    fun updateItemState(checkItemId: String) {
        put(
            COMPLETE_CHECK_ITEM,
            routeParams = mapOf("id" to (todayCard?.id ?: return), "idCheckItem" to checkItemId),
            params = mapOf("state" to listOf(ItemState.COMPLETE.value))
        )
    }

    fun refreshList() {
        val today = LocalDate.now()
        val lists = queryList()
        if (lists?.isEmpty() == true)
            showNotification(globalProject, CodeReviewBundle.message("c.r.notification.create.month.list"))
        if (dataPersistent.getCardList() == null
            || lists?.contains(dataPersistent.getCardList()) == false
            || dataPersistent.getCardList()!!.name != "${today.year}-${today.monthValue}"
        ) {
            val cardList = (lists?.firstOrNull { it.name == "${today.year}-${today.monthValue}" }
                ?: createCardList(today) ?: return)
            dataPersistent.setCardList(cardList)
        }
    }

    fun queryCurrentBoard(): Json? {
        val boardId = dataPersistent.getTrelloBoardId() ?: return null
        return query(
            BOARD,
            routeParams = mapOf("id" to boardId)
        )
    }

    fun refreshAll() {
        cards = trello.queryCards() ?: return
        refreshHistory()
        refreshMe()
        refreshToday()
        refreshFileRelation()
    }

    private fun refreshFileRelation() {
        fileRelationMap = cards.asSequence()
            .flatMap { it.actions }
            .map { it.data.text }
            .map {
                val split = it.split(FILE_RELATION_SPLITTER)
                split[0] to Code(split[1], split[2], split[3].toInt(), split[4].toInt())
            }.toMap()
    }

    private fun refreshToday() {
        val today = LocalDate.now()
        val todayStr = "${today.year}-${today.monthValue}-${today.dayOfMonth}"

        todayCard = cards.firstOrNull { it.name == todayStr }

        if (cards.isEmpty())
            showNotification(globalProject, CodeReviewBundle.message("c.r.notification.create.today.card"))
        if (todayCard == null
//            || cards.firstOrNull { it.id == todayCard?.id && it.name == todayStr } == null
            || todayCard!!.name != todayStr
        ) {
            todayCard = createCard()
        }
        todayRecords = todayCard?.let { card ->
            card.checklists
                .associateBy { it.name }
                .mapValues { entry -> entry.value.checkItems.filter { it.state == ItemState.INCOMPLETE.value } }
        } ?: emptyMap()
    }

    private fun refreshMe() {
        meRecords = baseRepository.currentUser?.let { historyRecords[memberMap[it.id]?.fullName] } ?: emptyList()
    }

    private fun refreshHistory() {
        historyRecords = cards.asSequence()
            .flatMap { it.checklists }
            .groupBy { it.name }
            .mapValues {
                it.value.flatMap { checkList -> checkList.checkItems }
                    .filter { item -> item.state == ItemState.INCOMPLETE.value }
            }
    }

    private fun queryList(): List<CardList>? {
        val boardId = dataPersistent.getTrelloBoardId() ?: return null
        return query(
            BOARD_LIST,
            routeParams = mapOf("id" to boardId)
        )?.asListOf(CardList::class.java)
    }

    fun queryCard(boardId: String): Json? = query(
        CARD,
        routeParams = mapOf("id" to boardId)
    )

    fun createReview(review: Review) {
        val checkList =
            todayCard?.checklists?.firstOrNull { it.name == review.userId } ?: createCheckList(review.userId)
        if (checkList == null) {
            showNotification(globalProject, CodeReviewBundle.message("c.r.notification.create.check.list.error"))
            return
        }
        val item = createCheckItem(checkList.id, review.comment)
        if (item == null) {
            showNotification(globalProject, CodeReviewBundle.message("c.r.notification.create.check.item.error"))
            return
        }

        createFileRelationShip(review.code, item.id)
        refreshAll()
    }

    private fun createFileRelationShip(code: Code, itemId: String) {
        val (project, file, start, end) = code
        val comment = listOf(itemId, project, file, start, end).joinToString(FILE_RELATION_SPLITTER)
        post(CREATE_FILE_RELATION, mapOf("id" to todayCard!!.id), mapOf("text" to listOf(comment)))
    }

    private fun createCheckItem(id: String, comment: String): CheckItem? {
        return post(CREATE_CHECK_ITEM, mapOf("id" to id), mapOf("name" to listOf(comment)))
            ?.`as`(CheckItem::class.java)
    }

    private fun createCheckList(memberName: String?): CheckList? {
        return todayCard?.id?.let {
            post(CREATE_CHECK_LIST, params = mapOf("idCard" to listOf(it), "name" to listOf(memberName ?: "unknown")))
                ?.`as`(CheckList::class.java)
        }
    }

    private fun queryCards(): List<Card>? {
        return query(
            CARDS,
            routeParams = mapOf("id" to (dataPersistent.getCardList()?.id ?: return null))
        )?.asListOf(Card::class.java)
    }

    fun queryBoardMembers(): List<Member>? {
        return query(
            BOARD_MEMBERS,
            mapOf("id" to (dataPersistent.getTrelloBoardId() ?: return null))
        )?.`as`(BoardMembers::class.java)?.members
    }

    private fun createCardList(today: LocalDate): CardList? {
        return post(
            CREATE_LIST,
            params = mapOf(
                "name" to listOf("${today.year}-${today.monthValue}"),
                "idBoard" to listOf((dataPersistent.getTrelloBoardId() ?: return null))
            )
        )?.`as`(CardList::class.java)

    }

    private fun createCard(): Card? {
        val today = LocalDate.now()
        val todayStr = "${today.year}-${today.monthValue}-${today.dayOfMonth}"
        return post(
            CREATE_CARD, params = mapOf(
                "name" to listOf(todayStr),
                "desc" to listOf("Code Review Records for $todayStr"),
                "idList" to listOf(dataPersistent.getCardList()?.id ?: return null)
            )
        )?.`as`(Card::class.java)

    }

    private fun query(
        api: TrelloApi,
        routeParams: Map<String, String> = emptyMap()
    ): Json? {
        val response: HttpResponse<Json>? =
            Unirest.get(api.url).apply {
                header("Accept", "application/json")
                routeParams.forEach { routeParam(it.key, it.value) }
                queryString("key", TrelloRepositoryType.DEVELOPER_KEY)
                queryString("token", baseRepository.password)
                api.params.forEach { queryString(it.key, it.value) }
            }
                .asJson()

        return response?.body()
    }

    private fun post(
        api: TrelloApi,
        routeParams: Map<String, String> = emptyMap(),
        params: Map<String, List<String>> = emptyMap()
    ): Json? = Unirest.post(api.url).apply { commonApply(routeParams, params) }.asJson()?.body()

    private fun put(
        api: TrelloApi,
        routeParams: Map<String, String> = emptyMap(),
        params: Map<String, List<String>> = emptyMap()
    ): Json? = Unirest.put(api.url).apply { commonApply(routeParams, params) }.asJson()?.body()

    private fun HttpRequestWithBody.commonApply(
        routeParams: Map<String, String>,
        params: Map<String, List<String>>
    ) {
        header("Accept", "application/json")
        routeParams.forEach { routeParam(it.key, it.value) }
        queryString("key", TrelloRepositoryType.DEVELOPER_KEY)
        queryString("token", baseRepository.password)
        params.forEach { queryString(it.key, it.value) }
    }
}


