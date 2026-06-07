package app.resultatsbridge.common.model

data class Carte(
    val valeur: String, // A, R, D, V, 10, 9,...
    val couleur: String // P, C, K, T
) {

    // Get affichage lisible : "A♣"
    val affichage: String
        get() = "$valeur$symbole"

    // Conversion couleur → symbole
    val symbole: String
        get() = when (couleur) {
            "P" -> "♠"
            "C" -> "♥"
            "K" -> "♦"
            "T" -> "♣"
            else -> "?"
        }

    // Code compact pour sauvegarde DB ou JSON
    val code: String
        get() = valeur + couleur

    companion object {
        // Création depuis un code string ("AT" → As de Trèfle)
        fun fromCode(code: String): Carte {
            val valeur = code.dropLast(1) // tout sauf dernière lettre
            val couleur = code.last().toString() // dernière lettre
            return Carte(valeur, couleur)
        }
    }
}
