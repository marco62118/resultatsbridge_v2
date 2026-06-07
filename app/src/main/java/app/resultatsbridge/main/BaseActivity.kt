package app.resultatsbridge.main

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity

open class BaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)

        // Bloque la taille de police
        config.fontScale = 1.0f

        // Bloque le zoom d'affichage (Grand affichage Samsung)
       config.densityDpi = 420

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onResume() {
        super.onResume()
        // Réapplique au retour de l'appli (si l'utilisateur change les réglages pendant l'appli)
        val config = Configuration(resources.configuration)
        config.fontScale = 1.0f
        config.densityDpi = 420
        createConfigurationContext(config)
    }
}