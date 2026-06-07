package app.resultatsbridge.organisateur.server

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.collections.iterator

object NetworkUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "IP inconnue"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkUtils", "Erreur IP : ${e.message}")
        }
        return "IP non détectée"
    }
}