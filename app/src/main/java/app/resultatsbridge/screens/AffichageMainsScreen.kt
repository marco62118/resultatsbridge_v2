package app.resultatsbridge.screens

import android.Manifest
import app.resultatsbridge.BuildConfig
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.resultatsbridge.client.RtDetrDetector
import app.resultatsbridge.client.Yolo11Detector
import app.resultatsbridge.common.ui.components.CarteUnifiee
import app.resultatsbridge.common.ui.components.SuiteSymbol
import app.resultatsbridge.common.EcranPleinScaffold
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.JeuDeCartes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private enum class SaisieMode { MANUEL, PHOTO }
private enum class ModelePhoto { ROBOFLOW, RT_DETR, YOLO11 }

private data class DiagnosticDonne(
    val manquantes   : List<String>,           // codes absents des 52 (identifiés uniques)
    val doublonsIntra: Map<Int, List<String>>, // main → codes apparaissant 2+ fois dans cette main
    val doublonsInter: Map<String, List<Int>>, // code → indices des mains qui le portent (≥2 mains)
    val inconnus     : Map<Int, Int>,          // main → nb de "?"
    val comptes      : List<Int>               // taille de chaque main
)

private fun returnCardToBlocFn(carte: Carte, blocs: SnapshotStateList<SnapshotStateList<Carte?>>) {
    val origIndex = JeuDeCartes.toutesLesCartes.indexOfFirst {
        it.valeur == carte.valeur && it.couleur == carte.couleur
    }
    if (origIndex < 0) return
    blocs.getOrNull(origIndex / 13)?.set(origIndex % 13, carte)
}

private fun trierMainFn(main: MutableList<Carte>) {
    val ordreCouleurs = listOf("P","C","T","K")
    val ordreValeurs  = listOf("A","R","D","V","10","9","8","7","6","5","4","3","2")
    main.sortWith(compareBy({ ordreCouleurs.indexOf(it.couleur) }, { ordreValeurs.indexOf(it.valeur) }))
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers analyse
// ─────────────────────────────────────────────────────────────────────────────

private fun loadBitmapExif(file: File): Bitmap? {
    val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val exif = ExifInterface(file.absolutePath)
    val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bmp
    }
    val matrix = Matrix().apply { postRotate(rot) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private fun parseRoboflowLabel(label: String): String? {
    if (label.length < 2) return null
    val couleur = when (label.last()) {
        'C' -> "C"; 'K' -> "K"; 'P' -> "P"; 'T' -> "T"; else -> return null
    }
    val valeur = when (val v = label.dropLast(1)) {
        "A","R","D","V","10","2","3","4","5","6","7","8","9" -> v
        else -> return null
    }
    return valeur + couleur
}

// ─────────────────────────────────────────────────────────────────────────────
// gapFill : reconstruit une main de 13 cartes à partir des détections brutes.
//
// ENTRÉE : liste de Triple(code_carte, position_x, confiance) triée par position_x
//          position_x : 0.0 = gauche de l'image, 1.0 = droite
//          confiance  : score du modèle, 0.0 → 1.0 (ex: 0.87 = 87 %)
//
// ÉTAPES (dans l'ordre) :
//   1. Chevauchement (distance < 0.02) — SEULEMENT si > 13 cartes :
//      deux boîtes qui se chevauchent = même carte lue 2 fois par le modèle
//      → on garde la plus confiante, on supprime l'autre
//      (si ≤ 13 cartes, on ne touche pas : ce sont peut-être vraiment 2 cartes proches)
//   2. Code doublon à deux positions différentes :
//      • si > 13 cartes → supprimer la moins confiante (en trop)
//      • si = 13 cartes → remplacer par "?" (vraie carte non détectée à cet endroit)
//   3. Si < 13 cartes → combler les trous avec "?" proportionnellement aux espaces visuels
//
// SORTIE : liste de 13 éléments, code carte ("RP", "8T"…) ou "?"
// ─────────────────────────────────────────────────────────────────────────────
private fun gapFill(sorted: List<Triple<String, Float, Float>>): List<String> {
    if (sorted.isEmpty()) return List(13) { "?" }

    Log.d("BridgeGapFill", "Détections (${sorted.size}): ${sorted.joinToString { "${it.first}@${"%.3f".format(it.second)}(${(it.third*100).toInt()}%)" }}")

    val working = sorted.toMutableList()

    // ── Étape 1 : boîtes qui se chevauchent (distance < 0.02) ────────────────
    // Appliqué TOUJOURS (même si ≤ 13) : deux détections à la même position
    // correspondent à la même carte physique → garder la PLUS CONFIANTE.
    // Si cela ramène à < 13, gapFill complétera avec des "?".
    var i = 0
    while (i < working.size - 1) {
        val dist = working[i + 1].second - working[i].second
        if (dist < 0.02f) {
            val keepFirst = working[i].third >= working[i + 1].third
            val removed = if (keepFirst) working[i + 1] else working[i]
            Log.d("BridgeGapFill", "Chevauch. supprimé: ${removed.first}(${(removed.third*100).toInt()}%) dist=${"%.3f".format(dist)}")
            working.removeAt(if (keepFirst) i + 1 else i)
            // Ne pas incrémenter i : on revérifie la nouvelle paire (i, i+1)
        } else {
            i++
        }
    }

    // ── Étape 2 : code doublon à 2 positions différentes ─────────────────────
    // (si < 13 après étape 1, pas de doublon possible — gapFill ajoutera des "?")
    if (working.size > 13) {
        // > 13 cartes : pour chaque code en double, supprimer le moins confiant
        val best = mutableMapOf<String, Int>()   // code → index de la meilleure occurrence
        val toRemove = mutableListOf<Int>()
        for (i in working.indices) {
            val code = working[i].first
            if (code == "?") continue
            val prev = best[code]
            if (prev != null) {
                if (working[i].third > working[prev].third) { toRemove.add(prev); best[code] = i }
                else toRemove.add(i)
            } else best[code] = i
        }
        if (toRemove.isNotEmpty()) {
            Log.d("BridgeGapFill", "Doublons code supprimés (${toRemove.size}): ${toRemove.map { working[it].first }}")
            toRemove.sortedDescending().forEach { working.removeAt(it) }
        }
    } else {
        // = 13 cartes : remplacer le MOINS CONFIANT des doublons par "?"
        val best = mutableMapOf<String, Int>()   // code → index de la meilleure occurrence
        val toReplace = mutableListOf<Int>()
        for (i in working.indices) {
            val code = working[i].first
            if (code == "?") continue
            val prev = best[code]
            if (prev != null) {
                if (working[i].third > working[prev].third) { toReplace.add(prev); best[code] = i }
                else toReplace.add(i)
            } else best[code] = i
        }
        for (i in toReplace) {
            Log.d("BridgeGapFill", "Doublon ${working[i].first}(${(working[i].third*100).toInt()}%) → ? (gardé: ${(working[best[working[i].first]!!].third*100).toInt()}%)")
            working[i] = Triple("?", working[i].second, 0f)
        }
    }

    val n = working.size
    val missing = (13 - n).coerceAtLeast(0)

    if (missing == 0) {
        Log.d("BridgeGapFill", "13 → OK : ${working.map { it.first }}")
        return working.map { it.first }
    }

    if (n <= 1) {
        val result = mutableListOf<String>()
        if (n == 1) result.add(working[0].first)
        while (result.size < 13) result.add("?")
        Log.d("BridgeGapFill", "≤1 carte → rembourrage ? : $result")
        return result
    }

    // ── Étape 3 : gap-fill proportionnel ─────────────────────────────────────
    // gap[0] = espace avant la 1ère carte, gap[i] = entre cartes i-1 et i,
    // gap[n] = après la dernière. On distribue les `missing` "?" proportionnellement.
    val positions = working.map { it.second }
    val gapSizes = FloatArray(n + 1)
    gapSizes[0] = positions[0]
    for (i in 1 until n) gapSizes[i] = positions[i] - positions[i - 1]
    gapSizes[n] = 1f - positions[n - 1]
    val totalGap = gapSizes.sum().coerceAtLeast(0.0001f)

    val insertions = IntArray(n + 1)
    var assigned = 0
    for (i in gapSizes.indices) {
        val v = ((gapSizes[i] / totalGap) * missing).toInt()
        insertions[i] = v; assigned += v
    }
    var remaining = missing - assigned
    gapSizes.indices.sortedByDescending { gapSizes[it] }.forEach { i ->
        if (remaining-- > 0) insertions[i]++
    }

    Log.d("BridgeGapFill", "Missing=$missing, gaps=${gapSizes.map { "%.3f".format(it) }}, insertions=${insertions.toList()}")

    val result = mutableListOf<String>()
    repeat(insertions[0]) { result.add("?") }
    result.add(working[0].first)
    for (i in 1 until n) {
        repeat(insertions[i]) { result.add("?") }
        result.add(working[i].first)
    }
    repeat(insertions[n]) { result.add("?") }
    Log.d("BridgeGapFill", "Résultat (${result.size}): $result")
    return result
}

private fun pivoter90CCW(bmp: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(-90f) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private fun pivoter90CW(bmp: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

// ─────────────────────────────────────────────────────────────────────────────
// analyserRoboflow : envoie la photo au cloud Roboflow et reçoit les détections.
//
// Fonctionnement :
//   1. Redimensionne l'image à max 640 px de large (limite de l'API)
//   2. Encode en JPEG base64 et envoie en POST
//   3. Parse le JSON de réponse : chaque "prediction" donne le label de la carte,
//      sa position x au centre de la boîte (normalisée 0→1) et le score de confiance
//   4. Trie par position x (gauche → droite = ordre des cartes dans la main)
//   5. confMap = pour chaque code carte, garde la confiance MAX (si doublon)
//   6. Appelle gapFill pour obtenir exactement 13 codes
//
// RETOUR : (liste 13 codes, map code→confiance)
// En cas d'erreur réseau ou HTTP ≠ 200 : retourne 13 "?"
//
// Pour changer de modèle : modifier l'URL ligne "val url = URL(...)"
//   format : https://detect.roboflow.com/<nom-projet>/<version>?api_key=<clé>
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun analyserRoboflow(bmp: Bitmap): Pair<List<String>, Map<String, Float>> = withContext(Dispatchers.IO) {
    try {
        val maxW = 640
        val scaled = if (bmp.width > maxW) {
            val r = maxW.toFloat() / bmp.width
            Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * r).toInt(), true)
        } else bmp
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        // ← changer ici pour tester un autre modèle Roboflow
        val url = URL("https://detect.roboflow.com/cartesbridgev4-4lzeb/9?api_key=V53Tf7oqdPXcCtcdp5y3")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true; connectTimeout = 15000; readTimeout = 15000
        }
        conn.outputStream.bufferedWriter().use { it.write(b64) }
        if (conn.responseCode != 200) return@withContext List(13) { "?" } to emptyMap()

        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val preds = json.getJSONArray("predictions")
        val imgW  = json.optJSONObject("image")?.optDouble("width", 640.0) ?: 640.0
        val dets  = (0 until preds.length()).mapNotNull { i ->
            val p = preds.getJSONObject(i)
            val code = parseRoboflowLabel(p.getString("class")) ?: return@mapNotNull null
            Triple(code, (p.getDouble("x") / imgW).toFloat(), p.getDouble("confidence").toFloat())
        }.sortedBy { it.second }
        // Pour chaque code, garde la confiance la plus haute (utile si même carte vue 2×)
        val confMap = dets.groupBy { it.first }.mapValues { (_, v) -> v.maxOf { it.third } }
        gapFill(dets) to confMap  // on passe les triples (code, position, confiance)
    } catch (e: Exception) {
        List(13) { "?" } to emptyMap()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// analyserRtDetr : analyse avec le modèle local RT-DETR (ONNX, embarqué dans l'APK).
//
// Plus rapide que Roboflow (pas de réseau), fonctionne hors ligne.
// detector.detect(bmp) retourne une liste de Triple(code, position_x, confiance).
// Même pipeline que Roboflow : tri par x → gapFill → 13 codes.
// ─────────────────────────────────────────────────────────────────────────────
private fun analyserDetectionsLocales(dets: List<Triple<String, Float, Float>>): Pair<List<String>, Map<String, Float>> {
    val confMap = dets.associate { it.first to it.third }
    return gapFill(dets) to confMap
}
private fun analyserRtDetr(detector: RtDetrDetector, bmp: Bitmap) = analyserDetectionsLocales(detector.detect(bmp))
private fun analyserYolo11(detector: Yolo11Detector, bmp: Bitmap)  = analyserDetectionsLocales(detector.detect(bmp))

private fun formatCodeCarte(code: String): String {
    if (code.length < 2) return code
    val sym = when (code.last()) { 'P' -> "♠"; 'C' -> "♥"; 'K' -> "♦"; 'T' -> "♣"; else -> "?" }
    return "${code.dropLast(1)}$sym"
}

private fun buildTextAvecSymboles(text: String): AnnotatedString = buildAnnotatedString {
    for (ch in text) {
        when (ch) {
            '♠' -> withStyle(SpanStyle(color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)) { append(ch) }
            '♣' -> withStyle(SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) { append(ch) }
            '♥' -> withStyle(SpanStyle(color = Color(0xFFEF9A9A), fontWeight = FontWeight.Bold)) { append(ch) }
            '♦' -> withStyle(SpanStyle(color = Color(0xFFFFCC80), fontWeight = FontWeight.Bold)) { append(ch) }
            else -> append(ch)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// diagnostiquerDonne : photographie l'état des 4 mains et détecte tous les problèmes.
//
// C'est la fonction centrale du moteur de correction. Elle est appelée à chaque
// recomposition (chaque fois qu'une carte est modifiée) et produit un snapshot
// complet que les autres fonctions (calculerErreursDonne, calculerSuggestionsDonne,
// calculerSuspectsDonne) utilisent ensuite sans recalculer.
//
// ENTRÉE : cartesCodes[0..3] = les 4 mains (Nord=0, Est=1, Sud=2, Ouest=3)
//          Chaque case est soit un code valide ("RP", "8T"…) soit "?" si inconnu.
//
// ── Ce que calcule chaque variable ──────────────────────────────────────────
//
//   identifiees[i] = codes valides de la main i (on exclut "?" et codes trop courts)
//   ex: Nord a ["RP","8T","?","3C"] → identifiees[0] = ["RP","8T","3C"]
//
//   presentes = union de toutes les cartes identifiées dans les 4 mains
//   ex: {"RP","8T","3C", "AC","2P", ...}
//
//   manquantes = cartes des 52 qui ne sont dans AUCUNE main
//   ex: si 5♣ n'apparaît nulle part → manquantes contient "5T"
//   ⚠ Ce sont les CANDIDATES pour corriger les "?" et les doublons :
//     si on retire un doublon, la vraie carte manquante est dans cette liste.
//
//   doublonsIntra = cartes présentes 2× DANS LA MÊME main
//   Structure : Map<indiceMain, List<codeCarte>>
//   ex: RP détecté 2 fois dans Nord (indice 0) → {0: ["RP"]}
//   Comment : on groupe les codes de chaque main, on filtre ceux avec >1 occurrence.
//
//   occMap = pour chaque code, liste des indices de mains qui le contiennent
//   ex: "RP" est dans Nord (0) et Est (1) → occMap["RP"] = [0, 1]
//   On utilise .distinct() pour ne compter chaque main qu'une fois même si la carte
//   y est en doublon intra (ce doublon est déjà traité séparément).
//
//   doublonsInter = cartes présentes dans au moins 2 MAINS DIFFÉRENTES
//   Structure : Map<codeCarte, List<indiceMain>>
//   ex: "RP" dans Nord et Est → {"RP": [0, 1]}
//
//   inconnus[i] = nombre de "?" dans la main i
//   ex: Nord a 2 "?" → inconnus[0] = 2
//
//   comptes[i] = taille totale de la main i (cartes valides + "?")
//   ex: Nord a 12 cartes valides + 1 "?" → comptes[0] = 13 ✓
//   ex: Est a 14 cartes → comptes[1] = 14 ✗ (erreur : doublon non nettoyé)
//
// RETOUR : DiagnosticDonne regroupant tous ces champs pour la suite du pipeline
// ─────────────────────────────────────────────────────────────────────────────
private fun diagnostiquerDonne(cartesCodes: List<out List<String>>): DiagnosticDonne {
    // Filtrer les "?" pour ne travailler qu'avec les codes identifiés
    val identifiees = List(4) { i -> cartesCodes[i].filter { it != "?" && it.length >= 2 } }

    // Toutes les cartes identifiées parmi les 4 mains (comme un set pour recherche rapide)
    val presentes = identifiees.flatten().toSet()

    // Cartes du jeu de 52 absentes de toutes les mains = candidates pour les corrections
    val manquantes = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in presentes }

    // Doublons intra-main : même code 2× dans la même main
    val doublonsIntra = (0..3).associate { i ->
        i to identifiees[i].groupBy { it }.filter { it.value.size > 1 }.keys.toList()
    }.filter { it.value.isNotEmpty() }

    // Doublons inter-mains : même code dans des mains différentes
    // On construit une map code → [indices des mains qui le contiennent]
    val occMap = mutableMapOf<String, MutableList<Int>>()
    for (i in 0..3) {
        identifiees[i].distinct()  // .distinct() : si RP est 2× dans Nord, on l'ajoute une seule fois
            .forEach { c -> occMap.getOrPut(c) { mutableListOf() }.add(i) }
    }
    val doublonsInter = occMap.filter { it.value.size > 1 }.mapValues { it.value.toList() }

    // Nombre de "?" par main et taille totale de chaque main
    val inconnus = (0..3).associate { i -> i to cartesCodes[i].count { it == "?" || it.length < 2 } }
    val comptes  = (0..3).map { cartesCodes[it].size }

    return DiagnosticDonne(manquantes, doublonsIntra, doublonsInter, inconnus, comptes)
}

// ─────────────────────────────────────────────────────────────────────────────
// calculerErreursDonne : liste TOUS les problèmes détectés, sans suggestion de correction.
//
// Utilisée pour :
//   • Le Logcat ([ROUGE]) → debug pendant les tests
//   • La section ATTENTE du BatchScreen (aperçu avant d'entrer en CORRECTION)
//
// Différence avec calculerSuggestionsDonne :
//   calculerErreursDonne = constat ("3C en double dans Nord ET Est")
//   calculerSuggestionsDonne = action  ("Remplacer 3♣ de Nord par 5♣")
//
// ── Les 5 types d'erreurs signalées ─────────────────────────────────────────
//
//   1. Compte ≠ 13 : la main n'a pas exactement 13 cartes
//      ex: "Nord : 14/13 cartes" → une carte en trop (doublon non nettoyé par gapFill)
//      ex: "Est : 11/13 cartes"  → 2 cartes manquantes (photo ratée)
//
//   2. Doublon intra : même carte 2× dans la même main
//      ex: "R♠ détecté 2× dans Nord"
//      Note : avec la version actuelle de gapFill, ce cas ne devrait plus arriver
//      pour les doublons de position (étape 1), mais peut survenir si le modèle
//      détecte la même carte à 2 positions éloignées (> 0.02).
//
//   3. Doublon inter : même carte dans 2 mains différentes
//      ex: "R♠ en double : Nord & Est"
//      C'est le cas le plus fréquent → le modèle a lu la même vraie carte
//      dans les deux photos (cartes proches sur la table, mauvais cadrage).
//
//   4. Cartes inconnues [?] : cases non identifiées dans une main
//      ex: "Nord : 2 carte(s) [?]"
//      Chaque "?" sera affiché en orange dans la grille.
//
//   5. Cartes manquantes des 52 : des cartes du jeu complet n'apparaissent nulle part
//      ex: "Manquante(s) des 52 : V♥, 8♦"
//      C'est souvent la conséquence d'un doublon (la carte qui devrait être là
//      à la place du doublon n'a pas été détectée).
//
// ⚠ Ces erreurs ne sont PAS affichées directement à l'opérateur (trop techniques).
//    Elles servent au Logcat et au comptage. L'opérateur voit les SUGGESTIONS.
// ─────────────────────────────────────────────────────────────────────────────
private fun calculerErreursDonne(diag: DiagnosticDonne, joueurs: List<String>): List<String> {
    val errors = mutableListOf<String>()

    // Erreur 1 : compte ≠ 13
    for (i in 0..3) {
        if (diag.comptes[i] != 13)
            errors.add("${joueurs[i]} : ${diag.comptes[i]}/13 cartes")
    }

    // Erreur 2 : doublon intra-main (même carte 2× dans la même main)
    for ((i, codes) in diag.doublonsIntra)
        codes.forEach { c -> errors.add("${formatCodeCarte(c)} détecté 2× dans ${joueurs[i]}") }

    // Erreur 3 : doublon inter-mains (même carte dans 2 mains différentes)
    for ((c, mains) in diag.doublonsInter)
        errors.add("${formatCodeCarte(c)} en double : ${mains.joinToString(" & ") { joueurs[it] }}")

    // Erreur 4 : cases inconnues "?"
    for (i in 0..3) {
        val n = diag.inconnus[i] ?: 0
        if (n > 0) errors.add("${joueurs[i]} : $n carte(s) [?]")
    }

    // Erreur 5 : cartes absentes des 52
    if (diag.manquantes.isNotEmpty())
        errors.add("Manquante(s) des 52 : ${diag.manquantes.joinToString(", ") { formatCodeCarte(it) }}")

    return errors
}

// ─────────────────────────────────────────────────────────────────────────────
// calculerSuggestionsDonne : traduit le diagnostic en actions concrètes.
//
// PRIORITÉ des actions (ordre d'importance) :
//   1. Mains avec trop de "?" (>= SEUIL) → photo ratée, demander de recommencer
//   2. Doublons dans la même main          → supprimer (si >13) ou remplacer (si 13)
//   3. Même carte dans 2 mains différentes → corriger la main la moins confiante
//   4. "?" avec des candidates connues     → identifier la carte manquante
//   5. Compte incorrect sans explication   → reprendre la photo
//
// Pour les doublons INTER-mains, on choisit quelle main corriger :
//   → Si on a les confidences des 2 détections : on corrige la moins confiante
//     (le modèle était moins sûr de lui = plus susceptible d'être faux)
//   → Sinon : on corrige la main qui n'a pas 13 cartes (si une des deux en a 14)
//   → Par défaut : la première main dans la liste
//
// "candidats" = cartes absentes des 52 = celles qui pourraient remplacer l'erreur
//   On affiche au plus 3 candidats pour ne pas surcharger l'affichage.
// ─────────────────────────────────────────────────────────────────────────────
private const val SEUIL_MAUVAISE_PHOTO = 5  // >= 5 "?" dans une main = photo inutilisable

private fun calculerSuggestionsDonne(
    diag: DiagnosticDonne,
    joueurs: List<String>,
    confidences: List<out Map<String, Float>>
): List<String> {
    val actions = mutableListOf<String>()

    // ── 1. Photos inutilisables (trop de "?") ────────────────────────────────
    // Si une main a 5 "?" ou plus, on ne peut pas corriger à la main :
    // le modèle a raté plus de la moitié des cartes → reprendre la photo.
    // Les autres erreurs éventuelles dans cette main sont ignorées.
    val mauvaisesPhotos = (0..3).filter { (diag.inconnus[it] ?: 0) >= SEUIL_MAUVAISE_PHOTO }.toSet()
    for (i in mauvaisesPhotos) {
        val nbQ = diag.inconnus[i] ?: 0
        actions.add("${joueurs[i]} : reprendre la photo ($nbQ ? ) — vérifier rotation 📷")
    }
    // Si TOUTES les mains photographiées sont mauvaises, inutile d'aller plus loin
    if (mauvaisesPhotos.size == (0..3).count { diag.comptes[it] > 0 }) return actions

    // Cartes candidates pour les remplacements (absentes des 52 cartes connues)
    fun candidats(): String = when {
        diag.manquantes.isEmpty() -> ""
        diag.manquantes.size == 1 -> " par ${formatCodeCarte(diag.manquantes[0])}"
        else -> " par ${diag.manquantes.take(3).joinToString(" ou ") { formatCodeCarte(it) }}"
    }

    // ── 2. Doublons intra-main (même carte 2× dans la même main) ─────────────
    // Ex: RP détecté 2× dans Nord.
    //   • Si Nord a 14 cartes : l'une des deux est fausse → supprimer
    //   • Si Nord a 13 cartes : le modèle a lu une vraie carte comme RP → remplacer
    for ((i, codes) in diag.doublonsIntra) {
        if (i in mauvaisesPhotos) continue
        codes.forEach { code ->
            if (diag.comptes[i] > 13)
                actions.add("Supprimer un ${formatCodeCarte(code)} de ${joueurs[i]}")
            else
                actions.add("Remplacer ${formatCodeCarte(code)} de ${joueurs[i]}${candidats()}")
        }
    }

    // ── 3. Doublons inter-mains (même carte dans 2 mains différentes) ─────────
    // Ex: RP dans Nord ET Est. La vraie RP est dans l'une des deux, l'autre est fausse.
    // On choisit la main à corriger selon la confiance du modèle.
    for ((code, mains) in diag.doublonsInter) {
        val mainsValides = mains.filter { it !in mauvaisesPhotos }
        if (mainsValides.isEmpty()) continue
        val confs = mainsValides.mapNotNull { idx -> confidences[idx][code]?.let { idx to it } }
        val mainAFixer = when {
            // Si on a la confiance pour toutes les mains → corriger la moins confiante
            confs.size == mainsValides.size -> confs.minByOrNull { it.second }!!.first
            // Sinon → corriger celle qui a un mauvais compte (ex: 14 cartes)
            else -> mainsValides.firstOrNull { diag.comptes[it] != 13 } ?: mainsValides[0]
        }
        if (diag.comptes[mainAFixer] > 13)
            actions.add("Supprimer ${formatCodeCarte(code)} de ${joueurs[mainAFixer]}")
        else
            actions.add("Remplacer ${formatCodeCarte(code)} de ${joueurs[mainAFixer]}${candidats()}")
    }

    // ── 4. "?" avec candidates identifiables ──────────────────────────────────
    // Les "?" restants (peu nombreux) peuvent être identifiés si on sait
    // quelles cartes manquent du jeu complet.
    val mainsAvecQ = (0..3).filter { it !in mauvaisesPhotos && (diag.inconnus[it] ?: 0) > 0 }
    for (i in mainsAvecQ) {
        val cand = candidats()
        if (cand.isNotEmpty())
            actions.add("Remplacer ? de ${joueurs[i]}$cand")
        else
            actions.add("Identifier carte(s) inconnue(s) de ${joueurs[i]}")
    }

    // ── 5. Compte incorrect sans explication connue ───────────────────────────
    // Une main n'a pas 13 cartes, mais pas de doublon qui l'explique :
    // le modèle a sauté des cartes sans laisser de "?". Reprendre la photo.
    for (i in 0..3) {
        if (i in mauvaisesPhotos) continue
        val nb = diag.comptes[i]
        if (nb > 0 && nb != 13 && !diag.doublonsIntra.containsKey(i) &&
            diag.doublonsInter.none { it.value.contains(i) } && (diag.inconnus[i] ?: 0) == 0)
            actions.add("${joueurs[i]} : ${nb}/13 — vérifier la photo")
    }

    return actions
}

// ─────────────────────────────────────────────────────────────────────────────
// calculerSuspectsDonne : détermine quelles cartes afficher en orange à l'écran.
//
// Une carte est "suspecte" (fond orange dans la grille) si :
//   • Elle est en doublon dans la même main (les 2 occurrences sont oranges)
//   • Elle est en doublon inter-mains ET est dans la main la moins confiante
//     (la main la plus confiante garde sa couleur normale car elle est probablement juste)
//   • C'est un "?" (carte non identifiée)
//
// RETOUR : liste de 4 ensembles d'indices (un par joueur)
//          ex: suspects[0] = {2, 7} → les cartes aux positions 2 et 7 de Nord sont oranges
// ─────────────────────────────────────────────────────────────────────────────
private fun calculerSuspectsDonne(
    diag: DiagnosticDonne,
    cartesCodes: List<out List<String>>,
    confidences: List<out Map<String, Float>>
): List<Set<Int>> {
    val suspects = List(4) { mutableSetOf<Int>() }
    // Doublons intra : les 2 occurrences sont toutes les deux oranges
    for ((i, codes) in diag.doublonsIntra)
        codes.forEach { code -> cartesCodes[i].forEachIndexed { idx, c -> if (c == code) suspects[i].add(idx) } }
    // Doublons inter : orange dans toutes les mains SAUF la plus confiante
    // (la plus confiante est probablement la bonne → elle garde sa couleur)
    for ((code, mains) in diag.doublonsInter) {
        val confs  = mains.mapNotNull { idx -> confidences[idx][code]?.let { idx to it } }
        val maxIdx = if (confs.size == mains.size) confs.maxByOrNull { it.second }?.first else null
        for (i in mains) if (i != maxIdx)
            cartesCodes[i].forEachIndexed { idx, c -> if (c == code) suspects[i].add(idx) }
    }
    // "?" : toujours orange (carte non identifiée)
    for (i in 0..3) cartesCodes[i].forEachIndexed { idx, c -> if (c == "?" || c.length < 2) suspects[i].add(idx) }
    return suspects.map { it.toSet() }
}

// Sauvegarde provisoire dans Pictures/BridgeCartes pour constituer un dataset
private fun sauvegarderPhotoDataset(context: android.content.Context, file: java.io.File, nomJoueur: String) {
    try {
        val nom = "bridge_${nomJoueur}_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nom)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BridgeCartes")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> file.inputStream().copyTo(os) } }
        } else {
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BridgeCartes")
            dir.mkdirs()
            file.copyTo(java.io.File(dir, nom), overwrite = true)
        }
    } catch (_: Exception) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Entrée publique — mode batch (4 photos → analyse → correction)
// ─────────────────────────────────────────────────────────────────────────────
private enum class EtapeBatch { CAPTURE, ATTENTE, CORRECTION }

@Composable
fun AffichageMainsBatchScreen(
    numeroDonne: Int,
    demarrerDirectement: Boolean = false,
    initialModele: String = "RT_DETR",
    initialRotation90: Boolean = false,
    initialCartes: List<List<String>>? = null,
    onRetour: () -> Unit,
    onEnregistrer: (List<List<Carte>>) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val joueurs = listOf("Nord", "Est", "Sud", "Ouest")

    var etape          by remember { mutableStateOf(if (initialCartes != null) EtapeBatch.CORRECTION else EtapeBatch.CAPTURE) }
    var modele         by remember { mutableStateOf(when (initialModele) { "YOLO11" -> ModelePhoto.YOLO11; "ROBOFLOW" -> ModelePhoto.ROBOFLOW; else -> ModelePhoto.RT_DETR }) }
    var modeleExpanded by remember { mutableStateOf(false) }
    var rotation90     by remember { mutableStateOf(initialRotation90) }
    var rotation90CW   by remember { mutableStateOf(false) }

    var joueurCourant     by remember { mutableStateOf(0) }
    var cameraOuverte     by remember { mutableStateOf(false) }
    var cameraAnnulee     by remember { mutableStateOf(false) }
    var triggerAutoCamera by remember { mutableStateOf(0) }
    val analyseTerminee = remember { if (initialCartes != null) mutableStateListOf(true, true, true, true) else mutableStateListOf(false, false, false, false) }

    val photoFiles   = remember { mutableStateListOf<File?>(null, null, null, null) }
    val cartesCodes  = remember { List(4) { i -> mutableStateListOf<String>().also { list -> initialCartes?.getOrNull(i)?.forEach { list.add(it) } } } }
    val confidences  = remember { List(4) { mutableStateMapOf<String, Float>() } }

    // État correction
    var joueurActif     by remember { mutableStateOf(0) }
    var selectedCardIdx by remember { mutableStateOf(-1) }
    var kbValue         by remember { mutableStateOf("") }
    var kbSuit          by remember { mutableStateOf("") }
    var alerteMsg        by remember { mutableStateOf<String?>(null) }
    var forceNewCode     by remember { mutableStateOf<String?>(null) }
    var forceAvert       by remember { mutableStateOf("") }
    var correctionAutoMsgs    by remember { mutableStateOf<List<String>>(emptyList()) }
    var autoCorrigesCodes by remember { mutableStateOf(emptySet<String>()) }
    var avertissementsCapture by remember { mutableStateOf<List<String>>(emptyList()) }
    var captureManuelle       by remember { mutableStateOf(false) }

    var tempsDebut            by remember { mutableStateOf(0L) }
    var dureeMsAnalyse     by remember { mutableStateOf(0L) }

    val scrollState   = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardHeightDp = (screenWidthDp / 13f).coerceAtLeast(22f) * 2.2f
    val density = LocalDensity.current

    // Correction par élimination : si N "?" = N cartes manquantes dans la même main → auto-identification
    fun autoCorrigerParElimination(): String? {
        val tousPresents = (0..3).flatMap { cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }.toSet()
        val manquantes = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in tousPresents }
        val pointsInterro = (0..3).flatMap { idx ->
            cartesCodes[idx].mapIndexedNotNull { i, c -> if (c == "?" || c.length < 2) (idx to i) else null }
        }
        if (pointsInterro.isEmpty() || manquantes.size != pointsInterro.size) return null
        val mainsDistinctes = pointsInterro.map { it.first }.toSet()
        if (mainsDistinctes.size != 1) return null  // "?" dans plusieurs mains → ambigu
        val hIdx = mainsDistinctes.first()
        val list = cartesCodes[hIdx].toMutableList()
        val positionsRemplies = mutableListOf<Int>()
        var mIdx = 0
        for (i in list.indices) {
            if ((list[i] == "?" || list[i].length < 2) && mIdx < manquantes.size) {
                list[i] = manquantes[mIdx++]
                positionsRemplies.add(i)
            }
        }
        // Pas de tri — la carte reste à sa position physique sur la table
        cartesCodes[hIdx].clear()
        cartesCodes[hIdx].addAll(list)
        autoCorrigesCodes = autoCorrigesCodes + manquantes.toSet()
        return if (manquantes.size == 1)
            "✅ ${formatCodeCarte(manquantes[0])} auto-identifiée dans ${joueurs[hIdx]}"
        else
            "✅ ${manquantes.size} cartes auto-identifiées dans ${joueurs[hIdx]} : ${manquantes.joinToString(", ") { formatCodeCarte(it) }}"
    }

    // Suppression automatique d'un doublon inter-mains quand une main à 13 l'a déjà
    fun autoCorrigerDoublons(): List<String> {
        val msgs = mutableListOf<String>()
        val occMap = mutableMapOf<String, MutableList<Int>>()
        for (i in 0..3) cartesCodes[i].filter { it != "?" && it.length >= 2 }.forEach { c -> occMap.getOrPut(c) { mutableListOf() }.add(i) }
        for ((code, mains) in occMap.filter { it.value.size > 1 }) {
            val mainsD = mains.distinct()
            val mains13   = mainsD.filter { cartesCodes[it].size == 13 }
            val mainsPlus = mainsD.filter { cartesCodes[it].size != 13 }
            if (mains13.size == 1 && mainsPlus.isNotEmpty()) {
                for (idx in mainsPlus) {
                    val list = cartesCodes[idx].toMutableList()
                    val pos = list.indexOfFirst { it == code }
                    if (pos >= 0) { list.removeAt(pos); cartesCodes[idx].clear(); cartesCodes[idx].addAll(list) }
                }
                val from = mainsPlus.joinToString(" et ") { joueurs[it] }
                msgs.add("✅ ${formatCodeCarte(code)} supprimé de $from (déjà dans ${joueurs[mains13[0]]})")
            }
        }
        return msgs
    }

    // Erreur unique : exactement 1 code en doublon inter-mains (2 mains à 13) + 1 carte manquante
    // → remplacer l'occurrence la moins confiante par la carte manquante (fond orange)
    // Retourne le message ET la position finale pour que l'appelant puisse focaliser le slot.
    fun autoCorrigerErreurUnique(): Pair<String, Pair<Int,Int>>? {
        val tousPresents = (0..3).flatMap { cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }
        val manquantes = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in tousPresents.toSet() }
        if (manquantes.size != 1) return null
        val manquante = manquantes[0]
        val occMap = mutableMapOf<String, MutableList<Int>>()
        for (i in 0..3) cartesCodes[i].filter { it != "?" && it.length >= 2 }.forEach { c -> occMap.getOrPut(c) { mutableListOf() }.add(i) }
        val doublonEntry = occMap.entries.firstOrNull { e ->
            e.value.distinct().size == 2 && e.value.distinct().all { idx -> cartesCodes[idx].size == 13 }
        } ?: return null
        if (occMap.values.count { it.distinct().size > 1 } != 1) return null
        val (codeDoublon, mains) = doublonEntry
        val mainsD = mains.distinct()
        val confA = confidences[mainsD[0]][codeDoublon] ?: 0f
        val confB = confidences[mainsD[1]][codeDoublon] ?: 0f
        val mainsCorrige = if (confA <= confB) mainsD[0] else mainsD[1]
        val list = cartesCodes[mainsCorrige].toMutableList()
        val pos = list.indexOfFirst { it == codeDoublon }
        if (pos < 0) return null
        list[pos] = manquante
        // Pas de tri — la carte remplacée reste à sa position physique sur la table
        cartesCodes[mainsCorrige].clear()
        cartesCodes[mainsCorrige].addAll(list)
        autoCorrigesCodes = autoCorrigesCodes + manquante
        return "✅ ${formatCodeCarte(codeDoublon)} → ${formatCodeCarte(manquante)} dans ${joueurs[mainsCorrige]} (erreur unique)" to (mainsCorrige to pos)
    }

    // Passage automatique en CORRECTION quand toutes les analyses sont terminées
    LaunchedEffect(analyseTerminee[0], analyseTerminee[1], analyseTerminee[2], analyseTerminee[3]) {
        if (etape == EtapeBatch.ATTENTE && analyseTerminee.all { it }) {
            val msgs = mutableListOf<String>()
            autoCorrigerParElimination()?.let { msgs.add(it) }
            msgs.addAll(autoCorrigerDoublons())
            autoCorrigerErreurUnique()?.let { (msg, _) -> msgs.add(msg) }
            correctionAutoMsgs = msgs
            if (tempsDebut > 0L) dureeMsAnalyse = System.currentTimeMillis() - tempsDebut
            // Logcat partiel — cartes + corrections auto (erreurs/suggestions loggées dans CORRECTION)
            val tag = "BridgeCorrection"
            Log.d(tag, "=== CORRECTION donne $numeroDonne ===")
            if (msgs.isEmpty()) Log.d(tag, "[BLEU] Aucune correction auto")
            else msgs.forEach { m -> Log.d(tag, "[BLEU] $m") }
            joueurs.forEachIndexed { i, nom ->
                val conf = cartesCodes[i].filter { c -> c != "?" && c.length >= 2 }.mapNotNull { c -> confidences[i][c] }
                val minPct = conf.minOrNull()?.let { c -> "${(c * 100).toInt()}%" } ?: "n/a"
                Log.d(tag, "  $nom (${cartesCodes[i].size}/13 — min $minPct): ${cartesCodes[i].joinToString()}")
            }
            val tousPresentsLog = (0..3).flatMap { cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }.toSet()
            val manquantesLog = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in tousPresentsLog }
            if (manquantesLog.isNotEmpty()) Log.d(tag, "  Manquantes des 52 : ${manquantesLog.joinToString()}")
            else Log.d(tag, "  52 cartes : aucune manquante")
            etape = EtapeBatch.CORRECTION
        }
    }

    // Avertissements doublons inter-mains dès la 2ème analyse terminée (CAPTURE / ATTENTE)
    LaunchedEffect(analyseTerminee[0], analyseTerminee[1], analyseTerminee[2], analyseTerminee[3]) {
        val analysed = (0..3).filter { analyseTerminee[it] }
        if (analysed.size < 2) { avertissementsCapture = emptyList(); return@LaunchedEffect }
        val occMap = mutableMapOf<String, MutableList<Int>>()
        analysed.forEach { i ->
            cartesCodes[i].filter { it != "?" && it.length >= 2 }.distinct()
                .forEach { c -> occMap.getOrPut(c) { mutableListOf() }.add(i) }
        }
        avertissementsCapture = occMap.filter { it.value.size > 1 }.map { (code, mains) ->
            "${formatCodeCarte(code)} : ${mains.joinToString(" & ") { joueurs[it] }}"
        }
    }

    LaunchedEffect(selectedCardIdx) {
        if (selectedCardIdx >= 0) {
            val h = with(density) { (cardHeightDp + 30f).dp.roundToPx() }
            scrollState.animateScrollTo(joueurActif * h)
        }
    }
    LaunchedEffect(joueurActif) {
        val h = with(density) { (cardHeightDp + 30f).dp.roundToPx() }
        scrollState.animateScrollTo(joueurActif * h)
    }

    fun mainComplete(idx: Int) = cartesCodes[idx].size == 13 && cartesCodes[idx].none { it == "?" || it.length < 2 }
    val toutesCompletes = (0..3).all { mainComplete(it) }

    fun appliquerCode(code: String) {
        val list = cartesCodes[joueurActif].toMutableList()
        if (selectedCardIdx < list.size) {
            list[selectedCardIdx] = code
            cartesCodes[joueurActif].clear()
            cartesCodes[joueurActif].addAll(list)
            val codeAvant = cartesCodes[joueurActif].getOrNull(selectedCardIdx)
        if (codeAvant != null) autoCorrigesCodes = autoCorrigesCodes - codeAvant
        }
        autoCorrigerParElimination()?.let { correctionAutoMsgs = correctionAutoMsgs + it }
        autoCorrigerErreurUnique()?.let { (msg, _) -> correctionAutoMsgs = correctionAutoMsgs + msg }
    }

    // showDialog=false sur les boutons valeur : avertissement inline seulement,
    // pas de dialog bloquant (l'opérateur peut encore changer la couleur librement).
    // showDialog=true sur les boutons couleur et Valider : dialog de confirmation.
    fun updateCard(showDialog: Boolean = true) {
        if (selectedCardIdx < 0 || kbValue.isEmpty() || kbSuit.isEmpty()) return
        val code = kbValue + kbSuit
        val list = cartesCodes[joueurActif].toMutableList()
        if (list.filterIndexed { i, c -> i != selectedCardIdx && c == code }.isNotEmpty()) { alerteMsg = "${formatCodeCarte(code)} déjà dans cette main !"; return }
        val autreIdx = (0..3).filter { it != joueurActif }.firstOrNull { code in cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }
        if (autreIdx != null) {
            if (showDialog) { forceNewCode = code; forceAvert = "${formatCodeCarte(code)} est déjà dans la main ${joueurs[autreIdx]}."; return }
            else { alerteMsg = "${formatCodeCarte(code)} déjà dans ${joueurs[autreIdx]} — changez la couleur"; return }
        }
        appliquerCode(code)
    }

    fun diagnostiquer() = diagnostiquerDonne(cartesCodes)

    fun calculerErreurs(diag: DiagnosticDonne)     = calculerErreursDonne(diag, joueurs)

    fun calculerSuggestions(diag: DiagnosticDonne) = calculerSuggestionsDonne(diag, joueurs, confidences)
    fun calculerSuspects(diag: DiagnosticDonne)    = calculerSuspectsDonne(diag, cartesCodes, confidences)

    // Lance l'analyse d'un joueur en arrière-plan (détecteur créé + fermé dans la coroutine)
    fun lancerAnalyse(idx: Int) {
        scope.launch {
            val file = photoFiles[idx]
            val (codes, conf) = if (file != null) withContext(Dispatchers.IO) {
                var bmp = loadBitmapExif(file) ?: return@withContext List(13) { "?" } to emptyMap<String, Float>()
                if (rotation90) bmp = pivoter90CCW(bmp) else if (rotation90CW) bmp = pivoter90CW(bmp)
                when (modele) {
                    ModelePhoto.ROBOFLOW -> analyserRoboflow(bmp)
                    ModelePhoto.RT_DETR  -> {
                        val det = try { RtDetrDetector(context) } catch (_: Exception) { null }
                        if (det == null) List(13) { "?" } to emptyMap()
                        else { val r = analyserRtDetr(det, bmp); det.close(); r }
                    }
                    ModelePhoto.YOLO11   -> {
                        val det = try { Yolo11Detector(context) } catch (_: Exception) { null }
                        if (det == null) List(13) { "?" } to emptyMap()
                        else { val r = analyserYolo11(det, bmp); det.close(); r }
                    }
                }
            } else List(13) { "?" } to emptyMap()
            cartesCodes[idx].clear(); cartesCodes[idx].addAll(codes)
            confidences[idx].clear(); confidences[idx].putAll(conf)
            analyseTerminee[idx] = true
        }
    }

    // Caméra
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            capturedPhotoFile = File.createTempFile("bridge_batch_", ".jpg", context.cacheDir)
            cameraOuverte = true
        }
    }
    fun lancerCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { permLauncher.launch(Manifest.permission.CAMERA); return }
        capturedPhotoFile = File.createTempFile("bridge_batch_", ".jpg", context.cacheDir)
        cameraOuverte = true
    }
    fun reprendreCapture() {
        for (i in 0..3) { analyseTerminee[i] = false; cartesCodes[i].clear(); confidences[i].clear(); photoFiles[i] = null }
        joueurCourant = 0; cameraAnnulee = false; correctionAutoMsgs = emptyList(); autoCorrigesCodes = emptySet(); avertissementsCapture = emptyList()
        tempsDebut = 0L; dureeMsAnalyse = 0L
        selectedCardIdx = -1; kbValue = ""; kbSuit = ""
        etape = EtapeBatch.CAPTURE
        lancerCamera()
    }

    // Enchaînement automatique : quand un joueur est photographié, ouvre la caméra pour le suivant
    LaunchedEffect(triggerAutoCamera) { if (triggerAutoCamera > 0) lancerCamera() }
    // Lancement immédiat si on vient du bouton "Saisir" (pas si cartes déjà chargées)
    LaunchedEffect(Unit) { if (initialCartes == null && demarrerDirectement) lancerCamera() }

    // Alertes
    if (alerteMsg != null) AlertDialog(onDismissRequest = { alerteMsg = null }, title = { Text("Erreur") }, text = { Text(alerteMsg!!) }, confirmButton = { TextButton(onClick = { alerteMsg = null }) { Text("OK") } })
    if (forceNewCode != null) AlertDialog(
        onDismissRequest = { forceNewCode = null },
        title = { Text("⚠ Doublon inter-mains") },
        text  = { Text("$forceAvert\n\nForcer quand même ?") },
        confirmButton = { TextButton(onClick = { val c = forceNewCode!!; forceNewCode = null; appliquerCode(c) }) { Text("Forcer", color = Color(0xFFE65100)) } },
        dismissButton = { TextButton(onClick = { forceNewCode = null }) { Text("Annuler") } }
    )

    val valRow1 = listOf("A","R","D","V","10"); val valRow2 = listOf("9","8","7","6","5","4","3","2")
    val suits   = listOf("P" to "♠", "C" to "♥", "K" to "♦", "T" to "♣")

    Box(Modifier.fillMaxSize()) {
    EcranPleinScaffold {
        Scaffold(
            modifier  = Modifier.fillMaxSize(),
            bottomBar = {
                when (etape) {

                    // ── Barre CAPTURE ──────────────────────────────────────────
                    EtapeBatch.CAPTURE -> Row(Modifier.fillMaxWidth().background(Color(0xFF1B5E20)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onRetour() }.padding(8.dp)) {
                            Box(Modifier.size(48.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                            Text("Retour", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Bouton photo : caméra fermée ET (mode manuel OU photo annulée)
                            if (!cameraOuverte && (!demarrerDirectement || cameraAnnulee)) {
                                Button(
                                    onClick = { cameraAnnulee = false; lancerCamera() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) {
                                    Text(
                                        "📷 ${if (cameraAnnulee) "Retenter" else "Photographier"} ${joueurs[joueurCourant]}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // ── Barre ATTENTE ──────────────────────────────────────────
                    EtapeBatch.ATTENTE -> Box(Modifier.fillMaxWidth().background(Color(0xFF1B5E20)).padding(12.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Analyse ${analyseTerminee.count { it }} / 4 terminée(s)…", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // ── Barre CORRECTION ───────────────────────────────────────
                    EtapeBatch.CORRECTION -> Column(Modifier.fillMaxWidth().background(Color(0xFF1B5E20))) {
                        val batchActions = calculerSuggestions(diagnostiquer())
                        if (batchActions.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(Color(0xFFBF360C)).padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                batchActions.forEach { Text(buildTextAvecSymboles("▶ $it"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            Text("✅ Donne prête", color = Color(0xFF80CBC4), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                        if (selectedCardIdx < 0) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onRetour() }.padding(8.dp)) {
                                    Box(Modifier.size(48.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                                    Text("Retour", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (toutesCompletes) {
                                    Button(
                                        onClick = { onEnregistrer(cartesCodes.map { codes -> codes.map { Carte.fromCode(it) } }) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                    ) { Text("Enregistrer la donne", fontWeight = FontWeight.Bold, color = Color.White) }
                                } else {
                                    Text("${(0..3).count { mainComplete(it) }} / 4 mains complètes", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                        if (selectedCardIdx >= 0) {
                            Column(Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    valRow1.forEach { v -> Button(onClick = { kbValue = v }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)) { Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    valRow2.forEach { v -> Button(onClick = { kbValue = v }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)) { Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp) } }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    suits.forEach { (code, _) ->
                                        val symColor = when (code) { "C", "K" -> Color.Red; "T" -> Color.Black; else -> Color.Black }
                                        Button(onClick = { kbSuit = code; updateCard() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (kbSuit == code) Color(0xFFFFD54F) else Color.White), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)) {
                                            SuiteSymbol(couleur = code, color = symColor, sizeDp = 22f)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Button(onClick = {
                                        val candidate = if (kbValue.isNotEmpty() && kbSuit.isNotEmpty()) kbValue + kbSuit else null
                                        val current = cartesCodes[joueurActif].getOrNull(selectedCardIdx)
                                        if (candidate != null && candidate != current) updateCard()
                                        if (forceNewCode == null) {
                                            val cur = cartesCodes[joueurActif].getOrNull(selectedCardIdx)
                                            if (cur == null || cur == "?" || cur.length < 2) alerteMsg = "Saisissez une valeur ET une couleur."
                                            else { selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                        }
                                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))) { Text("Valider", fontWeight = FontWeight.Bold) }
                                    if (cartesCodes[joueurActif].size >= 13) Button(
                                        onClick = {
                                            val l = cartesCodes[joueurActif].toMutableList()
                                            if (l.size > 13) l.removeAt(selectedCardIdx) else l[selectedCardIdx] = "?"
                                            cartesCodes[joueurActif].clear(); cartesCodes[joueurActif].addAll(l)
                                            selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                                    ) { Text(if (cartesCodes[joueurActif].size > 13) "Supprimer" else "→ ?", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFF0B6623)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (BuildConfig.DEBUG) Text("[ Saisie photo donne (batch) ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)

                // Ligne 1 : Modèle + chrono + N° donne
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Modèle :", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                    Box {
                        OutlinedButton(onClick = { modeleExpanded = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(when (modele) { ModelePhoto.RT_DETR -> "RT-DETR ⚡"; ModelePhoto.YOLO11 -> "YOLOv11s ⚡"; else -> "Roboflow ☁" }, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = modeleExpanded, onDismissRequest = { modeleExpanded = false }) {
                            DropdownMenuItem(text = { Text("RT-DETR ⚡ (local)") }, onClick = { modele = ModelePhoto.RT_DETR; modeleExpanded = false })
                            DropdownMenuItem(text = { Text("YOLOv11s ⚡ (local)") }, onClick = { modele = ModelePhoto.YOLO11; modeleExpanded = false })
                            DropdownMenuItem(text = { Text("Roboflow ☁ (web)") }, onClick = { modele = ModelePhoto.ROBOFLOW; modeleExpanded = false })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (dureeMsAnalyse > 0L) {
                        val s = dureeMsAnalyse / 1000
                        val d = (dureeMsAnalyse % 1000) / 100
                        Text("⏱ ${s}.${d}s", color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Donne N° $numeroDonne", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }

                // Ligne 2 : 4 joueurs — clic = photo rotation 90°
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    joueurs.forEachIndexed { idx, nom ->
                        val estJoueurCourant = joueurCourant == idx
                        val estActif    = joueurActif == idx
                        val estAnalyse  = analyseTerminee[idx]
                        val estComplet  = mainComplete(idx)
                        val photoOk     = photoFiles[idx] != null
                        val isYellow    = (etape == EtapeBatch.CORRECTION && estActif) || (etape == EtapeBatch.CAPTURE && estJoueurCourant)
                        Button(
                            onClick = {
                                captureManuelle = true
                                joueurCourant = idx; joueurActif = idx
                                selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                lancerCamera()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = when {
                                isYellow                  -> Color(0xFFFFD54F)
                                !estAnalyse && photoOk    -> Color(0xFF37474F)
                                estComplet                -> Color(0xFF2E7D32)
                                estAnalyse && !estComplet -> Color(0xFFB71C1C)
                                else                      -> Color(0xFF263238)
                            }),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(nom, fontSize = 11.sp,
                                        fontWeight = if (isYellow) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isYellow) Color.Black else Color.White)
                                    Spacer(Modifier.width(3.dp))
                                    Text("📷", fontSize = 10.sp)
                                }
                                Text(
                                    when {
                                        !estAnalyse && photoOk -> "⏳"
                                        estAnalyse -> "${cartesCodes[idx].count { it != "?" && it.length >= 2 }}/13"
                                        else -> "0/13"
                                    },
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (isYellow) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                when (etape) {

                    // ── CAPTURE ────────────────────────────────────────────────
                    EtapeBatch.CAPTURE -> {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                cameraAnnulee -> Text("⚠️ Photo annulée — cliquez sur un joueur pour retenter", color = Color(0xFFFF8A65), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                cameraOuverte -> Text("📷 Appareil photo ouvert…", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                else          -> Text("Cliquez sur un joueur pour photographier sa main.", color = Color(0xFFB3E5FC), fontSize = 13.sp)
                            }
                            if (avertissementsCapture.isNotEmpty()) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("⚠️ Doublons entre mains :", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        avertissementsCapture.forEach { msg -> Text(buildTextAvecSymboles("• $msg"), color = Color.White, fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }

                    // ── ATTENTE ────────────────────────────────────────────────
                    EtapeBatch.ATTENTE -> {
                        val toutesAnalysees = analyseTerminee.all { it }
                        val diagPreview     = if (toutesAnalysees) diagnostiquer() else null
                        val actionsAttentePreview = if (diagPreview != null) calculerSuggestions(diagPreview) else emptyList()
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(Modifier.height(16.dp))
                            when {
                                !toutesAnalysees              -> Text("Analyse en cours…", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                actionsAttentePreview.isEmpty() -> Text("✅ Prêt à enregistrer", color = Color(0xFF80CBC4), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                else                          -> Text("⚠️ ${actionsAttentePreview.size} correction(s) à faire", color = Color(0xFFFFD54F), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            joueurs.forEachIndexed { idx, nom ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (analyseTerminee[idx]) Text("✅", fontSize = 20.sp)
                                    else CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(nom, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            if (toutesAnalysees) {
                                if (actionsAttentePreview.isNotEmpty()) {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            actionsAttentePreview.forEach { Text(buildTextAvecSymboles("▶ $it"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                } else {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))) {
                                        Text("✅ Donne prête", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            } else if (avertissementsCapture.isNotEmpty()) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("⚠️ Doublons entre mains :", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        avertissementsCapture.forEach { Text(buildTextAvecSymboles("• $it"), color = Color.White, fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }

                    // ── CORRECTION ─────────────────────────────────────────────
                    EtapeBatch.CORRECTION -> {
                        val diag       = diagnostiquer()
                        val suspects   = calculerSuspects(diag)
                        val erreurs    = calculerErreurs(diag)
                        val logActions = calculerSuggestions(diag)
                        // Doublons intra et inter-mains → orange immédiat sur les 2 occurrences
                        val occDoublon = mutableMapOf<String, MutableList<Pair<Int,Int>>>()
                        for (jIdx in 0..3) cartesCodes[jIdx].forEachIndexed { i, c ->
                            if (c != "?" && c.length >= 2) occDoublon.getOrPut(c) { mutableListOf() }.add(jIdx to i)
                        }
                        val erreursDoublon = occDoublon.values.filter { it.size > 1 }.flatten().toSet()
                        if (erreurs.isEmpty()) Log.d("BridgeCorrection", "[ROUGE] Aucune erreur ✅")
                        else erreurs.forEach { e -> Log.d("BridgeCorrection", "[ROUGE] $e") }
                        if (logActions.isEmpty()) Log.d("BridgeCorrection", "[ORANGE] Donne prête ✅")
                        else logActions.forEach { a -> Log.d("BridgeCorrection", "[ORANGE] $a") }
                        Column(Modifier.weight(1f).verticalScroll(scrollState)) {
                            joueurs.forEachIndexed { idx, nom ->
                                val estActif    = joueurActif == idx
                                val estComplet  = mainComplete(idx)
                                val estAutoCalc = photoFiles[idx] == null && cartesCodes[idx].isNotEmpty()
                                val codes       = cartesCodes[idx]
                                val lineColor   = when { estActif -> Color.Yellow; estAutoCalc -> Color(0xFFFF8A65); estComplet -> Color(0xFF80CBC4); else -> Color.White }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    HorizontalDivider(Modifier.weight(1f), color = lineColor, thickness = if (estActif) 2.dp else 1.dp)
                                    Text("  $nom (${codes.count { it != "?" && it.length >= 2 }}/13)  ",
                                        color = lineColor,
                                        fontWeight = if (estActif) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 13.sp)
                                    if (!analyseTerminee[idx]) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    HorizontalDivider(Modifier.weight(1f), color = lineColor, thickness = if (estActif) 2.dp else 1.dp)
                                }
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val cardW = maxWidth.value / 13f
                                    val cardH = cardW * 2.2f
                                    Column(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                            for (i in 0 until minOf(13, codes.size)) {
                                                val code = codes.getOrNull(i)
                                                CardSlotPhoto(code = code, isSelected = estActif && i == selectedCardIdx, isSuspect = i in suspects[idx], isAutoCorrige = code != null && code.length >= 2 && code in autoCorrigesCodes, isErreur = (idx to i) in erreursDoublon, onClick = {
                                                    if (joueurActif != idx) { joueurActif = idx; selectedCardIdx = i; kbValue = code?.let { if (it != "?" && it.length >= 2) it.dropLast(1) else "" } ?: ""; kbSuit = code?.lastOrNull()?.toString()?.takeIf { it in listOf("P","C","K","T") } ?: "" }
                                                    else if (selectedCardIdx == i) { selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                                    else { selectedCardIdx = i; kbValue = code?.let { if (it != "?" && it.length >= 2) it.dropLast(1) else "" } ?: ""; kbSuit = code?.lastOrNull()?.toString()?.takeIf { it in listOf("P","C","K","T") } ?: "" }
                                                }, widthDp = cardW, heightDp = cardH)
                                            }
                                        }
                                        if (codes.size > 13) {
                                            Spacer(Modifier.height(2.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                                for (i in 13 until codes.size) {
                                                    val code = codes.getOrNull(i)
                                                    CardSlotPhoto(code = code, isSelected = estActif && i == selectedCardIdx, isSuspect = i in suspects[idx], isAutoCorrige = code != null && code.length >= 2 && code in autoCorrigesCodes, isErreur = (idx to i) in erreursDoublon, onClick = {
                                                        if (joueurActif != idx) { joueurActif = idx; selectedCardIdx = i; kbValue = code?.let { if (it != "?" && it.length >= 2) it.dropLast(1) else "" } ?: ""; kbSuit = code?.lastOrNull()?.toString()?.takeIf { it in listOf("P","C","K","T") } ?: "" }
                                                        else if (selectedCardIdx == i) { selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                                        else { selectedCardIdx = i; kbValue = code?.let { if (it != "?" && it.length >= 2) it.dropLast(1) else "" } ?: ""; kbSuit = code?.lastOrNull()?.toString()?.takeIf { it in listOf("P","C","K","T") } ?: "" }
                                                    }, widthDp = cardW, heightDp = cardH)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    // Overlay CameraX — s'affiche par-dessus l'interface, aucun bouton OK système
    if (cameraOuverte) {
        val f = capturedPhotoFile
        if (f != null) {
            key(f.absolutePath) {
                CameraPreviewScreen(
                    outputFile  = f,
                    label       = joueurs[joueurCourant],
                    onPhotoPrise = {
                        val file = capturedPhotoFile
                        cameraOuverte = false
                        if (file != null) {
                            photoFiles[joueurCourant] = file
                            sauvegarderPhotoDataset(context, file, joueurs[joueurCourant])
                            cameraAnnulee = false
                            if (tempsDebut == 0L) tempsDebut = System.currentTimeMillis()
                            analyseTerminee[joueurCourant] = false
                            lancerAnalyse(joueurCourant)
                            if (!captureManuelle) {
                                if (joueurCourant < 3) { joueurCourant++; triggerAutoCamera++ }
                                else etape = EtapeBatch.ATTENTE
                            } else {
                                captureManuelle = false
                                if (photoFiles.all { it != null } && etape == EtapeBatch.CAPTURE) etape = EtapeBatch.ATTENTE
                            }
                        }
                    },
                    onAnnulee = { cameraOuverte = false; cameraAnnulee = true }
                )
            }
        }
    }
    } // Box
}

// ─────────────────────────────────────────────────────────────────────────────
// Entrée publique
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AffichageMainsScreen(
    numeroDonne: Int,
    onRetour: () -> Unit,
    onEnregistrer: (List<List<Carte>>) -> Unit,
    demarrerPhoto: Boolean = false
) {
    val blocs = remember {
        JeuDeCartes.toutesLesCartes.chunked(13)
            .map { g -> g.map { it as Carte? }.toMutableStateList() }
            .toMutableStateList()
    }
    val selectedHands = remember { List(4) { mutableStateListOf<Carte>() } }
    val mode = remember { mutableStateOf(if (demarrerPhoto) SaisieMode.PHOTO else SaisieMode.MANUEL) }

    when (mode.value) {
        SaisieMode.MANUEL -> ManuelSaisieContent(
            numeroDonne   = numeroDonne,
            blocs         = blocs,
            selectedHands = selectedHands,
            onRetour      = onRetour,
            onEnregistrer = onEnregistrer
        )
        SaisieMode.PHOTO -> PhotoSaisieContent(
            numeroDonne   = numeroDonne,
            onRetour      = onRetour,
            onEnregistrer = onEnregistrer
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode PHOTO — style BridgeCardDetector, correction inline, toutes les mains visibles
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PhotoSaisieContent(
    numeroDonne: Int,
    onRetour: () -> Unit,
    onEnregistrer: (List<List<Carte>>) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val joueurs = listOf("Nord","Est","Sud","Ouest")

    var modele             by remember { mutableStateOf(ModelePhoto.ROBOFLOW) }
    var modeleMenuExpanded by remember { mutableStateOf(false) }
    val rotation90         = true
    val rotation90CW       = false
    var joueurActif        by remember { mutableStateOf(0) }
    var isAnalyzing        by remember { mutableStateOf(false) }
    var selectedCardIdx    by remember { mutableStateOf(-1) }
    var kbValue            by remember { mutableStateOf("") }
    var kbSuit             by remember { mutableStateOf("") }
    var alerteDoublons          by remember { mutableStateOf<String?>(null) }
    var alerteChangementJoueur  by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var forceNewCode            by remember { mutableStateOf<String?>(null) }
    var forceAvertissement      by remember { mutableStateOf("") }
    var rtDetr                  by remember { mutableStateOf<RtDetrDetector?>(null) }
    var rtDetrErreur            by remember { mutableStateOf(false) }
    var yolo11                  by remember { mutableStateOf<Yolo11Detector?>(null) }
    var yolo11Erreur            by remember { mutableStateOf(false) }
    var joueurAutoRempliIdx     by remember { mutableStateOf<Int?>(null) }
    var codesAutoRempli         by remember { mutableStateOf<List<String>?>(null) }
    var erreursParMain          by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var autocalcules            by remember { mutableStateOf(setOf<Int>()) }
    var alerteAutoCalc          by remember { mutableStateOf<Int?>(null) }
    var tempsDebut              by remember { mutableStateOf(0L) }
    var dureeMsAnalyse          by remember { mutableStateOf(0L) }
    var cameraOuverte           by remember { mutableStateOf(false) }

    val cartesCodes = remember { List(4) { mutableStateListOf<String>() } }
    val confidences = remember { List(4) { mutableStateMapOf<String, Float>() } }

    DisposableEffect(Unit) { onDispose { rtDetr?.close() } }

    val scrollState   = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardHeightDp  = (screenWidthDp / 13f).coerceAtLeast(22f) * 2.2f
    val density       = LocalDensity.current

    // Scroll vers le joueur actif quand le clavier de correction s'ouvre
    LaunchedEffect(selectedCardIdx) {
        if (selectedCardIdx >= 0) {
            val hauteurParJoueurPx = with(density) { (cardHeightDp + 30f).dp.roundToPx() }
            scrollState.animateScrollTo(joueurActif * hauteurParJoueurPx)
        }
    }
    LaunchedEffect(joueurActif) {
        val hauteurParJoueurPx = with(density) { (cardHeightDp + 30f).dp.roundToPx() }
        scrollState.animateScrollTo(joueurActif * hauteurParJoueurPx)
    }

    fun mainComplete(idx: Int) = cartesCodes[idx].size == 13 &&
            cartesCodes[idx].none { it == "?" || it.length < 2 }

    val toutesCompletes = (0..3).all { mainComplete(it) }

    fun calculerErreursMain(idx: Int): List<String> {
        val erreurs = mutableListOf<String>()
        val nb = cartesCodes[idx].size
        if (nb == 0) return erreurs
        if (nb != 13) erreurs.add("$nb/13 cartes")
        val nbQ = cartesCodes[idx].count { it == "?" || it.length < 2 }
        if (nbQ > 0) erreurs.add("$nbQ carte(s) [?]")
        val codesIdent = cartesCodes[idx].filter { it != "?" && it.length >= 2 }
        codesIdent.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { c ->
            erreurs.add("${formatCodeCarte(c)} en double dans cette main")
        }
        val autresPhoto = (0..3).filter { it != idx && cartesCodes[it].isNotEmpty() }
        if (autresPhoto.isNotEmpty()) {
            val autresCodes = autresPhoto.flatMap { i -> cartesCodes[i].filter { c -> c != "?" && c.length >= 2 } }.toSet()
            codesIdent.toSet().intersect(autresCodes).forEach { c ->
                val dans = autresPhoto.filter { c in cartesCodes[it] }.joinToString(" & ") { joueurs[it] }
                erreurs.add("${formatCodeCarte(c)} aussi dans $dans")
            }
        }
        return erreurs
    }

    // ── Mise à jour avec vérification des doublons ───────────────────────────
    fun appliquerCode(newCode: String) {
        val list = cartesCodes[joueurActif].toMutableList()
        if (selectedCardIdx < list.size) {
            list[selectedCardIdx] = newCode
            cartesCodes[joueurActif].clear()
            cartesCodes[joueurActif].addAll(list)
        }

        // Recalculer tous les joueurs : une correction peut résoudre un doublon inter-mains
        erreursParMain = (0..3).associate { it to calculerErreursMain(it) }

        // Si exactement 1 "?" restant et 1 carte manquante des 52 → auto-appliquer sans confirmation
        val tousPresents = (0..3).flatMap { cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }.toSet()
        val restants = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in tousPresents }
        val interros = (0..3).flatMap { i -> cartesCodes[i].indices.mapNotNull { pos -> if (cartesCodes[i][pos] == "?" || cartesCodes[i][pos].length < 2) (i to pos) else null } }
        if (restants.size == 1 && interros.size == 1) {
            val (hIdx, pos) = interros[0]
            val l = cartesCodes[hIdx].toMutableList()
            l[pos] = restants[0]
            // Pas de tri — la carte reste à sa position physique sur la table
            cartesCodes[hIdx].clear()
            cartesCodes[hIdx].addAll(l)
            erreursParMain = (0..3).associate { it to calculerErreursMain(it) }
        }
    }

    fun updateCard(showDialog: Boolean = true) {
        if (selectedCardIdx < 0 || kbValue.isEmpty() || kbSuit.isEmpty()) return
        val newCode = kbValue + kbSuit
        // Doublon dans cette main → bloquant (toujours une erreur)
        val list = cartesCodes[joueurActif].toMutableList()
        if (list.filterIndexed { i, c -> i != selectedCardIdx && c == newCode }.isNotEmpty()) {
            alerteDoublons = "${formatCodeCarte(newCode)} déjà dans cette main !"; return
        }
        // Doublon dans une autre main : si bouton valeur → inline, si bouton couleur/Valider → dialog
        val autreMainIdx = (0..3).filter { it != joueurActif }
            .firstOrNull { newCode in cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }
        if (autreMainIdx != null) {
            if (showDialog) { forceNewCode = newCode; forceAvertissement = "${formatCodeCarte(newCode)} est déjà dans la main ${joueurs[autreMainIdx]}."; return }
            else { alerteDoublons = "${formatCodeCarte(newCode)} déjà dans ${joueurs[autreMainIdx]} — changez la couleur"; return }
        }
        appliquerCode(newCode)
    }

    // ── Validation finale ────────────────────────────────────────────────────
    fun verifierEtEnregistrer() {
        if (autocalcules.isNotEmpty()) { alerteAutoCalc = autocalcules.first(); return }
        val toutesLesCodes = JeuDeCartes.toutesLesCartes.map { it.code }.toSet()
        // Pour chaque code valide, liste des mains qui le contiennent
        val apparitions = mutableMapOf<String, MutableList<String>>()
        for (i in 0..3) {
            cartesCodes[i].filter { it != "?" && it.length >= 2 }.forEach { code ->
                apparitions.getOrPut(code) { mutableListOf() }.add(joueurs[i])
            }
        }
        val messages = mutableListOf<String>()
        // Doublons avec noms des mains concernées
        apparitions.filter { it.value.size > 1 }.forEach { (code, mains) ->
            messages.add("${formatCodeCarte(code)} en double : ${mains.joinToString(" et ")}")
        }
        // Cartes manquantes (conséquence des doublons)
        val manquantes = toutesLesCodes - apparitions.keys.toSet()
        if (manquantes.isNotEmpty()) {
            messages.add("Manquante(s) : ${manquantes.joinToString(", ") { formatCodeCarte(it) }}")
        }
        if (messages.isNotEmpty()) {
            alerteDoublons = messages.joinToString("\n"); return
        }
        onEnregistrer(cartesCodes.map { codes -> codes.map { Carte.fromCode(it) } })
    }

    // ── Vérification avant changement de joueur ──────────────────────────────
    fun verifierMainAvantChangement(joueurIdx: Int): String? {
        val allCodes = cartesCodes[joueurIdx]
        if (allCodes.isEmpty()) return null  // pas encore photographié → OK

        // Cartes incomplètes (trous)
        val trous = allCodes.count { it == "?" || it.length < 2 }
        if (trous > 0)
            return "Main ${joueurs[joueurIdx]} : $trous carte(s) non saisie(s). Complétez tous les trous avant de continuer."

        // Nombre total
        if (allCodes.size != 13)
            return "Main ${joueurs[joueurIdx]} : ${allCodes.size}/13 cartes. Il faut exactement 13 cartes."

        val codes = allCodes.filter { it.length >= 2 }

        // Doublons intra-main
        val dupIntraMain = codes.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dupIntraMain.isNotEmpty())
            return "Main ${joueurs[joueurIdx]} : cartes en double dans cette main : ${dupIntraMain.joinToString(", ") { formatCodeCarte(it) }}"

        // Doublons inter-mains
        val autresCodes = (0..3).filter { it != joueurIdx && cartesCodes[it].isNotEmpty() }
            .flatMap { cartesCodes[it].filter { c -> c.length >= 2 } }.toSet()
        val dupInter = codes.toSet().intersect(autresCodes)
        if (dupInter.isNotEmpty())
            return "Main ${joueurs[joueurIdx]} : cartes déjà dans une autre main : ${dupInter.joinToString(", ") { formatCodeCarte(it) }}"

        return null
    }

    // ── Caméra — CameraX intégré (aucun bouton OK système) ───────────────────
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }

    fun traiterPhoto(file: File) {
        val idx = joueurActif
        sauvegarderPhotoDataset(context, file, joueurs[idx])
        isAnalyzing = true
        tempsDebut = System.currentTimeMillis()
        selectedCardIdx = -1; kbValue = ""; kbSuit = ""
        scope.launch {
            val (codes, conf) = withContext(Dispatchers.IO) {
                var bmp = loadBitmapExif(file) ?: return@withContext List(13) { "?" } to emptyMap<String, Float>()
                if (rotation90) bmp = pivoter90CCW(bmp) else if (rotation90CW) bmp = pivoter90CW(bmp)
                when (modele) {
                    ModelePhoto.ROBOFLOW -> analyserRoboflow(bmp)
                    ModelePhoto.RT_DETR  -> {
                        val det = rtDetr ?: try {
                            RtDetrDetector(context).also { rtDetr = it; rtDetrErreur = false }
                        } catch (e: Exception) { rtDetrErreur = true; null }
                        if (det == null) List(13) { "?" } to emptyMap() else analyserRtDetr(det, bmp)
                    }
                    ModelePhoto.YOLO11   -> {
                        val det = yolo11 ?: try {
                            Yolo11Detector(context).also { yolo11 = it; yolo11Erreur = false }
                        } catch (e: Exception) { yolo11Erreur = true; null }
                        if (det == null) List(13) { "?" } to emptyMap() else analyserYolo11(det, bmp)
                    }
                    else -> List(13) { "?" } to emptyMap()
                }
            }
            cartesCodes[idx].clear()
            cartesCodes[idx].addAll(codes)
            confidences[idx].clear()
            confidences[idx].putAll(conf)
            autocalcules = autocalcules - idx
            if (tempsDebut > 0L) dureeMsAnalyse = System.currentTimeMillis() - tempsDebut
            isAnalyzing = false
            val tousPresents = (0..3).flatMap { cartesCodes[it].filter { c -> c != "?" && c.length >= 2 } }.toSet()
            val restants = JeuDeCartes.toutesLesCartes.map { it.code }.filter { it !in tousPresents }
            val interros = (0..3).flatMap { i -> cartesCodes[i].indices.mapNotNull { pos -> if (cartesCodes[i][pos] == "?" || cartesCodes[i][pos].length < 2) (i to pos) else null } }
            if (restants.size == 1 && interros.size == 1) {
                val (hIdx, pos) = interros[0]
                val l = cartesCodes[hIdx].toMutableList()
                l[pos] = restants[0]
                cartesCodes[hIdx].clear()
                cartesCodes[hIdx].addAll(l)
            }
            erreursParMain = (0..3).associate { it to calculerErreursMain(it) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            capturedPhotoFile = File.createTempFile("bridge_", ".jpg", context.cacheDir)
            cameraOuverte = true
        }
    }

    fun lancerCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA); return
        }
        capturedPhotoFile = File.createTempFile("bridge_", ".jpg", context.cacheDir)
        cameraOuverte = true
    }

    val valRow1 = listOf("A","R","D","V","10")
    val valRow2 = listOf("9","8","7","6","5","4","3","2")
    val suits   = listOf("P" to "♠", "C" to "♥", "K" to "♦", "T" to "♣")

    if (alerteDoublons != null) {
        AlertDialog(
            onDismissRequest = { alerteDoublons = null },
            title = { Text("Carte en double") },
            text  = { Text(alerteDoublons!!) },
            confirmButton = { TextButton(onClick = { alerteDoublons = null }) { Text("OK") } }
        )
    }

    if (forceNewCode != null) {
        AlertDialog(
            onDismissRequest = { forceNewCode = null },
            title = { Text("⚠ Doublon inter-mains") },
            text  = { Text("$forceAvertissement\n\nForcer quand même ?") },
            confirmButton = {
                TextButton(onClick = {
                    val code = forceNewCode!!
                    forceNewCode = null
                    appliquerCode(code)
                }) { Text("Forcer", color = Color(0xFFE65100)) }
            },
            dismissButton = {
                TextButton(onClick = { forceNewCode = null }) { Text("Annuler") }
            }
        )
    }

    if (alerteChangementJoueur != null) {
        val (msg, targetIdx) = alerteChangementJoueur!!
        AlertDialog(
            onDismissRequest = { alerteChangementJoueur = null },
            title = { Text("Correction recommandée") },
            text  = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    alerteChangementJoueur = null
                    joueurActif = targetIdx; selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                }) { Text("Continuer quand même") }
            },
            dismissButton = {
                TextButton(onClick = { alerteChangementJoueur = null }) { Text("Corriger") }
            }
        )
    }

    if (alerteAutoCalc != null) {
        val idx = alerteAutoCalc!!
        AlertDialog(
            onDismissRequest = { alerteAutoCalc = null },
            title = { Text("⚠ Main non photographiée") },
            text  = { Text("${joueurs[idx]} a été auto-calculé(e) depuis les 3 autres mains — pas photographié(e).\n\nSi une carte a été mal détectée dans une autre main, ${joueurs[idx]} contiendra une carte incorrecte sans qu'aucune erreur soit signalée.\n\nVérifiez CHAQUE carte de ${joueurs[idx]} avant de valider.") },
            confirmButton = {
                TextButton(onClick = {
                    autocalcules = autocalcules - idx
                    alerteAutoCalc = null
                    verifierEtEnregistrer()
                }) { Text("J'ai vérifié carte par carte — Valider", color = Color(0xFFE65100)) }
            },
            dismissButton = {
                TextButton(onClick = { alerteAutoCalc = null; joueurActif = idx }) { Text("Aller vérifier") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    EcranPleinScaffold {
        Scaffold(
            modifier  = Modifier.fillMaxSize(),
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(Color(0xFF1B5E20))) {

                    // ── Actions compactes ─────────────────────────────────────
                    if (!isAnalyzing) {
                        val toutesPhotoOk = (0..3).all { cartesCodes[it].isNotEmpty() }
                        val photoDiag = if (toutesPhotoOk) diagnostiquerDonne(cartesCodes) else null
                        val photoActions = if (photoDiag != null) calculerSuggestionsDonne(photoDiag, joueurs, confidences) else emptyList()
                        if (photoActions.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(Color(0xFFBF360C)).padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                photoActions.forEach { Text(buildTextAvecSymboles("▶ $it"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    // ── Barre Retour / Enregistrer (masquée quand clavier ouvert) ──
                    if (selectedCardIdx < 0) {
                        Row(Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onRetour() }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(Modifier.size(48.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                Text("Retour", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (toutesCompletes) {
                                Button(
                                    onClick = { verifierEtEnregistrer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) { Text("Enregistrer la donne", fontWeight = FontWeight.Bold, color = Color.White) }
                            } else {
                                val nb = (0..3).count { mainComplete(it) }
                                Text("$nb / 4 mains complètes", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    // ── Clavier de correction (tout en bas, cache Retour) ──────
                    if (selectedCardIdx >= 0) {
                        Column(Modifier.fillMaxWidth().background(Color(0xFF2E7D32)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                valRow1.forEach { v ->
                                    Button(
                                        onClick = { kbValue = v },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)
                                    ) { Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                valRow2.forEach { v ->
                                    Button(
                                        onClick = { kbValue = v },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)
                                    ) { Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp) }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                suits.forEach { (code, _) ->
                                    val symColor = when (code) { "C", "K" -> Color.Red; "T" -> Color.Black; else -> Color.Black }
                                    Button(
                                        onClick = { kbSuit = code; updateCard() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (kbSuit == code) Color(0xFFFFD54F) else Color.White),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)
                                    ) { SuiteSymbol(couleur = code, color = symColor, sizeDp = 22f) }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Button(
                                    onClick = {
                                        val candidate = if (kbValue.isNotEmpty() && kbSuit.isNotEmpty()) kbValue + kbSuit else null
                                        val current = cartesCodes[joueurActif].getOrNull(selectedCardIdx)
                                        if (candidate != null && candidate != current) updateCard()
                                        if (forceNewCode == null) {
                                            val cur = cartesCodes[joueurActif].getOrNull(selectedCardIdx)
                                            if (cur == null || cur == "?" || cur.length < 2) alerteDoublons = "Saisissez une valeur ET une couleur avant de valider."
                                            else { selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                                ) { Text("Valider", fontWeight = FontWeight.Bold) }
                                if (cartesCodes[joueurActif].size >= 13) {
                                    Button(
                                        onClick = {
                                            val list = cartesCodes[joueurActif].toMutableList()
                                            if (list.size > 13) list.removeAt(selectedCardIdx) else list[selectedCardIdx] = "?"
                                            cartesCodes[joueurActif].clear()
                                            cartesCodes[joueurActif].addAll(list)
                                            selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                            erreursParMain = (0..3).associate { it to calculerErreursMain(it) }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                                    ) { Text(if (cartesCodes[joueurActif].size > 13) "Supprimer" else "→ ?", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }

                }
            }
        ) { innerPadding ->
            Column(
                Modifier.padding(innerPadding).fillMaxSize()
                    .background(Color(0xFF0B6623))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (BuildConfig.DEBUG) Text("[ Saisie photo en cours de tournoi ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                // ── Ligne 1 : sélecteur modèle + rotation ────────────────────
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Modèle :", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { modeleMenuExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text(when (modele) { ModelePhoto.RT_DETR -> "RT-DETR-L ⚡"; ModelePhoto.YOLO11 -> "YOLOv11s ⚡"; else -> "Roboflow ☁" }, fontSize = 12.sp) }
                        DropdownMenu(expanded = modeleMenuExpanded, onDismissRequest = { modeleMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("RT-DETR-L ⚡ (local)") }, onClick = { modele = ModelePhoto.RT_DETR; modeleMenuExpanded = false })
                            DropdownMenuItem(text = { Text("YOLOv11s ⚡ (local)") }, onClick = { modele = ModelePhoto.YOLO11; modeleMenuExpanded = false })
                            DropdownMenuItem(text = { Text("Roboflow ☁ (web)") }, onClick = { modele = ModelePhoto.ROBOFLOW; modeleMenuExpanded = false })
                        }
                    }
                    if (rtDetrErreur || yolo11Erreur) Text("  ⚠ modèle manquant", color = Color(0xFFFF8F00), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    if (dureeMsAnalyse > 0L) {
                        val s = dureeMsAnalyse / 1000
                        val d = (dureeMsAnalyse % 1000) / 100
                        Text("⏱ ${s}.${d}s", color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                    }
                    // ── Numéro de donne ───────────────────────────────────────
                    Text("Donne N° $numeroDonne", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp, modifier = Modifier.padding(bottom = 0.dp),
                        textAlign = TextAlign.Center)
                    }

                // ── Ligne 3 : 4 onglets joueurs, largeur partagée ────────────
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    joueurs.forEachIndexed { idx, nom ->
                        val estActif   = joueurActif == idx
                        val estComplet = mainComplete(idx)
                        val aDesErreurs  = erreursParMain[idx]?.isNotEmpty() == true
                        val analyseOk    = erreursParMain.containsKey(idx) && erreursParMain[idx]?.isEmpty() == true
                        val estAutoCalc  = idx in autocalcules
                        Button(
                            onClick = {
                                joueurActif = idx; selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                lancerCamera()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = when {
                                estActif                   -> Color(0xFFFFD54F)
                                idx == joueurAutoRempliIdx -> Color(0xFF1B5E20)
                                estAutoCalc                -> Color(0xFFE65100)  // orange = auto-calculé, non photographié
                                aDesErreurs                -> Color(0xFFB71C1C)  // rouge = erreurs détectées
                                analyseOk || estComplet    -> Color(0xFF2E7D32)  // vert = OK
                                else                       -> Color(0xFF37474F)
                            }),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(nom, fontSize = 11.sp, fontWeight = if (estActif) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (estActif) Color.Black else Color.White)
                                    Spacer(Modifier.width(3.dp))
                                    Text("📷", fontSize = 10.sp)
                                }
                                Text(
                                    when {
                                        isAnalyzing && joueurActif == idx -> "⏳"
                                        idx == joueurAutoRempliIdx -> "⏳ 13/13"
                                        estAutoCalc -> "⚠ Non photo"
                                        aDesErreurs -> "⚠ ${cartesCodes[idx].count { it != "?" && it.length >= 2 }}/13"
                                        else -> "${cartesCodes[idx].count { it != "?" && it.length >= 2 }}/13"
                                    },
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (estActif) Color.Black else Color.White)
                            }
                        }
                    }
                }


                // ── Diagnostic global (suspects uniquement — actions dans bottomBar) ──
                val toutesPhotographiees = (0..3).all { cartesCodes[it].isNotEmpty() }
                val diagGlobal      = if (toutesPhotographiees) diagnostiquerDonne(cartesCodes) else null
                val suspectsParMain = if (diagGlobal != null) calculerSuspectsDonne(diagGlobal, cartesCodes, confidences) else List(4) { emptySet<Int>() }
                // Doublons intra et inter-mains → orange immédiat sur les 2 occurrences
                val occDoublonPhoto = mutableMapOf<String, MutableList<Pair<Int,Int>>>()
                for (jIdx in 0..3) cartesCodes[jIdx].forEachIndexed { i, c ->
                    if (c != "?" && c.length >= 2) occDoublonPhoto.getOrPut(c) { mutableListOf() }.add(jIdx to i)
                }
                val erreursDoublonPhoto = occDoublonPhoto.values.filter { it.size > 1 }.flatten().toSet()

                // ── Avertissement main auto-calculée ─────────────────────────
                if (joueurActif in autocalcules) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100))) {
                        Text(
                            "⚠ ${joueurs[joueurActif]} non photographié — calculé depuis les 3 autres mains.\nUne détection erronée dans une autre main produirait une carte incorrecte ici. Vérifiez CHAQUE carte.",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // ── Zone scrollable : les 4 joueurs ──────────────────────────
                Column(Modifier.weight(1f).verticalScroll(scrollState)) {
                    joueurs.forEachIndexed { idx, nom ->
                        val estActif   = joueurActif == idx
                        val estComplet = mainComplete(idx)
                        val codes      = cartesCodes[idx]

                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f),
                                color = when { estActif -> Color.Yellow; estComplet -> Color(0xFF80CBC4); else -> Color.White },
                                thickness = if (estActif) 2.dp else 1.dp)
                            Text("  $nom (${codes.count { it != "?" && it.length >= 2 }}/13)  ",
                                color = when { estActif -> Color.Yellow; estComplet -> Color(0xFF80CBC4); else -> Color.White },
                                fontWeight = if (estActif) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 13.sp)
                            HorizontalDivider(Modifier.weight(1f),
                                color = when { estActif -> Color.Yellow; estComplet -> Color(0xFF80CBC4); else -> Color.White },
                                thickness = if (estActif) 2.dp else 1.dp)
                        }

                        // Cartes — 13 par ligne (+ ligne 2 si main > 13)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardW = maxWidth.value / 13f
                            val cardH = cardW * 2.2f
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    for (i in 0 until minOf(13, codes.size)) {
                                        val code = codes.getOrNull(i)
                                        CardSlotPhoto(
                                            code = code,
                                            isSelected = estActif && i == selectedCardIdx,
                                            isSuspect  = suspectsParMain[idx].contains(i),
                                            isErreur   = (idx to i) in erreursDoublonPhoto,
                                            onClick = {
                                                if (joueurActif != idx) { joueurActif = idx; selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                                if (selectedCardIdx == i && joueurActif == idx) {
                                                    selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                                } else {
                                                    selectedCardIdx = i
                                                    kbValue = if (code != null && code != "?" && code.length >= 2) code.dropLast(1) else ""
                                                    kbSuit  = if (code != null && code != "?" && code.length >= 1) code.last().toString().takeIf { it in listOf("P","C","K","T") } ?: "" else ""
                                                }
                                            },
                                            widthDp = cardW, heightDp = cardH
                                        )
                                    }
                                }
                                if (codes.size > 13) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                        for (i in 13 until codes.size) {
                                            val code = codes.getOrNull(i)
                                            CardSlotPhoto(
                                                code = code,
                                                isSelected = estActif && i == selectedCardIdx,
                                                isSuspect  = suspectsParMain[idx].contains(i),
                                                isErreur   = (idx to i) in erreursDoublonPhoto,
                                                onClick = {
                                                    if (joueurActif != idx) { joueurActif = idx; selectedCardIdx = -1; kbValue = ""; kbSuit = "" }
                                                    if (selectedCardIdx == i && joueurActif == idx) {
                                                        selectedCardIdx = -1; kbValue = ""; kbSuit = ""
                                                    } else {
                                                        selectedCardIdx = i
                                                        kbValue = if (code != null && code != "?" && code.length >= 2) code.dropLast(1) else ""
                                                        kbSuit  = if (code != null && code != "?" && code.length >= 1) code.last().toString().takeIf { it in listOf("P","C","K","T") } ?: "" else ""
                                                    }
                                                },
                                                widthDp = cardW, heightDp = cardH
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
    if (cameraOuverte) {
        val f = capturedPhotoFile
        if (f != null) {
            key(f.absolutePath) {
                CameraPreviewScreen(
                    outputFile   = f,
                    label        = joueurs[joueurActif],
                    onPhotoPrise = {
                        val file = capturedPhotoFile
                        cameraOuverte = false
                        if (file != null) traiterPhoto(file)
                    },
                    onAnnulee = { cameraOuverte = false }
                )
            }
        }
    }
    } // Box
}

// Slot carte photo — style identique à la saisie manuelle (valeur + symbole)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun CardSlotPhoto(
    code: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    widthDp: Float,
    heightDp: Float,
    isSuspect: Boolean = false,
    isAutoCorrige: Boolean = false,
    isErreur: Boolean = false
) {
    val isHole = code == null || code == "?" || code.length < 2
    val cardShape = RoundedCornerShape(topStart = 6.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 6.dp)
    Box(
        modifier = Modifier
            .width(widthDp.dp).height(heightDp.dp)
            .clip(cardShape)
            .background(when {
                code == null  -> Color(0xFFF0F0F0)
                isHole        -> Color(0xFFE8E8E8)
                isErreur      -> Color(0xFFFF8F00)
                isAutoCorrige -> Color(0xFFFF8F00)
                isSuspect     -> Color(0xFFFF8A65)
                else          -> Color(0xFFFFF9E6)
            })
            .then(if (isSelected) Modifier.border(2.dp, Color.Yellow, cardShape) else Modifier.border(1.dp, Color(0xFFBBBBBB), cardShape))
            .clickable(enabled = code != null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (code != null) {
            if (isHole) {
                Text("?", color = Color.LightGray, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            } else {
                val valeur   = code.dropLast(1)
                val couleur  = code.last().toString()
                val symColor = when (couleur) { "C", "K" -> Color(0xFFB71C1C); else -> Color.Black }
                Column(
                    modifier = Modifier.padding(horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(valeur, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color.Black)
                    SuiteSymbol(couleur = couleur, color = symColor, sizeDp = 18f)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode MANUEL — inchangé
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ManuelSaisieContent(
    numeroDonne: Int,
    blocs: SnapshotStateList<SnapshotStateList<Carte?>>,
    selectedHands: List<SnapshotStateList<Carte>>,
    onRetour: () -> Unit,
    onEnregistrer: (List<List<Carte>>) -> Unit
) {
    val joueurs = listOf("Nord","Est","Sud","Ouest")
    var joueurActif by remember { mutableStateOf(0) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPaddingDp = 8
    val spacingDp = 4
    val cardsInRow = 7
    val cardWidthDp  = ((screenWidthDp - 2 * horizontalPaddingDp - (cardsInRow - 1) * spacingDp) / cardsInRow.toFloat()).coerceAtLeast(36f)
    val cardHeightDp = cardWidthDp * 1.12f
    // Cartes de main : même style que l'écran photo (13 cartes sur 1 ligne)
    val handCardWidthDp  = ((screenWidthDp - 2 * horizontalPaddingDp) / 13f).coerceAtLeast(20f)
    val handCardHeightDp = handCardWidthDp * 2.2f

    fun couleurSymbole(codeCouleur: String) = when (codeCouleur) {
        "P","T" -> Color.Black; "C","K" -> Color(0xFFB71C1C); else -> Color.Black
    }

    fun returnCardToBloc(carte: Carte) = returnCardToBlocFn(carte, blocs)
    fun trierMain(main: MutableList<Carte>) = trierMainFn(main)

    val toutesCompletes = selectedHands.all { it.size == 13 }
    var alerteIdx      by remember { mutableStateOf(-1) }
    var prochainJoueur by remember { mutableStateOf(-1) }

    if (alerteIdx >= 0) {
        val nomAlerte = joueurs[alerteIdx]
        val nbAlerte  = selectedHands[alerteIdx].size
        AlertDialog(
            onDismissRequest = { alerteIdx = -1; prochainJoueur = -1 },
            title = { Text("Main incomplète") },
            text  = { Text("$nomAlerte n'a que $nbAlerte/13 cartes. Continuer quand même ?") },
            confirmButton = {
                TextButton(onClick = {
                    alerteIdx = -1
                    if (prochainJoueur >= 0) { joueurActif = prochainJoueur; prochainJoueur = -1 }
                }) { Text("Continuer") }
            },
            dismissButton = {
                TextButton(onClick = { alerteIdx = -1; prochainJoueur = -1 }) { Text("Corriger") }
            }
        )
    }

    EcranPleinScaffold {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Row(Modifier.fillMaxWidth().background(Color(0xFF0B6623)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(60.dp))
                    Button(onClick = { onEnregistrer(selectedHands.map { it.toList() }) },
                        enabled = toutesCompletes) { Text("Enregistrer") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onRetour() }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Box(Modifier.size(52.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Text("Retour", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { innerPadding ->
            Column(
                Modifier.padding(innerPadding).fillMaxSize()
                    .background(Color(0xFF0B6623))
                    .padding(horizontal = horizontalPaddingDp.dp, vertical = 8.dp)
            ) {
                if (BuildConfig.DEBUG) Text("[ Saisie manuelle donne ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Donne N° $numeroDonne", color = Color.White, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = TextAlign.Center)

                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    joueurs.forEachIndexed { idx, nom ->
                        val estActif = joueurActif == idx
                        val nbCartes = selectedHands[idx].size
                        Button(
                            onClick = {
                                if (idx == joueurActif) return@Button
                                if (selectedHands[joueurActif].size < 13 && selectedHands[joueurActif].isNotEmpty()) {
                                    alerteIdx = joueurActif; prochainJoueur = idx
                                } else {
                                    if (idx == 3 && selectedHands[3].isEmpty()) {
                                        val restantes = blocs.flatten().filterNotNull()
                                        if (restantes.size == 13) {
                                            selectedHands[3].addAll(restantes)
                                            blocs.forEach { bloc -> repeat(bloc.size) { i -> bloc[i] = null } }
                                        }
                                    }
                                    joueurActif = idx
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (estActif) Color(0xFFFFD54F) else Color(0xFF37474F)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(nom, fontSize = 12.sp,
                                    fontWeight = if (estActif) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (estActif) Color.Black else Color.White)
                                Text("$nbCartes/13", fontSize = 10.sp,
                                    color = if (estActif) Color.Black else Color(0xFFB0BEC5))
                            }
                        }
                    }
                }

                val toutesDistribuees = blocs.all { bloc -> bloc.all { it == null } }
                val hauteurMains = ((handCardHeightDp + 32f) * 2).dp
                val scrollStateMains = rememberScrollState()
                val density = LocalDensity.current

                LaunchedEffect(joueurActif) {
                    val hauteurParJoueurPx = with(density) { (handCardHeightDp + 32f).dp.roundToPx() }
                    scrollStateMains.animateScrollTo(joueurActif * hauteurParJoueurPx)
                }

                Column(
                    modifier = Modifier
                        .then(if (toutesDistribuees) Modifier.weight(1f) else Modifier.height(hauteurMains))
                        .verticalScroll(scrollStateMains)
                ) {
                    joueurs.forEachIndexed { idx, nom ->
                        val estActif = joueurActif == idx
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = if (estActif) Color.Yellow else Color.White, thickness = if (estActif) 2.dp else 1.dp)
                            Text("  $nom (${selectedHands[idx].size}/13)  ",
                                color = if (estActif) Color.Yellow else Color.White,
                                fontWeight = if (estActif) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 14.sp)
                            HorizontalDivider(Modifier.weight(1f), color = if (estActif) Color.Yellow else Color.White, thickness = if (estActif) 2.dp else 1.dp)
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardW = maxWidth.value / 13f
                            val cardH = cardW * 2.2f
                            Row(Modifier.fillMaxWidth()) {
                                for (i in 0 until 13) {
                                    val carte = selectedHands[idx].getOrNull(i)
                                    val code = if (carte != null) "${carte.valeur}${carte.couleur}" else null
                                    CardSlotPhoto(
                                        code = code,
                                        isSelected = false,
                                        onClick = {
                                            if (carte != null) {
                                                selectedHands[idx].remove(carte)
                                                returnCardToBloc(carte)
                                                joueurActif = idx
                                            }
                                        },
                                        widthDp = cardW,
                                        heightDp = cardH
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (!toutesDistribuees) {
                    HorizontalDivider(color = Color(0xFFFFD54F), thickness = 2.dp)
                    Spacer(Modifier.height(4.dp))
                }

                if (!toutesDistribuees) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    blocs.forEachIndexed { _, bloc ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (pos in 0 until 7) {
                                val carte = bloc.getOrNull(pos)
                                CardSlot(carte, cardWidthDp, cardHeightDp, {
                                    if (carte == null || selectedHands[joueurActif].size >= 13) return@CardSlot
                                    bloc[pos] = null
                                    selectedHands[joueurActif].add(carte)
                                    trierMain(selectedHands[joueurActif])
                                }, if (carte != null) couleurSymbole(carte.couleur) else Color.Transparent)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingDp.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (pos in 7 until 13) {
                                val carte = bloc.getOrNull(pos)
                                CardSlot(carte, cardWidthDp, cardHeightDp, {
                                    if (carte == null || selectedHands[joueurActif].size >= 13) return@CardSlot
                                    bloc[pos] = null
                                    selectedHands[joueurActif].add(carte)
                                    trierMain(selectedHands[joueurActif])
                                }, if (carte != null) couleurSymbole(carte.couleur) else Color.Transparent)
                            }
                            Spacer(Modifier.width(cardWidthDp.dp).height(cardHeightDp.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composables partagés
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HandPreview(
    hand: List<Carte>, cardWidthDp: Float = 0f, cardHeightDp: Float = 0f,
    couleurSymbole: (String) -> Color = { Color.Black }, spacingDp: Int = 2,
    onCardClick: (Carte) -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = spacingDp.dp
        val cw = (maxWidth - spacing * 12) / 13
        val ch = cw * 2.2f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            for (i in 0..12) {
                val c = hand.getOrNull(i)
                CarteUnifiee(
                    carte = c,
                    largeur = cw,
                    hauteur = ch,
                    shape = RoundedCornerShape(4.dp),
                    valeurSizeSp = (cw.value * 0.48f).coerceIn(9f, 20f),
                    symbolSizeDp = (cw.value * 0.45f).coerceIn(9f, 18f),
                    onClick = if (c != null) { { onCardClick(c) } } else null
                )
            }
        }
    }
}

@Composable
private fun CardSlot(carte: Carte?, widthDp: Float, heightDp: Float, onClick: () -> Unit, symbolColor: Color) {
    Box(
        modifier = Modifier.width(widthDp.dp).height(heightDp.dp).clip(RoundedCornerShape(10.dp))
            .background(if (carte == null) Color(0xFFF5F5F5) else Color(0xFFFFF9E6))
            .clickable(enabled = carte != null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (carte != null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text(carte.valeur, fontWeight = FontWeight.Black,
                    fontSize = (widthDp * 0.42f).coerceIn(12f, 20f).sp, color = Color.Black)
                Spacer(Modifier.weight(0.2f))
                SuiteSymbol(couleur = carte.couleur, color = symbolColor,
                    sizeDp = (widthDp * 0.40f).coerceIn(11f, 18f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lecture seule
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AffichageMainsLectureScreen(numeroDonne: Int, mains: List<List<Carte>>, onRetour: () -> Unit) {
    val joueurs = listOf("Nord","Est","Sud","Ouest")

    EcranPleinScaffold {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Row(Modifier.fillMaxWidth().background(Color(0xFF0B6623)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = onRetour) { Text("OK") }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().background(Color(0xFF0B6623)).padding(8.dp)) {
                if (BuildConfig.DEBUG) Text("[ Visionnage mains enregistrées ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onRetour() }.padding(4.dp)) {
                        Box(Modifier.size(48.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Text("Retour", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Donne N° $numeroDonne", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    }
                    Spacer(Modifier.size(48.dp))
                }
                val ordreC = mapOf("P" to 0, "C" to 1, "T" to 2, "K" to 3)
                val ordreV = mapOf("A" to 0,"R" to 1,"D" to 2,"V" to 3,"10" to 4,"9" to 5,"8" to 6,"7" to 7,"6" to 8,"5" to 9,"4" to 10,"3" to 11,"2" to 12)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (i in 0..3) {
                        Text("Main de ${joueurs[i]}", color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                        val mainTriee = (mains.getOrNull(i) ?: emptyList())
                            .sortedWith(compareBy({ ordreC[it.couleur] ?: 99 }, { ordreV[it.valeur] ?: 99 }))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardW = maxWidth.value / 13f
                            val cardH = cardW * 2.2f
                            Row(Modifier.fillMaxWidth()) {
                                for (j in 0 until 13) {
                                    val carte = mainTriee.getOrNull(j)
                                    val code = if (carte != null) "${carte.valeur}${carte.couleur}" else null
                                    CardSlotPhoto(code = code, isSelected = false, onClick = {}, widthDp = cardW, heightDp = cardH)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
