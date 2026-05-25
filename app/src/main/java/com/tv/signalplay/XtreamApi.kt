package com.tv.signalplay

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Modelo que representa um filme no servidor Xtream
data class XtreamVod(
    val stream_id: Int,
    val name: String,
    val stream_icon: String?
)

// O "Carteiro"
interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_streams"
    ): List<XtreamVod>
}

object XtreamClient {
    fun create(baseUrl: String): XtreamApiService {
        // Garante que a URL sempre termine com uma barra "/"
        val formatUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        return Retrofit.Builder()
            .baseUrl(formatUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }
}
