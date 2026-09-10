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
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

val Context.nobelStore by preferencesDataStore("nobel_auth")

class OwnNobelRepository(private val ctx: Context) : NobelRepository {
    private val client =
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 20000 }
        }
    private val tokenKey = stringPreferencesKey("token")
    private val baseKey = stringPreferencesKey("base")
    val authenticated = ctx.nobelStore.data.map { !it[tokenKey].isNullOrBlank() }

    suspend fun login(base: String, user: String, password: String) {
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "Укажите http:// или https://"
        }
        val body = buildJsonObject {
            put("username", user)
            put("password", password)
        }
        val result =
            client
                .post(base.trimEnd('/') + "/login") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                .bodyAsText()
        val token = Json.parseToJsonElement(result).jsonObject["token"]!!.jsonPrimitive.content
        ctx.nobelStore.edit {
            it[tokenKey] = token
            it[baseKey] = base.trimEnd('/')
        }
    }

    override suspend fun prizes(year: String, category: String): List<Prize> {
        val prefs = ctx.nobelStore.data.first()
        val base = prefs[baseKey] ?: return emptyList()
        val result =
            client
                .get("$base/prizes") {
                    prefs[tokenKey]?.let { bearerAuth(it) }
                    if (year.isNotBlank()) parameter("year", year)
                    if (category.isNotBlank()) parameter("category", category)
                }
                .bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(result)
    }

    override suspend fun detail(laureate: Laureate) = laureate
}
