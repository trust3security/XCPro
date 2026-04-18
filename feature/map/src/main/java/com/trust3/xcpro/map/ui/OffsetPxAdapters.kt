package com.trust3.xcpro.map.ui

import androidx.compose.ui.geometry.Offset
import com.trust3.xcpro.core.common.geometry.OffsetPx

fun OffsetPx.toComposeOffset(): Offset = Offset(x, y)

fun Offset.toOffsetPx(): OffsetPx = OffsetPx(x, y)
