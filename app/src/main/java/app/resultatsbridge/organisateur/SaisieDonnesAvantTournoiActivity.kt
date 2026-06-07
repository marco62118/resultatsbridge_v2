// ACTIVITÉ : Saisie donnes avant tournoi — liste + saisie photo par numéro de donne
package app.resultatsbridge.organisateur

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.main.BaseActivity
import app.resultatsbridge.common.AppScaffold
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.DonneAvantTournoi
import app.resultatsbridge.common.theme.BridgeServeurTheme
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.organisateur.data.DatabaseManager
import app.resultatsbridge.screens.AffichageMainsBatchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaisieDonnesAvantTournoiActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ipServeur  = intent.getStringExtra("ip_serveur") ?: ""
        val modeOnline = intent.getBooleanExtra("mode_online", false)

        if (modeOnline) ClientNetworkUtils.initialiserServeur(ipServeur)

        setContent {
            BridgeServeurTheme {
                val scope = rememberCoroutineScope()
                var donnes by remember { mutableStateOf<List<DonneAvantTournoi>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var donneEnCours by remember { mutableStateOf<Int?>(null) }
                var demarrerBatchDirectement by remember { mutableStateOf(false) }
                var numeroDonneSaisie by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                var derniereDonneEnregistree by remember { mutableStateOf<Int?>(null) }
                var modeleRtDetr by remember { mutableStateOf(false) }
                var modeleExpanded by remember { mutableStateOf(false) }
                val rotation90 = true
                var donneInitialeCartes by remember { mutableStateOf<List<List<String>>?>(null) }

                fun chargerDonnes() {
                    scope.launch {
                        isLoading = true
                        donnes = if (modeOnline) {
                            withContext(Dispatchers.IO) { ClientNetworkUtils.getMainsAvantTournoi() }
                        } else {
                            DatabaseManager.getMainsAvantTournoi(this@SaisieDonnesAvantTournoiActivity)
                        }
                        isLoading = false
                    }
                }

                LaunchedEffect(Unit) { chargerDonnes() }

                if (donneEnCours != null) {
                    AffichageMainsBatchScreen(
                        numeroDonne = donneEnCours!!,
                        demarrerDirectement = demarrerBatchDirectement,
                        initialModeleRtDetr = modeleRtDetr,
                        initialRotation90 = rotation90,
                        initialCartes = donneInitialeCartes,
                        onRetour = { donneEnCours = null; demarrerBatchDirectement = false; donneInitialeCartes = null },
                        onEnregistrer = { mainsCartes ->
                            val mainsCodes = mainsCartes.map { main -> main.map { it.code } }
                            val numDonne = donneEnCours!!
                            donneEnCours = null
                            demarrerBatchDirectement = false
                            donneInitialeCartes = null
                            scope.launch {
                                val ok = if (modeOnline) {
                                    withContext(Dispatchers.IO) {
                                        ClientNetworkUtils.sauvegarderMainsAvantTournoi(numDonne, mainsCodes)
                                    }
                                } else {
                                    DatabaseManager.sauvegarderMainsAvantTournoi(
                                        this@SaisieDonnesAvantTournoiActivity, numDonne, mainsCodes
                                    )
                                }
                                if (ok) {
                                    chargerDonnes()
                                    derniereDonneEnregistree = numDonne
                                } else {
                                    message = "❌ Erreur sauvegarde donne $numDonne"
                                }
                            }
                        }
                    )
                    return@BridgeServeurTheme
                }

                AppScaffold(ipAddress = ipServeur, numeroTournoi = null, equipeChoisie = null, nomEcran = "Saisie donnes avant tournoi") {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

                        // ── En-tête fixe ──────────────────────────────────────────
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Préparation des donnes", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Saisissez les 4 mains de chaque donne avant le tournoi. " +
                            "Tenez le smartphone en mode paysage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black
                        )

                        if (message.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(message, color = if (message.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828), fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── N° de donne + Modèle sur la même ligne ────────────────
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = numeroDonneSaisie,
                                onValueChange = { numeroDonneSaisie = it.filter { c -> c.isDigit() } },
                                label = { Text("N° de donne à saisir", color = Color.Black) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    val n = numeroDonneSaisie.toIntOrNull() ?: 0
                                    if (n > 0) {
                                        derniereDonneEnregistree = null
                                        demarrerBatchDirectement = true
                                        donneEnCours = n
                                        numeroDonneSaisie = ""
                                    }
                                }),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Color.White,
                                    unfocusedBorderColor = Color.White,
                                    focusedLabelColor    = Color.Black,
                                    unfocusedLabelColor  = Color.Black,
                                    focusedTextColor     = Color.Black,
                                    unfocusedTextColor   = Color.Black,
                                    cursorColor          = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { modeleExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, Color.White),
                                ) { Text(if (modeleRtDetr) "RT-DETR ⚡" else "Roboflow ☁", fontSize = 12.sp, color = Color.Black) }
                                DropdownMenu(expanded = modeleExpanded, onDismissRequest = { modeleExpanded = false }) {
                                    DropdownMenuItem(text = { Text("RT-DETR ⚡ (local)") }, onClick = { modeleRtDetr = true; modeleExpanded = false })
                                    DropdownMenuItem(text = { Text("Roboflow ☁ (web)") }, onClick = { modeleRtDetr = false; modeleExpanded = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Liste donnes scrollable (prend tout l'espace restant) ─
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                donnes.isEmpty() -> Text("Aucune donne préparée pour l'instant.", color = Color.Black)
                                else -> {
                                    Column {
                                        Text(
                                            "${donnes.size} donne(s) préparée(s)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(donnes) { donne ->
                                                Card(
                                                    onClick = {
                                                        derniereDonneEnregistree = null
                                                        demarrerBatchDirectement = false
                                                        donneInitialeCartes = donne.mains
                                                        donneEnCours = donne.numeroDonne
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "Donne ${donne.numeroDonne}",
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            "Donneur: ${donne.donneur}  Vulnérable: ${donne.vulnerable}",
                                                            fontSize = 12.sp,
                                                            color = Color.Black
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("✅", fontSize = 18.sp)
                                                    }
                                                }
                                            }
                                            if (derniereDonneEnregistree != null) {
                                                item {
                                                    val existants = donnes.map { it.numeroDonne }.toSet()
                                                    val numSuivant = generateSequence(1) { it + 1 }.first { it !in existants }
                                                    Card(
                                                        onClick = {
                                                            derniereDonneEnregistree = null
                                                            demarrerBatchDirectement = true
                                                            donneInitialeCartes = null
                                                            donneEnCours = numSuivant
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                "Donne $numSuivant",
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Button(
                                                                onClick = {
                                                                    derniereDonneEnregistree = null
                                                                    demarrerBatchDirectement = true
                                                                    donneInitialeCartes = null
                                                                    donneEnCours = numSuivant
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                            ) {
                                                                Text("📷 OK", fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Bouton Terminer fixe en bas ───────────────────────────
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) { Text("✅ Terminer la préparation") }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
