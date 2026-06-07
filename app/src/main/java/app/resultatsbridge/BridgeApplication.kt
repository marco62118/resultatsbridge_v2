package app.resultatsbridge

import android.app.Application
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class BridgeApplication : Application() {

    companion object {
        var customSslSocketFactory: SSLSocketFactory? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("BridgeApp", "### onCreate SDK=${Build.VERSION.SDK_INT} v3 ###")

        // ISRG Root X1 (Let's Encrypt) n'est dans le trust store Android qu'à partir
        // de la mise à jour sécurité 7.1.1 (API 25). Android 6 et 7.0 sont affectés.
        // On installe manuellement le trust store étendu sur ces versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            installerSslIsrgRootX1()
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun installerSslIsrgRootX1() {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val trustedRoots = mutableListOf<X509Certificate>()

            // Chaque certificat est chargé indépendamment : sur Android 6,
            // ISRG Root X2 (EC P-384) provoque un WRONG_TAG dans le parser ASN.1
            // de BoringSSL. On garde X1 (RSA) même si X2 échoue.
            try {
                val cert = resources.openRawResource(R.raw.isrg_root_x1).use {
                    cf.generateCertificate(it) as X509Certificate
                }
                trustedRoots.add(cert)
                Log.e("SSL_DEBUG", "certIsrg1 OK: subject=${cert.subjectDN} algo=${cert.publicKey.algorithm}")
            } catch (e: Exception) {
                Log.e("SSL_DEBUG", "certIsrg1 FAIL: ${e.message}")
            }

            try {
                val cert = resources.openRawResource(R.raw.isrg_root_x2).use {
                    cf.generateCertificate(it) as X509Certificate
                }
                trustedRoots.add(cert)
                Log.e("SSL_DEBUG", "certIsrg2 OK: subject=${cert.subjectDN} algo=${cert.publicKey.algorithm}")
            } catch (e: Exception) {
                Log.e("SSL_DEBUG", "certIsrg2 FAIL (EC P-384 non supporté Android 6): ${e.message}")
            }

            if (trustedRoots.isEmpty()) {
                Log.e("BridgeApplication", "❌ Aucun certificat racine chargé — SSL non installé")
                return
            }

            val tmSystem = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .also { it.init(null as KeyStore?) }
                .trustManagers.first { it is X509TrustManager } as X509TrustManager

            val tmCombine = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    tmSystem.acceptedIssuers + trustedRoots.toTypedArray()

                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    tmSystem.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    Log.e("SSL_DEBUG", "Chaîne reçue : ${chain.size} cert(s)")
                    chain.forEachIndexed { i, cert ->
                        Log.e("SSL_DEBUG", "  [$i] Subject: ${cert.subjectDN}")
                        Log.e("SSL_DEBUG", "  [$i] Issuer : ${cert.issuerDN} sigAlgo=${cert.sigAlgName}")
                    }

                    // Essai 1 : trust store système (Android 7+ avec ISRG Root X1 inclus)
                    try {
                        tmSystem.checkServerTrusted(chain, authType)
                        Log.e("SSL_DEBUG", "✅ tmSystem OK")
                        return
                    } catch (e: CertificateException) {
                        Log.e("SSL_DEBUG", "⚠️ tmSystem échoué")
                    }

                    // Essai 2 : trust store combiné racines + intermédiaires de la chaîne.
                    // Sur Android 6, le PKIX path builder ne peut pas valider une chaîne
                    // qui remonte à une racine absente du store système. En ajoutant E7
                    // (l'intermédiaire fourni par le serveur) comme trust anchor explicite,
                    // le validateur termine le chemin [leaf → E7] sans chercher plus haut.
                    val ksAvecIntermediaires = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(null, null)
                        trustedRoots.forEachIndexed { i, root -> setCertificateEntry("root_$i", root) }
                        chain.drop(1).forEachIndexed { i, cert -> setCertificateEntry("intermediate_$i", cert) }
                    }
                    val tmAvecIntermediaires = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        .also { it.init(ksAvecIntermediaires) }
                        .trustManagers.first { it is X509TrustManager } as X509TrustManager
                    tmAvecIntermediaires.checkServerTrusted(chain, authType)
                    Log.e("SSL_DEBUG", "✅ validation avec intermédiaires OK")
                }
            }

            val sslContext = SSLContext.getInstance("TLS").also {
                it.init(null, arrayOf(tmCombine), null)
            }
            SSLContext.setDefault(sslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            customSslSocketFactory = sslContext.socketFactory
            Log.i("BridgeApplication", "✅ ISRG Root X1 + X2 installés pour Android 6")

        } catch (e: Exception) {
            Log.e("BridgeApplication", "❌ Échec installation SSL Android 6 : ${e.javaClass.name} ${e.message}")
        }
    }
}
