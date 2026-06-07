package app.resultatsbridge.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.model.DonneResultatDetail
import app.resultatsbridge.common.ui.components.*
import app.resultatsbridge.common.utils.calculerPoints
import app.resultatsbridge.organisateur.data.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Convertit un code couleur stocké (P/C/K/T/SA) en symbole affiché par les dropdowns
private fun codeVersSymbole(code: String): String = when (code) {
    "P" -> "♠"; "C" -> "♥"; "K" -> "♦"; "T" -> "♣"; else -> code
}

// Parse un code de contrat stocké ("3PX", "4SA", "0") en (niveau, couleur, insulte)
private fun parseContratCode(code: String): Triple<Int, String, String> {
    if (code.isEmpty() || code == "0") return Triple(0, "", "")
    val niveau = code.firstOrNull()?.digitToIntOrNull() ?: return Triple(0, "", "")
    var reste = code.drop(1)
    val insulte = when {
        reste.endsWith("XX") -> { reste = reste.dropLast(2); "XX" }
        reste.endsWith("X")  -> { reste = reste.dropLast(1); "X" }
        else -> ""
    }
    return Triple(niveau, codeVersSymbole(reste), insulte)
}

// Parse une entame stockée ("AP", "10K") en (carte, couleur symbol)
private fun parseEntame(code: String): Pair<String, String> {
    if (code.isEmpty()) return "" to ""
    val couleurCode = code.last().toString()
    val carte = code.dropLast(1)
    return carte to codeVersSymbole(couleurCode)
}

@Composable
fun CorrectionResultatScreen(
    idTournoi: Int,
    useLocal: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resultats by remember { mutableStateOf<List<DonneResultatDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var erreur by remember { mutableStateOf<String?>(null) }

    var resultSelectionne by remember { mutableStateOf<DonneResultatDetail?>(null) }
    var enregistrementEnCours by remember { mutableStateOf(false) }
    var messageRetour by remember { mutableStateOf<String?>(null) }

    // Chargement de la liste des résultats
    LaunchedEffect(idTournoi) {
        isLoading = true; erreur = null
        resultats = if (useLocal)
            withContext(Dispatchers.IO) { DatabaseManager.getDonneResultatDetails(context, idTournoi) }
        else
            withContext(Dispatchers.IO) { ClientNetworkUtils.recupererDetailsDonnes(idTournoi) } ?: emptyList()
        isLoading = false
        if (resultats.isEmpty()) erreur = "Aucun résultat enregistré pour ce tournoi."
    }

    // Dialog de correction
    resultSelectionne?.let { res ->
        val (nivInit, coulInit, insInit) = remember(res) { parseContratCode(res.contrat) }
        val (entCarteInit, entCoulInit) = remember(res) { parseEntame(res.carteEntame) }

        var niveau   by remember { mutableStateOf(nivInit) }
        var couleur  by remember { mutableStateOf(coulInit) }
        var insulte  by remember { mutableStateOf(insInit) }
        var declarant by remember { mutableStateOf(res.declarant) }
        var signe    by remember { mutableStateOf(res.resultatContrat) }
        var plis     by remember { mutableStateOf(res.nombrePli) }
        var entameCarte   by remember { mutableStateOf(entCarteInit) }
        var entameCouleur by remember { mutableStateOf(entCoulInit) }

        // couleur est déjà en format symbole (♠♥♦♣/SA) car il vient du dropdown
        val pts = remember(niveau, couleur, insulte, declarant, signe, plis, res.vulnerable) {
            val contratComplet = niveau > 0 && couleur.isNotEmpty() && declarant.isNotEmpty()
            val resultatCoherent = (signe == "=" && plis == 0) || (signe != "=" && plis != 0)
            if (contratComplet && resultatCoherent)
                calculerPoints(insulte, couleur, res.vulnerable, declarant, niveau, signe, plis)
            else 9999
        }

        val fondActif = MaterialTheme.colorScheme.surface

        AlertDialog(
            onDismissRequest = { resultSelectionne = null },
            title = {
                Text(
                    "Corriger — Donne N°${res.numeroDonne}",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("NS Éq.${res.equipeNS}  vs  EO Éq.${res.equipeEO}",
                        style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()

                    // Contrat
                    Text("Contrat", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(52.dp).height(44.dp)) {
                            DropdownMenuNiveau(niveau, { niveau = it }, true, fondActif)
                        }
                        Box(Modifier.width(58.dp).height(44.dp)) {
                            DropdownMenuCouleur(couleur, { couleur = it }, true, fondActif)
                        }
                        Box(Modifier.width(58.dp).height(44.dp)) {
                            DropdownMenuInsulte(insulte, { insulte = it }, true, fondActif)
                        }
                        Box(Modifier.width(74.dp).height(44.dp)) {
                            DropdownMenuDeclarant(declarant, { declarant = it }, true, fondActif)
                        }
                    }

                    // Résultat
                    Text("Résultat", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(84.dp).height(44.dp)) {
                            DropdownMenuSigne(signe, plis, { signe = it }, true, fondActif)
                        }
                        Box(Modifier.width(84.dp).height(44.dp)) {
                            DropdownMenuPlis(plis, niveau, signe, { plis = it }, true, fondActif)
                        }
                        Box(
                            Modifier.height(44.dp).padding(start = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (pts == 9999) "— pts" else "$pts pts",
                                fontWeight = FontWeight.Bold,
                                color = if (pts == 9999) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Entame
                    Text("Entame", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(66.dp).height(48.dp)) {
                            DropdownMenuEntameCarte(entameCarte, { entameCarte = it }, true, fondActif)
                        }
                        Box(Modifier.width(66.dp).height(48.dp)) {
                            DropdownMenuEntameCouleur(entameCouleur, { entameCouleur = it }, true, fondActif)
                        }
                    }

                    messageRetour?.let {
                        Text(it,
                            color = if (it.startsWith("✅")) Color(0xFF2E7D32) else Color.Red,
                            fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !enregistrementEnCours && niveau > 0 && couleur.isNotEmpty() && declarant.isNotEmpty(),
                    onClick = {
                        enregistrementEnCours = true
                        messageRetour = null
                        val contratCode = normaliserAnnonce("$niveau$couleur$insulte")
                        val entameCode  = normaliserAnnonce("$entameCarte$entameCouleur")
                        val pointsFinal = if (pts == 9999) 0 else pts
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                if (useLocal)
                                    DatabaseManager.corrigerResultat(
                                        context, idTournoi, res.numeroDonne, res.equipeNS,
                                        contratCode, declarant, signe, pointsFinal, plis, entameCode
                                    )
                                else
                                    ClientNetworkUtils.corrigerResultat(
                                        idTournoi, res.numeroDonne, res.equipeNS,
                                        contratCode, declarant, signe, pointsFinal, plis, entameCode
                                    )
                            }
                            enregistrementEnCours = false
                            if (ok) {
                                messageRetour = "✅ Correction enregistrée"
                                // Recharger la liste
                                resultats = if (useLocal)
                                    withContext(Dispatchers.IO) { DatabaseManager.getDonneResultatDetails(context, idTournoi) }
                                else
                                    withContext(Dispatchers.IO) { ClientNetworkUtils.recupererDetailsDonnes(idTournoi) } ?: emptyList()
                                resultSelectionne = null
                            } else {
                                messageRetour = "❌ Erreur lors de la correction"
                            }
                        }
                    }
                ) {
                    if (enregistrementEnCours) Text("Enregistrement...") else Text("✅ Enregistrer")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { resultSelectionne = null; messageRetour = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Écran principal
    Column(modifier = Modifier.fillMaxSize()) {
        // Barre de titre
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(Color.Red, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text("Corriger un résultat — Tournoi $idTournoi",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            erreur != null -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(erreur!!, color = Color.Red)
                }
            }
            else -> {
                Text(
                    "  Touchez un résultat pour le corriger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(resultats) { res ->
                        val signe = res.resultatContrat
                        val signeTexte = when (signe) {
                            "=" -> "="
                            "+" -> "+${res.nombrePli}"
                            "-" -> "-${res.nombrePli}"
                            else -> signe
                        }
                        val pts = if (res.pointsNS != 0) res.pointsNS else res.pointsEO
                        Card(
                            onClick = { resultSelectionne = res; messageRetour = null },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Donne N°${res.numeroDonne}",
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("NS Éq.${res.equipeNS}  vs  EO Éq.${res.equipeEO}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${res.declarant} ${res.contrat}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Résultat: $signeTexte   Score: ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${if (pts > 0) "+" else ""}$pts pts",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                pts > 0 -> Color(0xFF2E7D32)
                                                pts < 0 -> Color.Red
                                                else    -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
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
