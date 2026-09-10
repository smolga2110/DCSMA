package edu.practice.data

import edu.practice.domain.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

fun JsonObject.text(key: String): String {
    val e = get(key) ?: return ""
    return if (e is JsonObject) e["en"]?.jsonPrimitive?.contentOrNull ?: ""
    else e.jsonPrimitive.contentOrNull ?: ""
}

class PublicNobelRepository : NobelRepository {
    private val client =
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 20000 }
        }

    override suspend fun prizes(year: String, category: String): List<Prize> {
        val categories =
            mapOf(
                "physics" to "phy",
                "chemistry" to "che",
                "literature" to "lit",
                "peace" to "pea",
                "medicine" to "med",
                "economics" to "eco",
            )
        val response =
            client
                .get("https://api.nobelprize.org/2.1/nobelPrizes") {
                    header("User-Agent", "Mozilla/5.0")
                    parameter("limit", 100)
                    if (year.isNotBlank()) parameter("nobelPrizeYear", year)
                    categories[category]?.let { parameter("nobelPrizeCategory", it) }
                }
                .bodyAsText()
        val rows =
            Json.parseToJsonElement(response).jsonObject["nobelPrizes"]?.jsonArray ?: emptyList()
        return rows.map { e ->
            val p = e.jsonObject
            val y = p.text("awardYear").toInt()
            val c = p.text("category")
            Prize(
                "$y-$c",
                y,
                c,
                (p["laureates"] as? JsonArray)?.map { l ->
                    val o = l.jsonObject
                    Laureate(
                        o.text("id"),
                        o.text("fullName")
                            .ifBlank { o.text("orgName") }
                            .ifBlank { o.text("knownName") },
                        o.text("motivation"),
                        o.text("portion"),
                    )
                } ?: emptyList(),
            )
        }
    }

    override suspend fun detail(laureate: Laureate): Laureate {
        val raw =
            Json.parseToJsonElement(
                    client
                        .get("https://api.nobelprize.org/2.1/laureate/${laureate.id}") {
                            header("User-Agent", "Mozilla/5.0")
                        }
                        .bodyAsText()
                )
                .jsonArray
                .first()
                .jsonObject
        val birth = raw["birth"] as? JsonObject
        val place = birth?.get("place") as? JsonObject
        return laureate.copy(
            birthCountry =
                place?.text("country")
                    ?: ((raw["founded"] as? JsonObject)?.get("place") as? JsonObject)
                        ?.text("country")
                        .orEmpty()
        )
    }
}
