package app.resultatsbridge.client

import android.util.Log
import androidx.compose.runtime.MutableState
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.ChangementDeMouvement
import app.resultatsbridge.common.model.ChangementDeMouvementEntry
import app.resultatsbridge.common.model.ClassementItem
import app.resultatsbridge.common.model.DonneDetail
import app.resultatsbridge.common.model.Equipe
import app.resultatsbridge.common.model.Joueur
import app.resultatsbridge.common.model.Mouvement
import app.resultatsbridge.common.model.MouvementResult
import app.resultatsbridge.common.model.Tour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import app.resultatsbridge.BridgeApplication
import java.net.URLEncoder
import com.google.gson.Gson
import app.resultatsbridge.common.model.DonneResultatDetail
import app.resultatsbridge.common.model.AnnonceJoueur
import app.resultatsbridge.common.model.AuthAssociation
import app.resultatsbridge.common.model.AuthJoueur
import app.resultatsbridge.common.model.CreationTournoiResult
import app.resultatsbridge.common.model.DonneAvantTournoi
import app.resultatsbridge.common.model.DonneComplete
import app.resultatsbridge.common.model.EquipeDonneInfo
import app.resultatsbridge.common.model.ErreurLogItem
import app.resultatsbridge.common.model.TournoiConfig
import app.resultatsbridge.common.URL_SERVEUR_ONLINE
import org.json.JSONArray

object ClientNetworkUtils {
    /**
     * établit les requetes vers le serveur distant HTTP
     * sur le smartphone Organisateur
     */
    private var ipServeur: String? = null
    private const val port = 8080
    private var tokenApi: String = ""
    private var tokenJoueur: String = ""
    // 🔧 Initialise l'IP du serveur
    fun initialiserServeur(ip: String) {
        ipServeur = ip
        Log.i("ClientNetworkUtils", "🔗 IP serveur initialisée : $ip")
    }

    suspend fun connexionAssociation(email: String, mdp: String): AuthAssociation? {
        return try {
            val ip = ipServeur ?: return null
            val urlStr = if (ip.startsWith("http://") || ip.startsWith("https://")) {
                "$ip/connexionAssociation.php"
            } else {
                "http://$ip:$port/connexionAssociation.php"
            }
            Log.i("ClientNetworkUtils", "🔐 connexionAssociation POST → $urlStr")

            val bodyJson = JSONObject().apply {
                put("email", email)
                put("mdp", mdp)
            }.toString()

            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 Réponse connexionAssociation : $response")

            val json = JSONObject(response)
            if (json.optString("etat") != "OK") {
                Log.e("ClientNetworkUtils", "❌ Connexion refusée : ${json.optString("message")}")
                return null
            }

            val auth = AuthAssociation(
                idAssociation = json.getInt("idAssociation"),
                nom = json.getString("nom"),
                tokenApi = json.getString("token_api")
            )
            tokenApi = auth.tokenApi
            tokenJoueur = ""
            Log.i("ClientNetworkUtils", "✅ Auth OK → assoc=${auth.nom}")
            auth

        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ connexionAssociation erreur : ${e.message}")
            null
        }
    }


    // Inscription d'un joueur depuis l'app. Le serveur détecte automatiquement
    // si le code correspond à un membre du club ou à un joueur externe.
    // Retourne "OK", "CODE_INVALIDE", "EMAIL_EXISTE" ou "ERREUR".
    suspend fun inscrireJoueur(nom: String, prenom: String, email: String, mdp: String, code: String): String {
        return try {
            val urlStr = "${URL_SERVEUR_ONLINE}/traitement_inscription_joueur.php"
            Log.i("ClientNetworkUtils", "📝 inscrireJoueur POST → $urlStr")
            val bodyJson = JSONObject().apply {
                put("nom", nom)
                put("prenom", prenom)
                put("email", email)
                put("mdp", mdp)
                put("code", code)
            }.toString()
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 inscrireJoueur réponse : $response")
            JSONObject(response).optString("etat", "ERREUR")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ inscrireJoueur : ${e.message}")
            "ERREUR"
        }
    }

    // Vérifie au démarrage de l'app que le token stocké est toujours valide.
    // Retourne true si pas de réseau (accès offline autorisé avec token local).
    suspend fun verifierTokenJoueur(token: String): Boolean {
        return try {
            val urlStr = "${URL_SERVEUR_ONLINE}" +
                    "/api_adherent.php?action=verifierToken&token=$token"
            Log.i("ClientNetworkUtils", "🔍 verifierTokenJoueur → $urlStr")
            val response = withContext(Dispatchers.IO) { readUrl(urlStr) }
            val valide = JSONObject(response).optString("etat") == "OK"
            Log.i("ClientNetworkUtils", if (valide) "✅ Token valide" else "❌ Token invalide")
            valide
        } catch (e: Exception) {
            Log.w("ClientNetworkUtils", "⚠️ verifierTokenJoueur - pas de réseau, accès autorisé")
            true
        }
    }

    // Connexion d'un joueur (adhérent ou externe) avec ses identifiants personnels.
    // Stocke tokenJoueur et efface tokenApi — à utiliser côté joueur uniquement.
    suspend fun connexionJoueur(email: String, mdp: String): AuthJoueur? {
        return try {
            val ip = ipServeur ?: return null
            val urlStr = if (ip.startsWith("http://") || ip.startsWith("https://")) {
                "$ip/api_adherent.php?action=connexion"
            } else {
                "http://$ip:$port/api_adherent.php?action=connexion"
            }
            Log.i("ClientNetworkUtils", "🔐 connexionJoueur POST → $urlStr")

            val bodyJson = JSONObject().apply {
                put("email", email)
                put("mdp", mdp)
            }.toString()

            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 Réponse connexionJoueur : $response")

            val json = JSONObject(response)
            if (json.optString("etat") != "OK") {
                Log.e("ClientNetworkUtils", "❌ Connexion joueur refusée")
                return null
            }

            val auth = AuthJoueur(
                idAssociation = json.getInt("idAssociation"),
                nom = json.getString("nom"),
                prenom = json.getString("prenom"),
                nomClub = json.getString("nomClub"),
                tokenJoueur = json.getString("token")
            )
            tokenJoueur = auth.tokenJoueur
            tokenApi = ""
            Log.i("ClientNetworkUtils", "✅ Joueur connecté → ${auth.prenom} ${auth.nom} (${auth.nomClub})")
            auth
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ connexionJoueur erreur : ${e.message}")
            null
        }
    }

    // Sur Android 6, setDefaultSSLSocketFactory est ignoré par le moteur OkHttp interne.
    // On applique donc la factory directement sur chaque connexion HTTPS.
    private fun ouvrirConnexion(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            val factory = BridgeApplication.customSslSocketFactory
            Log.e("SSL_DEBUG", "ouvrirConnexion HTTPS — factory=${if (factory != null) "NON-NULL" else "NULL"} conn=${conn.javaClass.name}")
            factory?.let { conn.sslSocketFactory = it }
        }
        return conn
    }

    private fun readUrl(urlStr: String): String {
        val conn = ouvrirConnexion(urlStr)
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        return conn.inputStream.bufferedReader().readText()
    }

    private fun postUrl(urlStr: String, bodyJson: String): String {
        val conn = ouvrirConnexion(urlStr)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.outputStream.use { it.write(bodyJson.toByteArray()) }
        return conn.inputStream.bufferedReader().readText()
    }

    // 🔧 Construit l'URL complète avec port
    private fun urlAvecPort(path: String): String {
        val ip = ipServeur ?: throw IllegalStateException("IP serveur non initialisée")
        val sep = if (path.contains("?")) "&" else "?"
        val token = when {
            tokenApi.isNotEmpty()    -> "${sep}token_api=$tokenApi"
            tokenJoueur.isNotEmpty() -> "${sep}token_joueur=$tokenJoueur"
            else                     -> ""
        }
        return if (ip.startsWith("http://") || ip.startsWith("https://")) {
            "$ip/serverBridge.php/$path$token"
        } else {
            "http://$ip:$port/$path$token"
        }
    }

    // 🔍 Test de connexion au serveur
    suspend fun testConnection(): Boolean {
        val ip = ipServeur ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = if (ip.startsWith("http://") || ip.startsWith("https://")) {
                    "$ip/serverBridge.php/verifierTournoiOuvert"
                } else {
                    "http://$ip:$port"
                }
                Log.i("ClientNetworkUtils", "🌍 Test de connexion : $urlStr")
                val http = ouvrirConnexion(urlStr)
                http.connectTimeout = 3000
                http.readTimeout = 3000
                http.requestMethod = "GET"
                http.connect()
                val success = http.responseCode == 200
                Log.i("ClientNetworkUtils", "📶 Réponse code = ${http.responseCode}")
                success
            } catch (e: Exception) {
                Log.e("ClientNetworkUtils", "❌ Erreur de connexion", e)
                false
            }
        }
    }

    // 🔍 Vérifie si un tournoi est ouvert
    suspend fun verifierTournoiOuvert(): Pair<Int, String>? {
        return try {
            val urlStr = urlAvecPort("verifierTournoiOuvert")
            Log.i("ClientNetworkUtils", "🌍 GET $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📄 Réponse brute : $reponse")

            val json           = JSONObject(reponse)
            val id             = json.optInt("id", 0)
            val type           = json.optString("type", "")
            val nbreMouvements = json.optInt("nbreMouvements", 0)
            val nbreDonnes     = json.optInt("nbreDonnesParTable", 0)
            val equipeRelais   = json.optInt("equipeRelais", 0)
            val nbreTables     = json.optInt("nbreTables", 0)
            TournoiConfig.EQUIPE_RELAIS = if (equipeRelais > 0) equipeRelais else null
            Log.i("ClientNetworkUtils", "🏖️ Équipe relais : ${TournoiConfig.EQUIPE_RELAIS ?: "aucune"}")

            if (id > 0 && type.isNotBlank()) {
                TournoiConfig.NBRE_MOUVEMENTS       = nbreMouvements
                TournoiConfig.NBRE_DONNES_PAR_TABLE = nbreDonnes
                TournoiConfig.NBRE_TABLES           = nbreTables
                Log.i("ClientNetworkUtils", "✅ Tournoi détecté : ID=$id Type=$type, " +
                        "mvnts=${TournoiConfig.NBRE_MOUVEMENTS}, donnes/table=${TournoiConfig.NBRE_DONNES_PAR_TABLE}, tables=${TournoiConfig.NBRE_TABLES}")
                id to type
            } else {
                Log.i("ClientNetworkUtils", "❌ Aucun tournoi ouvert")
                null
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Erreur tournoi ouvert", e)
            null
        }
    }

    suspend fun fermerTournoiOuvert(): Boolean {
        return try {
            val urlStr = urlAvecPort("fermerTournoiOuvert")
            Log.i("ClientNetworkUtils", "🔒 fermerTournoiOuvert : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse : $reponse")
            val json = JSONObject(reponse)
            json.getString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur fermerTournoiOuvert", e)
            false
        }
    }

    suspend fun getListeTypesTournoi(): List<Triple<String, Int, Int>> {
        return try {
            val urlStr = urlAvecPort("getListeTypesTournoi")
            Log.i("ClientNetworkUtils", "🌍 getListeTypesTournoi : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse : $reponse")
            val jsonArray = JSONArray(reponse)
            val liste = mutableListOf<Triple<String, Int, Int>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                liste.add(Triple(
                    obj.getString("type"),
                    obj.getInt("nombre_table"),
                    obj.getInt("nombre_donne")
                ))
            }
            liste
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur getListeTypesTournoi", e)
            emptyList()
        }
    }


    fun creerTournoi(
        type: String,
        nbreEquipes: Int,
        nbreDonnes: Int,
        nbreEnregistrement: Int
    ): CreationTournoiResult {
        return try {
            val urlStr = urlAvecPort("creerTournoi")
            val bodyJson = Gson().toJson(mapOf(
                "type"               to type,
                "nbreEquipes"        to nbreEquipes,
                "nbreDonnes"         to nbreDonnes,
                "nbreEnregistrement" to nbreEnregistrement
            ))
            Log.i("ClientNetworkUtils", "➡️ creerTournoi POST → $urlStr")
            val response = postUrl(urlStr, bodyJson)
            Log.i("ClientNetworkUtils", "📩 Réponse creerTournoi : $response")

            val json = JSONObject(response)
            val etat = json.optString("etat")
            val avertissement = json.optString("avertissement", "").ifEmpty { null }

            when (etat) {
                "OK"      -> CreationTournoiResult(json.getInt("idTournoi"), avertissement)
                "BLOQUE"  -> CreationTournoiResult(-1, json.optString("message"))
                else      -> CreationTournoiResult(-1, null)
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur creerTournoi", e)
            CreationTournoiResult(-1, null)
        }
    }

    suspend fun getTousLesJoueurs(): List<Joueur> {
        return try {
            val urlStr = urlAvecPort("getTousLesJoueurs")
            Log.i("ClientNetworkUtils", "🌍 getTousLesJoueurs : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse : $reponse")
            val jsonArray = JSONArray(reponse)
            val liste = mutableListOf<Joueur>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                liste.add(
                    Joueur(
                        idJoueur = obj.getInt("ID"),
                        nom = obj.getString("nom"),
                        prenom = obj.getString("prenom")
                    )
                )
            }
            liste
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur getTousLesJoueurs", e)
            emptyList()
        }
    }

    suspend fun importerJoueursDepuisServeurEnLigne(token: String): List<Joueur> {
        return try {
            val urlStr = "${URL_SERVEUR_ONLINE}/api_adherent.php?action=joueurs&token=$token"
            Log.i("ClientNetworkUtils", "☁️ importerJoueursDepuisServeurEnLigne → $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            val json = JSONObject(reponse)
            if (json.optString("etat") != "OK") return emptyList()
            val arr = json.getJSONArray("joueurs")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Joueur(
                    idJoueur = obj.getInt("ID"),
                    nom = obj.getString("nom"),
                    prenom = obj.getString("prenom")
                )
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ importerJoueursDepuisServeurEnLigne", e)
            emptyList()
        }
    }

    suspend fun ajouterNouveauJoueur(nom: String, prenom: String): Joueur? {
        return try {
            val urlStr = urlAvecPort("ajouterNouveauJoueur")
            val bodyJson = Gson().toJson(mapOf("nom" to nom, "prenom" to prenom))
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 ajouterNouveauJoueur : $response")
            val json = JSONObject(response)
            if (json.getString("etat") == "OK") {
                Joueur(idJoueur = json.getInt("idJoueur"), nom = nom, prenom = prenom)
            } else null
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur ajouterNouveauJoueur", e)
            null
        }
    }

    suspend fun enregistrerEquipes(idTournoi: Int, equipes: List<Equipe>): Boolean {
        return try {
            val urlStr = urlAvecPort("enregistrerEquipes")
            val listeJson = equipes.map { eq ->
                mapOf(
                    "equipeNumero" to eq.equipeNumero,
                    "joueur1_id" to eq.joueur1.idJoueur,
                    "joueur2_id" to eq.joueur2.idJoueur
                )
            }
            val bodyJson = Gson().toJson(mapOf("idTournoi" to idTournoi, "equipes" to listeJson))
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 enregistrerEquipes : $response")
            JSONObject(response).getString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur enregistrerEquipes", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MITCHELL : mise à jour du tournoi après constitution des équipes.
    // Appelée dans ConstitutionEquipesScreen au "Enregistrer et Démarrer".
    // ─────────────────────────────────────────────────────────────────────
    fun miseAJourTournoiMitchell(
        idTournoi: Int,
        nbreEquipes: Int
    ): Boolean {
        val nbreTables         = nbreEquipes / 2
        val nbreMouvements     = if (nbreTables % 2 == 0) nbreTables - 1 else nbreTables
        val nbreDonnesTotal    = TournoiConfig.NBRE_DONNES_PAR_TABLE * nbreTables
        val nbreEnregistrement = nbreDonnesTotal * nbreMouvements

        TournoiConfig.NBRE_MOUVEMENTS = nbreMouvements

        return try {
            val urlStr   = urlAvecPort("miseAJourTournoiMitchell")
            val bodyJson = Gson().toJson(mapOf(
                "idTournoi"          to idTournoi,
                "nbreEquipes"        to nbreEquipes,
                "nbreDonnes"         to nbreDonnesTotal,
                "nbreEnregistrement" to nbreEnregistrement,
                "nbreMouvements"     to nbreMouvements
            ))
            val response = postUrl(urlStr, bodyJson)
            JSONObject(response).optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur miseAJourTournoiMitchell", e)
            false
        }
    }

    fun miseAJourTournoiMitchellGueridon(
        idTournoi: Int,
        nbreEquipes: Int
    ): Boolean {
        val nbreTables         = nbreEquipes / 2
        val nbreMouvements     = nbreTables    // pas de skip en Guéridon
        val nbreDonnesTotal    = TournoiConfig.NBRE_DONNES_PAR_TABLE * nbreTables
        val nbreEnregistrement = nbreDonnesTotal * nbreMouvements

        TournoiConfig.NBRE_MOUVEMENTS = nbreMouvements

        return try {
            val urlStr   = urlAvecPort("miseAJourTournoiMitchell")
            val bodyJson = Gson().toJson(mapOf(
                "idTournoi"          to idTournoi,
                "nbreEquipes"        to nbreEquipes,
                "nbreDonnes"         to nbreDonnesTotal,
                "nbreEnregistrement" to nbreEnregistrement,
                "nbreMouvements"     to nbreMouvements
            ))
            val response = postUrl(urlStr, bodyJson)
            JSONObject(response).optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur miseAJourTournoiMitchellGueridon", e)
            false
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // Retourne pour chaque équipe sa table et orientation au mouvement 1.
    // Appelée depuis ConstitutionEquipesScreen après constitution des équipes.
    // Le type de tournoi n'est plus envoyé au serveur : il est lu en BD côté
    // serveur à partir de l'idTournoi (le tournoi est déjà créé à ce stade).
    // Retourne Map<equipeNumero, Pair<orientation, table>>
    // ─────────────────────────────────────────────────────────────────────
    fun getPositionsMouvement1(idTournoi: Int): Map<Int, Pair<String, Int>> {
        return try {
            val urlStr = urlAvecPort("getPositionsMouvement1?idTournoi=$idTournoi")
            Log.i("ClientNetworkUtils", "🌍 GET $urlStr")
            val response = readUrl(urlStr)
            Log.i("ClientNetworkUtils", "📩 Réponse getPositionsMouvement1 : $response")

            val json = JSONObject(response)
            if (json.optString("etat") != "OK") return emptyMap()

            val positions = mutableMapOf<Int, Pair<String, Int>>()
            val jsonPositions = json.optJSONObject("positions") ?: return emptyMap()
            val keys = jsonPositions.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val equipeNumero = key.toIntOrNull() ?: continue
                val obj = jsonPositions.optJSONObject(key) ?: continue
                val orientation = obj.optString("orientation")
                val table       = obj.optInt("table")
                positions[equipeNumero] = Pair(orientation, table)
            }
            Log.i("ClientNetworkUtils", "✅ getPositionsMouvement1 : ${positions.size} équipes")
            positions
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur getPositionsMouvement1", e)
            emptyMap()
        }
    }

    suspend fun toutesEquipesOntTermineMouvement(idTournoi: Int, mvntNumero: Int): Boolean {
        return try {
            val urlStr = urlAvecPort("verifierFinMouvement?idTournoi=$idTournoi&mvntNumero=$mvntNumero")
            Log.i("ClientNetworkUtils", "🌍 verifierFinMouvement : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse : $reponse")
            JSONObject(reponse).getBoolean("termine")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ toutesEquipesOntTermineMouvement", e)
            false
        }
    }

    /**
     * Récupère les équipes ayant joué une donne via le serveur
     */
    suspend fun getEquipesAyantJoueDonne(
        idTournoi: Int,
        numeroDonne: Int
    ): List<EquipeDonneInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = urlAvecPort("getEquipesAyantJoueDonne?idTournoi=$idTournoi&numeroDonne=$numeroDonne")
                Log.i("ClientNetworkUtils", "🌍 Appel GET → $urlStr")
                val response = readUrl(urlStr)
                Log.i("ClientNetworkUtils", "📩 Réponse brute → $response")
                val jsonArray = JSONArray(response)
                val liste = mutableListOf<EquipeDonneInfo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    liste.add(
                        EquipeDonneInfo(
                            equipeNS = obj.getInt("equipeNS"),
                            equipeEO = obj.getInt("equipeEO"),
                            contrat = obj.optString("contrat", ""),
                            declarant = obj.optString("declarant", "")
                        )
                    )
                }
                Log.i("ClientNetworkUtils", "✅ ${liste.size} équipes récupérées pour donne $numeroDonne")
                liste
            } catch (e: Exception) {
                Log.e("ClientNetworkUtils", "❌ Erreur getEquipesAyantJoueDonne : ${e.message}")
                emptyList()
            }
        }
    }

    // 📦 Récupère la liste des équipes (version JSON)
    suspend fun recupererListeEquipes(idTournoi: Int): List<Equipe>? {
        return try {
            val urlStr = urlAvecPort("getEquipes?idTournoi=$idTournoi")
            Log.i("ClientNetworkUtils", "🌍 GET $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            if (reponse.isBlank()) {
                Log.e("ClientNetworkUtils", "❌ Réponse vide du serveur")
                return null
            }
            Log.i("ClientNetworkUtils", "📄 Réponse brute : $reponse")
            val json = JSONObject(reponse)
            val jsonEquipes = json.getJSONArray("listeEquipes")
            val equipes = mutableListOf<Equipe>()
            for (i in 0 until jsonEquipes.length()) {
                val obj = jsonEquipes.getJSONObject(i)
                val joueur1Json = obj.getJSONObject("joueur1")
                val joueur2Json = obj.getJSONObject("joueur2")
                equipes.add(
                    Equipe(
                        equipeNumero = obj.getInt("equipeNumero"),
                        joueur1 = Joueur(
                            idJoueur = joueur1Json.getInt("idJoueur"),
                            nom = joueur1Json.getString("nom"),
                            prenom = joueur1Json.getString("prenom")
                        ),
                        joueur2 = Joueur(
                            idJoueur = joueur2Json.getInt("idJoueur"),
                            nom = joueur2Json.getString("nom"),
                            prenom = joueur2Json.getString("prenom")
                        ),
                        idTournoi = obj.getInt("idTournoi")
                    )
                )
            }
            Log.i("ClientNetworkUtils", "✅ ${equipes.size} équipes reconstruites")
            equipes
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Erreur récupération équipes", e)
            null
        }
    }

    // 📤 Envoie l'équipe sélectionnée au serveur
    suspend fun equipeSelectionnee(equipe: String) {
        try {
            val nomEncode = URLEncoder.encode(equipe, "UTF-8")
            val urlStr = urlAvecPort("choisirEquipe?nom=$nomEncode")
            Log.i("ClientNetworkUtils", "🌍 GET $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📨 Réponse serveur : $reponse")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur envoi équipe sélectionnée", e)
        }
    }

    suspend fun passerTableRelais(idTournoi: Int, numeroEquipe: Int): Boolean {
        return try {
            val urlStr = urlAvecPort("passerTableRelais?idTournoi=$idTournoi&equipe=$numeroEquipe")
            Log.i("ClientNetworkUtils", "🏖️ passerTableRelais : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse passerTableRelais : $reponse")
            JSONObject(reponse).getString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur passerTableRelais", e)
            false
        }
    }

    suspend fun recupererMouvement(idTournoi: Int, equipeNumero: Int): MouvementResult? {
        return try {
            val urlStr = urlAvecPort("getMouvement?idTournoi=$idTournoi&equipeNumero=$equipeNumero")
            Log.i("ClientNetworkUtils", "🌍 Appel GET → $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse brute → $reponse")

            val json = JSONObject(reponse)
            when (val etat = json.optString("etat")) {
                "MOUVEMENT" -> {
                    val donnesJsonArray = json.getJSONArray("donnes")
                    val donneList = mutableListOf<DonneDetail>()
                    for (i in 0 until donnesJsonArray.length()) {
                        val d = donnesJsonArray.getJSONObject(i)
                        val mainsJson = d.optJSONArray("mains")
                        val mainsParsed: List<List<String>>? = mainsJson?.let { arr ->
                            (0 until arr.length()).map { idx ->
                                val handArr = arr.getJSONArray(idx)
                                (0 until handArr.length()).map { handArr.getString(it) }
                            }
                        }
                        donneList.add(
                            DonneDetail(
                                numero = d.getInt("numero"),
                                donneur = d.optString("donneur", ""),
                                vulnerable = d.optString("vulnerable", ""),
                                mains = mainsParsed
                            )
                        )
                    }
                    val mouvement = Mouvement(
                        mvntNumero = json.getInt("mvntNumero"),
                        tableNumero = json.getInt("tableNumero"),
                        equipeNS = json.getInt("equipeNS"),
                        joueur1NSNom = json.getString("joueur1NSNom"),
                        joueur1NSPrenom = json.getString("joueur1NSPrenom"),
                        joueur2NSNom = json.getString("joueur2NSNom"),
                        joueur2NSPrenom = json.getString("joueur2NSPrenom"),
                        equipeEO = json.getInt("equipeEO"),
                        joueur1EONom = json.getString("joueur1EONom"),
                        joueur1EOPrenom = json.getString("joueur1EOPrenom"),
                        joueur2EONom = json.getString("joueur2EONom"),
                        joueur2EOPrenom = json.getString("joueur2EOPrenom"),
                        donnes = donneList,
                        indexDonneAJouer = json.optInt("indexDonneAJouer", 0)
                    )
                    Log.i("ClientNetworkUtils", "✅ Mouvement reconstruit : mvnt=${mouvement.mvntNumero} indexDonne=${mouvement.indexDonneAJouer}")
                    MouvementResult.Complet(mouvement, json.optBoolean("tousTermines", true))
                }
                "ATTENTE_CLASSEMENT" -> MouvementResult.ClassementEnAttente(json.optInt("nbreEnregistrement", 0))
                "ERREUR" -> {
                    val message = json.optString("message", "Erreur inconnue")
                    Log.e("ClientNetworkUtils", "❌ Erreur serveur : $message")
                    MouvementResult.Erreur(message)
                }
                else -> {
                    Log.e("ClientNetworkUtils", "⚠️ Etat inconnu : $etat")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Exception lors récupération mouvement", e)
            null
        }
    }

    fun enregistreDonne(
        idTournoi: Int,
        mvntNumero: Int,
        equipeNS: Int,
        equipeEO: Int,
        numeroTable: Int,
        numeroDonne: Int,
        indexDonneJouee: Int,
        contrat: String,
        declarant: String,
        resultatContrat: String,
        points: Int,
        nombrePlis: Int,
        carteEntame: String,
        historique: List<Tour>?,
        mains: List<List<String>>?
    ): Int {
        val bodyJson = Gson().toJson(mapOf(
            "idTournoi"       to idTournoi,
            "mvntNumero"      to mvntNumero,
            "equipeNS"        to equipeNS,
            "equipeEO"        to equipeEO,
            "numeroTable"     to numeroTable,
            "numeroDonne"     to numeroDonne,
            "indexDonneJouee" to indexDonneJouee,
            "contrat"         to contrat,
            "declarant"       to declarant,
            "resultatContrat" to resultatContrat,
            "points"          to points,
            "nombrePlis"      to nombrePlis,
            "carteEntame"     to carteEntame,
            "historique"      to historique,
            "mains"           to mains
        ))
        val urlStr = urlAvecPort("enregistreDonne")
        Log.i("ClientNetworkUtils", "➡️ enregistreDonne POST → $urlStr")
        Log.i("ClientNetworkUtils", "📦 JSON envoyé = $bodyJson")
        return try {
            val response = postUrl(urlStr, bodyJson)
            Log.i("ClientNetworkUtils", "📩 Réponse brute : $response")
            JSONObject(response).getInt("indexDonneAJouer")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur POST : ${e.message}")
            -1
        }
    }

    suspend fun getFuturMouvement(
        idTournoi: Int,
        mvntActuel: Int,
        equipeNS: Int,
        equipeEO: Int
    ): ChangementDeMouvement? {
        return try {
            val urlStr = urlAvecPort("getFuturMouvement?idTournoi=$idTournoi&mvntActuel=$mvntActuel&equipeNS=$equipeNS&equipeEO=$equipeEO")
            Log.i("ClientNetworkUtils", "🌍 getFuturMouvement : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse getFuturMouvement : $reponse")

            val json = JSONObject(reponse)
            val mvntSuivant = json.getInt("mvntSuivant")
            val entriesArray = json.getJSONArray("entries")
            val entriesList = mutableListOf<ChangementDeMouvementEntry>()

            for (i in 0 until entriesArray.length()) {
                val e = entriesArray.getJSONObject(i)
                val equipeObj    = e.getJSONObject("equipe")
                val adversaireObj = e.getJSONObject("adversaire")
                val tableNumero  = e.getInt("tableNumero")
                val eqNS = Equipe(
                    equipeNumero = equipeObj.getInt("equipeNumero"),
                    joueur1 = Joueur(
                        idJoueur = equipeObj.getJSONObject("joueur1").getInt("id"),
                        nom = equipeObj.getJSONObject("joueur1").getString("nom"),
                        prenom = equipeObj.getJSONObject("joueur1").getString("prenom")
                    ),
                    joueur2 = Joueur(
                        idJoueur = equipeObj.getJSONObject("joueur2").getInt("id"),
                        nom = equipeObj.getJSONObject("joueur2").getString("nom"),
                        prenom = equipeObj.getJSONObject("joueur2").getString("prenom")
                    ),
                    idTournoi = equipeObj.getInt("idTournoi")
                )
                val eqEO = Equipe(
                    equipeNumero = adversaireObj.getInt("equipeNumero"),
                    joueur1 = Joueur(
                        idJoueur = adversaireObj.getJSONObject("joueur1").getInt("id"),
                        nom = adversaireObj.getJSONObject("joueur1").getString("nom"),
                        prenom = adversaireObj.getJSONObject("joueur1").getString("prenom")
                    ),
                    joueur2 = Joueur(
                        idJoueur = adversaireObj.getJSONObject("joueur2").getInt("id"),
                        nom = adversaireObj.getJSONObject("joueur2").getString("nom"),
                        prenom = adversaireObj.getJSONObject("joueur2").getString("prenom")
                    ),
                    idTournoi = adversaireObj.getInt("idTournoi")
                )
                entriesList.add(
                    ChangementDeMouvementEntry(
                        equipe = eqNS, adversaire = eqEO, tableNumero = tableNumero
                    )
                )
            }
            ChangementDeMouvement(mvntSuivant = mvntSuivant, entries = entriesList)
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Erreur getFuturMouvement : ${e.message}", e)
            null
        }
    }

    /**
     * Interroge le serveur pour obtenir l'état du tournoi (TERMINE, NON_TERMINE, ERREUR)
     */
    suspend fun verifierEtatTournoi(idTournoi: Int): String {
        Log.i("ClientNetworkUtils", "🚦 Début verifierEtatTournoi (id=$idTournoi)")
        return try {
            val urlStr = urlAvecPort("getEtatTournoi?idTournoi=$idTournoi")
            Log.i("ClientNetworkUtils", "🌍 Appel à : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse : $reponse")
            JSONObject(reponse).optString("etat", "ERREUR")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Erreur verifierEtatTournoi", e)
            "ERREUR_RESEAU"
        }
    }

    suspend fun recupererResultatsTournoi(
        idTournoi: Int,
        equipes: List<Equipe>,
        etape: MutableState<EtapeClient>? = null,
        typeTournoi: String = ""
    ): List<ClassementItem>? {
        Log.i("ClientNetworkUtils", "🚦 Début recupererResultatsTournoi — étape actuelle = ${etape?.value}")
        return try {
            val urlStr = urlAvecPort("getResultatsTournoi?idTournoi=$idTournoi")
            Log.i("ClientNetworkUtils", "🌍 getResultatsTournoi : $urlStr")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            Log.i("ClientNetworkUtils", "📩 Réponse getResultatsTournoi : $reponse")

            val json = JSONObject(reponse)
            when (json.optString("etat")) {
                "RESULTATS" -> {
                    val arr = json.getJSONArray("classement")
                    Log.i("ClientNetworkUtils", "📦 ${arr.length()} équipes dans le classement")
                    val mapEquipes  = equipes.associateBy { it.equipeNumero }
                    val nbreEquipes = equipes.size
                    val nbreTables  = nbreEquipes / 2

                    // Détecter si c'est un tournoi Mitchell depuis le type du tournoi
                    // On le détecte via l'URL du tournoi déjà connu dans TournoiConfig
                    // ou plus simplement depuis les équipes : on vérifiera côté appelant
                    // Ici on passe typeTournoi en paramètre
                    val list = mutableListOf<ClassementItem>()
                    for (i in 0 until arr.length()) {
                        val o            = arr.getJSONObject(i)
                        val rang         = o.getInt("rang")
                        val numeroEquipe = o.getInt("numeroEquipe")
                        val pts          = o.optDouble("totalPts", 0.0)
                        val eq           = mapEquipes[numeroEquipe]
                        val j1           = eq?.joueur1
                        val j2           = eq?.joueur2
                        Log.i("ClientNetworkUtils", "➡️ Eq.$numeroEquipe | Rang=$rang | Pts=$pts | " +
                                "${j1?.prenom ?: "?"} ${j1?.nom ?: "?"} & ${j2?.prenom ?: "?"} ${j2?.nom ?: "?"}")
                        list.add(
                            ClassementItem(
                                rang = rang,
                                numeroEquipe = numeroEquipe,
                                pts = pts,
                                joueur1Nom = j1?.nom ?: "?",
                                joueur1Prenom = j1?.prenom ?: "?",
                                joueur2Nom = j2?.nom ?: "?",
                                joueur2Prenom = j2?.prenom ?: "?",
                                orientationMitchell = if (typeTournoi == "Mitchell") {
                                    if (numeroEquipe > nbreTables) "NS" else "EO"
                                } else null,
                                scorePct = o.optDouble("scorePct", 0.0)
                            )
                        )
                    }
                    Log.i("ClientNetworkUtils", "✅ Classement final prêt (${list.size} équipes)")
                    list
                }
                "TOURNOI_NON_TERMINE" -> {
                    Log.w("ClientNetworkUtils", "⏳ Tournoi non terminé")
                    etape?.value = EtapeClient.ATTENTE_CLASSEMENT
                    null
                }
                "AUCUN_RESULTAT" -> {
                    Log.w("ClientNetworkUtils", "⚠️ Aucun résultat disponible")
                    etape?.value = EtapeClient.ATTENTE_CLASSEMENT
                    null
                }
                else -> {
                    Log.e("ClientNetworkUtils", "⚠️ Réponse inattendue : $reponse")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "⚠️ Erreur recupererResultatsTournoi", e)
            null
        }
    }

    suspend fun recupererDetailsDonnes(idTournoi: Int): List<DonneResultatDetail>? {
        return try {
            val urlStr = urlAvecPort("getDetailsDonnes?idTournoi=$idTournoi")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            val jsonArray = JSONArray(reponse)
            val list = mutableListOf<DonneResultatDetail>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DonneResultatDetail(
                        numeroDonne = obj.getInt("numero_donne"),
                        equipeNS = obj.getInt("equipeNS"),
                        equipeEO = obj.getInt("equipeEO"),
                        contrat = obj.getString("contrat"),
                        declarant = obj.getString("declarant"),
                        resultatContrat = obj.getString("resultat_contrat"),
                        nombrePli = obj.getInt("nombre_pli"),
                        carteEntame = obj.getString("carteEntame"),
                        pointsNS = obj.getInt("pointsNS"),
                        pointsEO = obj.getInt("pointsEO"),
                        ptsNS = obj.getDouble("ptsNS"),
                        ptsEO = obj.getDouble("ptsEO"),
                        vulnerable = obj.getString("vulnerable")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "Erreur lors de la récupération des détails", e)
            null
        }
    }

    suspend fun corrigerResultat(
        idTournoi: Int, numeroDonne: Int, equipeNS: Int,
        contrat: String, declarant: String, resultatContrat: String,
        points: Int, nombrePli: Int, carteEntame: String
    ): Boolean {
        return try {
            val urlStr = urlAvecPort("corrigerResultat")
            val body = JSONObject().apply {
                put("idTournoi", idTournoi); put("numeroDonne", numeroDonne); put("equipeNS", equipeNS)
                put("contrat", contrat); put("declarant", declarant); put("resultatContrat", resultatContrat)
                put("points", points); put("nombrePli", nombrePli); put("carteEntame", carteEntame)
            }.toString()
            val resp = withContext(Dispatchers.IO) { postUrl(urlStr, body) }
            JSONObject(resp).optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ corrigerResultat : ${e.message}")
            false
        }
    }

    suspend fun recupererDonneComplete(
        idTournoi: Int,
        numeroDonne: Int,
        equipeNS: Int
    ): DonneComplete? {
        return try {
            val urlStr = urlAvecPort("getDonneComplete?idTournoi=$idTournoi&numeroDonne=$numeroDonne&equipeNS=$equipeNS")
            val reponse = withContext(Dispatchers.IO) { readUrl(urlStr) }
            val jsonGlobal = JSONObject(reponse)

            val mapMains = mutableMapOf<String, List<Carte>>()
// Le serveur peut retourner null, un objet {} ou un tableau vide []
// On vérifie que c'est bien un objet avant de parser
            val mainsRaw = jsonGlobal.opt("mains")
            if (mainsRaw is JSONObject) {
                for (pos in listOf("N", "S", "E", "O")) {
                    val arr = mainsRaw.getJSONArray(pos)
                    mapMains[pos] = (0 until arr.length()).map { Carte.fromCode(arr.getString(it)) }
                }
                Log.i("ClientNetworkUtils", "✅ Mains récupérées")
            } else {
                Log.i("ClientNetworkUtils", "⚠️ Pas de mains pour cette donne (reçu : ${mainsRaw?.javaClass?.simpleName ?: "null"})")
            }

            val jsonEncheres = jsonGlobal.getJSONArray("encheres")
            val listeEncheres = (0 until jsonEncheres.length()).map {
                val obj = jsonEncheres.getJSONObject(it)
                AnnonceJoueur(joueur = obj.getString("joueur"), annonce = obj.getString("annonce"))
            }

            val vulnerable = jsonGlobal.optString("vulnerable", "P")
            val donneur    = jsonGlobal.optString("donneur", "N")
            val contrat    = jsonGlobal.optString("contrat", "")
            val declarant  = jsonGlobal.optString("declarant", "")

            Log.i("ClientNetworkUtils", "📥 getDonneComplete - Vulnerable: '$vulnerable', Donneur: '$donneur', Contrat: '$contrat', Déclarant: '$declarant'")
            Log.i("ClientNetworkUtils", "   Mains: ${if (mapMains.isEmpty()) "absentes" else "présentes"}, Enchères: ${listeEncheres.size}")

            DonneComplete(mapMains, listeEncheres, vulnerable, donneur, contrat, declarant)
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "Erreur lors de la récupération de la donne complète : ${e.message}")
            null
        }
    }

    private fun parseEquipeString(str: String): Equipe {
        val regex = Regex(
            """equipeNumero=(\d+).*?idJoueur=(\d+), nom=([^,]+), prenom=([^)]+)\).*?idJoueur=(\d+), nom=([^,]+), prenom=([^)]+)\).*?idTournoi=(\d+)"""
        )
        val match = regex.find(str)
            ?: throw IllegalArgumentException("Format équipe invalide: $str")
        val (eqNum, j1Id, j1Nom, j1Prenom, j2Id, j2Nom, j2Prenom, idTournoi) = match.destructured
        return Equipe(
            equipeNumero = eqNum.toInt(),
            joueur1 = Joueur(j1Id.toInt(), j1Nom.trim(), j1Prenom.trim()),
            joueur2 = Joueur(j2Id.toInt(), j2Nom.trim(), j2Prenom.trim()),
            idTournoi = idTournoi.toInt()
        )
    }

    private fun encodeMains(mains: List<List<Carte>>?): String {
        if (mains == null) return "[]"
        return mains.joinToString(prefix = "[", postfix = "]") { main ->
            main.joinToString(prefix = "[", postfix = "]") { carte ->
                "\"${carte.valeur}${carte.couleur}\""
            }
        }
    }

    suspend fun envoyerMajPointsDonne(
        idTournoi: Int,
        numeroDonne: Int,
        numeroTable: Int,
        ptsNS: Double,
        ptsEO: Double
    ) {
        try {
            val urlStr = urlAvecPort("majPointsDonne?idTournoi=$idTournoi&numeroDonne=$numeroDonne&numeroTable=$numeroTable&ptsNS=$ptsNS&ptsEO=$ptsEO")
            Log.i("ClientNetworkUtils", "🌍 Envoi majPointsDonne : $urlStr")
            withContext(Dispatchers.IO) { readUrl(urlStr) }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur majPointsDonne : ${e.message}")
        }
    }

    suspend fun envoyerMajClassementEquipe(
        idTournoi: Int,
        numeroEquipe: Int,
        totalPts: Double,
        rang: Int
    ) {
        try {
            val urlStr = urlAvecPort("majClassementEquipe?idTournoi=$idTournoi&numeroEquipe=$numeroEquipe&totalPts=$totalPts&rang=$rang")
            Log.i("ClientNetworkUtils", "🌍 Envoi majClassementEquipe : $urlStr")
            withContext(Dispatchers.IO) { readUrl(urlStr) }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ Erreur majClassementEquipe : ${e.message}")
        }
    }

    // ── À AJOUTER dans ClientNetworkUtils.kt ──────────────────────────────────

    /**
     * Enregistre les mains d'une donne relais sur le serveur distant.
     * Appel POST vers l'endpoint "enregistrerMainsRelais".
     */
    suspend fun enregistrerMainsRelais(
        idTournoi: Int,
        numeroDonne: Int,
        mains: List<List<String>>
    ): Boolean {
        return try {
            val gson = Gson()
            val bodyJson = gson.toJson(
                mapOf(
                    "idTournoi"    to idTournoi,
                    "numeroDonne"  to numeroDonne,
                    "mains"        to mains
                )
            )
            val urlStr = urlAvecPort("enregistrerMainsRelais")
            Log.i("ClientNetworkUtils", "➡️ enregistrerMainsRelais POST → $urlStr")

            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 enregistrerMainsRelais réponse : $response")

            val json = JSONObject(response)
            json.optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ enregistrerMainsRelais : ${e.message}", e)
            false
        }
    }

// =========================================================
// REMPLACER dans ClientNetworkUtils.kt la fonction exporterTournoi
// =========================================================

    suspend fun exporterTournoi(jsonTournoi: String): String {
        return try {
            val urlStr = urlAvecPort("importerTournoi")
            Log.i("ClientNetworkUtils", "📤 exporterTournoi POST → $urlStr")

            val conn = ouvrirConnexion(urlStr)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout    = 15000
            conn.outputStream.use { it.write(jsonTournoi.toByteArray()) }

            val response = conn.inputStream.bufferedReader().readText()
            Log.i("ClientNetworkUtils", "📩 exporterTournoi réponse : $response")

            val json = JSONObject(response)
            val etat = json.optString("etat", "ERREUR")

            if (etat == "OK") {
                val idTournoi = json.optInt("idTournoi", -1)
                if (idTournoi > 0) "OK:$idTournoi" else "OK"
            } else {
                "ERREUR: ${json.optString("message", "inconnue")}"
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ exporterTournoi erreur : ${e.message}")
            "ERREUR"
        }
    }

    suspend fun majCodeAdherent(codeAdherent: String): String {
        return try {
            val urlStr = urlAvecPort("majCodeAdherent")
            Log.i("ClientNetworkUtils", "➡️ majCodeAdherent POST → $urlStr")

            val bodyJson = Gson().toJson(mapOf("code_adherent" to codeAdherent))

            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            Log.i("ClientNetworkUtils", "📩 Réponse majCodeAdherent : $response")

            JSONObject(response).optString("etat", "ERREUR")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ majCodeAdherent erreur : ${e.message}")
            "ERREUR"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Journal des erreurs
    // ─────────────────────────────────────────────────────────────────────────

    fun logErreurServeur(idTournoi: Int, equipeNumero: Int, etape: String, message: String) {
        try {
            val url = urlAvecPort(
                "logErreur?idTournoi=$idTournoi&equipeNumero=$equipeNumero" +
                "&etape=${URLEncoder.encode(etape, "UTF-8")}" +
                "&message=${URLEncoder.encode(message, "UTF-8")}"
            )
            postUrl(url, "")
            Log.i("ClientNetworkUtils", "📋 Erreur loggée sur serveur : [$etape] $message")
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ logErreurServeur : ${e.message}")
        }
    }

    fun recupererErreursLog(idTournoi: Int): List<ErreurLogItem> {
        return try {
            val url = urlAvecPort("getErreursLog?idTournoi=$idTournoi")
            val json = readUrl(url)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ErreurLogItem(
                    id = o.optInt("id"),
                    timestamp = o.optString("timestamp"),
                    idTournoi = o.optInt("id_tournoi"),
                    equipeNumero = o.optInt("equipe_numero"),
                    etape = o.optString("etape"),
                    message = o.optString("message")
                )
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ recupererErreursLog : ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Donnes avant tournoi
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun sauvegarderMainsAvantTournoi(numeroDonne: Int, mains: List<List<String>>): Boolean {
        return try {
            val bodyJson = Gson().toJson(mapOf("numeroDonne" to numeroDonne, "mains" to mains))
            val urlStr = urlAvecPort("sauvegarderMainsAvantTournoi")
            Log.i("ClientNetworkUtils", "➡️ sauvegarderMainsAvantTournoi POST donne=$numeroDonne")
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            JSONObject(response).optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ sauvegarderMainsAvantTournoi : ${e.message}", e)
            false
        }
    }

    suspend fun getMainsAvantTournoi(): List<DonneAvantTournoi> {
        return try {
            val urlStr = urlAvecPort("getMainsAvantTournoi")
            Log.i("ClientNetworkUtils", "🌍 getMainsAvantTournoi : $urlStr")
            val response = withContext(Dispatchers.IO) { readUrl(urlStr) }
            val json = JSONObject(response)
            if (json.optString("etat") != "OK") return emptyList()
            val arr = json.getJSONArray("donnes")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val mainsArr = o.getJSONArray("mains")
                val mains = (0 until mainsArr.length()).map { j ->
                    val m = mainsArr.getJSONArray(j)
                    (0 until m.length()).map { k -> m.getString(k) }
                }
                DonneAvantTournoi(
                    numeroDonne = o.getInt("numero_donne"),
                    donneur     = o.optString("donneur", "N"),
                    vulnerable  = o.optString("vulnerable", "P"),
                    mains       = mains
                )
            }
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ getMainsAvantTournoi : ${e.message}", e)
            emptyList()
        }
    }

    suspend fun supprimerMainsAvantTournoi(): Boolean {
        return try {
            val urlStr = urlAvecPort("supprimerMainsAvantTournoi")
            Log.i("ClientNetworkUtils", "➡️ supprimerMainsAvantTournoi POST")
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, "{}") }
            JSONObject(response).optString("etat") == "OK"
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ supprimerMainsAvantTournoi : ${e.message}", e)
            false
        }
    }

    suspend fun attribuerDonnesAuTournoi(idTournoi: Int): Int {
        return try {
            val bodyJson = Gson().toJson(mapOf("idTournoi" to idTournoi))
            val urlStr = urlAvecPort("attribuerDonnesAuTournoi")
            Log.i("ClientNetworkUtils", "➡️ attribuerDonnesAuTournoi POST tournoi=$idTournoi")
            val response = withContext(Dispatchers.IO) { postUrl(urlStr, bodyJson) }
            val json = JSONObject(response)
            if (json.optString("etat") == "OK") json.optInt("nb", 0) else -1
        } catch (e: Exception) {
            Log.e("ClientNetworkUtils", "❌ attribuerDonnesAuTournoi : ${e.message}", e)
            -1
        }
    }

}