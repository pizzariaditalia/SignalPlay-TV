package com.signalplay.tv

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirCategorias(categorias: List<CategoriaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirCanais(canais: List<CanalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirFilmesSeries(itens: List<FilmeEntity>)

    @Query("SELECT * FROM categorias WHERE tipo = :tipo ORDER BY nome ASC")
    suspend fun getCategoriasPorTipo(tipo: String): List<CategoriaEntity>

    @Query("SELECT * FROM canais WHERE categoryId = :catId")
    suspend fun getCanaisPorCategoria(catId: String): List<CanalEntity>

    @Query("SELECT * FROM canais")
    suspend fun getTodosCanais(): List<CanalEntity>

    @Query("SELECT * FROM filmes_series WHERE tipo = 'filme' AND categoryId = :catId")
    suspend fun getFilmesPorCategoria(catId: String): List<FilmeEntity>

    @Query("SELECT * FROM filmes_series WHERE tipo = 'serie' AND categoryId = :catId")
    suspend fun getSeriesPorCategoria(catId: String): List<FilmeEntity>

    @Query("SELECT * FROM filmes_series WHERE tipo = 'filme'")
    suspend fun getTodosFilmes(): List<FilmeEntity>

    @Query("SELECT * FROM filmes_series WHERE tipo = 'serie'")
    suspend fun getTodasSeries(): List<FilmeEntity>

    @Query("DELETE FROM categorias")
    suspend fun limparCategorias()

    @Query("DELETE FROM canais")
    suspend fun limparCanais()

    @Query("DELETE FROM filmes_series")
    suspend fun limparFilmesSeries()
}
