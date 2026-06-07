package app.resultatsbridge.common.model

data class DonneDetail(
    val numero: Int, //numéro de la donne dans le tournoi
    val donneur: String, // N, S, E ,O
    val vulnerable: String, // vunérablité tous, personne, NS, EO
    val mains: List<List<String>>? = null
)

