package app.resultatsbridge.common.model

data class ClassementItem(
    val rang: Int,
    val numeroEquipe: Int,
    val pts: Double,
    val joueur1Nom: String,
    val joueur1Prenom: String,
    val joueur2Nom: String,
    val joueur2Prenom: String,
    val orientationMitchell: String? = null,  // "NS", "EO", ou null si pas Mitchell
    val scorePct: Double = 0.0                // pourcentage Neuberg
)
