package edu.practice

import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*

fun main(args: Array<String>) = runBlocking {
    val fail = args.contains("--fail")
    val random = args.contains("--random-failure")
    val elapsed = measureTimeMillis {
        supervisorScope {
            suspend fun <T> safe(block: suspend () -> T): Result<T> =
                try {
                    Result.success(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            val users = async {
                safe {
                    delay(1800)
                    if (fail || (random && Random.nextInt(5) == 0))
                        error("Источник пользователей недоступен")
                    listOf("Alice", "Bob", "Ivan", "Olga")
                }
            }
            val sales = async {
                safe {
                    delay(1200)
                    mapOf("Coffee" to 42, "Tea" to 19)
                }
            }
            val weather = async {
                safe {
                    delay(2500)
                    listOf("Москва: -3°C", "Лондон: +8°C", "Токио: +11°C")
                }
            }
            val results = listOf(users.await(), sales.await(), weather.await())
            results.forEachIndexed { i, r ->
                println(
                    "${listOf("Пользователи","Продажи","Погода")[i]}: ${r.fold({it.toString()},{"Ошибка: ${it.message}"})}"
                )
            }
        }
    }
    println("Общее время: ${elapsed/1000.0} с")
}
