package app.resultatsbridge.common.model

data class DonneComplete(

val mains: Map<String, List<Carte>>,
val encheres: List<AnnonceJoueur>,
val vulnerable: String,  // "NS", "EO", "T", "P"
val donneur: String,
val contrat: String = "",     // ✅ AJOUT
val declarant: String = ""
)