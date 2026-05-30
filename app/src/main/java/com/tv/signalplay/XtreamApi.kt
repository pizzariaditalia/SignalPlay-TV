package com.tv.signalplay

import com.google.gson.JsonElement
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamClient {

    // O Comando exato que faltava para o Login funcionar
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") user: String,
        @Query("password") pass: String
    ): JsonElement

    // As fundações preparadas para quando formos carregar os filmes e canais
    @GET("player_api.php?action=get_live_categories")
    suspend fun getLiveCategories(@Query("username") u: String, @Query("password") p: String): JsonElement

    @GET("player_api.php?action=get_live_streams")
    suspend fun getLiveStreams(@Query("username") u: String, @Query("password") p: String): JsonElement

    @GET("player_api.php?action=get_vod_categories")
    suspend fun getVodCategories(@Query("username") u: String, @Query("password") p: String): JsonElement

    @GET("player_api.php?action=get_vod_streams")
    suspend fun getVodStreams(@Query("username") u: String, @Query("password") p: String): JsonElement

    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(@Query("username") u: String, @Query("password") p: String): JsonElement

    @GET("player_api.php?action=get_series")
    suspend fun getSeriesStreams(@Query("username") u: String, @Query("password") p: String): JsonElement

    companion object {
        fun create(baseUrl: String): XtreamClient {
            // Garante que a URL sempre termine com uma barra "/" para não dar erro no motor
            val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            
            val retrofit = Retrofit.Builder()
                .baseUrl(cleanUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(XtreamClient::class.java)
        }
    }
}
