package com.homehabit.app.server

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /** Local IPv4 address (Wifi/Ethernet), null if unavailable. */
    fun getLocalIpAddress(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filterNot { it.isLoopback }
            .filter { it.isUp }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterNot { it.isLoopbackAddress }
            .firstOrNull { it.hostAddress?.contains(':') == false } // IPv4 only
            ?.hostAddress
    }.getOrNull()
}
