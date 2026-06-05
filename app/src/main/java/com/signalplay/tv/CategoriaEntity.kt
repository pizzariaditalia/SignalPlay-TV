package com.signalplay.tv

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorias")
data class CategoriaEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val tipo: String,
    val ordem: Int
)