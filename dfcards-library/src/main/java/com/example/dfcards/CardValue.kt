package com.example.dfcards

data class CardValue(
    val primary: String,
    val secondary: String? = null,
    val primaryColorOverride: Long? = null
)
