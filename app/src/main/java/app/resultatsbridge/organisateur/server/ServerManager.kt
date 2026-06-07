package app.resultatsbridge.organisateur.server

import android.content.Context
import android.util.Log

object ServerManager {
    private var serveurHTTP: ClientServeurHTTP? = null

    fun startServer(context: Context): String? {
        return try {
            // Arrêter l'ancien serveur s'il tourne encore (évite EADDRINUSE)
            serveurHTTP?.stop()
            serveurHTTP = null

            serveurHTTP = ClientServeurHTTP(8080, context)

            // Récupération de l'IP locale
            val ip = NetworkUtils.getLocalIpAddress()
            Log.i("ServerManager", "🚀 Serveur démarré sur IP : $ip")

            ip
        } catch (e: Exception) {
            Log.e("ServerManager", "❌ Erreur au démarrage du serveur : ${e.message}")
            null
        }
    }

    fun stopServer() {
        serveurHTTP?.stop()
        Log.i("ServerManager", "🛑 Serveur arrêté")
        serveurHTTP = null
    }
}