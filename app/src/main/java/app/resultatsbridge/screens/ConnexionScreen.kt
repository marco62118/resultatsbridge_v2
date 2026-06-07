// ÉCRAN : Connexion joueur (email + mot de passe)
package app.resultatsbridge.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.resultatsbridge.common.utils.getSecurePrefs
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.model.Equipe
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import app.resultatsbridge.common.URL_SERVEUR_ONLINE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnexionScreen(
    modeOnlineInitial: Boolean = true,
    onEquipesRecues: (Int, String, List<Equipe>) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("BridgePrefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val securePrefs = remember { getSecurePrefs(context) }
    var modeOnline by remember { mutableStateOf(modeOnlineInitial) }

    // IPs locales
    val savedIps = remember {
        prefs.getStringSet("recentServerIps", setOf("192.168.137.1"))?.toMutableSet()
            ?: mutableSetOf()
    }
    var expanded by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(savedIps.firstOrNull() ?: "") }

    // Identifiants mémorisés
    var emailInput  by remember { mutableStateOf(securePrefs.getString("lastEmail", "") ?: "") }
    var mdpInput    by remember { mutableStateOf(securePrefs.getString("lastMdp", "") ?: "") }
    var mdpVisible  by remember { mutableStateOf(false) }

    var messageErreur by remember { mutableStateOf<String?>(null) }
    var chargement    by remember { mutableStateOf(false) }

    val dateDuJour = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "📶 Connexion au tournoi du $dateDuJour",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))

        // ── Switch Local / Online ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Local")
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = modeOnline,
                onCheckedChange = { modeOnline = it; messageErreur = null }
            )
            Spacer(Modifier.width(8.dp))
            Text("Online")
        }

        Spacer(Modifier.height(16.dp))

        if (modeOnline) {
            // 🌐 Mode Online
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🌐 Serveur en ligne", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(URL_SERVEUR_ONLINE, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it; messageErreur = null },
                label = { Text("Votre email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = mdpInput,
                onValueChange = { mdpInput = it; messageErreur = null },
                label = { Text("Mot de passe") },
                visualTransformation = if (mdpVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { mdpVisible = !mdpVisible }) {
                        Icon(
                            imageVector = if (mdpVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (mdpVisible) "Masquer" else "Afficher"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

        } else {
            // 📡 Mode Local
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ouvrir les paramètres Wi-Fi")
            }
            Spacer(Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Adresse IP du serveur") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    savedIps.forEach { ip ->
                        DropdownMenuItem(
                            text = { Text(ip) },
                            onClick = { ipInput = ip; expanded = false }
                        )
                    }
                }
            }
        }

        // ── Message erreur ────────────────────────────────────────────
        messageErreur?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Bouton Se connecter ───────────────────────────────────────
        Button(
            onClick = {
                scope.launch {
                    messageErreur = null
                    chargement = true

                    if (modeOnline) {
                        if (emailInput.isBlank() || mdpInput.isBlank()) {
                            messageErreur = "❌ Email et mot de passe obligatoires"
                            chargement = false
                            return@launch
                        }
                        Log.i("ConnexionScreen", "🔐 Connexion Online : $emailInput")
                        ClientNetworkUtils.initialiserServeur(URL_SERVEUR_ONLINE)

                        val auth = ClientNetworkUtils.connexionJoueur(emailInput, mdpInput)
                        if (auth == null) {
                            messageErreur = "❌ Email ou mot de passe incorrect"
                            chargement = false
                            return@launch
                        }
                        Log.i("ConnexionScreen", "✅ Auth OK → ${auth.prenom} ${auth.nom} (${auth.nomClub})")

                        securePrefs.edit()
                            .putString("lastEmail", emailInput)
                            .putString("lastMdp", mdpInput)
                            .putString("tokenJoueur", auth.tokenJoueur)
                            .apply()

                        val tournoi = ClientNetworkUtils.verifierTournoiOuvert()
                        if (tournoi == null) {
                            messageErreur = "⏳ Pas de tournoi ouvert\nVeuillez patienter que l'Organisateur crée le tournoi"
                            chargement = false
                            return@launch
                        }

                        val equipes = ClientNetworkUtils.recupererListeEquipes(tournoi.first)
                        if (equipes.isNullOrEmpty()) {
                            messageErreur = "❌ Aucune équipe disponible"
                            chargement = false
                            return@launch
                        }

                        chargement = false
                        onEquipesRecues(tournoi.first, tournoi.second, equipes)

                    } else {
                        // Mode Local
                        Log.i("ConnexionScreen", "🔌 Connexion locale à $ipInput")
                        ClientNetworkUtils.initialiserServeur(ipInput)

                        val connected = ClientNetworkUtils.testConnection()
                        if (!connected) {
                            messageErreur = "❌ Connexion échouée"
                            onMessage("Connexion échouée")
                            chargement = false
                            return@launch
                        }

                        val tournoi = ClientNetworkUtils.verifierTournoiOuvert()
                        if (tournoi == null) {
                            messageErreur = "⏳ Pas de tournoi ouvert\nVeuillez patienter que l'Organisateur crée le tournoi"
                            onMessage("Aucun tournoi ouvert")
                            chargement = false
                            return@launch
                        }

                        val equipes = ClientNetworkUtils.recupererListeEquipes(tournoi.first)
                        if (equipes.isNullOrEmpty()) {
                            messageErreur = "❌ Aucune équipe disponible"
                            onMessage("Aucune équipe disponible")
                            chargement = false
                            return@launch
                        }

                        savedIps.add(ipInput)
                        if (savedIps.size > 5) savedIps.remove(savedIps.first())
                        prefs.edit().putStringSet("recentServerIps", savedIps).apply()

                        chargement = false
                        onEquipesRecues(tournoi.first, tournoi.second, equipes)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !chargement
        ) {
            Text(if (chargement) "Connexion en cours..." else "Se connecter")
        }
    }
}
