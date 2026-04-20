package com.trust3.xcpro.simulator.condor

import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidLocalNetworkInfoPort @Inject constructor() : LocalNetworkInfoPort {
    override fun currentIpv4Address(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .asSequence()
                .filter { networkInterface ->
                    runCatching { networkInterface.isUp && !networkInterface.isLoopback }
                        .getOrDefault(false)
                }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses?.toList().orEmpty().asSequence()
                }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { address ->
                    !address.isLoopbackAddress && !address.isLinkLocalAddress
                }
                ?.hostAddress
        }.getOrNull()
}
