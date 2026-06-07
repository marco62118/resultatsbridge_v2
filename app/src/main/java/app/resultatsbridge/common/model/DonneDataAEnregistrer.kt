package app.resultatsbridge.common.model

data class DonneDataAEnregistrer(
    val contrat: String, //3C, 3SA 3K
    val declarant: String, //N,S,E,O
    val signe: String, // "+", "=", "-"
    val points: Int,
    val plis: Int,//nombre de plis en plus ou de chutte
    val carteEntame: String, //2 à 10, V ,D,R,As ex AsK
    val numeroDonne: Int,
    val indexDonneJouee: Int,
    val mvntNumero: Int,
    val equipeNS: Int? = null, // numéro d'équipe dans le tournoi
    val equipeEO: Int? = null,
    val numeroTable: Int? = null,
    val historique: List<Tour>? = null,
    val mains: List<List<String>>? = null

)
