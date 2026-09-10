package edu.practice.domain
data class Photo(
    val id: String,
    val author: String,
    val width: Int,
    val height: Int,
    val url: String,
    val downloadUrl: String,
)

interface PhotoRepository {
    suspend fun photos(): List<Photo>
}

class GetPhotos(private val repo: PhotoRepository) {
    suspend operator fun invoke() = repo.photos()
}
