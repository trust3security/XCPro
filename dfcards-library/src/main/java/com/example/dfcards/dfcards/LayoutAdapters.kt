package com.example.dfcards.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.trust3.xcpro.core.common.geometry.DensityScale
import com.trust3.xcpro.core.common.geometry.IntSizePx

fun IntSize.toIntSizePx(): IntSizePx = IntSizePx(width, height)

fun Density.toDensityScale(): DensityScale = DensityScale(density, fontScale)

