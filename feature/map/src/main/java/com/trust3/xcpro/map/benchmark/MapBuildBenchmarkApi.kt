package com.trust3.xcpro.map.benchmark

import com.trust3.xcpro.benchmark.CoreBuildBenchmarkApi

object MapBuildBenchmarkApi {
    const val MAP_ABI_VERSION: Int = 1
    const val CORE_CHAIN_VERSION: Int = CoreBuildBenchmarkApi.ABI_VERSION
    private const val IMPL_VERSION: Int = 2

    fun renderImplMarker(): String =
        "map-impl-$IMPL_VERSION-core=${CoreBuildBenchmarkApi.renderImplMarker()}"
}
