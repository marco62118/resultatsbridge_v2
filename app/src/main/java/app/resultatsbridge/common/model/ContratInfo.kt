package app.resultatsbridge.common.model


data class ContratInfo(
    val niveau: Int = 0, //niveau du contrat 1 à 7; 0 passe général
    val couleur: String = "", //Trèfle Carreau Coeur Pique Sans Atout
    val declarant: String = "", // rien contre  surcontre
    val insulte: String = "", //Joueur qui joue le contrat N, S, E, O
    // première carte jouée par l'aversaire
    val entameCarte: String = "", // la hauteur de la carte
    val entameCouleur: String = "", // la couleur de la carte
    val historique: List<Tour>? = null,//liste des enchères si pas null
    val signe: String = "=",
    val plis: Int = 0,
    val points: Int = 0
)
