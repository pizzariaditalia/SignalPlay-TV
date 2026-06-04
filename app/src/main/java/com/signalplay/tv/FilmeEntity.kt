package com.signalplay.tv

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filmes_series")
data class FilmeEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val urlImagem: String,
    val streamUrl: String,
    val tipo: String, // "filme" ou "serie"
    val categoryId: String
)
