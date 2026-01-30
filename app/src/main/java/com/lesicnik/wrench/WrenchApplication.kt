package com.lesicnik.wrench

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WrenchApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // Interceptor to handle LubeLogger's quirky 302 + image body responses
        val redirectFixInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())

            // If we get a 302 with image content-type, treat it as 200
            if (response.code == 302 && response.header("content-type")?.startsWith("image/") == true) {
                response.newBuilder()
                    .code(200)
                    .message("OK")
                    .build()
            } else {
                response
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(redirectFixInterceptor)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
