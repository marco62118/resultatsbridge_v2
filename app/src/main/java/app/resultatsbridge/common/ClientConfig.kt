package app.resultatsbridge.common

import android.util.Log



/**
 * Configure le mode de fonctionnement du client (Mode jeu local ou Mode client distant).
 */
object ClientConfig {
    /**
     * Indique si l'application fonctionne en mode client sur le même appareil que le serveur.
     * Si 'true', les appels réseau doivent être court-circuités vers DatabaseManager.
     */
    var isLocalServerPlayer: Boolean = false
        private set // L'état peut être lu publiquement, mais modifié uniquement via la fonction ci-dessous

    fun setLocalServerPlayerMode(isLocal: Boolean) {
        isLocalServerPlayer = isLocal
        if (isLocal) {
            Log.i("ClientConfig", "Mode CLIENT-SERVEUR LOCAL activé.")
        } else {
            Log.i("ClientConfig", "Mode CLIENT DISTANT (via réseau) activé.")
        }
    }
}