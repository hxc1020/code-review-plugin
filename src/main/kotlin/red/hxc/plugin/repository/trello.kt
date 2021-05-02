package red.hxc.plugin.repository

import com.intellij.tasks.trello.TrelloRepository
import com.intellij.tasks.trello.TrelloRepositoryType
import io.joshworks.restclient.http.HttpResponse
import io.joshworks.restclient.http.Json
import io.joshworks.restclient.http.Unirest
import red.hxc.plugin.CardList
import red.hxc.plugin.Member
import red.hxc.plugin.dataPersistent
import red.hxc.plugin.repository.Trello.TrelloApi.*
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
    val state: String,
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

class Trello(
    private val baseRepository: TrelloRepository
) {
    fun queryBoardsForMe(): List<SimpleBoard>? {
        return query(
            BOARDS_FOR_ME,
            mapOf("id" to (baseRepository.currentUser ?: return emptyList()).id)
        )?.asListOf(SimpleBoard::class.java)
    }


    fun queryCurrentBoard(): Json? {
        val boardId = dataPersistent.getTrelloBoardId() ?: return null
        return query(
            BOARD,
            routeParams = mapOf("id" to boardId)
        )
    }

    fun refreshCards() {
        cards = trello.queryCards() ?: return
        historyRecords = cards.asSequence()
            .flatMap { it.checklists }
            .groupBy { it.name }
            .mapValues {
                it.value.flatMap { checkList -> checkList.checkItems }
                    .filter { item -> item.state == ItemState.INCOMPLETE.value }
            }
        meRecords = baseRepository.currentUser?.let { historyRecords[it.username] } ?: emptyList()
    }

    fun initTodayCard() {
        val today = LocalDate.now()
        if (cards.isEmpty()) {
            refreshCards()
        }
        todayCard =
            (cards.firstOrNull { it.name == "${today.year}-${today.monthValue}-${today.dayOfMonth}" }
                ?: trello.createCard())
                ?: return
    }

    fun queryList(): List<CardList>? {
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

    fun createCardList(today: LocalDate): CardList? {
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

    private enum class TrelloApi(val url: String, val params: Map<String, List<String>> = emptyMap()) {
        BOARDS_FOR_ME(
            "https://api.trello.com/1/members/{id}/boards",
            mapOf("fields" to listOf("id", "name", "memberships"))
        ),
        BOARD("https://api.trello.com/1/boards/{id}"),
        BOARD_LIST("https://api.trello.com/1/boards/{id}/lists"),
        CREATE_LIST("https://api.trello.com/1/lists"),
        BOARD_MEMBERS(
            "https://api.trello.com/1/boards/{id}",
            mapOf(
                "fields" to listOf("id"),
                "members" to listOf("all"),
                "member_fields" to listOf("id", "username", "fullName")
            )
        ),
        CARD(
            "https://api.trello.com/1/cards/{id}",
            mapOf(
                "fields" to listOf(
                    "id", "name", "desc", "idCheckList", "idList",
                    "idBoard", "due", "closed", "shortUrl", "idMembers"
                )
            )
        ),

        CARDS(
            "https://api.trello.com/1/list/{id}/cards",
            mapOf(
                "fields" to listOf(
                    "id", "name", "desc", "idCheckList", "idList",
                    "idBoard", "due", "closed", "shortUrl", "idMembers"
                ),
                "actions" to listOf("commentCard"),
                "action_fields" to listOf("data"),
                "checklists" to listOf("all"),
                "checklist_fields" to listOf("id", "name")
            )
        ),
        CREATE_CARD("https://api.trello.com/1/cards")
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
    ): Json? {
        val response: HttpResponse<Json>? =
            Unirest.post(api.url).apply {
                header("Accept", "application/json")
                routeParams.forEach { routeParam(it.key, it.value) }
                queryString("key", TrelloRepositoryType.DEVELOPER_KEY)
                queryString("token", baseRepository.password)
                params.forEach { queryString(it.key, it.value) }
            }
                .asJson()

        return response?.body()
    }
    // boards "https://api.trello.com/1/members/me/boards"
    // token "0e990a39ddabac457d4769c337b350bc193f328b7cd4362a5f7aa0bc8ef0b180"
}


