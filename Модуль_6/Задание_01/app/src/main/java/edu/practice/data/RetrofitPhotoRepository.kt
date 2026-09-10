package edu.practice.data

import edu.practice.domain.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class PhotoDto(
    val id: String,
    val author: String,
    val width: Int,
    val height: Int,
    val url: String,
    val download_url: String,
)

interface PhotoApi {
    @GET("v2/list")
    suspend fun list(@Query("page") page: Int, @Query("limit") limit: Int = 30): List<PhotoDto>
}

class RetrofitPhotoRepository : PhotoRepository {
    private val api =
        Retrofit.Builder()
            .baseUrl("https://picsum.photos/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhotoApi::class.java)

    override suspend fun photos() =
        api.list(kotlin.random.Random.nextInt(1, 8)).map {
            Photo(it.id, it.author, it.width, it.height, it.url, it.download_url)
        }
}
