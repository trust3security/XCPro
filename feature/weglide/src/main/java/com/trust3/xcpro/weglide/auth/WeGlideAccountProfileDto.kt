package com.trust3.xcpro.weglide.auth

import com.google.gson.annotations.SerializedName

data class WeGlideAccountProfileDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null
)
