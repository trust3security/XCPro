package com.example.xcpro.weglide.domain

import kotlinx.coroutines.flow.Flow

interface WeGlideAccountStore {
    val accountLink: Flow<WeGlideAccountLink?>

    suspend fun saveAccountLink(accountLink: WeGlideAccountLink)

    suspend fun clearAccountLink()
}
