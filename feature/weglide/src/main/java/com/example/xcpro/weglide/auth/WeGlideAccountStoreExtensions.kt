package com.example.xcpro.weglide.auth

import com.example.xcpro.weglide.domain.WeGlideAccountLink
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import kotlinx.coroutines.flow.firstOrNull

suspend fun WeGlideAccountStore.accountLinkReplaySafe(): WeGlideAccountLink? {
    return accountLink.firstOrNull()
}
