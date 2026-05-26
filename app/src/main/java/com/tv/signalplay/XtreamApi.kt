package com.tv.signalplay

import com.google.gson.JsonElement
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class XtreamVod(val stream_id: Int, val name: String, val stream_icon: String?, val category_name: String? = null, val rating: Double? = 0.0)
data class XtreamSerie(val series_id: Int, val name: String, val cover: String?, val category_name: String? = null, val rating: Double? = 0.0)
data class XtreamLive(val stream_id: Int, val name: String, val stream_icon: String?, val category_name: String? = null)

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getVodStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_streams"): JsonElement 

    @GET("player_api.php")
    suspend fun getSeriesStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series"): JsonElement 

    @GET("player_api.php")
    suspend fun getLiveStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_live_streams"): JsonElement

    // NOVOS: Pedir informações detalhadas (Sinopse)
    @GET("player_api.php")
    suspend fun getVodInfo(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_info", @Query("vod_id") id: Int): JsonElement

    @GET("player_api.php")
    suspend fun getSeriesInfo(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series_info", @Query("series_id") id: Int): JsonElement
}

object XtreamClient {
    fun create(baseUrl: String): XtreamApiService {
        val formatUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder().baseUrl(formatUrl).addConverterFactory(GsonConverterFactory.create()).build().create(XtreamApiService::class.java)
    }
}
