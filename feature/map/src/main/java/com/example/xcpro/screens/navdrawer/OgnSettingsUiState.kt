package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX

data class OgnSettingsUiState(
    val iconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX,
    val ownFlarmDraft: String = "",
    val ownIcaoDraft: String = "",
    val ownFlarmError: String? = null,
    val ownIcaoError: String? = null,
    val savedOwnFlarmHex: String? = null,
    val savedOwnIcaoHex: String? = null
)

