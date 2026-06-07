package app.resultatsbridge.common.model

data class EtatEnchere(
    val derniereAnnonceur: Int? = null,   // joueur qui a parlé en dernier (0=Ouest,1=Nord,2=Est,3=Sud)
    val derniereEnchere: Enchere? = null, // ex : 2♥
    val contreActif: Int? = null,         // joueur qui a contré (null si aucun)
    val surcontreActif: Int? = null       // joueur qui a surcontré (null si aucun)
)

