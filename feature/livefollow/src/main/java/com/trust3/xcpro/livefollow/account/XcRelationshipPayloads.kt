package com.trust3.xcpro.livefollow.account

import com.google.gson.JsonObject

internal fun parseCurrentApiXcSearchResponse(body: String): List<XcSearchPilot> {
    val root = parseXcAccountRootObject(body)
    return root.requiredArray("users").map { entry ->
        val user = entry.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Invalid user entry")
        parseCurrentApiXcSearchPilot(user)
    }
}

internal fun parseCurrentApiXcFollowRequestResponse(body: String): XcFollowRequestItem {
    return parseCurrentApiXcFollowRequestResponse(parseXcAccountRootObject(body))
}

internal fun parseCurrentApiXcFollowRequestsResponse(body: String): List<XcFollowRequestItem> {
    val root = parseXcAccountRootObject(body)
    return root.requiredArray("requests").map { entry ->
        val request = entry.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Invalid request entry")
        parseCurrentApiXcFollowRequestResponse(request)
    }
}

private fun parseCurrentApiXcSearchPilot(root: JsonObject): XcSearchPilot {
    return XcSearchPilot(
        userId = root.requiredString("user_id"),
        handle = root.requiredString("handle"),
        displayName = root.optionalString("display_name"),
        compNumber = root.optionalString("comp_number"),
        relationshipState = XcRelationshipState.fromWireValue(
            root.requiredString("relationship_state")
        )
    )
}

private fun parseCurrentApiXcFollowRequestResponse(root: JsonObject): XcFollowRequestItem {
    return XcFollowRequestItem(
        requestId = root.requiredString("request_id"),
        status = XcFollowRequestStatus.fromWireValue(root.requiredString("status")),
        direction = XcFollowRequestDirection.fromWireValue(root.requiredString("direction")),
        counterpart = root.optionalObject("counterpart")?.let(::parseCurrentApiXcProfileResponse)
            ?: throw IllegalArgumentException("Missing counterpart"),
        relationshipState = XcRelationshipState.fromWireValue(
            root.requiredString("relationship_state")
        )
    )
}
