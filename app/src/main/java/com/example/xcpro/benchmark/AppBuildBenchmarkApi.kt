package com.example.xcpro.benchmark

import com.example.xcpro.map.benchmark.MapBuildBenchmarkApi

object AppBuildBenchmarkApi {
    private const val IMPL_VERSION: Int = 1

    val marker: String =
        "app-impl-$IMPL_VERSION-mapAbi=${MapBuildBenchmarkApi.MAP_ABI_VERSION}" +
            "-coreChain=${MapBuildBenchmarkApi.CORE_CHAIN_VERSION}" +
            "-${MapBuildBenchmarkApi.renderImplMarker()}"
}
