package com.trust3.xcpro.weglide.auth

import com.trust3.xcpro.weglide.domain.WeGlideAccountLink
import com.trust3.xcpro.weglide.domain.WeGlideAccountStore
import kotlinx.coroutines.flow.firstOrNull

suspend fun WeGlideAccountStore.accountLinkReplaySafe(): WeGlideAccountLink? {
    return accountLink.firstOrNull()
}
