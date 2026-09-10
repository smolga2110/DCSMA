package edu.practice

import edu.practice.data.MemoryPrizeRepository
import edu.practice.domain.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlinx.serialization.json.*

class ServerTest {
    @Test
    fun authorizationAndFavorites() = testApplication {
        application {
            module(
                mapOf("JWT_SECRET" to "a-test-signing-key-with-more-than-32-characters"),
                MemoryPrizeRepository(
                    listOf(
                        Prize(
                            "2023-physics",
                            2023,
                            "physics",
                            listOf(Laureate("1", "Test Laureate", "Research")),
                        )
                    )
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me").status)
        val denied =
            client.post("/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"student","password":"wrong"}""")
            }
        assertEquals(HttpStatusCode.Unauthorized, denied.status)
        val response =
            client.post("/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"student","password":"Student123!"}""")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        val token =
            Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["token"]!!
                .jsonPrimitive
                .content
        assertEquals(
            HttpStatusCode.OK,
            client.get("/prizes/2023/physics") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/prizes/invalid/physics") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/prizes/1900/physics") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.Created,
            client.post("/users/me/prizes/2023-physics") { bearerAuth(token) }.status,
        )
        assertTrue(
            client
                .get("/users/me/prizes") { bearerAuth(token) }
                .bodyAsText()
                .contains("Test Laureate")
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/users/me/prizes/2023-physics") { bearerAuth(token) }.status,
        )
        assertEquals("[]", client.get("/users/me/prizes") { bearerAuth(token) }.bodyAsText())
    }

    @Test
    fun passwords() {
        val hash = edu.practice.security.Passwords.hash("correct")
        assertTrue(edu.practice.security.Passwords.verify("correct", hash))
        assertFalse(edu.practice.security.Passwords.verify("wrong", hash))
    }
}
