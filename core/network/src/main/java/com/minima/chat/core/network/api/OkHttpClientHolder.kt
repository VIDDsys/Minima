package com.minima.chat.core.network.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutable OkHttp holder so optional certificate pins can be applied without
 * restarting the process. Blank pins = pinning off (default).
 *
 * No callTimeout: SSE streams can outlive a single wall call.
 * readTimeout covers stalled streams; AgentLoop adds an idle watchdog too.
 */
@Singleton
class OkHttpClientHolder @Inject constructor() {
    @Volatile
    private var pinsKey: String = ""

    @Volatile
    var client: OkHttpClient = build(null)
        private set

    @Synchronized
    fun applyCertificatePins(raw: String?) {
        val key = raw.orEmpty().trim()
        if (key == pinsKey) return
        pinsKey = key
        client = build(key)
    }

    private fun build(pinsRaw: String?): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // unbounded; AgentLoop wall + idle watchdog own the ceiling
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val clean = req.newBuilder()
                    .removeHeader("X-Debug-Api-Key")
                    .build()
                chain.proceed(clean)
            }
        CertificatePinConfig.buildPinner(pinsRaw)?.let { b.certificatePinner(it) }
        return b.build()
    }
}
