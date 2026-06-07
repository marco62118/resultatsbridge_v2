package app.resultatsbridge.common.model

import android.util.Log
import kotlin.collections.iterator

object ClassementManager {

    /**
     * Calcule le classement à partir des résultats récupérés du serveur
     * Retourne AUSSI les points Simplifiés par donne pour l'enregistrement en BD
     */
    fun calculerClassementTournoi(
        resultats: List<ResultatLigne>
    ): ResultatCalcul {
        Log.i("ClassementManager", "🏁 Début calcul classement à partir des résultats fournis")

        // 1️⃣ Regrouper les résultats par donne
        val resultatsParDonne = mutableMapOf<Int, MutableList<ResultatLigne>>()
        for (ligne in resultats) {
            val key = ligne.numeroDonne
            if (!resultatsParDonne.containsKey(key)) resultatsParDonne[key] = ArrayList()
            resultatsParDonne[key]!!.add(ligne)
        }

        Log.i("ClassementManager", "📊 ${resultatsParDonne.size} donnes regroupées")

        // 2️⃣ Calcul des points par donne (barème Simplifiés simplifié)
        val pointsParEquipe  = mutableMapOf<Int, Double>() // numeroEquipe -> totalPts
        val dealsParEquipe   = mutableMapOf<Int, Int>()    // numeroEquipe -> nb de donnes jouées

        // ✅ NOUVEAU : Stocker les points Simplifiés par donne
        val pointsSimplifiesParDonne = mutableListOf<ResultatLigneAvecPointsSimplifies>()

        val nTables = resultats.map { it.numeroTable }.distinct().size
        // N = référence Neuberg = nombre max de fois où une donne est jouée
        val N    = resultatsParDonne.values.maxOf { it.size }
        val Ntop = (N - 1) * 2  // TOP de référence commun à toutes les donnes

        Log.i("ClassementManager", "🎯 Nombre de tables: $nTables, N (Neuberg ref): $N, Ntop: $Ntop")

        for ((numeroDonne, lignes) in resultatsParDonne) {
            val nFois = lignes.size
            val top   = (nFois - 1) * 2
            Log.i("ClassementManager", "📋 Donne $numeroDonne jouée $nFois fois → TOP brut=$top, Neuberg N=$N")

            // ✅ CORRECTION CRITIQUE : Calculer le score du point de vue NS
            // Si pointsNS existe → score positif pour NS
            // Si pointsEO existe → score négatif pour NS (car c'est EO qui a gagné)

            data class LigneAvecScore(
                val ligne: ResultatLigne,
                val scoreNS: Int  // Score du point de vue NS (peut être négatif)
            )

            val lignesAvecScores = lignes.map { ligne ->
                val scoreNS = if (ligne.pointsNS != null) {
                    ligne.pointsNS        // NS a gagné (positif)
                } else {
                    -ligne.pointsEO!!     // EO a gagné → négatif pour NS
                }
                LigneAvecScore(ligne, scoreNS)
            }

            // Trier par score NS (meilleur au pire)
            val scoresNS = lignesAvecScores.map { it.scoreNS }.sortedDescending()

            Log.i("ClassementManager", "📋 Donne $numeroDonne - Scores NS triés: $scoresNS")

            val pointsAttribues = mutableMapOf<Int, Double>()

            // Calculer les points pour chaque score unique
            val scoresUniques = scoresNS.distinct()

            for (score in scoresUniques) {
                val indicesEgaux = scoresNS.withIndex()
                    .filter { it.value == score }
                    .map { it.index }

                // ✅ CORRECTION : Utiliser average() pour avoir une vraie moyenne
                val moyennePlace = indicesEgaux.average()

                // Formule Simplifiés : top - (moyennePlace × pas)
                val pas       = if (nFois > 1) top.toDouble() / (nFois - 1) else 2.0
                val scoreReel = top - (moyennePlace * pas)

                // Formule de Neuberg : ajuste le score si cette donne a été jouée moins de N fois
                val scoreAjuste = if (nFois < N) {
                    ((N.toDouble() * (scoreReel + 1.0)) / nFois.toDouble()) - 1.0
                } else {
                    scoreReel
                }

                pointsAttribues[score] = scoreAjuste

                Log.i("ClassementManager",
                    "   Score $score → places $indicesEgaux → moy=${moyennePlace} → brut=${scoreReel} → Neuberg=${"%.1f".format(scoreAjuste)}")
            }

            // Attribution des points aux équipes
            for (ligneAvecScore in lignesAvecScores) {
                val ligne = ligneAvecScore.ligne
                val scoreNS = ligneAvecScore.scoreNS

                val ptsNS = pointsAttribues[scoreNS] ?: 0.0
                val ptsEO = Ntop.toDouble() - ptsNS  // complément Neuberg

                pointsParEquipe[ligne.equipeNS] = (pointsParEquipe[ligne.equipeNS] ?: 0.0) + ptsNS
                pointsParEquipe[ligne.equipeEO] = (pointsParEquipe[ligne.equipeEO] ?: 0.0) + ptsEO
                dealsParEquipe[ligne.equipeNS]  = (dealsParEquipe[ligne.equipeNS]  ?: 0) + 1
                dealsParEquipe[ligne.equipeEO]  = (dealsParEquipe[ligne.equipeEO]  ?: 0) + 1

                pointsSimplifiesParDonne.add(
                    ResultatLigneAvecPointsSimplifies(
                        numeroDonne = ligne.numeroDonne,
                        numeroTable = ligne.numeroTable,
                        equipeNS    = ligne.equipeNS,
                        equipeEO    = ligne.equipeEO,
                        pointsNS    = ligne.pointsNS,
                        pointsEO    = ligne.pointsEO,
                        ptsNS       = ptsNS,
                        ptsEO       = ptsEO
                    )
                )

                Log.i("ClassementManager",
                    "🧮 Donne ${ligne.numeroDonne} Table ${ligne.numeroTable} : " +
                            "Score NS=$scoreNS → Neuberg NS=${"%.1f".format(ptsNS)} EO=${"%.1f".format(ptsEO)}")
            }
        }

        // 3️⃣ Attribution des rangs
        val classement = ArrayList<ClassementEquipe>()
        val sorted = pointsParEquipe.entries.sortedByDescending { it.value }
        var rang = 1
        var precedentScore = Double.MIN_VALUE

        for ((index, entry) in sorted.withIndex()) {
            val numeroEquipe = entry.key
            val totalPts = entry.value
            if (totalPts != precedentScore) {
                rang = index + 1
                precedentScore = totalPts
            }
            val nDeals   = dealsParEquipe[numeroEquipe] ?: 0
            val scorePct = if (nDeals > 0 && Ntop > 0) (totalPts / (nDeals.toDouble() * Ntop)) * 100.0 else 0.0
            classement.add(ClassementEquipe(numeroEquipe, totalPts, rang, scorePct))
            Log.i("ClassementManager", "🥇 Équipe $numeroEquipe → ${"%.1f".format(totalPts)} pts (rang $rang)")
        }

        Log.i("ClassementManager", "✅ Classement calculé")

        // ✅ Retourner à la fois le classement ET les points par donne
        return ResultatCalcul(classement, pointsSimplifiesParDonne)
    }

    // ✅ Data class pour retourner les deux résultats
    data class ResultatCalcul(
        val classement: List<ClassementEquipe>,
        val pointsParDonne: List<ResultatLigneAvecPointsSimplifies>
    )

    // ✅ Data class avec les points Simplifiés
    data class ResultatLigneAvecPointsSimplifies(
        val numeroDonne: Int,
        val numeroTable: Int,
        val equipeNS: Int,
        val equipeEO: Int,
        val pointsNS: Int?,    // Points bruts
        val pointsEO: Int?,    // Points bruts
        val ptsNS: Double,     // Points Simplifiés Neuberg
        val ptsEO: Double      // Points Simplifiés Neuberg
    )

    data class ResultatLigne(
        val numeroDonne: Int,
        val numeroTable: Int,
        val equipeNS: Int,
        val equipeEO: Int,
        val pointsNS: Int?,
        val pointsEO: Int?
    )

    data class ClassementEquipe(
        val numeroEquipe: Int,
        val totalPts: Double,
        val rang: Int,
        val scorePct: Double = 0.0
    )
}