package app.resultatsbridge.client

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


@Composable
fun ClientTestUI(
    date: String,
    onResult: (Boolean) -> Unit,
    onIpReady: (String) -> Unit = {}     // ✅ nouveau param optionnel
) {
    var ipInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("🎮 Réservé aux joueurs du tournoi du $date", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "📶 Si vous n'êtes pas connecté en Wi-Fi au réseau \"ABE\" \n Ouvrez les paramètres Wi-Fi pour sélectionner le réseau \"ABE\"",
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                context.startActivity(intent)
                Log.i("ClientTestUI", "⚙️ Ouverture des paramètres Wi-Fi")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ouvrir les paramètres Wi-Fi")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("📥 Veuillez entrer l'adresse fournie par le Organisateur", fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            label = { Text("Adresse IP du serveur") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                result = "⏳ Test en cours..."
                Log.i("ClientTestUI", "🔄 Début du test de connexion vers $ipInput")
                scope.launch {
                    val success = ClientNetworkUtils.testConnection()
                    result = if (success) "✅ Connexion réussie !" else "❌ Échec de la connexion"
                    Log.i("ClientTestUI", "📶 Résultat du test : $result")
                    if (success) onIpReady(ipInput)   // ✅ on renvoie l’IP saisie au parent
                    onResult(success)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connexion au Organisateur") }

        Spacer(modifier = Modifier.height(16.dp))
        Text(result, fontSize = 16.sp)
    }
}
