package edu.practice.domain

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val email: String,
    val image: String,
)

interface AuthRepository {
    suspend fun login(username: String, password: String)

    suspend fun users(): List<User>

    suspend fun user(id: Int): User

    suspend fun logout()

    suspend fun hasToken(): Boolean
}

class Login(private val repo: AuthRepository) {
    suspend operator fun invoke(username: String, password: String) {
        require(username.isNotBlank() && password.isNotBlank()) { "Заполните логин и пароль" }
        repo.login(username, password)
    }
}
