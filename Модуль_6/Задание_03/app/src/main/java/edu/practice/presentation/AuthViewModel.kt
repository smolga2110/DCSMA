package edu.practice.presentation

import android.app.Application
import androidx.lifecycle.*
import edu.practice.data.*
import edu.practice.domain.*
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AuthState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val users: List<User> = emptyList(),
    val error: String = "",
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AuthRepository = KtorAuthRepository(app)
    private val loginUseCase = Login(repo)
    val state = MutableStateFlow(AuthState())

    init {
        viewModelScope.launch {
            if (repo.hasToken()) {
                state.value = AuthState(loggedIn = true)
                loadUsers()
            }
        }
    }

    private fun message(e: Exception) =
        if (e is ClientRequestException && e.response.status.value in listOf(400, 401))
            "Неверные данные или истёк срок входа"
        else e.message ?: "Нет соединения"

    fun login(user: String, password: String) {
        viewModelScope.launch {
            state.value = AuthState(loading = true)
            try {
                loginUseCase(user, password)
                state.value = AuthState(loggedIn = true, users = repo.users())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = AuthState(error = message(e))
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = "")
            try {
                state.value = AuthState(loggedIn = true, users = repo.users())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = message(e))
            }
        }
    }

    fun detail(id: Int, done: (User) -> Unit) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = "")
            try {
                done(repo.user(id))
            } catch (e: Exception) {
                state.value = state.value.copy(error = message(e))
            } finally {
                state.value = state.value.copy(loading = false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            state.value = AuthState()
        }
    }
}
