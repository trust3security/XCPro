package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OGN_RECEIVE_RADIUS_DEFAULT_KM
import com.example.xcpro.ogn.OgnDisplayUpdateMode

data class OgnSettingsUiState(
    val iconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX,
    val receiveRadiusKm: Int = OGN_RECEIVE_RADIUS_DEFAULT_KM,
    val autoReceiveRadiusEnabled: Boolean = false,
    val displayUpdateMode: OgnDisplayUpdateMode = OgnDisplayUpdateMode.DEFAULT,
    val ownFlarmDraft: String = "",
    val ownIcaoDraft: String = "",
    val ownFlarmError: String? = null,
    val ownIcaoError: String? = null,
    val savedOwnFlarmHex: String? = null,
    val savedOwnIcaoHex: String? = null
)
