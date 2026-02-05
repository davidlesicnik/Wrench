package com.lesicnik.wrench.data.remote

import com.lesicnik.wrench.util.LocalNetwork
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class LocalHttpOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.scheme == "http" && !LocalNetwork.isLocalHost(url.host)) {
            throw IOException("Insecure HTTP is only allowed for local network addresses")
        }

        return chain.proceed(request)
    }
}

