// ÉCRAN : Détails résultats — format 4 équipes / 2 tables / 21 donnes
package app.resultatsbridge.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import app.resultatsbridge.common.model.ClassementManager4Equ2T21D
import app.resultatsbridge.common.model.DonneResultatDetail
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.AnnonceJoueur

// =============================================================================
// ResultatsDetails4Equ2T21DScreen
// =============================================================================
// Écran dédié au tournoi "par4equ2t21d".
// Affiche un tableau de résultats par donne avec :
//   - une ligne séparatrice par mouvement
//   - 2 lignes par donne (T1 et T2) avec cellules EBL fusionnées
//   - une ligne de total par mouvement
//
// Les résultats reçus sont déjà filtrés par mouvement depuis ClientActivity :
//   mvnt 1 terminé → donnes 1-7
//   mvnt 2 terminé → donnes 1-14
//   fin tournoi    → donnes 1-21
// =============================================================================

private val HAUTEUR_LIGNE_4EQ = 36.dp

private fun mouvementPourDonne4Eq(numeroDonne: Int): Int = when (numeroDonne) {
    in 1..7   -> 1
    in 8..14  -> 2
    in 15..21 -> 3
    else      -> 0
}

private fun libellesEbl4Eq(mouvement: Int): Pair<String, String> = when (mouvement) {
    1    -> Pair("Éq 1&4", "Éq 2&3")
    2    -> Pair("Éq 1&2", "Éq 3&4")
    3    -> Pair("Éq 1&3", "Éq 2&4")
    else -> Pair("Pts",    "Pts")
}

// Calcule ebl1 et ebl2 pour une paire de lignes (T1+T2) d'une donne
private fun calculerEblPaire(
    paire: List<DonneResultatDetail>,
    mouvement: Int
): Pair<Int, Int> {
    val ptsNS_eq1 = paire.firstOrNull { it.equipeNS == 1 }?.pointsNS ?: 0
    val ptsNS_eq2 = paire.firstOrNull { it.equipeNS == 2 }?.pointsNS ?: 0
    val ptsNS_eq3 = paire.firstOrNull { it.equipeNS == 3 }?.pointsNS ?: 0
    val ptsNS_eq4 = paire.firstOrNull { it.equipeNS == 4 }?.pointsNS ?: 0
    val ptsEO_eq2 = paire.firstOrNull { it.equipeEO == 2 }?.pointsEO ?: 0
    val ptsEO_eq3 = paire.firstOrNull { it.equipeEO == 3 }?.pointsEO ?: 0
    val ptsEO_eq4 = paire.firstOrNull { it.equipeEO == 4 }?.pointsEO ?: 0
    val (recapEq1, recapEq2) = when (mouvement) {
        1    -> Pair(ptsNS_eq1 + ptsEO_eq4, ptsNS_eq3 + ptsEO_eq2)
        2    -> Pair(ptsNS_eq1 + ptsEO_eq2, ptsNS_eq4 + ptsEO_eq3)
        3    -> Pair(ptsNS_eq1 + ptsEO_eq3, ptsNS_eq2 + ptsEO_eq4)
        else -> Pair(0, 0)
    }
    val diff = recapEq1 - recapEq2
    val ebl1 = if (diff > 0) ClassementManager4Equ2T21D.pointsVersEbl(diff)  else 0
    val ebl2 = if (diff < 0) ClassementManager4Equ2T21D.pointsVersEbl(-diff) else 0
    return Pair(ebl1, ebl2)
}

// =============================================================================
// Items de la LazyColumn — sealed class pour typer chaque ligne
// =============================================================================
private sealed class Item4Eq {
    data class Separateur(val mouvement: Int, val libelleNS: String, val libelleEO: String) : Item4Eq()
    data class Paire(val ligneT1: DonneResultatDetail, val ligneT2: DonneResultatDetail?, val mouvement: Int) : Item4Eq()
    data class Total(val mouvement: Int, val totalEbl1: Int, val totalEbl2: Int) : Item4Eq()
}

@Composable
fun ResultatsDetails4Equ2T21DScreen(
    idTournoi      : Int,
    dateTournoi    : String,
    titreMouvement : String,          // ex: "Mouvement 1 (donnes 1-7)"
    resultats      : List<DonneResultatDetail>,
    useLocal       : Boolean,
    onBack         : () -> Unit,
    onSelectDonne  : (Map<String, List<Carte>>, List<AnnonceJoueur>, Int, Int, Int, String, String, String, String) -> Unit
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // Forcer orientation paysage
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val orig = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = orig ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ── Construction de la liste d'items à afficher ───────────────────────────
    // Grouper par mouvement puis par donne → liste plate d'items typés
    val items: List<Item4Eq> = remember(resultats) {
        val liste = mutableListOf<Item4Eq>()
        val parMouvement = resultats
            .groupBy { mouvementPourDonne4Eq(it.numeroDonne) }
            .entries
            .sortedBy { it.key }

        for ((mouvement, lignesMvnt) in parMouvement) {
            val (libelleNS, libelleEO) = libellesEbl4Eq(mouvement)

            // Séparateur de mouvement
            liste.add(Item4Eq.Separateur(mouvement, libelleNS, libelleEO))

            // Paires T1+T2 par donne
            val paires = lignesMvnt
                .groupBy { it.numeroDonne }
                .entries
                .sortedBy { it.key }
                .map { it.value }

            var totalEbl1 = 0
            var totalEbl2 = 0
            for (paire in paires) {
                liste.add(Item4Eq.Paire(paire.first(), paire.getOrNull(1), mouvement))
                val (e1, e2) = calculerEblPaire(paire, mouvement)
                totalEbl1 += e1
                totalEbl2 += e2
            }

            // Total du mouvement
            liste.add(Item4Eq.Total(mouvement, totalEbl1, totalEbl2))
        }
        liste
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Bandeau titre ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).background(Color.Red, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("Tournoi N° $idTournoi du $dateTournoi",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(titreMouvement,
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFF1565C0))
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {

                // ── En-tête fixe ──────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Color.LightGray)) {
                    val hMod = Modifier.border(0.5.dp, Color.Gray).padding(vertical = 4.dp)
                    TableCell("Donne",     0.8f, hMod, isHeader = true)
                    TableCell("NS",        0.5f, hMod, isHeader = true)
                    TableCell("EO",        0.5f, hMod, isHeader = true)
                    TableCell("Contrat",   0.8f, hMod, isHeader = true)
                    TableCell("Déclarant", 1f,   hMod, isHeader = true)
                    TableCell("Résultat",  0.8f, hMod, isHeader = true)
                    TableCell("Entame",    0.8f, hMod, isHeader = true)
                    TableCell("Sc. NS",    0.7f, hMod, isHeader = true)
                    TableCell("Sc. EO",    0.7f, hMod, isHeader = true)
                    // 2 colonnes EBL fixes à 36.dp
                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(hMod), contentAlignment = Alignment.Center) {
                        Text("Pts", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                    }
                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(hMod), contentAlignment = Alignment.Center) {
                        Text("Pts", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                    }
                }

                // ── LazyColumn simple — un item par élément ───────────────────
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items) { item ->
                        when (item) {

                            // ── Séparateur mouvement ──────────────────────────
                            is Item4Eq.Separateur -> {
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Color(0xFFBBDEFB))) {
                                    val mMod = Modifier.border(0.5.dp, Color(0xFF1565C0))
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()
                                        .border(0.5.dp, Color(0xFF1565C0)).padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterStart) {
                                        Text("Mouvement ${item.mouvement}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                    }
                                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(mMod), contentAlignment = Alignment.Center) {
                                        Text(item.libelleNS, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center, color = Color(0xFF1565C0)))
                                    }
                                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(mMod), contentAlignment = Alignment.Center) {
                                        Text(item.libelleEO, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center, color = Color(0xFF1565C0)))
                                    }
                                }
                            }

                            // ── Paire T1 + T2 avec cellules EBL fusionnées ────
                            is Item4Eq.Paire -> {
                                val (ebl1, ebl2) = calculerEblPaire(
                                    listOfNotNull(item.ligneT1, item.ligneT2), item.mouvement
                                )
                                LigneDonnePar4(
                                    ligneT1       = item.ligneT1,
                                    ligneT2       = item.ligneT2,
                                    idTournoi     = idTournoi,
                                    useLocal      = useLocal,
                                    scope         = scope,
                                    context       = context,
                                    onSelectDonne = onSelectDonne,
                                    eblOverride1  = ebl1,
                                    eblOverride2  = ebl2
                                )
                            }

                            // ── Total mouvement ───────────────────────────────
                            is Item4Eq.Total -> {
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Color(0xFFE3F2FD))) {
                                    val tMod = Modifier.border(0.5.dp, Color(0xFF1565C0))
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()
                                        .border(0.5.dp, Color(0xFF1565C0)).padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterEnd) {
                                        Text("Total mouvement ${item.mouvement} →",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                    }
                                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(tMod)
                                        .background(Color(0xFFBBDEFB)), contentAlignment = Alignment.Center) {
                                        Text("${item.totalEbl1}", style = TextStyle(fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                                    }
                                    Box(modifier = Modifier.width(36.dp).fillMaxHeight().then(tMod)
                                        .background(Color(0xFFBBDEFB)), contentAlignment = Alignment.Center) {
                                        Text("${item.totalEbl2}", style = TextStyle(fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
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
