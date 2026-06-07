package app.resultatsbridge.common.model

data class DonneResultatDetail(
    val numeroDonne: Int,
    val equipeNS: Int,
    val equipeEO: Int,
    val contrat: String,
    val declarant: String,
    val resultatContrat: String,
    val nombrePli: Int,
    val carteEntame: String,
    val pointsNS: Int,
    val pointsEO: Int,
    val ptsNS: Double,
    val ptsEO: Double,
    val vulnerable: String  // ✅ AJOUTER
)