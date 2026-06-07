package app.resultatsbridge.common.model

object JeuDeCartes {

    private val valeurs = listOf("A", "R", "D", "V", "10", "9", "8", "7", "6", "5", "4", "3", "2")

    private val couleurs = listOf("P", "C", "K", "T")

    val toutesLesCartes: List<Carte> by lazy {
        val cartes = mutableListOf<Carte>()

        for (couleur in couleurs) {
            for (valeur in valeurs) {
                cartes.add(Carte(valeur, couleur))
            }
        }
        cartes
    }
}