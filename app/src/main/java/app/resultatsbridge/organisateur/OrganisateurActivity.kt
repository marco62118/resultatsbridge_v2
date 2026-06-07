// ACTIVITÉ : Organisateur — tableau de bord principal du DT
package app.resultatsbridge.organisateur

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.utils.getSecurePrefs
import app.resultatsbridge.main.BaseActivity
import app.resultatsbridge.common.AppScaffold
import app.resultatsbridge.common.theme.BridgeServeurTheme
import app.resultatsbridge.organisateur.data.DatabaseManager
import app.resultatsbridge.common.model.Joueur
import app.resultatsbridge.organisateur.data.DatabaseHelper
import app.resultatsbridge.organisateur.server.ServerManager
import app.resultatsbridge.common.ClientConfig
import app.resultatsbridge.client.ClientActivity
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.URL_SERVEUR_ONLINE
import app.resultatsbridge.common.model.ErreurLogItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class OrganisateurActivity : BaseActivity() {

    private val ipServeurState       = mutableStateOf("")
    private val idTournoiState       = mutableStateOf<Int?>(null)
    private val typeTournoiState     = mutableStateOf("")
    private val tournoiOuvertState   = mutableStateOf(false)
    private val modeOnlineState      = mutableStateOf(true)
    private val modeJeuState         = mutableStateOf("rapide")

    private lateinit var securePrefs: SharedPreferences

    private lateinit var creationLauncher: ActivityResultLauncher<Intent>
    private lateinit var constitutionLauncher: ActivityResultLauncher<Intent>
    private lateinit var participationLauncher: ActivityResultLauncher<Intent>
    private var estPremierLancement = true
    private var clientActivityLancee = false
    private var organisateurResteOrganisateur = false
    private var constitutionEquipesLancee = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        securePrefs = getSecurePrefs(this)

        val modeOnlineIntent = intent.getBooleanExtra("mode_online", true)
        modeOnlineState.value = modeOnlineIntent
        modeJeuState.value = intent.getStringExtra("mode_jeu") ?: "rapide"

        if (!modeOnlineIntent) {
            DatabaseHelper.initializeDatabase(this)
            val tournoiExistant = DatabaseManager.getTournoiOuvert(this)
            if (tournoiExistant != null) {
                idTournoiState.value = tournoiExistant.first
                typeTournoiState.value = tournoiExistant.second
                tournoiOuvertState.value = true
            } else {
                // Aucun tournoi ouvert — cherche le dernier tournoi fermé pour permettre la correction
                val dernierFerme = DatabaseManager.getDernierTournoiFerme(this)
                idTournoiState.value = dernierFerme
                tournoiOuvertState.value = false
            }
        }

        creationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val idTournoi   = data?.getIntExtra("id_tournoi", -1) ?: -1
                val typeTournoi = data?.getStringExtra("type_tournoi") ?: ""
                val nbEquipes   = data?.getIntExtra("nombre_equipes", 0) ?: 0
                if (idTournoi != -1) {
                    idTournoiState.value = idTournoi
                    typeTournoiState.value = typeTournoi
                    constitutionEquipesLancee = true  // empêche onResume de lancer Participation avant la constitution
                    val scope = CoroutineScope(Dispatchers.Main)
                    scope.launch {
                        // Attribuer les donnes préparées avant tournoi à ce tournoi
                        withContext(Dispatchers.IO) {
                            if (modeOnlineState.value) ClientNetworkUtils.attribuerDonnesAuTournoi(idTournoi)
                            else DatabaseManager.attribuerDonnesAuTournoi(this@OrganisateurActivity, idTournoi)
                        }
                        val joueurs: List<Joueur> = if (modeOnlineState.value)
                            withContext(Dispatchers.IO) { ClientNetworkUtils.getTousLesJoueurs() }
                        else DatabaseManager.getTousLesJoueurs(this@OrganisateurActivity)
                       /* val intent = Intent(this@OrganisateurActivity, ConstitutionEquipesActivity::class.java).apply {
                            putExtra("id_tournoi", idTournoi); putExtra("nombre_equipes", nbEquipes)
                            putParcelableArrayListExtra("joueurs", ArrayList(joueurs))
                            putExtra("mode_online", modeOnlineState.value)
                        }*/
                        val intent = Intent(this@OrganisateurActivity, ConstitutionEquipesActivity::class.java).apply {
                            putExtra("id_tournoi", idTournoi)
                            putExtra("nombre_equipes", nbEquipes)  // -1 si Mitchell
                            putParcelableArrayListExtra("joueurs", ArrayList(joueurs))
                            putExtra("mode_online", modeOnlineState.value)
                            // Donnes par table pour Mitchell (0 pour les autres types)
                            putExtra("nbre_donnes_par_table", data?.getIntExtra("nbre_donnes_par_table", 0) ?: 0)
                            putExtra("type_tournoi", typeTournoi)
                        }
                        constitutionLauncher.launch(intent)
                    }
                } else Toast.makeText(this, "Erreur création tournoi", Toast.LENGTH_LONG).show()
            }
        }

        constitutionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val equipesSuccess = data?.getBooleanExtra("equipes_success", false) ?: false
                val idTournoi = data?.getIntExtra("id_tournoi", -1) ?: -1
                if (equipesSuccess && idTournoi != -1) {
                    tournoiOuvertState.value = true  // constitution réussie — onResume() lancera Participation
                } else Toast.makeText(this, "Enregistrement des équipes échoué", Toast.LENGTH_LONG).show()
            }
        }

        participationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val veutJouer = result.data?.getBooleanExtra("organisateur_veut_jouer", false) ?: false
                val idTournoi = result.data?.getIntExtra("id_tournoi", idTournoiState.value ?: -1) ?: idTournoiState.value ?: -1
                val ipServeur = result.data?.getStringExtra("ip_serveur") ?: ipServeurState.value
                ClientConfig.setLocalServerPlayerMode(veutJouer)
                if (veutJouer) {
                    clientActivityLancee = true
                    val intent = Intent(this, ClientActivity::class.java).apply {
                        putExtra("id_tournoi", idTournoi); putExtra("ip_serveur", ipServeur)
                        putExtra("Organisateur_is_Joueur", true)
                        putExtra("mode_online", modeOnlineState.value)
                        putExtra("mode_jeu", modeJeuState.value)
                    }
                    startActivity(intent)
                } else {
                    organisateurResteOrganisateur = true  // ← il a choisi "Non, rester organisateur"
                }
            }
        }
        setContent {
            BridgeServeurTheme {
                AppScaffold(
                    ipAddress = if (modeOnlineState.value) URL_SERVEUR_ONLINE else ipServeurState.value,
                    numeroTournoi = idTournoiState.value,
                    equipeChoisie = null,
                    nomEcran = "Organisateur — Tableau de bord"
                ) {
                    val scope = rememberCoroutineScope()

                    var estConnecteOnline by remember { mutableStateOf(false) }
                    var emailInput by remember {
                        mutableStateOf(
                            securePrefs.getString(
                                "lastEmail",
                                ""
                            ) ?: ""
                        )
                    }
                    var mdpInput by remember {
                        mutableStateOf(
                            securePrefs.getString("lastMdp", "") ?: ""
                        )
                    }
                    // ── Toggle visibilité mot de passe connexion ──
                    var mdpVisible by remember { mutableStateOf(false) }
                    var nomAssociation by remember { mutableStateOf("") }
                    var messageErreur by remember { mutableStateOf<String?>(null) }
                    var chargement by remember { mutableStateOf(false) }
                    var showJournalErreurs by remember { mutableStateOf(false) }
                    var journalErreurs by remember { mutableStateOf<List<ErreurLogItem>>(emptyList()) }
                    var showCorrectionResultat by remember { mutableStateOf(false) }
                    var importJoueursEnCours by remember { mutableStateOf(false) }
                    var messageImportJoueurs by remember { mutableStateOf<String?>(null) }

                    // ── Écran de correction des résultats ─────────────────────────
                    if (showCorrectionResultat && idTournoiState.value != null) {
                        app.resultatsbridge.screens.CorrectionResultatScreen(
                            idTournoi = idTournoiState.value!!,
                            useLocal = !modeOnlineState.value,
                            onBack = { showCorrectionResultat = false }
                        )
                        return@AppScaffold
                    }

                    var codeAdherent by remember { mutableStateOf("") }
                    var nouveauCode by remember { mutableStateOf("") }
                    var msgCode by remember { mutableStateOf<String?>(null) }
                    var editCodeVisible by remember { mutableStateOf(false) }

                    // ── Dialogue journal des erreurs ──────────────────────────────
                    if (showJournalErreurs) {
                        AlertDialog(
                            onDismissRequest = { showJournalErreurs = false },
                            title = { Text("Journal des erreurs") },
                            text = {
                                if (journalErreurs.isEmpty()) {
                                    Text("Aucune erreur enregistrée.")
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 400.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        journalErreurs.forEach { log ->
                                            val equipeText =
                                                if (log.equipeNumero > 0) "Éq. ${log.equipeNumero}" else "—"
                                            Card(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        "${log.timestamp} · $equipeText",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Text(
                                                        log.message,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        log.etape,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showJournalErreurs = false }) { Text("Fermer") }
                            },
                            dismissButton = {
                                if (!modeOnlineState.value) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                DatabaseManager.effacerErreursLog(this@OrganisateurActivity)
                                            }
                                            journalErreurs = emptyList()
                                            showJournalErreurs = false
                                        }
                                    }) { Text("Effacer tout") }
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                        // ════════════════════════════════════════
                        // MODE LOCAL
                        // ════════════════════════════════════════
                        if (!modeOnlineState.value) {

                            if (tournoiOuvertState.value) {
                                Text("Maintenance", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val success =
                                            DatabaseManager.fermerTournoiOuvert(this@OrganisateurActivity)
                                        if (success) {
                                            idTournoiState.value = null; typeTournoiState.value = ""
                                            tournoiOuvertState.value = false; ipServeurState.value =
                                                ""
                                            Toast.makeText(
                                                this@OrganisateurActivity,
                                                "Tournoi fermé",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                ) { Text("🧹 Fermer les tournois ouverts", fontSize = 17.sp) }
                                Spacer(Modifier.height(16.dp))
                            }

                            var exportEnCours by remember { mutableStateOf(false) }
                            var exportMessage by remember { mutableStateOf<String?>(null) }
                            var showDialogExport by remember { mutableStateOf(false) }
                            var showResultatExport by remember { mutableStateOf(false) }
                            var emailExport by remember {
                                mutableStateOf(
                                    securePrefs.getString(
                                        "lastEmail",
                                        ""
                                    ) ?: ""
                                )
                            }
                            var mdpExport by remember {
                                mutableStateOf(
                                    securePrefs.getString(
                                        "lastMdp",
                                        ""
                                    ) ?: ""
                                )
                            }
                            // ── Toggle visibilité mot de passe export ──
                            var mdpExportVisible by remember { mutableStateOf(false) }

                            if (showResultatExport && exportMessage != null) {
                                AlertDialog(
                                    onDismissRequest = { showResultatExport = false },
                                    title = { Text(if (exportMessage!!.startsWith("✅")) "✅ Export réussi" else "❌ Erreur export") },
                                    text = {
                                        Text(
                                            exportMessage!!,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            showResultatExport = false
                                        }) { Text("OK") }
                                    }
                                )
                            }

                            if (showDialogExport) {
                                AlertDialog(
                                    onDismissRequest = { showDialogExport = false },
                                    title = { Text("☁️ Connexion au compte cloud") },
                                    text = {
                                        Column {
                                            Text(
                                                "Entrez vos identifiants TournoiBridgeOnline :",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = emailExport,
                                                onValueChange = { emailExport = it },
                                                label = { Text("Email") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            // ── Mot de passe avec icône œil ──────────────
                                            OutlinedTextField(
                                                value = mdpExport,
                                                onValueChange = { mdpExport = it },
                                                label = { Text("Mot de passe") },
                                                visualTransformation = if (mdpExportVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                trailingIcon = {
                                                    IconButton(onClick = {
                                                        mdpExportVisible = !mdpExportVisible
                                                    }) {
                                                        Icon(
                                                            imageVector = if (mdpExportVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                            contentDescription = if (mdpExportVisible) "Masquer" else "Afficher"
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            showDialogExport = false
                                            scope.launch {
                                                val idTournoi = withContext(Dispatchers.IO) {
                                                    DatabaseManager.getTournoiOuvertEtTermine(this@OrganisateurActivity)
                                                }
                                                if (idTournoi == null) {
                                                    exportMessage =
                                                        "❌ Aucun tournoi terminé trouvé."
                                                    showResultatExport = true
                                                    return@launch
                                                }
                                                // ✅ FIX : collecter les données AVANT de fermer le tournoi
                                                lancerExport(
                                                    context = this@OrganisateurActivity,
                                                    idTournoi = idTournoi,
                                                    email = emailExport,
                                                    mdp = mdpExport,
                                                    securePrefs = securePrefs,
                                                    onEnCours = { exportEnCours = it },
                                                    onMessage = { msg ->
                                                        exportMessage = msg; showResultatExport =
                                                        true
                                                    },
                                                    onSuccess = {
                                                        // Fermer le tournoi APRÈS export réussi
                                                        DatabaseManager.fermerTournoiOuvert(this@OrganisateurActivity)
                                                        tournoiOuvertState.value = false
                                                    }
                                                )
                                            }
                                        }) { Text("Exporter") }
                                    },
                                    dismissButton = {
                                        OutlinedButton(onClick = {
                                            showDialogExport = false
                                        }) { Text("Annuler") }
                                    }
                                )
                            }

                            if (tournoiOuvertState.value) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val cm =
                                                getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                                            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                                            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                                                exportMessage =
                                                    "⚠️ Pas de connexion internet !\n\nDésactivez le point d'accès mobile puis reconnectez-vous au Wi-Fi avant d'exporter."
                                                showResultatExport = true; return@launch
                                            }
                                            val idTournoi = withContext(Dispatchers.IO) {
                                                DatabaseManager.getTournoiOuvertEtTermine(this@OrganisateurActivity)
                                            }
                                            if (idTournoi == null) {
                                                exportMessage = "❌ Tournoi pas encore terminé."
                                                showResultatExport = true; return@launch
                                            }
                                            val emailMemo =
                                                securePrefs.getString("lastEmail", "") ?: ""
                                            val mdpMemo = securePrefs.getString("lastMdp", "") ?: ""
                                            if (emailMemo.isNotEmpty() && mdpMemo.isNotEmpty()) {
                                                // ✅ FIX : collecter AVANT de fermer
                                                lancerExport(
                                                    context = this@OrganisateurActivity,
                                                    idTournoi = idTournoi,
                                                    email = emailMemo,
                                                    mdp = mdpMemo,
                                                    securePrefs = securePrefs,
                                                    onEnCours = { exportEnCours = it },
                                                    onMessage = { msg ->
                                                        exportMessage = msg; showResultatExport =
                                                        true
                                                    },
                                                    onSuccess = {
                                                        // Fermer le tournoi APRÈS export réussi
                                                        DatabaseManager.fermerTournoiOuvert(this@OrganisateurActivity)
                                                        tournoiOuvertState.value = false
                                                    }
                                                )
                                            } else {
                                                showDialogExport = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !exportEnCours,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                ) {
                                    Text(if (exportEnCours) "⏳ Export en cours..." else "☁️ Exporter le tournoi", fontSize = 17.sp)
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Créer un point d'accès mobile nommé \"ABE\" " +
                                                "\n dans les paramètres / Connexions / points d'accès mobile et modem. " +
                                                "\n Nom: ABE \n Mot de passe: MiMa0562+ \n Sécurité: WPA2-personal. " +
                                                "\n Enregistrer et Activer (cela désactive le Wi-Fi).",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val ip = ServerManager.startServer(this@OrganisateurActivity)
                                    if (ip == null) {
                                        Toast.makeText(
                                            this@OrganisateurActivity,
                                            "Impossible de démarrer le serveur",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        ipServeurState.value = ip
                                        val tournoi =
                                            DatabaseManager.getTournoiOuvert(this@OrganisateurActivity)
                                        if (tournoi != null) {
                                            idTournoiState.value =
                                                tournoi.first; typeTournoiState.value =
                                                tournoi.second; tournoiOuvertState.value = true
                                            val intent = Intent(
                                                this@OrganisateurActivity,
                                                ParticipationOrganisateurActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    "id_tournoi",
                                                    tournoi.first
                                                ); putExtra("ip_serveur", ip)
                                            }
                                            participationLauncher.launch(intent)
                                        } else {
                                            val intent = Intent(
                                                this@OrganisateurActivity,
                                                CreationTournoiActivity::class.java
                                            ).apply {
                                                putExtra("ip_serveur", ip); putExtra(
                                                "mode_online",
                                                false
                                            )
                                            }
                                            creationLauncher.launch(intent)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                            ) { Text(if (tournoiOuvertState.value) "Démarrer le serveur" else "Créer un nouveau tournoi", fontSize = 17.sp) }

                            if (ipServeurState.value.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Serveur : ${ipServeurState.value}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            if (idTournoiState.value != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showCorrectionResultat = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                                    border = BorderStroke(1.dp, Color(0xFFE65100)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                ) { Text("🔧 Corriger un résultat", fontSize = 17.sp) }
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        journalErreurs = withContext(Dispatchers.IO) {
                                            DatabaseManager.getErreursLog(this@OrganisateurActivity)
                                        }
                                        showJournalErreurs = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                            ) { Text("Journal des erreurs", fontSize = 17.sp) }
                        }

                        // ════════════════════════════════════════
                        // MODE ONLINE
                        // ════════════════════════════════════════
                        if (modeOnlineState.value) {

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "🌐 Serveur en ligne",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        URL_SERVEUR_ONLINE,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            if (!estConnecteOnline) {
                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = { emailInput = it; messageErreur = null },
                                    label = { Text("Email de l'association") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                // ── Mot de passe avec icône œil ──────────────────
                                OutlinedTextField(
                                    value = mdpInput,
                                    onValueChange = { mdpInput = it; messageErreur = null },
                                    label = { Text("Mot de passe") },
                                    visualTransformation = if (mdpVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { mdpVisible = !mdpVisible }) {
                                            Icon(
                                                imageVector = if (mdpVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = if (mdpVisible) "Masquer le mot de passe" else "Afficher le mot de passe"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))

                                messageErreur?.let { msg ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Text(
                                            msg, modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Button(
                                    onClick = {
                                        if (emailInput.isBlank() || mdpInput.isBlank()) {
                                            messageErreur =
                                                "Email et mot de passe obligatoires"; return@Button
                                        }
                                        scope.launch {
                                            chargement = true; messageErreur = null
                                            val auth = withContext(Dispatchers.IO) {
                                                ClientNetworkUtils.initialiserServeur(
                                                    URL_SERVEUR_ONLINE
                                                )
                                                ClientNetworkUtils.connexionAssociation(
                                                    emailInput,
                                                    mdpInput
                                                )
                                            }
                                            if (auth == null) {
                                                messageErreur =
                                                    "❌ Email ou mot de passe incorrect"; chargement =
                                                    false; return@launch
                                            }
                                            securePrefs.edit().putString("lastEmail", emailInput)
                                                .putString("lastMdp", mdpInput).apply()
                                            nomAssociation = auth.nom
                                            val codeExistant = withContext(Dispatchers.IO) {
                                                try {
                                                    val url =
                                                        "${URL_SERVEUR_ONLINE}/serverBridge.php/getCodeAdherent?token_api=${auth.tokenApi}"
                                                    val resp = URL(url).readText()
                                                    JSONObject(resp).optString("code_adherent", "")
                                                } catch (e: Exception) {
                                                    ""
                                                }
                                            }
                                            codeAdherent = codeExistant; nouveauCode = codeExistant
                                            val tournoi =
                                                withContext(Dispatchers.IO) { ClientNetworkUtils.verifierTournoiOuvert() }
                                            estConnecteOnline = true; chargement = false
                                            if (tournoi != null) {
                                                idTournoiState.value =
                                                    tournoi.first; typeTournoiState.value =
                                                    tournoi.second; tournoiOuvertState.value = true
                                            } else tournoiOuvertState.value = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !chargement,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                ) { Text(if (chargement) "Connexion..." else "🌐 Se connecter", fontSize = 17.sp) }
                            }

                            if (estConnecteOnline) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "✅ Connecté",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            nomAssociation,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "🔑 Code adhérent",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        if (codeAdherent.isNotEmpty()) {
                                            Text(
                                                codeAdherent,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Donnez ce code à vos adhérents pour qu'ils s'inscrivent",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        } else Text(
                                            "Aucun code défini",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(onClick = {
                                            editCodeVisible = !editCodeVisible
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(if (editCodeVisible) "Annuler" else "✏️ Modifier le code")
                                        }
                                        if (editCodeVisible) {
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = nouveauCode,
                                                onValueChange = { nouveauCode = it.uppercase() },
                                                label = { Text("Nouveau code") },
                                                placeholder = { Text("Ex: EMBRUN25") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            msgCode?.let {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    it, style = MaterialTheme.typography.bodySmall,
                                                    color = if (it.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    if (nouveauCode.length < 4) {
                                                        msgCode =
                                                            "❌ Code trop court (4 caractères minimum)"; return@Button
                                                    }
                                                    scope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            ClientNetworkUtils.majCodeAdherent(
                                                                nouveauCode
                                                            )
                                                        }
                                                        when (result) {
                                                            "OK" -> {
                                                                codeAdherent =
                                                                    nouveauCode; msgCode =
                                                                    "✅ Code mis à jour"; editCodeVisible =
                                                                    false
                                                            }

                                                            "CODE_EXISTE" -> msgCode =
                                                                "❌ Ce code est déjà utilisé par un autre club"

                                                            else -> msgCode = "❌ Erreur serveur"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Enregistrer") }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))

                                if (tournoiOuvertState.value) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "🎯 Tournoi en cours",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "ID : ${idTournoiState.value}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "Type : ${typeTournoiState.value}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(
                                                this@OrganisateurActivity,
                                                ParticipationOrganisateurActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    "id_tournoi",
                                                    idTournoiState.value ?: -1
                                                ); putExtra("ip_serveur", URL_SERVEUR_ONLINE)
                                            }
                                            participationLauncher.launch(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) { Text("▶️ Reprendre le tournoi", fontSize = 17.sp) }
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val success =
                                                    withContext(Dispatchers.IO) { ClientNetworkUtils.fermerTournoiOuvert() }
                                                if (success) {
                                                    idTournoiState.value =
                                                        null; typeTournoiState.value =
                                                        ""; tournoiOuvertState.value =
                                                        false; Toast.makeText(
                                                        this@OrganisateurActivity,
                                                        "Tournoi fermé",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else Toast.makeText(
                                                    this@OrganisateurActivity,
                                                    "Erreur fermeture tournoi",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) { Text("🧹 Fermer le tournoi Online", fontSize = 17.sp) }
                                }

                                if (!tournoiOuvertState.value) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(
                                                this@OrganisateurActivity,
                                                CreationTournoiActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    "ip_serveur",
                                                    URL_SERVEUR_ONLINE
                                                ); putExtra("mode_online", true)
                                            }
                                            creationLauncher.launch(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) { Text("🌐 Créer un tournoi Online", fontSize = 17.sp) }
                                }

                                val idTournoiPourLog = idTournoiState.value
                                if (idTournoiPourLog != null) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                journalErreurs = withContext(Dispatchers.IO) {
                                                    ClientNetworkUtils.recupererErreursLog(
                                                        idTournoiPourLog
                                                    )
                                                }
                                                showJournalErreurs = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) { Text("Journal des erreurs", fontSize = 17.sp) }
                                }
                                if (idTournoiState.value != null) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { showCorrectionResultat = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                                        border = BorderStroke(1.dp, Color(0xFFE65100)),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                    ) { Text("🔧 Corriger un résultat", fontSize = 17.sp) }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            importJoueursEnCours = true
                                            messageImportJoueurs = null
                                            val joueurs = withContext(Dispatchers.IO) {
                                                ClientNetworkUtils.getTousLesJoueurs()
                                            }
                                            if (joueurs.isEmpty()) {
                                                messageImportJoueurs = "❌ Aucun joueur récupéré du serveur"
                                            } else {
                                                val n = withContext(Dispatchers.IO) {
                                                    DatabaseManager.importerJoueursLocaux(
                                                        this@OrganisateurActivity, joueurs
                                                    )
                                                }
                                                messageImportJoueurs = if (n >= 0)
                                                    "✅ $n joueurs importés en local"
                                                else
                                                    "❌ Erreur lors de l'import"
                                            }
                                            importJoueursEnCours = false
                                        }
                                    },
                                    enabled = !importJoueursEnCours,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                                ) {
                                    Text(if (importJoueursEnCours) "⏳ Import en cours..." else "⬇️ Importer les joueurs du serveur", fontSize = 17.sp)
                                }
                                messageImportJoueurs?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (it.startsWith("✅")) Color.White else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Fonction d'export vers le cloud ──────────────────────────────────────
    // ✅ FIX : ajout du paramètre onSuccess appelé APRÈS l'export réussi
    // La fermeture du tournoi se fait dans onSuccess, PAS avant l'export.
    // Ainsi collecterTournoiPourExport trouve bien le tournoi encore ouvert.
    private suspend fun lancerExport(
        context: Context,
        idTournoi: Int,
        email: String,
        mdp: String,
        securePrefs: SharedPreferences,
        onEnCours: (Boolean) -> Unit,
        onMessage: (String) -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        onEnCours(true)
        onMessage("⏳ Connexion au cloud...")
        try {
            ClientNetworkUtils.initialiserServeur(URL_SERVEUR_ONLINE)
            val auth = withContext(Dispatchers.IO) { ClientNetworkUtils.connexionAssociation(email, mdp) }
            if (auth == null) { onMessage("❌ Identifiants incorrects"); onEnCours(false); return }
            securePrefs.edit().putString("lastEmail", email).putString("lastMdp", mdp).apply()

            onMessage("⏳ Collecte des données...")
            // ✅ Collecte avec le tournoi encore OUVERT en base
            val json = withContext(Dispatchers.IO) { DatabaseManager.collecterTournoiPourExport(context, idTournoi) }
            if (json == null) { onMessage("❌ Erreur lors de la collecte des données"); onEnCours(false); return }

            onMessage("⏳ Envoi vers le cloud...")
            val etat = withContext(Dispatchers.IO) { ClientNetworkUtils.exporterTournoi(json) }

            if (etat.startsWith("OK:")) {
                val idCloud = etat.substringAfter("OK:")
                onMessage("✅ Tournoi exporté ! N°  $idCloud dans le cloud :")
                // ✅ Fermeture du tournoi APRÈS export réussi
                withContext(Dispatchers.IO) { onSuccess() }
            } else if (etat == "OK") {
                onMessage("✅ Tournoi exporté avec succès !")
                withContext(Dispatchers.IO) { onSuccess() }
            } else {
                onMessage("❌ Erreur lors de l'envoi : $etat")
            }
        } catch (e: Exception) {
            Log.e("OrganisateurActivity", "❌ lancerExport erreur : ${e.message}", e)
            onMessage("❌ Erreur : ${e.message}")
        } finally {
            onEnCours(false)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        ServerManager.stopServer()
        Log.i("OrganisateurActivity", "🔴 onDestroy — serveur arrêté")
    }

    override fun onResume() {
        super.onResume()
        if (estPremierLancement) { estPremierLancement = false; return }
        if (clientActivityLancee) { clientActivityLancee = false; return }
        if (organisateurResteOrganisateur) { organisateurResteOrganisateur = false; return }
        if (constitutionEquipesLancee) { constitutionEquipesLancee = false; return } // ← stoppe la boucle
        val ipAUtiliser = if (modeOnlineState.value) URL_SERVEUR_ONLINE else ipServeurState.value
        if (ipAUtiliser.isNotEmpty() && tournoiOuvertState.value) {
            val intent = Intent(this, ParticipationOrganisateurActivity::class.java).apply {
                putExtra("id_tournoi", idTournoiState.value ?: -1)
                putExtra("ip_serveur", ipAUtiliser)
            }
            participationLauncher.launch(intent)
        }
    }

}
