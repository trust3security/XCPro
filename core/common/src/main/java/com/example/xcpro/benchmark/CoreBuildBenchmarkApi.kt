package com.example.xcpro.benchmark

object CoreBuildBenchmarkApi {
    const val ABI_VERSION: Int = 1
    private const val IMPL_VERSION: Int = 1

    fun renderImplMarker(): String = "core-impl-$IMPL_VERSION"
}
