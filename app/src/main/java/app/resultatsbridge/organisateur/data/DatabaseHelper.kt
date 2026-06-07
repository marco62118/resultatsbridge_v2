package app.resultatsbridge.organisateur.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DatabaseHelper {

    /**
     *  base de données "tournoi.db" contien tous les types de tournoi
     *  tous les tournois fermé et encours (ouvert)
     *  tous les joueurs du l'amicale de Bridge d'Embrun
     */
    private const val DATABASE_NAME = "tournoi.db"
    private const val ASSETS_DATABASE = "tournoi_vierge.db"
    private var db: SQLiteDatabase? = null

    // 🔹 Initialisation de la base : copie depuis assets si elle n’existe pas encore
    fun initializeDatabase(context: Context) {
        try {
            val dbPath = context.getDatabasePath(DATABASE_NAME)
            if (!dbPath.exists()) {
                Log.i("DatabaseHelper", "📁 Copie de la base depuis assets (premier lancement)")
                copyDatabaseFromAssets(context, dbPath)
            } else {
                Log.i("DatabaseHelper", "✅ Base déjà existante détectée")
            }
            db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            Log.i("DatabaseHelper", "📂 Base ouverte avec succès")
            migrerSiNecessaire(db!!)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "❌ Erreur lors de l’initialisation de la base", e)
        }
    }

    private fun migrerSiNecessaire(database: SQLiteDatabase) {
        val colonnesExistantes = mutableSetOf<String>()
        database.rawQuery("PRAGMA table_info(tournois)", null).use { c ->
            while (c.moveToNext()) colonnesExistantes.add(c.getString(c.getColumnIndexOrThrow("name")))
        }
        val aAjouter = mapOf(
            "nbre_mouvements"      to "INTEGER NOT NULL DEFAULT 0",
            "nbre_donnes_par_table" to "INTEGER NOT NULL DEFAULT 0",
            "nbre_tables"          to "INTEGER NOT NULL DEFAULT 0"
        )
        aAjouter.forEach { (col, def) ->
            if (col !in colonnesExistantes) {
                database.execSQL("ALTER TABLE tournois ADD COLUMN $col $def")
                Log.i("DatabaseHelper", "🔧 Colonne ajoutée : $col")
            }
        }
        if ("nbre_donne" in colonnesExistantes && "nbre_donne_total" !in colonnesExistantes) {
            database.execSQL("ALTER TABLE tournois RENAME COLUMN nbre_donne TO nbre_donne_total")
            Log.i("DatabaseHelper", "🔧 Colonne renommée : nbre_donne → nbre_donne_total")
        }
        // Ajouter MitchellGuéridon dans la table type si absent (tables=0 = type dynamique)
        database.rawQuery("SELECT COUNT(*) FROM type WHERE type = 'MitchellGueridon'", null).use { c ->
            if (c.moveToFirst() && c.getInt(0) == 0) {
                database.execSQL("INSERT INTO type (type, nombre_table, nombre_donne) VALUES ('MitchellGueridon', 0, 0)")
                Log.i("DatabaseHelper", "🔧 Type MitchellGuéridon ajouté dans la table type")
            }
        }
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS erreurs_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                id_tournoi INTEGER DEFAULT -1,
                equipe_numero INTEGER DEFAULT -1,
                etape TEXT DEFAULT '',
                message TEXT DEFAULT ''
            )"""
        )
        Log.i("DatabaseHelper", "🔧 Table erreurs_log vérifiée/créée")
    }

    // 🔹 Ouvre la base (lecture/écriture)
    fun getDatabase(context: Context): SQLiteDatabase {
        if (db == null || !db!!.isOpen) {
            val dbPath = context.getDatabasePath(DATABASE_NAME)
            db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            Log.i("DatabaseHelper", "📂 Base OUVERTE - Stack: ${Thread.currentThread().stackTrace[3]}")  // ✅ AJOUTEZ CECI
        }
        return db!!
    }


    // ✅ Fermeture forcée avec libération du pool
    fun closeDatabase() {
        try {
            db?.let {
                if (it.isOpen) {
                    // ✅ Forcer la libération des connexions du pool
                    it.acquireReference()
                    try {
                        it.releaseReference()
                    } catch (e: Exception) {
                        Log.w("DatabaseHelper", "Erreur releaseReference: ${e.message}")
                    }
                    it.close()
                    Log.i("DatabaseHelper", "📌 Base FERMÉE - Stack: ${Thread.currentThread().stackTrace[3]}")  // ✅ AJOUTEZ CECI
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "❌ Erreur lors de la fermeture de la base", e)
        } finally {
            db = null
            // ✅ Forcer le garbage collector à nettoyer
            System.gc()
        }
    }

    // ✅ NOUVELLE FONCTION : Fermeture forcée avec attente
    fun closeDatabaseAndWait() {
        closeDatabase()
        try {
            Thread.sleep(200)  // Attendre que le pool libère
            Log.i("DatabaseHelper", "⏱️ Attente libération pool terminée")
        } catch (e: InterruptedException) {
            Log.w("DatabaseHelper", "Interruption pendant l'attente")
        }
    }

    // 🔹 Copie le fichier tournoi.db depuis les assets
    private fun copyDatabaseFromAssets(context: Context, destinationFile: File) {
        try {
            val inputStream = context.assets.open(ASSETS_DATABASE)
            destinationFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            Log.i("DatabaseHelper", "📦 Base copiée avec succès depuis les assets")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "❌ Erreur lors de la copie de la base depuis assets", e)
        }
    }
}
