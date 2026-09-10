package edu.practice.routing

import edu.practice.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class TokenResponse(val token: String, val expiresIn: Int = 1800)

fun Application.routing(service: PrizeService, auth: AuthService, dbMode: Boolean) {
    routing {
        get("/") { call.respondText("Nobel Prize API. Documentation: /docs") }
        get("/health") {
            call.respond(
                mapOf("status" to "ok", "storage" to if (dbMode) "postgresql" else "memory")
            )
        }
        get("/openapi.json") {
            call.respondText(
                Thread.currentThread().contextClassLoader.getResource("openapi.json")!!.readText(),
                ContentType.Application.Json,
            )
        }
        get("/docs") {
            call.respondText(
                """<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Nobel Prize API</title><link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.20.0/swagger-ui.css"></head><body><div id="swagger-ui"></div><script src="https://unpkg.com/swagger-ui-dist@5.20.0/swagger-ui-bundle.js"></script><script>SwaggerUIBundle({url:'/openapi.json',dom_id:'#swagger-ui'});</script></body></html>""",
                ContentType.Text.Html,
            )
        }
        suspend fun login(call: ApplicationCall) {
            val request = call.receive<LoginRequest>()
            val token =
                withContext(Dispatchers.IO) { auth.login(request.username, request.password) }
            if (token == null)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Неверный логин или пароль"),
                )
            else call.respond(TokenResponse(token))
        }
        post("/auth/login") { login(call) }
        post("/login") { login(call) }
        suspend fun list(call: ApplicationCall) {
            call.respond(
                withContext(Dispatchers.IO) {
                    service.list(
                        call.request.queryParameters["year"],
                        call.request.queryParameters["category"],
                    )
                }
            )
        }
        if (dbMode) get("/prizes") { list(call) }
        authenticate("jwt") {
            if (!dbMode) get("/prizes") { list(call) }
            get("/prizes/{year}/{category}") {
                val year =
                    call.parameters["year"]?.toIntOrNull()
                        ?: throw IllegalArgumentException("Некорректный год")
                val p =
                    withContext(Dispatchers.IO) {
                        service.detail(year, call.parameters["category"]!!)
                    }
                if (p == null)
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Премия не найдена"))
                else call.respond(p)
            }
            get("/prizes/{year}/{category}/laureates") {
                val year =
                    call.parameters["year"]?.toIntOrNull()
                        ?: throw IllegalArgumentException("Некорректный год")
                val p =
                    withContext(Dispatchers.IO) {
                        service.detail(year, call.parameters["category"]!!)
                    }
                if (p == null)
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Премия не найдена"))
                else call.respond(p.laureates)
            }
            fun ApplicationCall.userId() = principal<JWTPrincipal>()!!.payload.subject.toLong()
            get("/users/me") {
                val u = withContext(Dispatchers.IO) { service.profile(call.userId()) }
                if (u == null) call.respond(HttpStatusCode.NotFound) else call.respond(u)
            }
            get("/users/me/prizes") {
                call.respond(withContext(Dispatchers.IO) { service.favorites(call.userId()) })
            }
            post("/users/me/prizes/{prizeId}") {
                val ok =
                    withContext(Dispatchers.IO) {
                        service.add(call.userId(), call.parameters["prizeId"]!!)
                    }
                call.respond(
                    if (ok) HttpStatusCode.Created else HttpStatusCode.NotFound,
                    mapOf("success" to ok),
                )
            }
            delete("/users/me/prizes/{prizeId}") {
                val ok =
                    withContext(Dispatchers.IO) {
                        service.remove(call.userId(), call.parameters["prizeId"]!!)
                    }
                call.respond(if (ok) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
        }
    }
}
