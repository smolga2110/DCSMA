package edu.practice.domain

import kotlinx.serialization.Serializable

@Serializable
data class Laureate(
    val id: String,
    val fullName: String,
    val motivation: String,
    val portion: String = "1",
    val birthCountry: String = "",
    val portraitUrl: String = "",
)

@Serializable
data class Prize(
    val id: String,
    val year: Int,
    val category: String,
    val laureates: List<Laureate>,
)

@Serializable data class User(val id: Long, val username: String, val role: String = "student")

data class Credentials(val user: User, val hash: String)

interface PrizeRepository : AutoCloseable {
    fun prizes(): List<Prize>

    fun credentials(username: String): Credentials?

    fun user(id: Long): User?

    fun favorites(userId: Long): List<Prize>

    fun addFavorite(userId: Long, id: String): Boolean

    fun removeFavorite(userId: Long, id: String): Boolean

    override fun close() {}
}
