package com.tv.signalplay

import com.google.gson.JsonElement
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Modelo de Filme
data class XtreamVod(
    val stream_id: Int,
    val name: String,
    val stream_icon: String?,
    val category_name: String? = null // <- O detalhe que faltava para criar os trilhos premium!
)

// Modelo de Canal Ao Vivo
data class XtreamLive(
    val stream_id: Int,
    val name: String,
    val stream_icon: String?,
    val category_name: String? = null
)

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_vod_streams"
    ): JsonElement 

    // Chamada para pedir Canais Ao Vivo
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_streams"
    ): JsonElement
}

object XtreamClient {
    fun create(baseUrl: String): XtreamApiService {
        val formatUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        return Retrofit.Builder()
            .baseUrl(formatUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }
}
