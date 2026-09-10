package edu.practice.presentation

import android.app.Application
import androidx.lifecycle.*
import edu.practice.data.*
import edu.practice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NobelState(
    val loading: Boolean = false,
    val prizes: List<Prize> = emptyList(),
    val error: String = "",
)

class NobelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: NobelRepository = PublicNobelRepository()
    private val getPrizes = GetPrizes(repo)
    val state = MutableStateFlow(NobelState())
    private var job: Job? = null

    init {
        load("2023", "")
    }

    fun load(year: String, category: String) {
        job?.cancel()
        job =
            viewModelScope.launch {
                state.value = state.value.copy(loading = true, error = "")
                try {
                    state.value = NobelState(prizes = getPrizes(year, category))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.value = NobelState(error = e.message ?: "Ошибка загрузки")
                }
            }
    }

    suspend fun detail(l: Laureate): Laureate =
        try {
            repo.detail(l)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.value = state.value.copy(error = "Не удалось загрузить сведения о лауреате")
            l
        }
}
