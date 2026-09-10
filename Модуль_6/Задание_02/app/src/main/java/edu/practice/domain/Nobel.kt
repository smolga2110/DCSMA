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

interface NobelRepository {
    suspend fun prizes(year: String, category: String): List<Prize>

    suspend fun detail(laureate: Laureate): Laureate
}

class GetPrizes(private val repository: NobelRepository) {
    suspend operator fun invoke(year: String, category: String): List<Prize> {
        require(year.isBlank() || (year.toIntOrNull() ?: 0) in 1901..java.time.Year.now().value) {
            "Укажите год от 1901 до текущего"
        }
        return repository.prizes(year, category)
    }
}
