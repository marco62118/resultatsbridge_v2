package app.resultatsbridge.organisateur.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import app.resultatsbridge.organisateur.data.DatabaseManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import app.resultatsbridge.common.model.MouvementResult
import app.resultatsbridge.common.model.TournoiConfig

class ClientServeurHTTP(port: Int = 8080, private val context: Context) : NanoHTTPD(port) {

    init {
        start(SOCKET_READ_TIMEOUT, false)
        Log.i("ClientServeurHTTP", "✅ Serveur démarré sur le port $port")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.i("ClientServeurHTTP", "🔗 Requête reçue : $uri")

        return when {
            uri.startsWith("/verifierTournoiOuvert") -> {

                // 🔧 Vérifie si un tournoi est ouvert aujourd'hui
                val tournoi = DatabaseManager.getTournoiOuvert(context)
                Log.i(
                    "ClientServeurHTTP", if (tournoi != null)
                        "✅ Tournoi ouvert : ID=${tournoi.first}, Type=${tournoi.second}"
                    else
                        "❌ Aucun tournoi ouvert aujourd'hui"
                )
                //return tournoi
                val id = tournoi?.first ?: 0
                val type = tournoi?.second ?: ""
                val json = JSONObject().apply {
                    put("id", id)
                    put("type", type)
                    put("nbreMouvements", TournoiConfig.NBRE_MOUVEMENTS)
                    put("nbreDonnesParTable", TournoiConfig.NBRE_DONNES_PAR_TABLE)
                    put("equipeRelais", TournoiConfig.EQUIPE_RELAIS ?: 0)
                    put("nbreTables", TournoiConfig.NBRE_TABLES)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }


            uri.startsWith("/verifierFinMouvement") -> {
                val params = session.parameters
                val idTournoi  = params["idTournoi"]?.firstOrNull()?.toIntOrNull() ?: 0
                val mvntNumero = params["mvntNumero"]?.firstOrNull()?.toIntOrNull() ?: 0
                val termine = DatabaseManager.toutesEquipesOntTermineMouvement(context, idTournoi, mvntNumero)
                Log.i("ClientServeurHTTP", "🏁 verifierFinMouvement idTournoi=$idTournoi mvnt=$mvntNumero → $termine")
                newFixedLengthResponse("{\"termine\": $termine}")
            }


            uri.startsWith("/getEquipesAyantJoueDonne") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull() ?: 0
                val numeroDonne = params["numeroDonne"]?.firstOrNull()?.toIntOrNull() ?: 0

                if (idTournoi > 0 && numeroDonne > 0) {
                    val equipes = DatabaseManager.getEquipesAyantJoueDonne(context, idTournoi, numeroDonne)

                    val jsonArray = JSONArray()  // ✅ Tableau direct
                    equipes.forEach { info ->
                        val obj = JSONObject().apply {
                            put("equipeNS", info.equipeNS)
                            put("equipeEO", info.equipeEO)
                            put("contrat", info.contrat)
                            put("declarant", info.declarant)
                        }
                        jsonArray.put(obj)
                    }

                    // ✅ RETOURNER LE TABLEAU DIRECTEMENT
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        jsonArray.toString()  // ✅ Pas d'objet wrapper !
                    )
                } else {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        "[]"  // ✅ Tableau vide direct
                    )
                }
            }

            uri.startsWith("/getEquipes") -> {
                // 🔧 Récupère toutes les équipes d'un tournoi
                val idTournoi = session.parameters["idTournoi"]?.firstOrNull()?.toIntOrNull()
                if (idTournoi != null) {
                    val equipes = DatabaseManager.getEquipesDuTournoi(context, idTournoi)
                    Log.i("ClientServeurHTTP", "✅ ${equipes.size} équipes récupérées pour tournoi $idTournoi")

                    // 🔧 Conversion en JSON
                    val jsonEquipes = JSONArray()
                    for (equipe in equipes) {
                        val jsonEquipe = JSONObject()
                        jsonEquipe.put("equipeNumero", equipe.equipeNumero)
                        jsonEquipe.put("idTournoi", equipe.idTournoi)

                        val jsonJoueur1 = JSONObject()
                        jsonJoueur1.put("idJoueur", equipe.joueur1.idJoueur)
                        jsonJoueur1.put("nom", equipe.joueur1.nom)
                        jsonJoueur1.put("prenom", equipe.joueur1.prenom)

                        val jsonJoueur2 = JSONObject()
                        jsonJoueur2.put("idJoueur", equipe.joueur2.idJoueur)
                        jsonJoueur2.put("nom", equipe.joueur2.nom)
                        jsonJoueur2.put("prenom", equipe.joueur2.prenom)

                        jsonEquipe.put("joueur1", jsonJoueur1)
                        jsonEquipe.put("joueur2", jsonJoueur2)

                        jsonEquipes.put(jsonEquipe)
                    }

                    val message = JSONObject()
                    message.put("type", "equipes")
                    message.put("tournoiId", idTournoi)
                    message.put("listeEquipes", jsonEquipes)

                   //return newFixedLengthResponse(Response.Status.OK, "application/json", message.toString())
                    Log.i("ClientServeurHTTP", "📤 JSON envoyé : $message")
                    return newFixedLengthResponse(Response.Status.OK, "application/json", message.toString())

                } else {
                    Log.e("ClientServeurHTTP", "❌ idTournoi manquant ou invalide")
                    return newFixedLengthResponse("❌ idTournoi manquant ou invalide")
                }
            }

            uri.startsWith("/getMouvement") -> {
                val idTournoi    = session.parameters["idTournoi"]?.firstOrNull()?.toIntOrNull()
                val equipeNumero = session.parameters["equipeNumero"]?.firstOrNull()?.toIntOrNull()

                Log.i("ClientServeurHTTP", "📥 /getMouvement appelé idTournoi=$idTournoi equipeNumero=$equipeNumero")

                if (idTournoi == null || equipeNumero == null) {
                    Log.e("ClientServeurHTTP", "❌ Paramètres manquants")
                    return newFixedLengthResponse(Response.Status.OK, "application/json",
                        JSONObject().apply { put("etat", "ERREUR_PARAM") }.toString())
                }

                val mouvementResult = DatabaseManager.getMouvementPourEquipe(context, idTournoi, equipeNumero)
                val gson = Gson()

                val jsonString: String = when (mouvementResult) {
                    is MouvementResult.Complet -> {
                        val mvnt = mouvementResult.mouvement
                        val reponseMap = mutableMapOf<String, Any>(
                            "etat"             to "MOUVEMENT",
                            "tousTermines"     to mouvementResult.tousTermines,
                            "mvntNumero"       to mvnt.mvntNumero,
                            "tableNumero"         to mvnt.tableNumero,
                            "indexDonneAJouer"    to mvnt.indexDonneAJouer,
                            "equipeNS"            to mvnt.equipeNS,
                            "joueur1NSNom"        to mvnt.joueur1NSNom,
                            "joueur1NSPrenom"     to mvnt.joueur1NSPrenom,
                            "joueur2NSNom"        to mvnt.joueur2NSNom,
                            "joueur2NSPrenom"     to mvnt.joueur2NSPrenom,
                            "equipeEO"            to mvnt.equipeEO,
                            "joueur1EONom"        to mvnt.joueur1EONom,
                            "joueur1EOPrenom"     to mvnt.joueur1EOPrenom,
                            "joueur2EONom"        to mvnt.joueur2EONom,
                            "joueur2EOPrenom"     to mvnt.joueur2EOPrenom,
                            "donnes"              to mvnt.donnes
                        )
                        gson.toJson(reponseMap)
                    }

                    is MouvementResult.ClassementEnAttente -> {
                        JSONObject().apply {
                            put("etat", "ATTENTE_CLASSEMENT")
                            put("nbreEnregistrement", mouvementResult.nbreEnregistrement)
                        }.toString()
                    }

                    is MouvementResult.Erreur -> {
                        JSONObject().apply {
                            put("etat", "ERREUR")
                            put("message", mouvementResult.message)
                        }.toString()
                    }

                    else -> JSONObject().apply { put("etat", "ERREUR_INCONNUE") }.toString()
                }

                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonString)
            }
// ... (Autres imports) ...

            uri.startsWith("/enregistreDonne") -> {
                // 1. Déclarer le Map pour les fichiers/contenu et forcer la lecture du corps POST
                // NanoHTTPD va lire le flux et stocker le contenu dans le Map 'files'
                val files: MutableMap<String, String> = HashMap()
                session.parseBody(files)

                // 2. Récupérer le contenu JSON. Le corps POST est souvent stocké sous la clé "postData"
                // ou "__CONTENT__" ou "content" lorsque NanoHTTPD ne reconnaît pas le Content-Type.
                val receivedJson = files["postData"] ?: files["__CONTENT__"] ?: files["content"] ?: ""

                // 3. Désérialiser le JSON. Utilisation de map vide si le JSON est vide ou invalide.
                val rawMap = if (receivedJson.isNotEmpty()) Gson().fromJson(receivedJson, Map::class.java) else null

                // Sécurisation du cast : on filtre les clés non-String et on caste en Map<String, Any?>.
                val parmsMap = (rawMap as? Map<*, *>)?.filterKeys { it is String } as? Map<String, Any?> ?: emptyMap()

                // 4. Extraction des paramètres du Map désérialisé (Attention : les Ints sont lus comme Double par Gson)
                val idTournoi   = (parmsMap["idTournoi"] as? Double)?.toInt() ?: 0
                val mvntNumero = (parmsMap["mvntNumero"] as? Double)?.toInt() ?: 0
                val equipeNS  = (parmsMap["equipeNS"] as? Double)?.toInt() ?: 0
                val equipeEO  = (parmsMap["equipeEO"] as? Double)?.toInt() ?: 0
                val numeroTable = (parmsMap["numeroTable"] as? Double)?.toInt() ?: 0
                val numeroDonne = (parmsMap["numeroDonne"] as? Double)?.toInt() ?: 0
                val indexDonneJouee = (parmsMap["indexDonneJouee"] as? Double)?.toInt() ?: 0
                val contrat     = parmsMap["contrat"] as? String ?: ""
                val declarant    = parmsMap["declarant"] as? String ?: ""
                val resultatContrat    = parmsMap["resultatContrat"] as? String ?: ""
                val points = (parmsMap["points"] as? Double)?.toInt() ?: 0
                val nombrePlis = (parmsMap["nombrePlis"] as? Double)?.toInt() ?: 0
                val carteEntame = parmsMap["carteEntame"] as? String ?: ""

                // 5. Re-sérialisation des listes complexes (historique et mains)
                val historiqueList = parmsMap["historique"] as? List<*>?
                val mainsList = parmsMap["mains"] as? List<*>?

                val historiqueJson = if (historiqueList != null) Gson().toJson(historiqueList) else "[]"
                val mainsJson = if (mainsList != null) Gson().toJson(mainsList) else "[]"

                // 6. Logs de vérification
                Log.i("ClientServeurHTTP", "📦 Contenu JSON Reçu : $receivedJson")
                Log.i("ClientServeurHTTP", "💾 2Enregistrement donne : tournoi= $idTournoi donne= $numeroDonne " +
                        "NS= $equipeNS EO= $equipeEO contrat= $contrat résultat signe= \" $resultatContrat \" ")

                // 7. Appel au gestionnaire de base de données
                val indexDonneAJouer = DatabaseManager.enregistreDonne(
                    context,
                    idTournoi,
                    mvntNumero,
                    equipeNS,
                    equipeEO,
                    numeroTable,
                    numeroDonne,
                    indexDonneJouee,
                    contrat,
                    declarant,
                    resultatContrat,
                    points,
                    nombrePlis,
                    carteEntame,
                    historiqueJson,
                    mainsJson
                )

                // 8. Retour de la réponse
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"indexDonneAJouer\": $indexDonneAJouer}"
                )
            }

            uri.startsWith("/passerTableRelais") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull()
                val numeroEquipe = params["equipe"]?.firstOrNull()?.toIntOrNull()

                if (idTournoi == null || numeroEquipe == null) {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"error":"Paramètres manquants"}"""
                    )
                }

                Log.i("ClientServeurHTTP", "🏖️ /passerTableRelais appelé idTournoi=$idTournoi équipe=$numeroEquipe")

                DatabaseManager.incrementerMouvementEquipe(
                    context = context,
                    idTournoi = idTournoi,
                    numeroEquipe = numeroEquipe
                )

                val json = JSONObject().apply {
                    put("etat", "OK")
                }

                Log.i("ClientServeurHTTP", "✅ passerTableRelais OK")
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

               // BLOC 1 : getMainsRelais ===

                    uri.startsWith("/getMainsRelais") -> {
                val params = session.parameters
                val idTournoi   = params["idTournoi"]?.firstOrNull()?.toIntOrNull() ?: 0
                val numeroDonne = params["numeroDonne"]?.firstOrNull()?.toIntOrNull() ?: 0
                Log.i("ClientServeurHTTP", "📥 /getMainsRelais idTournoi=$idTournoi numeroDonne=$numeroDonne")
                if (idTournoi <= 0 || numeroDonne <= 0) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"etat":"ERREUR_PARAM"}""")
                }
                val mains = DatabaseManager.getMainsRelais(context, idTournoi, numeroDonne)
                if (mains == null) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json", """{"etat":"AUCUNE_MAIN"}""")
                }
                val jsonMains = JSONArray()
                mains.forEach { main -> val jsonMain = JSONArray(); main.forEach { jsonMain.put(it) }; jsonMains.put(jsonMain) }
                val json = JSONObject().apply { put("etat", "OK"); put("mains", jsonMains) }
                Log.i("ClientServeurHTTP", "✅ getMainsRelais mains envoyées donne=$numeroDonne")
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

                // BLOC 2 : enregistrerMainsRelais ===

                    uri.startsWith("/enregistrerMainsRelais") -> {
                val files: MutableMap<String, String> = HashMap()
                session.parseBody(files)
                val receivedJson = files["postData"] ?: files["__CONTENT__"] ?: files["content"] ?: ""
                val rawMap = if (receivedJson.isNotEmpty()) Gson().fromJson(receivedJson, Map::class.java) else null
                val parmsMap = (rawMap as? Map<*, *>)?.filterKeys { it is String } as? Map<String, Any?> ?: emptyMap()
                val idTournoi   = (parmsMap["idTournoi"]   as? Double)?.toInt() ?: 0
                val numeroDonne = (parmsMap["numeroDonne"] as? Double)?.toInt() ?: 0
                val mainsList   = parmsMap["mains"] as? List<*>
                Log.i("ClientServeurHTTP", "📥 /enregistrerMainsRelais idTournoi=$idTournoi numeroDonne=$numeroDonne mains=${mainsList?.size}")
                if (idTournoi <= 0 || numeroDonne <= 0 || mainsList == null || mainsList.size != 4) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"etat":"ERREUR_PARAM"}""")
                }
                val mains: List<List<String>> = mainsList.mapNotNull { main -> (main as? List<*>)?.mapNotNull { it as? String } }
                val ok = DatabaseManager.enregistrerMainsRelais(context, idTournoi, numeroDonne, mains)
                val json = JSONObject().apply { put("etat", if (ok) "OK" else "ERREUR") }
                Log.i("ClientServeurHTTP", "${if (ok) "✅" else "❌"} enregistrerMainsRelais donne=$numeroDonne")
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }



            uri.startsWith("/getFuturMouvement") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull()
                val mvntActuel = params["mvntActuel"]?.firstOrNull()?.toIntOrNull()
                val equipeNS = params["equipeNS"]?.firstOrNull()?.toIntOrNull()
                val equipeEO = params["equipeEO"]?.firstOrNull()?.toIntOrNull()

                if (idTournoi == null || mvntActuel == null || equipeNS == null || equipeEO == null) {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"error":"Paramètres manquants"}"""
                    )
                }

                Log.i("ClientServeurHTTP", "📥 /getFuturMouvement appelé idTournoi=$idTournoi mvntActuel=$mvntActuel equipeNS=$equipeNS equipeEO=$equipeEO")

                val futurMouvement = DatabaseManager.getFuturMouvement(
                    context = context,
                    idTournoi = idTournoi,
                    mvntActuel = mvntActuel,
                    equipeNS = equipeNS,
                    equipeEO = equipeEO
                )

                if (futurMouvement == null) {
                    Log.w("ClientServeurHTTP", "⚠️ Aucun mouvement futur trouvé pour mvntActuel=$mvntActuel")
                    val json = JSONObject().apply {
                        put("etat", "AUCUN_CHANGEMENT")
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }

                // ✅ Construction du JSON à la main (CORRECTEMENT)
                val jsonFuturMouvement = JSONObject().apply {
                    put("etat", "CHANGEMENT_DE_MOUVEMENT")
                    put("mvntSuivant", futurMouvement.mvntSuivant)

                    val entriesArray = JSONArray()
                    futurMouvement.entries.forEach { entry ->
                        val obj = JSONObject().apply {
                            put("tableNumero", entry.tableNumero)

                            // ✅ Équipe
                            put("equipe", JSONObject().apply {
                                put("equipeNumero", entry.equipe.equipeNumero)
                                put("joueur1", JSONObject().apply {
                                    put("id", entry.equipe.joueur1.idJoueur)
                                    put("nom", entry.equipe.joueur1.nom)
                                    put("prenom", entry.equipe.joueur1.prenom)
                                })
                                put("joueur2", JSONObject().apply {
                                    put("id", entry.equipe.joueur2.idJoueur)
                                    put("nom", entry.equipe.joueur2.nom)
                                    put("prenom", entry.equipe.joueur2.prenom)
                                })
                                put("idTournoi", entry.equipe.idTournoi)
                            })

                            // ✅ Adversaire
                            put("adversaire", JSONObject().apply {
                                put("equipeNumero", entry.adversaire.equipeNumero)
                                put("joueur1", JSONObject().apply {
                                    put("id", entry.adversaire.joueur1.idJoueur)
                                    put("nom", entry.adversaire.joueur1.nom)
                                    put("prenom", entry.adversaire.joueur1.prenom)
                                })
                                put("joueur2", JSONObject().apply {
                                    put("id", entry.adversaire.joueur2.idJoueur)
                                    put("nom", entry.adversaire.joueur2.nom)
                                    put("prenom", entry.adversaire.joueur2.prenom)
                                })
                                put("idTournoi", entry.adversaire.idTournoi)
                            })
                        }
                        entriesArray.put(obj)
                    }
                    put("entries", entriesArray)
                }

                Log.i("ClientServeurHTTP", "📤 JSON envoyé : $jsonFuturMouvement")
                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonFuturMouvement.toString())
            }

                uri.startsWith("/getEtatTournoi") -> {
                    val params = session.parameters
                    val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull()

                    if (idTournoi == null || idTournoi <= 0) {
                        val jsonErr = JSONObject().apply {
                            put("etat", "ERREUR_PARAM")
                        }
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            jsonErr.toString()
                        )
                    }

                    // 🔑 APPEL AU DATABASE MANAGER (qui gère la lecture de nbre_enregistrement)
                    val etatTournoi = DatabaseManager.verifierEtatTournoi(context, idTournoi)

                    Log.i("ClientServeurHTTP", "✅ État du tournoi $idTournoi renvoyé : $etatTournoi")

                    val jsonResp = JSONObject().apply {
                        put("etat", etatTournoi) // TERMINE, NON_TERMINE, ou ERREUR
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResp.toString())
                }



            uri.startsWith("/getResultatsTournoi") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull()

                Log.i("ClientServeurHTTP", "📥 /getResultatsTournoi appelé idTournoi=$idTournoi")

                if (idTournoi == null || idTournoi <= 0) {
                    val jsonErr = JSONObject().apply {
                        put("etat", "ERREUR_PARAM")
                        put("message", "idTournoi manquant ou invalide")
                    }
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        jsonErr.toString()
                    )
                }

                // 🔍 Vérifie l’état du tournoi avant de renvoyer les résultats
                val etatTournoi = DatabaseManager.verifierEtatTournoi(context, idTournoi)

                if (etatTournoi == "NON_TERMINE") {
                    Log.w("ClientServeurHTTP", "⚠️ Tournoi $idTournoi non terminé")
                    val json = JSONObject().apply { put("etat", "TOURNOI_NON_TERMINE") }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }

                if (etatTournoi == "ERREUR") {
                    val json = JSONObject().apply { put("etat", "ERREUR") }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }

                //etatTournoi == "TERMINE" le classement a déjà été effectué
                // 🔹 Étape 1 (MODIFIÉE) : Lecture du CLASSEMENT FINAL déjà calculé
                val classementFinal = DatabaseManager.getClassementStocke(context, idTournoi) // 🔑 NOUVELLE FONCTION

                if (classementFinal.isEmpty()) {
                    val json = JSONObject().apply { put("etat", "AUCUN_RESULTAT") }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }

    /*            // 🔹 Étape 1 : lecture brute
                val resultatsList = DatabaseManager.getResultatsTournoi(context, idTournoi)

                if (resultatsList.isEmpty()) {
                    val json = JSONObject().apply { put("etat", "AUCUN_RESULTAT") }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }

                // 🔹 Étape 2 : calcul du classement (pts Simplifiés + rang)
                val classement = ClassementManager.calculerClassementTournoi(resultatsList)

                // 🔹 Étape 3 : mise à jour en base (points + classement)
                for (ligne in resultatsList) {
                    DatabaseManager.majPointsDonne(
                        context,
                        idTournoi,
                        ligne.numeroDonne,
                        ligne.numeroTable,
                        ligne.pointsNS,
                        ligne.pointsEO
                    )
                }

                for (c in classement) {
                    DatabaseManager.majClassementEquipe(
                        context,
                        idTournoi,
                        c.numeroEquipe,
                        c.totalPts,
                        c.rang
                    )
                }
  */
                Log.i("ClientServeurHTTP", "✅ Classement calculé et enregistré pour le tournoi $idTournoi")

                // 🔹 Étape 2 : retour au client
                val classementArray = JSONArray()
                for (c in classementFinal) { // Utiliser la liste lue
                    classementArray.put(
                        JSONObject().apply {
                            put("numeroEquipe", c.numeroEquipe)
                            put("totalPts", c.totalPts)
                            put("rang", c.rang)
                            put("scorePct", c.scorePct)
                        }
                    )
                }
                // ... (Retour JSON inchangé) ...
           /*
                // 🔹 Étape 4 : retour au client
                val classementArray = JSONArray()
                for (c in classement) {
                    classementArray.put(
                        JSONObject().apply {
                            put("numeroEquipe", c.numeroEquipe)
                            put("totalPts", c.totalPts)
                            put("rang", c.rang)
                        }
                    )
                }
                */

                val jsonResp = JSONObject().apply {
                    put("etat", "RESULTATS")
                    put("classement", classementArray)
                }

                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResp.toString())
            }

            uri.startsWith("/getDetailsDonnes") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull()
                if (idTournoi != null ) {
                    // 1. Récupérer les données depuis DatabaseManager
                    val details = DatabaseManager.getDonneResultatDetails(context, idTournoi)

                    // 2. Convertir la liste en JSON
                    val jsonArray = JSONArray()
                    details.forEach { d ->
                        val obj = JSONObject().apply {
                            put("numero_donne", d.numeroDonne)
                            put("equipeNS", d.equipeNS)
                            put("equipeEO", d.equipeEO)
                            put("contrat", d.contrat)
                            put("declarant", d.declarant)
                            put("resultat_contrat", d.resultatContrat)
                            put("nombre_pli", d.nombrePli)
                            put("carteEntame", d.carteEntame)
                            put("pointsNS", d.pointsNS)
                            put("pointsEO", d.pointsEO)
                            put("ptsNS", d.ptsNS)
                            put("ptsEO", d.ptsEO)
                            put("vulnerable", d.vulnerable)
                        }
                        jsonArray.put(obj)
                    }

                    // 3. Envoyer la réponse

                    return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
                } else {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "ID Tournoi manquant ou invalide")
                }
            }

            uri.startsWith("/getDonneComplete") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull() ?: -1
                val numDonne = params["numeroDonne"]?.firstOrNull()?.toIntOrNull() ?: -1
                val eqNS = params["equipeNS"]?.firstOrNull()?.toIntOrNull() ?: -1

                if (idTournoi != -1 && numDonne != -1 && eqNS != -1) {
                    val data = DatabaseManager.getDonneComplete(context, idTournoi, numDonne, eqNS)
                    if (data != null) {
                        // ✅ SUPPRIMÉ : val (mains, encheres) = data
                        val jsonGlobal = JSONObject()

                        // 1. Mains
                        val jsonMains = JSONObject()
                        data.mains.forEach { (pos, cartes) ->  // ✅ MODIF : mains → data.mains
                            val array = JSONArray()
                            cartes.forEach { array.put(it.code) }
                            jsonMains.put(pos, array)
                        }
                        jsonGlobal.put("mains", jsonMains)

                        // 2. Enchères (AnnonceJoueur)
                        val jsonEncheres = JSONArray()
                        data.encheres.forEach { enchere ->  // ✅ MODIF : encheres → data.encheres
                            val obj = JSONObject()
                            obj.put("joueur", enchere.joueur)
                            obj.put("annonce", enchere.annonce)
                            jsonEncheres.put(obj)
                        }
                        jsonGlobal.put("encheres", jsonEncheres)

                        // ✅ AJOUT : Vulnerable et Donneur
                        jsonGlobal.put("vulnerable", data.vulnerable)
                        jsonGlobal.put("donneur", data.donneur)

// ✅ AJOUT : Contrat et Déclarant
                        jsonGlobal.put("contrat", data.contrat)
                        jsonGlobal.put("declarant", data.declarant)

                        // ✅ AJOUT : Log du JSON envoyé
                        Log.i("ClientServeurHTTP", "📤 Envoi getDonneComplete")
                        Log.i("ClientServeurHTTP", "   Vulnerable: '${data.vulnerable}'")
                        Log.i("ClientServeurHTTP", "   Donneur: '${data.donneur}'")
                        Log.i("ClientServeurHTTP", "   JSON: $jsonGlobal")

                        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonGlobal.toString())
                    }
                }

                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Donne introuvable")
            }


         /*   uri.startsWith("/getEquipesAyantJoueDonne") -> {
                val params = session.parameters
                val idTournoi = params["idTournoi"]?.firstOrNull()?.toIntOrNull() ?: 0
                val numeroDonne = params["numeroDonne"]?.firstOrNull()?.toIntOrNull() ?: 0

                if (idTournoi > 0 && numeroDonne > 0) {
                    val equipes = DatabaseManager.getEquipesAyantJoueDonne(context, idTournoi, numeroDonne)

                    val jsonArray = JSONArray()
                    equipes.forEach { info ->
                        val obj = JSONObject().apply {
                            put("equipeNS", info.equipeNS)
                            put("equipeEO", info.equipeEO)
                            put("contrat", info.contrat)
                            put("declarant", info.declarant)
                        }
                        jsonArray.put(obj)
                    }

                    Log.i("ClientServeurHTTP", "✅ getEquipesAyantJoueDonne → ${equipes.size} équipes")
                    return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
                } else {
                    Log.e("ClientServeurHTTP", "❌ Paramètres invalides")
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "[]")
                }
            }*/

            else -> newFixedLengthResponse("Erreur 404 : Page non trouvée")
        }


    }


}