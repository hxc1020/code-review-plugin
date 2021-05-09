package red.hxc.plugin.repository

enum class TrelloApi(val url: String, val params: Map<String, List<String>> = emptyMap()) {
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
    CREATE_CARD("https://api.trello.com/1/cards"),
    CREATE_CHECK_LIST("https://api.trello.com/1/checklists"),
    CREATE_CHECK_ITEM("https://api.trello.com/1/checklists/{id}/checkItems"),
    CREATE_FILE_RELATION("https://api.trello.com/1/cards/{id}/actions/comments"),
    COMPLETE_CHECK_ITEM("https://api.trello.com/1/cards/{id}/checkItem/{idCheckItem}")
}
