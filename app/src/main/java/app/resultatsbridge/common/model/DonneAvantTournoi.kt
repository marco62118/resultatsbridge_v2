package app.resultatsbridge.common.model

data class DonneAvantTournoi(
    val numeroDonne: Int,
    val donneur: String,
    val vulnerable: String,
    val mains: List<List<String>>
)
