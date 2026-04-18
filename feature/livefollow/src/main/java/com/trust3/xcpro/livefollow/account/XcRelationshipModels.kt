package com.trust3.xcpro.livefollow.account

enum class XcRelationshipState(
    val wireValue: String,
    val title: String
) {
    NONE(
        wireValue = "none",
        title = "Not following"
    ),
    OUTGOING_PENDING(
        wireValue = "outgoing_pending",
        title = "Request sent"
    ),
    INCOMING_PENDING(
        wireValue = "incoming_pending",
        title = "Requested you"
    ),
    FOLLOWING(
        wireValue = "following",
        title = "Following"
    ),
    FOLLOWED_BY(
        wireValue = "followed_by",
        title = "Follows you"
    ),
    MUTUAL(
        wireValue = "mutual",
        title = "Mutual"
    );

    companion object {
        fun fromWireValue(rawValue: String): XcRelationshipState {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported relationship state: $rawValue")
        }
    }
}

enum class XcFollowRequestStatus(
    val wireValue: String
) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined");

    companion object {
        fun fromWireValue(rawValue: String): XcFollowRequestStatus {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported follow request status: $rawValue")
        }
    }
}

enum class XcFollowRequestDirection(
    val wireValue: String
) {
    INCOMING("incoming"),
    OUTGOING("outgoing");

    companion object {
        fun fromWireValue(rawValue: String): XcFollowRequestDirection {
            return entries.firstOrNull { it.wireValue == rawValue.trim() }
                ?: throw IllegalArgumentException("Unsupported follow request direction: $rawValue")
        }
    }
}

data class XcSearchPilot(
    val userId: String,
    val handle: String,
    val displayName: String?,
    val compNumber: String?,
    val relationshipState: XcRelationshipState
)

data class XcFollowRequestItem(
    val requestId: String,
    val status: XcFollowRequestStatus,
    val direction: XcFollowRequestDirection,
    val counterpart: XcPilotProfile,
    val relationshipState: XcRelationshipState
)
