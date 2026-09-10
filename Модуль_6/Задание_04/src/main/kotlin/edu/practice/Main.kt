package edu.practice

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import edu.practice.data.*
import edu.practice.domain.*
import edu.practice.routing.*
import edu.practice.security.*
import edu.practice.service.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.Json

fun configuration(): Map<String, String> {
    val f = File(".env")
    val local =
        if (f.isFile)
            f.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    val clean = line.trim().removePrefix("\uFEFF")
                    if (clean.startsWith("#") || !clean.contains('=')) null
                    else
                        clean.substringBefore('=').trim() to
                            clean.substringAfter('=').trim().trim('"', '\'')
                }
                .toMap()
        else emptyMap()
    return local + System.getenv()
}

fun main() {
    val config = configuration()
    val port = config["PORT"]?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port) { module(config) }.start(wait = true)
}

fun Application.module(
    config: Map<String, String> = configuration(),
    repositoryOverride: PrizeRepository? = null,
) {
    val dbMode = false
    val seedText =
        Thread.currentThread().contextClassLoader.getResource("prizes.json")?.readText() ?: "[]"
    val seeds = Json { ignoreUnknownKeys = true }.decodeFromString<List<Prize>>(seedText)
    val repo =
        repositoryOverride
            ?: if (dbMode)
                PostgresPrizeRepository(
                    config["DATABASE_URL"] ?: error("Добавьте DATABASE_URL в локальный .env"),
                    seeds,
                )
            else MemoryPrizeRepository(seeds)
    val secret =
        config["JWT_SECRET"]
            ?: Base64.getEncoder()
                .encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    require(secret.length >= 32) { "JWT_SECRET должен содержать не менее 32 символов" }
    val auth = AuthService(repo, secret)
    val service = PrizeService(repo)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
    install(CallLogging)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (e.message ?: "Некорректный запрос")),
            )
        }
        exception<Throwable> { call, e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            call.application.environment.log.error("Request failed: ${e.javaClass.simpleName}")
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Внутренняя ошибка сервера"),
            )
        }
    }
    install(Authentication) {
        jwt("jwt") {
            realm = "Nobel Prize API"
            verifier(
                JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("nobel-practice")
                    .withAudience("nobel-client")
                    .build()
            )
            validate { credentials ->
                if (credentials.payload.subject?.toLongOrNull() != null)
                    JWTPrincipal(credentials.payload)
                else null
            }
        }
    }
    routing(service, auth, dbMode)
    monitor.subscribe(ApplicationStopped) { repo.close() }
}
