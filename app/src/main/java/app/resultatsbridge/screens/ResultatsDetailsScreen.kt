// ÉCRAN : Détails résultats d'une donne — tableau par donne (Mitchell / Howell)
package app.resultatsbridge.screens

import android.app.Activity
import app.resultatsbridge.v2.BuildConfig
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
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
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.EcranPleinScaffold
import app.resultatsbridge.organisateur.data.DatabaseManager
import app.resultatsbridge.common.model.DonneResultatDetail
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.AnnonceJoueur
import app.resultatsbridge.common.model.ClassementManager4Equ2T21D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.floor

private const val TYPE_PAR4EQU2T21D = "par4equ2t21d"
private val HAUTEUR_LIGNE = 36.dp

private fun formatPts(v: Double): String =
    if (v == floor(v)) v.toInt().toString() else "%.1f".format(v)

private fun mouvementPourDonne(numeroDonne: Int): Int = when (numeroDonne) {
    in 1..7   -> 1
    in 8..14  -> 2
    in 15..21 -> 3
    else      -> 0
}

private fun libellesColonnesEbl(mouvement: Int): Pair<String, String> = when (mouvement) {
    1    -> Pair("Éq 1&4", "Éq 2&3")
    2    -> Pair("Éq 1&2", "Éq 3&4")
    3    -> Pair("Éq 1&3", "Éq 2&4")
    else -> Pair("Pts", "Pts")
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    modifier: Modifier,
    isHeader: Boolean = false,
    backgroundColor: Color = Color.Transparent
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .then(modifier)
            .background(backgroundColor)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = if (isHeader) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = if (isHeader) 2 else 1
        )
    }
}

// =============================================================================
// LigneDonnePar4 : affiche une paire T1+T2 en un seul item Compose
// =============================================================================
// Structure :
//   Box (hauteur = HAUTEUR_LIGNE * 2)
//   ├── Column
//   │   ├── Row T1 (hauteur = HAUTEUR_LIGNE) — toutes les colonnes SAUF EBL
//   │   └── Row T2 (hauteur = HAUTEUR_LIGNE) — toutes les colonnes SAUF EBL
//   └── Row EBL (hauteur = HAUTEUR_LIGNE * 2, aligné à droite)
//       ├── Box camp A (pleine hauteur, fond vert si > 0)
//       └── Box camp B (pleine hauteur, fond vert si > 0)
//
// Les cellules EBL sont dans un Row superposé en position absolute (Alignment.End)
// ce qui leur permet d'avoir leur propre hauteur indépendante des Row T1/T2.
// =============================================================================
@Composable
fun LigneDonnePar4(
    ligneT1: DonneResultatDetail,
    ligneT2: DonneResultatDetail?,
    idTournoi: Int,
    useLocal: Boolean,
    scope: CoroutineScope,
    context: Context,
    onSelectDonne: (Map<String, List<Carte>>, List<AnnonceJoueur>, Int, Int, Int, String, String, String, String) -> Unit,
    // Valeurs EBL précalculées (optionnel — si null, calculées ici depuis les points bruts)
    eblOverride1: Int? = null,
    eblOverride2: Int? = null
) {
    // ─────────────────────────────────────────────────────────────────────────
    // Calcul des récapitulatifs par équipes depuis les points bruts en base.
    //
    // Mouvement 1 (donnes  1- 7) :
    //   recap Éq1&4 = pointsNS(ligne où equipeNS=1) + pointsEO(ligne où equipeEO=4)
    //   recap Éq2&3 = pointsNS(ligne où equipeNS=3) + pointsEO(ligne où equipeEO=2)
    //
    // Mouvement 2 (donnes  8-14) :
    //   recap Éq1&2 = pointsNS(ligne où equipeNS=1) + pointsEO(ligne où equipeEO=2)
    //   recap Éq3&4 = pointsNS(ligne où equipeNS=4) + pointsEO(ligne où equipeEO=3)
    //
    // Mouvement 3 (donnes 15-21) :
    //   recap Éq1&3 = pointsNS(ligne où equipeNS=1) + pointsEO(ligne où equipeEO=3)
    //   recap Éq2&4 = pointsNS(ligne où equipeNS=2) + pointsEO(ligne où equipeEO=4)
    //
    // EBL(|recapA - recapB|) → au gagnant, 0 au perdant.
    // ─────────────────────────────────────────────────────────────────────────
    val mouvement = mouvementPourDonne(ligneT1.numeroDonne)
    val lignes = listOfNotNull(ligneT1, ligneT2)

    // ── Récapitulatifs par groupe d'équipes liées selon le mouvement ────────
    // Chaque groupe = 1 équipe qui joue NS + 1 équipe liée qui joue EO (autre table)
    //
    // Mouvement 1 : groupe Éq1&4  →  ptsNS_eq1 + ptsEO_eq4
    //               groupe Éq2&3  →  ptsNS_eq3 + ptsEO_eq2
    // Mouvement 2 : groupe Éq1&2  →  ptsNS_eq1 + ptsEO_eq2
    //               groupe Éq3&4  →  ptsNS_eq4 + ptsEO_eq3
    // Mouvement 3 : groupe Éq1&3  →  ptsNS_eq1 + ptsEO_eq3
    //               groupe Éq2&4  →  ptsNS_eq2 + ptsEO_eq4

    val ptsNS_eq1 = lignes.firstOrNull { it.equipeNS == 1 }?.pointsNS ?: 0
    val ptsNS_eq2 = lignes.firstOrNull { it.equipeNS == 2 }?.pointsNS ?: 0
    val ptsNS_eq3 = lignes.firstOrNull { it.equipeNS == 3 }?.pointsNS ?: 0
    val ptsNS_eq4 = lignes.firstOrNull { it.equipeNS == 4 }?.pointsNS ?: 0
    val ptsEO_eq2 = lignes.firstOrNull { it.equipeEO == 2 }?.pointsEO ?: 0
    val ptsEO_eq3 = lignes.firstOrNull { it.equipeEO == 3 }?.pointsEO ?: 0
    val ptsEO_eq4 = lignes.firstOrNull { it.equipeEO == 4 }?.pointsEO ?: 0

    // Récapitulatifs nommés par les équipes liées à chaque mouvement
    val recapEq1Eq4 = ptsNS_eq1 + ptsEO_eq4   // mouvement 1
    val recapEq2Eq3 = ptsNS_eq3 + ptsEO_eq2   // mouvement 1
    val recapEq1Eq2 = ptsNS_eq1 + ptsEO_eq2   // mouvement 2
    val recapEq3Eq4 = ptsNS_eq4 + ptsEO_eq3   // mouvement 2
    val recapEq1Eq3 = ptsNS_eq1 + ptsEO_eq3   // mouvement 3
    val recapEq2Eq4 = ptsNS_eq2 + ptsEO_eq4   // mouvement 3

    // EBL de la différence → gagnant reçoit EBL(|diff|), perdant reçoit 0
    val diff = when (mouvement) {
        1    -> recapEq1Eq4 - recapEq2Eq3
        2    -> recapEq1Eq2 - recapEq3Eq4
        3    -> recapEq1Eq3 - recapEq2Eq4
        else -> 0
    }
    val ebl1 = if (diff > 0) ClassementManager4Equ2T21D.pointsVersEbl(diff)  else 0
    val ebl2 = if (diff < 0) ClassementManager4Equ2T21D.pointsVersEbl(-diff) else 0

    // Largeur des 2 cellules EBL = proportion des weights
    // On les fixe à une largeur absolue pour qu'elles s'alignent avec l'en-tête
    val largeurEbl = 72.dp  // 2 × 36.dp (approximation, ajustable)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HAUTEUR_LIGNE * 2)
    ) {
        // ── Colonne gauche : T1 + T2 empilés ─────────────────────────────────
        // Occupe toute la largeur SAUF les 2 cellules EBL à droite
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = largeurEbl)  // laisse la place aux cellules EBL
        ) {
            // Ligne T1
            LigneSimple(
                item = ligneT1,
                idTournoi = idTournoi,
                useLocal = useLocal,
                scope = scope,
                context = context,
                onSelectDonne = onSelectDonne
            )
            // Ligne T2 (peut être null si données incomplètes)
            if (ligneT2 != null) {
                LigneSimple(
                    item = ligneT2,
                    idTournoi = idTournoi,
                    useLocal = useLocal,
                    scope = scope,
                    context = context,
                    onSelectDonne = onSelectDonne
                )
            } else {
                // Ligne vide si T2 manquante
                Box(modifier = Modifier.fillMaxWidth().height(HAUTEUR_LIGNE))
            }
        }

        // ── Cellules EBL superposées à droite, pleine hauteur ────────────────
        Row(
            modifier = Modifier
                .width(largeurEbl)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            // Cellule camp A
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(0.5.dp, Color.LightGray)
                    .background(if (ebl1 > 0) Color(0xFF81C784) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (ebl1 > 0) {
                    Text(
                        "$ebl1",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
            // Cellule camp B
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(0.5.dp, Color.LightGray)
                    .background(if (ebl2 > 0) Color(0xFF81C784) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (ebl2 > 0) {
                    Text(
                        "$ebl2",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

// =============================================================================
// LigneSimple : une ligne de données sans les cellules EBL
// =============================================================================
@Composable
private fun LigneSimple(
    item: DonneResultatDetail,
    idTournoi: Int,
    useLocal: Boolean,
    scope: CoroutineScope,
    context: Context,
    onSelectDonne: (Map<String, List<Carte>>, List<AnnonceJoueur>, Int, Int, Int, String, String, String, String) -> Unit
) {
    val nsVul = (item.vulnerable == "NS" || item.vulnerable == "T")
    val eoVul = (item.vulnerable == "EO" || item.vulnerable == "T")
    val cMod = Modifier.border(0.5.dp, Color.LightGray)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HAUTEUR_LIGNE)
            .clickable {
                scope.launch {
                    chargerEtAfficherDonne(useLocal, context, idTournoi, item, onSelectDonne)
                }
            }
    ) {
        TableCell("${item.numeroDonne}", 0.8f, cMod)
        TableCell("${item.equipeNS}", 0.5f, cMod,
            backgroundColor = if (nsVul) Color(0xFFEF5350) else Color.Transparent)
        TableCell("${item.equipeEO}", 0.5f, cMod,
            backgroundColor = if (eoVul) Color(0xFFEF5350) else Color.Transparent)
        TableCell(item.contrat, 0.8f, cMod)
        TableCell(item.declarant, 1f, cMod)
        val resText = if (item.resultatContrat == "=" || item.resultatContrat == "0")
            "égal" else "${item.resultatContrat} ${item.nombrePli}"
        TableCell(resText, 0.8f, cMod)
        TableCell(item.carteEntame, 0.8f, cMod)
        TableCell("${item.pointsNS}", 0.7f, cMod)
        TableCell("${item.pointsEO}", 0.7f, cMod)
    }
}

// =============================================================================
// ResultatsDetailsScreen
// =============================================================================
@Composable
fun ResultatsDetailsScreen(
    idTournoi: Int,
    dateTournoi: String,
    resultats: List<DonneResultatDetail>,
    OrganisateurIsJoueur: Boolean,
    useLocal: Boolean,
    typeTournoi: String = "",
    onBack: () -> Unit,
    onSelectDonne: (Map<String, List<Carte>>, List<AnnonceJoueur>, Int, Int, Int, String, String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val estPar4Equ2T21D = (typeTournoi == TYPE_PAR4EQU2T21D)

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    EcranPleinScaffold {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (BuildConfig.DEBUG) Text("[ Résultats détails équipe ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).background(Color.Red, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = "Tournoi N° $idTournoi du $dateTournoi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {

                    // ── En-tête ───────────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(Color.LightGray)
                    ) {
                        val hMod = Modifier.border(0.5.dp, Color.Gray).padding(vertical = 4.dp)
                        TableCell("Donne", 0.8f, hMod, isHeader = true)
                        TableCell("NS", 0.5f, hMod, isHeader = true)
                        TableCell("EO", 0.5f, hMod, isHeader = true)
                        TableCell("Contrat", 0.8f, hMod, isHeader = true)
                        TableCell("Déclarant", 1f, hMod, isHeader = true)
                        TableCell("Résultat", 0.8f, hMod, isHeader = true)
                        TableCell("Entame", 0.8f, hMod, isHeader = true)
                        TableCell("Score NS", 0.7f, hMod, isHeader = true)
                        TableCell("Score EO", 0.7f, hMod, isHeader = true)
                        if (estPar4Equ2T21D) {
                            // Largeur fixe 36.dp = même largeur que les cellules EBL dans LigneDonnePar4
                            Box(
                                modifier = Modifier.width(36.dp).fillMaxHeight().then(hMod),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Pts", style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier.width(36.dp).fillMaxHeight().then(hMod),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Pts", style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                                    )
                                )
                            }
                        } else {
                            TableCell("Pts NS", 0.6f, hMod, isHeader = true)
                            TableCell("Pts EO", 0.6f, hMod, isHeader = true)
                        }
                    }

                    // ── Corps ─────────────────────────────────────────────────────
                    // Structure par4equ2t21d :
                    //   Row fixe    : en-tête colonnes  (déjà affiché au-dessus)
                    //   Column scroll : tout le reste — mouvement 1, donnes, total,
                    //                   mouvement 2, donnes, total, mouvement 3...
                    // Ainsi l'en-tête reste fixe et tout le contenu scrolle ensemble.

                    if (estPar4Equ2T21D) {
                        val parMouvement = resultats
                            .groupBy { mouvementPourDonne(it.numeroDonne) }
                            .entries
                            .sortedBy { it.key }
                            .map { Pair(it.key, it.value) }

                        // ── Pour chaque mouvement : Column scrollable + Total fixe ─
                        // Structure :
                        //   for chaque mouvement :
                        //     Column(weight(1f) + verticalScroll)
                        //       ├── Row : titre mouvement + libellés équipes
                        //       └── N × LigneDonnePar4
                        //     Row fixe : Total mouvement  ← en dehors du scroll
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            for ((mouvement, lignesMvnt) in parMouvement) {
                                val (libelleNS, libelleEO) = libellesColonnesEbl(mouvement)

                                val paires = lignesMvnt
                                    .groupBy { it.numeroDonne }
                                    .entries
                                    .sortedBy { it.key }
                                    .map { it.value }

                                // Calcul du total EBL pour ce mouvement
                                var totalEbl1 = 0
                                var totalEbl2 = 0
                                for (paire in paires) {
                                    val lignes = paire
                                    val ptsNS_eq1 =
                                        lignes.firstOrNull { it.equipeNS == 1 }?.pointsNS ?: 0
                                    val ptsNS_eq2 =
                                        lignes.firstOrNull { it.equipeNS == 2 }?.pointsNS ?: 0
                                    val ptsNS_eq3 =
                                        lignes.firstOrNull { it.equipeNS == 3 }?.pointsNS ?: 0
                                    val ptsNS_eq4 =
                                        lignes.firstOrNull { it.equipeNS == 4 }?.pointsNS ?: 0
                                    val ptsEO_eq2 =
                                        lignes.firstOrNull { it.equipeEO == 2 }?.pointsEO ?: 0
                                    val ptsEO_eq3 =
                                        lignes.firstOrNull { it.equipeEO == 3 }?.pointsEO ?: 0
                                    val ptsEO_eq4 =
                                        lignes.firstOrNull { it.equipeEO == 4 }?.pointsEO ?: 0
                                    val (recapEq1, recapEq2) = when (mouvement) {
                                        1 -> Pair(ptsNS_eq1 + ptsEO_eq4, ptsNS_eq3 + ptsEO_eq2)
                                        2 -> Pair(ptsNS_eq1 + ptsEO_eq2, ptsNS_eq4 + ptsEO_eq3)
                                        3 -> Pair(ptsNS_eq1 + ptsEO_eq3, ptsNS_eq2 + ptsEO_eq4)
                                        else -> Pair(0, 0)
                                    }
                                    val diff = recapEq1 - recapEq2
                                    totalEbl1 += if (diff > 0) ClassementManager4Equ2T21D.pointsVersEbl(
                                        diff
                                    ) else 0
                                    totalEbl2 += if (diff < 0) ClassementManager4Equ2T21D.pointsVersEbl(
                                        -diff
                                    ) else 0
                                }

                                // ── Column scrollable : titre + donnes ────────────
                                // weight(1f) = chaque mouvement prend 1/3 de l'espace
                                // verticalScroll = titre + donnes scrollent ensemble
                                // jusqu'à ce que la dernière donne remonte en haut
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    // Titre mouvement
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .background(Color(0xFFBBDEFB))
                                    ) {
                                        val mMod = Modifier.border(0.5.dp, Color(0xFF1565C0))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f).fillMaxHeight()
                                                .border(0.5.dp, Color(0xFF1565C0))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                "Mouvement $mouvement",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1565C0)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.width(36.dp).fillMaxHeight()
                                                .then(mMod),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                libelleNS, style = TextStyle(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    color = Color(0xFF1565C0)
                                                )
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.width(36.dp).fillMaxHeight()
                                                .then(mMod),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                libelleEO, style = TextStyle(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    color = Color(0xFF1565C0)
                                                )
                                            )
                                        }
                                    }
                                    // N × LigneDonnePar4
                                    paires.forEach { paire ->
                                        LigneDonnePar4(
                                            ligneT1 = paire.first(),
                                            ligneT2 = paire.getOrNull(1),
                                            idTournoi = idTournoi,
                                            useLocal = useLocal,
                                            scope = scope,
                                            context = context,
                                            onSelectDonne = onSelectDonne
                                        )
                                    }
                                }

                                // ── Total FIXE : en dehors du scroll ─────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(HAUTEUR_LIGNE)
                                        .background(Color(0xFFE3F2FD))
                                ) {
                                    val tMod = Modifier.border(0.5.dp, Color(0xFF1565C0))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f).fillMaxHeight()
                                            .border(0.5.dp, Color(0xFF1565C0))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            "Total mouvement $mouvement →",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, color = Color(0xFF1565C0)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.width(36.dp).fillMaxHeight().then(tMod)
                                            .background(Color(0xFFBBDEFB)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$totalEbl1", style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.width(36.dp).fillMaxHeight().then(tMod)
                                            .background(Color(0xFFBBDEFB)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$totalEbl2", style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                }
                            }
                        }

                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // ── Mode standard inchangé ────────────────────────────
                            items(resultats) { item ->
                                val nsVulnerable =
                                    (item.vulnerable == "NS" || item.vulnerable == "T")
                                val eoVulnerable =
                                    (item.vulnerable == "EO" || item.vulnerable == "T")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .clickable {
                                            scope.launch {
                                                chargerEtAfficherDonne(
                                                    useLocal,
                                                    context,
                                                    idTournoi,
                                                    item,
                                                    onSelectDonne
                                                )
                                            }
                                        }
                                ) {
                                    val cMod = Modifier.border(0.5.dp, Color.LightGray)
                                    TableCell("${item.numeroDonne}", 0.8f, cMod)
                                    TableCell(
                                        "${item.equipeNS}", 0.5f, cMod,
                                        backgroundColor = if (nsVulnerable) Color(0xFFEF5350) else Color.Transparent
                                    )
                                    TableCell(
                                        "${item.equipeEO}", 0.5f, cMod,
                                        backgroundColor = if (eoVulnerable) Color(0xFFEF5350) else Color.Transparent
                                    )
                                    TableCell(item.contrat, 0.8f, cMod)
                                    TableCell(item.declarant, 1f, cMod)
                                    val resText =
                                        if (item.resultatContrat == "=" || item.resultatContrat == "0")
                                            "égal" else "${item.resultatContrat} ${item.nombrePli}"
                                    TableCell(resText, 0.8f, cMod)
                                    TableCell(item.carteEntame, 0.8f, cMod)
                                    TableCell("${item.pointsNS}", 0.7f, cMod)
                                    TableCell("${item.pointsEO}", 0.7f, cMod)
                                    TableCell(formatPts(item.ptsNS), 0.6f, cMod)
                                    TableCell(formatPts(item.ptsEO), 0.6f, cMod)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Utilitaire
// =============================================================================
private suspend fun chargerEtAfficherDonne(
    useLocal: Boolean,
    context: Context,
    idTournoi: Int,
    item: DonneResultatDetail,
    onSelectDonne: (Map<String, List<Carte>>, List<AnnonceJoueur>, Int, Int, Int, String, String, String, String) -> Unit
) {
    val resultatComplet = if (useLocal) {
        DatabaseManager.getDonneComplete(context, idTournoi, item.numeroDonne, item.equipeNS)
    } else {
        ClientNetworkUtils.recupererDonneComplete(idTournoi, item.numeroDonne, item.equipeNS)
    }
    if (resultatComplet != null) {
        onSelectDonne(
            resultatComplet.mains, resultatComplet.encheres,
            item.numeroDonne, item.equipeNS, item.equipeEO,
            resultatComplet.vulnerable, resultatComplet.donneur,
            resultatComplet.contrat, resultatComplet.declarant
        )
    } else {
        Toast.makeText(context, "Erreur de chargement", Toast.LENGTH_SHORT).show()
    }
}
