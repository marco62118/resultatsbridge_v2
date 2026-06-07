package app.resultatsbridge.common.ui.components

import androidx.compose.ui.graphics.Color

// Point d'entrée unique pour les couleurs et symboles des 4 couleurs du bridge.
// Modifier ici change TOUT dans l'app.
object SuiteStyle {

    val rouge  = Color(0xFFB71C1C)
    val noir   = Color.Black

    // Couleur d'affichage à partir d'un CODE ("P","C","K","T")
    fun couleurCode(code: String): Color = when (code) {
        "C", "K" -> rouge
        else     -> noir
    }

    // Couleur d'affichage à partir d'un SYMBOLE ("♠","♥","♦","♣")
    fun couleurSymbole(symbole: String): Color = when (symbole) {
        "♥", "♦" -> rouge
        else     -> noir
    }

    // Symbole Unicode à partir d'un CODE
    fun symbole(code: String): String = when (code) {
        "P" -> "♠"; "C" -> "♥"; "K" -> "♦"; "T" -> "♣"; else -> "?"
    }

    // CODE à partir d'un SYMBOLE (pour les dropdowns qui stockent "♣" etc.)
    fun code(symbole: String): String = when (symbole) {
        "♠" -> "P"; "♥" -> "C"; "♦" -> "K"; "♣" -> "T"; else -> ""
    }
}
