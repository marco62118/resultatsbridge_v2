package app.resultatsbridge.common.utils


fun calculerPoints(
    insulte: String,       // "", "X", "XX"
    couleur: String,       // "♣", "♦", "♥", "♠", "SA"
    vulnerable: String,    // "T","P","NS","EO"
    declarant: String,     // "Nord","Sud","Est","Ouest"
    niveau: Int,           // 1..7
    signe: String,         // "=", "+", "-"
    plis: Int              // nb levées supplémentaires ou manquantes selon signe
): Int {
    val multiplicateur = when (insulte) {
        "" -> 1
        "X" -> 2
        "XX" -> 4
        else -> 1
    }

    val estVul = when (vulnerable.uppercase()) {
        "T" -> true
        "P" -> false
        "NS" -> declarant.equals("Nord", true) || declarant.equals("Sud", true)
        "EO" -> declarant.equals("Est", true) || declarant.equals("Ouest", true)
        else -> false
    }

    val baseDemandee = if (couleur == "SA") {
        40 + (niveau - 1) * 30
    } else {
        when (couleur) {
            "♣", "♦" -> niveau * 20
            "♥", "♠" -> niveau * 30
            else -> 0
        }
    }

    // 🔻 CHUTE
    if (signe == "-") {
        val chutes = plis
        return when (insulte) {
            "" -> if (estVul) 100 * chutes else 50 * chutes
            "X" -> {
                if (!estVul) {
                    when (chutes) {
                        1 -> 100
                        2 -> 300
                        3 -> 500
                        else -> 500 + (chutes - 3) * 300
                    }
                } else {
                    when (chutes) {
                        1 -> 200
                        2 -> 500
                        3 -> 800
                        else -> 800 + (chutes - 3) * 300
                    }
                }
            }
            "XX" -> {
                if (!estVul) {
                    when (chutes) {
                        1 -> 200
                        2 -> 600
                        3 -> 1000
                        else -> 1000 + (chutes - 3) * 600
                    }
                } else {
                    when (chutes) {
                        1 -> 400
                        2 -> 1000
                        3 -> 1600
                        else -> 1600 + (chutes - 3) * 600
                    }
                }
            }
            else -> 0
        }
    }

    // ✅ RÉUSSI (= ou +)
    val pointsBase = multiplicateur * baseDemandee
    val surlevees = if (signe == "+") plis else 0

    val pointsSurlevees = when {
        surlevees == 0 -> 0
        insulte == "" -> {
            val valeur = if (couleur == "SA") 30 else when (couleur) {
                "♣", "♦" -> 20
                "♥", "♠" -> 30
                else -> 0
            }
            surlevees * valeur
        }
        insulte == "X" -> surlevees * if (estVul) 200 else 100
        insulte == "XX" -> surlevees * if (estVul) 400 else 200
        else -> 0
    }

    val primeManche = if (pointsBase >= 100) {
        if (estVul) 500 else 300
    } else {
        50
    }

    val primeContre = when (insulte) {
        "X" -> 50
        "XX" -> 100
        else -> 0
    }

    val primeChelem = when (niveau) {
        6 -> if (estVul) 750 else 500
        7 -> if (estVul) 1500 else 1000
        else -> 0
    }

    return pointsBase + pointsSurlevees + primeManche + primeContre + primeChelem
}
