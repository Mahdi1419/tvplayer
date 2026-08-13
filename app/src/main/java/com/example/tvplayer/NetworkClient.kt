package com.example.tvplayer

import android.content.Context
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Network client used by the player and list downloader.
 *
 * Some Android TV devices ship with an old CA store and reject otherwise
 * reachable HTTPS servers with certificate-chain errors. On TV devices only,
 * the app uses a compatibility TLS client that accepts the server certificate
 * without CA/hostname validation. Phones keep normal certificate validation.
 */
object NetworkClient {

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun create(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)

        if (!isTv(context)) {
            return builder.build()
        }

        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())

        val hostnameVerifier = HostnameVerifier { _, _ -> true }

        return builder
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier(hostnameVerifier)
            .build()
    }
}
