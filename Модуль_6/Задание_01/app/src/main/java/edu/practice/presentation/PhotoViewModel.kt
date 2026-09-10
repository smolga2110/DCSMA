package edu.practice.presentation

import androidx.lifecycle.*
import edu.practice.data.*
import edu.practice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PhotoState(
    val loading: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val error: String = "",
)

class PhotoViewModel : ViewModel() {
    private val getPhotos = GetPhotos(RetrofitPhotoRepository())
    val state = MutableStateFlow(PhotoState())

    init {
        load()
    }

    fun load() {
        if (state.value.loading) return
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = "")
            try {
                state.value = PhotoState(photos = getPhotos())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value =
                    PhotoState(error = "Не удалось загрузить фотографии. Проверьте подключение")
            }
        }
    }
}
