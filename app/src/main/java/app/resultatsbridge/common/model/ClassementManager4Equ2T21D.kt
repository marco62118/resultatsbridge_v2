package app.resultatsbridge.common.model

import android.util.Log

/**
 * =============================================================================
 * ClassementManager4Equ2T21D
 * =============================================================================
 * Calcul du classement pour le tournoi type "par4equ2t21d" :
 *   - 4 équipes (numéros 1=A, 2=B, 3=C, 4=D)
 *   - 2 tables, 3 mouvements, 7 donnes par mouvement (21 donnes au total)
 *
 * LOGIQUE DE CALCUL :
 * ─────────────────────────────────────────────────────────────────────────────
 * Étape 1 — Croisement des 2 tables pour chaque donne :
 *   recapNS = pointsNS(table1) + pointsEO(table2)
 *   recapEO = pointsEO(table1) + pointsNS(table2)
 *
 * Étape 2 — Calcul de la différence :
 *   diff = recapNS - recapEO
 *   • diff > 0 → camp NS gagne : EBL(diff) aux équipes NS récap, 0 aux équipes EO récap
 *   • diff < 0 → camp EO gagne : EBL(|diff|) aux équipes EO récap, 0 aux équipes NS récap
 *   • diff = 0 → égalité : 0 aux deux camps
 *
 * Étape 3 — Attribution aux équipes selon le mouvement :
 *   Mvnt 1 (donnes  1- 7) : équipes NS récap = {1,4}  | équipes EO récap = {2,3}
 *   Mvnt 2 (donnes  8-14) : équipes NS récap = {1,2}  | équipes EO récap = {3,4}
 *   Mvnt 3 (donnes 15-21) : équipes NS récap = {1,3}  | équipes EO récap = {2,4}
 *
 * Étape 4 — Classement final par total de points EBL décroissant.
 * =============================================================================
 */
object ClassementManager4Equ2T21D {

    private const val TAG = "ClasseManager4Equ2T21D"

    // -------------------------------------------------------------------------
    // Structures de données
    // -------------------------------------------------------------------------

    /** Ligne de résultat brut lue depuis la table "resultats" */
    data class ResultatLigne(
        val numeroDonne : Int,
        val numeroTable : Int,
        val equipeNS    : Int,
        val equipeEO    : Int,
        val pointsNS    : Int?,   // null si le camp NS n'a pas marqué
        val pointsEO    : Int?    // null si le camp EO n'a pas marqué
    )

    /**
     * Points calculés pour une donne après croisement et application du barème EBL.
     * ptsEblNS / ptsEblEO sont les points attribués aux camps NS récap et EO récap.
     * L'un des deux vaut toujours 0 (le perdant), sauf égalité (les deux à 0).
     */
    data class DonnePoints(
        val numeroDonne : Int,
        val recapNS     : Int,    // pointsNS_T1 + pointsEO_T2
        val recapEO     : Int,    // pointsEO_T1 + pointsNS_T2
        val diff        : Int,    // recapNS - recapEO (positif = NS gagne, négatif = EO gagne)
        val ptsEblNS    : Int,    // EBL attribué aux équipes NS récap (0 si perdant)
        val ptsEblEO    : Int     // EBL attribué aux équipes EO récap (0 si perdant)
    )

    /** Classement final d'une équipe */
    data class ClassementEquipe(
        val numeroEquipe : Int,
        val totalPtsEbl  : Int,
        val rang         : Int
    )

    /** Résultat complet retourné par calculerClassement() */
    data class ResultatCalcul(
        val classement     : List<ClassementEquipe>,
        val pointsParDonne : List<DonnePoints>
    )

    // -------------------------------------------------------------------------
    // Attribution des équipes par mouvement
    // -------------------------------------------------------------------------

    /**
     * Retourne (équipesNSRecap, équipesEORecap) selon le numéro de mouvement.
     *
     * Mvnt 1 : NS récap → {1,4}  EO récap → {2,3}
     * Mvnt 2 : NS récap → {1,2}  EO récap → {3,4}
     * Mvnt 3 : NS récap → {1,3}  EO récap → {2,4}
     */
    private fun getEquipesPourMouvement(mvntNumero: Int): Pair<List<Int>, List<Int>> {
        return when (mvntNumero) {
            1    -> Pair(listOf(1, 4), listOf(2, 3))
            2    -> Pair(listOf(1, 2), listOf(3, 4))
            3    -> Pair(listOf(1, 3), listOf(2, 4))
            else -> Pair(emptyList(), emptyList())
        }
    }

    /**
     * Retourne le numéro de mouvement (1, 2 ou 3) selon le numéro de donne (1-21).
     */
    private fun getMouvementPourDonne(numeroDonne: Int): Int {
        return when (numeroDonne) {
            in 1..7   -> 1
            in 8..14  -> 2
            in 15..21 -> 3
            else      -> throw IllegalArgumentException("Numéro de donne invalide : $numeroDonne")
        }
    }

    // -------------------------------------------------------------------------
    // Barème EBL  (différence de points bruts → indice 0 à 25)
    // -------------------------------------------------------------------------

    /**
     * Convertit une différence de points bruts en points EBL (0 à 25).
     * S'applique sur la valeur ABSOLUE de la différence recapNS - recapEO.
     *
     * Barème officiel :
     *      0 –   10 →  0      491 –  590 → 11
     *     11 –   40 →  1      591 –  740 → 12
     *     41 –   80 →  2      741 –  890 → 13
     *     81 –  120 →  3      891 – 1090 → 14
     *    121 –  160 →  4     1091 – 1290 → 15
     *    161 –  210 →  5     1291 – 1490 → 16
     *    211 –  260 →  6     1491 – 1740 → 17
     *    261 –  310 →  7     1741 – 1990 → 18
     *    311 –  360 →  8     1991 – 2240 → 19
     *    361 –  420 →  9     2241 – 2490 → 20
     *    421 –  490 → 10     2491 – 2740 → 21
     *                        2741 – 2990 → 22
     *                        2991 – 3240 → 23
     *                        3241 – 3490 → 24
     *                        3491+       → 25
     */
    fun pointsVersEbl(points: Int): Int {
        if (points <= 0) return 0
        return when (points) {
            in 1..10       ->  0
            in 11..40      ->  1
            in 41..80      ->  2
            in 81..120     ->  3
            in 121..160    ->  4
            in 161..210    ->  5
            in 211..260    ->  6
            in 261..310    ->  7
            in 311..360    ->  8
            in 361..420    ->  9
            in 421..490    -> 10
            in 491..590    -> 11
            in 591..740    -> 12
            in 741..890    -> 13
            in 891..1090   -> 14
            in 1091..1290  -> 15
            in 1291..1490  -> 16
            in 1491..1740  -> 17
            in 1741..1990  -> 18
            in 1991..2240  -> 19
            in 2241..2490  -> 20
            in 2491..2740  -> 21
            in 2741..2990  -> 22
            in 2991..3240  -> 23
            in 3241..3490  -> 24
            else           -> 25
        }
    }

    // -------------------------------------------------------------------------
    // Calcul principal
    // -------------------------------------------------------------------------

    /**
     * Calcule le classement complet du tournoi par4equ2t21d.
     *
     * @param resultats  Toutes les lignes de la table "resultats" pour ce tournoi
     * @return ResultatCalcul contenant le classement + les points EBL par donne
     */
    fun calculerClassement(resultats: List<ResultatLigne>): ResultatCalcul {

        // Accumulation des points EBL totaux par équipe (1 à 4)
        val totalEblParEquipe = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)

        // Liste des points EBL par donne (pour mise à jour en base)
        val donnesPoints = mutableListOf<DonnePoints>()

        // Grouper par numéro de donne, trier par numéro croissant.
        // Conversion en List<Pair> pour éviter l'ambiguïté d'itérateur sur Map.Entry.
        val parDonne: List<Pair<Int, List<ResultatLigne>>> = resultats
            .groupBy { it.numeroDonne }
            .entries
            .sortedBy { it.key }
            .map { Pair(it.key, it.value) }

        // ── Traitement donne par donne ────────────────────────────────────────
        for ((numeroDonne, lignes) in parDonne) {

            // Récupérer les lignes des 2 tables pour cette donne
            val ligneTable1 = lignes.firstOrNull { it.numeroTable == 1 }
            val ligneTable2 = lignes.firstOrNull { it.numeroTable == 2 }

            if (ligneTable1 == null || ligneTable2 == null) {
                Log.w(TAG, "⚠️ Donne $numeroDonne : données manquantes " +
                        "(table1=${ligneTable1 != null}, table2=${ligneTable2 != null}) — ignorée")
                continue
            }

            // Points bruts de chaque équipe à sa table
            // T1 : equipeNS joue NS, equipeEO joue EO
            // T2 : equipeNS joue NS, equipeEO joue EO
            val ptsNS_eqNS_T1 = ligneTable1.pointsNS ?: 0  // pts NS de l'équipe NS à T1
            val ptsEO_eqEO_T1 = ligneTable1.pointsEO ?: 0  // pts EO de l'équipe EO à T1
            val ptsNS_eqNS_T2 = ligneTable2.pointsNS ?: 0  // pts NS de l'équipe NS à T2
            val ptsEO_eqEO_T2 = ligneTable2.pointsEO ?: 0  // pts EO de l'équipe EO à T2

            // Récapitulatifs des 2 groupes d'équipes liées selon le mouvement :
            //   Mvnt 1 → recapEq1Eq4 = ptsNS_eq1(T1) + ptsEO_eq4(T2)
            //            recapEq2Eq3 = ptsNS_eq3(T2) + ptsEO_eq2(T1)
            //   Mvnt 2 → recapEq1Eq2 = ptsNS_eq1(T1) + ptsEO_eq2(T2)
            //            recapEq3Eq4 = ptsNS_eq4(T2) + ptsEO_eq3(T1)
            //   Mvnt 3 → recapEq1Eq3 = ptsNS_eq1(T1) + ptsEO_eq3(T2)
            //            recapEq2Eq4 = ptsNS_eq2(T2) + ptsEO_eq4(T1)
            // Dans tous les cas : groupe1 = ptsNS_T1 + ptsEO_T2
            //                     groupe2 = ptsEO_T1 + ptsNS_T2
            val recapGroupe1 = ptsNS_eqNS_T1 + ptsEO_eqEO_T2
            val recapGroupe2 = ptsEO_eqEO_T1 + ptsNS_eqNS_T2

            // Différence et attribution EBL
            // diff > 0 → groupe1 gagne → EBL(diff) au groupe1, 0 au groupe2
            // diff < 0 → groupe2 gagne → EBL(|diff|) au groupe2, 0 au groupe1
            // diff = 0 → égalité → 0 aux deux groupes
            val diff = recapGroupe1 - recapGroupe2
            val ptsEblGroupe1: Int
            val ptsEblGroupe2: Int
            when {
                diff > 0 -> {
                    ptsEblGroupe1 = pointsVersEbl(diff)
                    ptsEblGroupe2 = 0
                }
                diff < 0 -> {
                    ptsEblGroupe1 = 0
                    ptsEblGroupe2 = pointsVersEbl(-diff)
                }
                else -> {
                    // Égalité parfaite : 0 point pour les deux groupes
                    ptsEblGroupe1 = 0
                    ptsEblGroupe2 = 0
                }
            }

            // Libellés des groupes selon le mouvement pour les logs
            val mvnt = getMouvementPourDonne(numeroDonne)
            val (libelleGroupe1, libelleGroupe2) = when (mvnt) {
                1    -> Pair("Éq1&4", "Éq2&3")
                2    -> Pair("Éq1&2", "Éq3&4")
                3    -> Pair("Éq1&3", "Éq2&4")
                else -> Pair("G1",    "G2")
            }

            Log.i(TAG,
                "🃏 Donne $numeroDonne (mvnt $mvnt) | " +
                        "ptsNS_T1=${ptsNS_eqNS_T1} ptsEO_T1=${ptsEO_eqEO_T1} | " +
                        "ptsNS_T2=${ptsNS_eqNS_T2} ptsEO_T2=${ptsEO_eqEO_T2} | " +
                        "recap$libelleGroupe1=${recapGroupe1} recap$libelleGroupe2=${recapGroupe2} | " +
                        "diff=$diff | " +
                        "EBL $libelleGroupe1=$ptsEblGroupe1  EBL $libelleGroupe2=$ptsEblGroupe2"
            )

            donnesPoints.add(DonnePoints(numeroDonne, recapGroupe1, recapGroupe2, diff, ptsEblGroupe1, ptsEblGroupe2))

            // ── Attribution aux équipes selon le mouvement ────────────────────
            val (equipesGroupe1, equipesGroupe2) = getEquipesPourMouvement(mvnt)

            for (eq in equipesGroupe1) {
                totalEblParEquipe[eq] = (totalEblParEquipe[eq] ?: 0) + ptsEblGroupe1
                Log.i(TAG, "   ➕ Équipe $eq ($libelleGroupe1) += $ptsEblGroupe1 → total=${totalEblParEquipe[eq]}")
            }
            for (eq in equipesGroupe2) {
                totalEblParEquipe[eq] = (totalEblParEquipe[eq] ?: 0) + ptsEblGroupe2
                Log.i(TAG, "   ➕ Équipe $eq ($libelleGroupe2) += $ptsEblGroupe2 → total=${totalEblParEquipe[eq]}")
            }
        }

        // ── Classement trié par points décroissants ───────────────────────────
        val classementTrie: List<ClassementEquipe> = totalEblParEquipe.entries
            .sortedByDescending { it.value }
            .mapIndexed { index, mapEntry ->
                ClassementEquipe(
                    numeroEquipe = mapEntry.key,
                    totalPtsEbl  = mapEntry.value,
                    rang         = index + 1
                )
            }

        Log.i(TAG, "🏆 Classement final par4equ2t21d :")
        for (c in classementTrie) {
            Log.i(TAG, "   Rang ${c.rang} → Équipe ${c.numeroEquipe} : ${c.totalPtsEbl} pts EBL")
        }

        return ResultatCalcul(classement = classementTrie, pointsParDonne = donnesPoints)
    }
}
