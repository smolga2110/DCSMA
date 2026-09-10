package edu.practice.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import edu.practice.domain.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

val Context.authStore by preferencesDataStore("auth")

class KtorAuthRepository(private val ctx: Context) : AuthRepository {
    private val client =
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 20000 }
        }
    private val key = stringPreferencesKey("access_token")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun login(username: String, password: String) {
        val response =
            client.post("https://dummyjson.com/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                            put("username", username)
                            put("password", password)
                            put("expiresInMins", 30)
                        }
                        .toString()
                )
            }
        val token =
            json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["accessToken"]!!
                .jsonPrimitive
                .content
        ctx.authStore.edit { it[key] = token }
    }

    private suspend fun token() = ctx.authStore.data.first()[key] ?: error("Нужно войти в аккаунт")

    override suspend fun users(): List<User> {
        val t = token()
        val response =
            client
                .get("https://dummyjson.com/users") {
                    bearerAuth(t)
                    parameter("limit", 0)
                }
                .bodyAsText()
        return json.decodeFromJsonElement(json.parseToJsonElement(response).jsonObject["users"]!!)
    }

    override suspend fun user(id: Int): User {
        val t = token()
        return json.decodeFromString(
            client.get("https://dummyjson.com/users/$id") { bearerAuth(t) }.bodyAsText()
        )
    }

    override suspend fun logout() {
        ctx.authStore.edit { it.remove(key) }
    }

    override suspend fun hasToken() = !ctx.authStore.data.first()[key].isNullOrBlank()
}
