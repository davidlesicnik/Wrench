package com.lesicnik.wrench.data.remote

import com.lesicnik.wrench.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var lubeLoggerApi: LubeLoggerApi? = null

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Redact sensitive headers from logs
            val redactedMessage = if (message.contains("x-api-key", ignoreCase = true)) {
                message.replace(Regex("x-api-key:\\s*\\S+", RegexOption.IGNORE_CASE), "x-api-key: [REDACTED]")
            } else {
                message
            }
            android.util.Log.d("OkHttp", redactedMessage)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun getApi(baseUrl: String): LubeLoggerApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        // Reuse existing instance if base URL hasn't changed
        if (normalizedUrl == currentBaseUrl && lubeLoggerApi != null) {
            return lubeLoggerApi!!
        }

        // Create new Retrofit instance for new base URL
        currentBaseUrl = normalizedUrl
        retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        lubeLoggerApi = retrofit!!.create(LubeLoggerApi::class.java)
        return lubeLoggerApi!!
    }

    @Synchronized
    fun clearCache() {
        currentBaseUrl = null
        retrofit = null
        lubeLoggerApi = null
    }
}
