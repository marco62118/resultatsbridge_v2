// ÉCRAN : Saisie enchères par joueur — grille 1♣ → 7SA, contrat + déclarant + résultat
package app.resultatsbridge.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import app.resultatsbridge.common.ui.components.SymboleSuite
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.AnnonceJoueur
import app.resultatsbridge.common.model.ContratInfo
import app.resultatsbridge.common.model.Tour

val VERT_TAPIS  = Color(0xFF2E7D32)
val VERT_BOUTON = Color(0xFF004D00)  // ✅ vert plus foncé

// =====================================================
//  ECRAN SAISIE ENCHERES JOUEUR PAR JOUEUR
// =====================================================
@Composable
fun EncheresParJoueurScreen(
    donneur: String,
    onEnregistre: (ContratInfo?) -> Unit
) {
    val joueurs      = listOf("N", "E", "S", "O")
    val nomsComplets = mapOf("N" to "Nord", "E" to "Est", "S" to "Sud", "O" to "Ouest")

    var indexJoueurActuel by remember { mutableStateOf(0) }
    val annoncesParJoueur = remember { mutableStateMapOf<String, MutableList<String>>() }
    val annoncesCourantes = remember { mutableStateListOf<String>() }

    val toutesLesAnnonces by remember {
        derivedStateOf { reconstruireOrdreChronologique(annoncesParJoueur, donneur) }
    }

    val etapeFinale = indexJoueurActuel >= joueurs.size
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VERT_TAPIS)
    ) {
        if (!etapeFinale) {
            val joueurActuel = joueurs[indexJoueurActuel]
            val nomJoueur    = nomsComplets[joueurActuel] ?: joueurActuel

            // ── Zone scrollable ───────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // ✅ "Enchères de ..."
                BandeauJoueur(nom = "Enchères de $nomJoueur")

                // Zone pavés
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(vertical = 4.dp)
                ) {
                    if (annoncesCourantes.isNotEmpty()) {
                        FlowRowPaves(annonces = annoncesCourantes.toList())
                    }
                }

                // ✅ Séparateur bien blanc
                HorizontalDivider(
                    color     = Color.White,
                    thickness = 1.5.dp
                )
                Spacer(Modifier.height(8.dp))

                // ── 5 boutons sur une ligne ───────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BoutonEnchere(
                        texte    = "Passe",
                        couleur  = VERT_BOUTON,
                        modifier = Modifier.weight(1.5f)
                    ) { annoncesCourantes.add("Passe") }

                    BoutonEnchere(
                        texte    = "X",
                        couleur  = Color.Red,
                        modifier = Modifier.weight(1f)
                    ) { annoncesCourantes.add("Contre") }

                    BoutonEnchere(
                        texte    = "XX",
                        couleur  = Color(0xFF000080),
                        modifier = Modifier.weight(1f)
                    ) { annoncesCourantes.add("Surcontre") }

                    BoutonEnchere(
                        texte    = "⌫",
                        couleur  = Color(0xFF795548),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (annoncesCourantes.isNotEmpty())
                            annoncesCourantes.removeAt(annoncesCourantes.lastIndex)
                    }

                    val estDernierJoueur = indexJoueurActuel == joueurs.lastIndex
                    val prochainNom = if (!estDernierJoueur)
                        nomsComplets[joueurs[indexJoueurActuel + 1]] ?: "" else ""
                    // ✅ Vérifier visible seulement si Ouest a saisi au moins une annonce
                    val boutonSuivantVisible = annoncesCourantes.isNotEmpty()
                    if (boutonSuivantVisible) {
                        BoutonEnchere(
                            texte    = if (estDernierJoueur) "Vérifier ▶" else "▶ $prochainNom",
                            couleur  = if (estDernierJoueur) Color(0xFF00897B) else Color(0xFF1565C0),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            annoncesParJoueur[joueurActuel] = annoncesCourantes.toMutableList()
                            val joueurSuivant = if (!estDernierJoueur) joueurs[indexJoueurActuel + 1] else null
                            annoncesCourantes.clear()
                            if (joueurSuivant != null) {
                                annoncesCourantes.addAll(annoncesParJoueur[joueurSuivant] ?: emptyList())
                            }
                            Log.i("EncheresParJoueur", "✅ $nomJoueur : ${annoncesParJoueur[joueurActuel]}")
                            indexJoueurActuel++
                        }
                    } else {
                        // ✅ Placeholder pour garder l'alignement des autres boutons
                        Spacer(modifier = Modifier.weight(1.5f))
                    }
                }

                // Grille 1♣ → 7SA
                GrilleAnnoncesLibre(onAnnonceClick = { annonce ->
                    annoncesCourantes.add(annonce)
                    Log.i("EncheresParJoueur", "➕ $nomJoueur : $annonce")
                })
            }

            // ── Corriger + Retour en bas ─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (indexJoueurActuel > 0) {
                    BoutonEnchere(
                        texte    = "◀ vérifier ${nomsComplets[joueurs[indexJoueurActuel - 1]]}",
                        couleur  = Color(0xFF795548),
                        modifier = Modifier.weight(1f)
                    ) {
                        annoncesParJoueur[joueurActuel] = annoncesCourantes.toMutableList()
                        indexJoueurActuel--
                        val joueurPrec = joueurs[indexJoueurActuel]
                        annoncesCourantes.clear()
                        annoncesCourantes.addAll(annoncesParJoueur[joueurPrec] ?: emptyList())
                        annoncesParJoueur.remove(joueurPrec)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onEnregistre(null) }.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(52.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text("Retour", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

        } else {
            EtapeFinaleEncheres(
                toutesLesAnnonces = toutesLesAnnonces,
                donneur           = donneur,
                annoncesParJoueur = annoncesParJoueur,
                onEnregistre      = onEnregistre,
                onCorrigerOuest   = {
                    indexJoueurActuel = joueurs.lastIndex
                    val joueurPrec = joueurs[joueurs.lastIndex]
                    annoncesCourantes.clear()
                    annoncesCourantes.addAll(annoncesParJoueur[joueurPrec] ?: emptyList())
                    annoncesParJoueur.remove(joueurPrec)
                }
            )
        }
    }
}

// =====================================================
//  BANDEAU JOUEUR avec dividers
// =====================================================
@Composable
fun BandeauJoueur(nom: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = Color.White,
            thickness = 1.dp
        )
        Text(
            text       = "  $nom  ",
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = Color.White,
            thickness = 1.dp
        )
    }
}

// =====================================================
//  FLOW ROW PAVÉS
// =====================================================
@Composable
fun FlowRowPaves(annonces: List<String>) {
    val espacement       = 4.dp
    val largeursMesurees = remember { mutableStateMapOf<String, Int>() }
    val density          = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val largeurDisponiblePx = constraints.maxWidth
        val espacementPx: Int   = with(density) { espacement.roundToPx() }

        val toutMesure = annonces.all { largeursMesurees.containsKey(clePave(it)) }

        if (toutMesure && largeursMesurees.isNotEmpty()) {
            val lignes          = mutableListOf<MutableList<String>>()
            var ligneCourante   = mutableListOf<String>()
            var largeurRestante = largeurDisponiblePx

            annonces.forEach { ann ->
                val lp = (largeursMesurees[clePave(ann)] ?: 0) + espacementPx
                if (largeurRestante < lp && ligneCourante.isNotEmpty()) {
                    lignes.add(ligneCourante)
                    ligneCourante   = mutableListOf()
                    largeurRestante = largeurDisponiblePx
                }
                ligneCourante.add(ann)
                largeurRestante -= lp
            }
            if (ligneCourante.isNotEmpty()) lignes.add(ligneCourante)

            Column(verticalArrangement = Arrangement.spacedBy(espacement)) {
                lignes.forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(espacement)) {
                        ligne.forEach { ann -> PaveAnnonce(ann) }
                    }
                }
            }
        } else {
            // Rendu invisible pour mesurer
            Row {
                listOf("Passe", "Contre", "Surcontre", "1♣").forEach { specimen ->
                    Box(
                        modifier = Modifier
                            .alpha(0f)
                            .onGloballyPositioned { coords ->
                                val cle = clePave(specimen)
                                if (!largeursMesurees.containsKey(cle)) {
                                    largeursMesurees[cle] = coords.size.width
                                    Log.i("FlowRowPaves", "📏 '$specimen' = ${coords.size.width} px")
                                }
                            }
                    ) { PaveAnnonce(specimen) }
                }
            }
        }
    }
}

fun clePave(ann: String): String = when {
    ann == "Passe"     -> "Passe"
    ann == "Contre"    -> "Contre"
    ann == "Surcontre" -> "Surcontre"
    else               -> "enchere"
}

// =====================================================
//  PAVÉ INDIVIDUEL
// =====================================================
@Composable
fun PaveAnnonce(annonce: String) {
    val texte       = formaterAnnonce(annonce)
    val couleurFond = when (annonce) {
        "Passe"     -> Color(0xFFE8F5E9)
        "Contre"    -> Color(0xFFFFEBEE)
        "Surcontre" -> Color(0xFFE3F2FD)
        else        -> Color.White
    }
    val couleurBord = when (annonce) {
        "Passe"     -> Color(0xFF388E3C)
        "Contre"    -> Color.Red
        "Surcontre" -> Color(0xFF1565C0)
        else        -> Color.Gray
    }
    Surface(
        color  = couleurFond,
        shape  = RoundedCornerShape(6.dp),
        border = BorderStroke(1.5.dp, couleurBord)
    ) {
        Text(
            text       = texte,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            color      = couleurAnnonce(annonce),
            maxLines   = 1
        )
    }
}

// =====================================================
//  ETAPE FINALE
// =====================================================
@Composable
fun EtapeFinaleEncheres(
    toutesLesAnnonces: List<AnnonceJoueur>,
    donneur: String,
    annoncesParJoueur: Map<String, List<String>>,
    onEnregistre: (ContratInfo?) -> Unit,
    onCorrigerOuest: () -> Unit
) {
    val nomsComplets = mapOf("N" to "Nord", "E" to "Est", "S" to "Sud", "O" to "Ouest")
    val scrollState  = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BandeauJoueur(nom = "Vérification")

            listOf("N", "E", "S", "O").forEach { pos ->
                val annonces = annoncesParJoueur[pos] ?: emptyList()
                if (annonces.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text       = "${nomsComplets[pos]} :",
                            color      = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
                            modifier   = Modifier
                                .width(52.dp)
                                .padding(top = 6.dp)
                        )
                        FlowRowPaves(annonces = annonces)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TableauEncheres(
                    encheres   = toutesLesAnnonces,
                    vulnerable = "P",
                    donneur    = donneur
                )
            }
        }

        // 3 boutons en bas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoutonEnchere(
                texte    = "◀ Corriger Ouest",
                couleur  = Color(0xFF795548),
                modifier = Modifier.weight(1.2f)
            ) { onCorrigerOuest() }

            BoutonEnchere(
                texte    = "Enregistrer ✓",
                couleur  = Color(0xFF00897B),
                modifier = Modifier.weight(1.2f)
            ) {
                val contratInfo = construireContratInfo(toutesLesAnnonces)
                Log.i("EncheresParJoueur", "📝 Enregistrement : $contratInfo")
                onEnregistre(contratInfo)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onEnregistre(null) }.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(52.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("Retour", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =====================================================
//  GRILLE LIBRE 1♣ → 7SA
// =====================================================
@Composable
fun GrilleAnnoncesLibre(onAnnonceClick: (String) -> Unit) {
    val couleurs = listOf("♣", "♦", "♥", "♠", "SA")
    val niveaux  = 1..7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        niveaux.forEach { niveau ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                couleurs.forEach { couleur ->
                    val couleurTexte = when (couleur) {
                        "♣"  -> Color.Black
                        "♦"  -> Color(0xFFB71C1C)
                        "♥"  -> Color(0xFFC2185B)
                        "♠"  -> Color.Black
                        "SA" -> Color(0xFF1565C0)
                        else -> Color.Gray
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .clickable { onAnnonceClick("$niveau$couleur") },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "$niveau",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.Black
                            )
                            Spacer(Modifier.width(3.dp))
                            SymboleSuite(symbole = couleur, color = couleurTexte, sizeDp = 16f)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================
//  BOUTON GÉNÉRIQUE
// =====================================================
@Composable
fun BoutonEnchere(
    texte    : String,
    couleur  : Color,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    Surface(
        modifier = modifier.height(44.dp).clickable { onClick() },
        color    = couleur,
        shape    = RoundedCornerShape(6.dp),
        border   = BorderStroke(1.dp, Color.Black)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text       = texte,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// =====================================================
//  UTILITAIRES
// =====================================================
fun reconstruireOrdreChronologique(
    annoncesParJoueur: Map<String, List<String>>,
    donneur: String
): List<AnnonceJoueur> {
    val ordreComplet = listOf("O", "N", "E", "S")
    val indexDonneur = ordreComplet.indexOf(donneur.uppercase()).coerceAtLeast(0)
    val ordreBridge  = (0..3).map { ordreComplet[(indexDonneur + it) % 4] }
    val result       = mutableListOf<AnnonceJoueur>()
    val maxTours     = ordreBridge.maxOf { annoncesParJoueur[it]?.size ?: 0 }
    for (tour in 0 until maxTours) {
        for (joueur in ordreBridge) {
            val annonces = annoncesParJoueur[joueur] ?: continue
            if (tour < annonces.size)
                result.add(AnnonceJoueur(joueur = joueur, annonce = annonces[tour]))
        }
    }
    return result
}

fun construireContratInfo(annonces: List<AnnonceJoueur>): ContratInfo {
    val derniereEnchere = annonces.lastOrNull {
        it.annonce != "Passe" && it.annonce != "Contre" && it.annonce != "Surcontre"
    }

    // ✅ Index de la dernière enchère réelle
    val indexDerniereEnchere = annonces.indexOfLast {
        it.annonce != "Passe" && it.annonce != "Contre" && it.annonce != "Surcontre"
    }

    // ✅ Chercher Contre/Surcontre uniquement APRÈS la dernière enchère réelle
    val annoncesApres = if (indexDerniereEnchere >= 0)
        annonces.subList(indexDerniereEnchere + 1, annonces.size)
    else
        emptyList()

    val contreActuel    = annoncesApres.any { it.annonce == "Contre" }
    val surcontreActuel = annoncesApres.any { it.annonce == "Surcontre" }

    val insulte = when {
        surcontreActuel -> "XX"
        contreActuel    -> "X"
        else            -> ""
    }

    val annonce = derniereEnchere?.annonce ?: ""
    val niveau  = annonce.firstOrNull()?.digitToIntOrNull() ?: 0
    val couleur = if (annonce.length > 1) annonce.drop(1) else ""

    fun estPartenaire(j1: String, j2: String) =
        (j1 == "N" && j2 == "S") || (j1 == "S" && j2 == "N") ||
                (j1 == "E" && j2 == "O") || (j1 == "O" && j2 == "E")

    val joueurContrat   = derniereEnchere?.joueur ?: ""
    val declarantAbbrev = if (couleur.isNotEmpty()) {
        annonces.firstOrNull { ann ->
            ann.annonce != "Passe" && ann.annonce != "Contre" && ann.annonce != "Surcontre" &&
                    ann.annonce.drop(1) == couleur &&
                    (ann.joueur == joueurContrat || estPartenaire(ann.joueur, joueurContrat))
        }?.joueur ?: joueurContrat
    } else joueurContrat

    val declarant = when (declarantAbbrev) {
        "N" -> "Nord"; "S" -> "Sud"; "E" -> "Est"; "O" -> "Ouest"; else -> ""
    }
    return ContratInfo(
        niveau = niveau,
        couleur = couleur,
        insulte = insulte,
        declarant = declarant,
        entameCarte = "",
        entameCouleur = "",
        historique = annonces.map { Tour(joueur = it.joueur, annonce = it.annonce) }
    )
}
fun formaterAnnonce(annonce: String): String = when (annonce.uppercase()) {
    "PASSE" -> "Passe"; "CONTRE" -> "X"; "SURCONTRE" -> "XX"; else -> annonce
}

fun couleurAnnonce(annonce: String): Color = when {
    annonce.contains("♥") || annonce.contains("♦") -> Color(0xFFB71C1C)
    annonce == "Contre"                             -> Color.Red
    annonce == "Surcontre"                          -> Color(0xFF000080)
    annonce == "Passe"                              -> Color(0xFF006400)
    else                                            -> Color.Black
}
