package com.signalplay.tv

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canais")
data class CanalEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val urlImagem: String,
    val categoryId: String,
    val streamUrl: String,
    val epgChannelId: String
)
