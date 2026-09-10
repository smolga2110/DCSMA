package edu.practice

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun sha256(file: File): String =
    withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        file.inputStream().use { stream ->
            while (true) {
                ensureActive()
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

fun main(args: Array<String>) = runBlocking {
    val dir = File(args.getOrNull(0) ?: "samples")
    val seconds = args.getOrNull(1)?.toDoubleOrNull() ?: 10.0
    if (!dir.isDirectory || seconds <= 0) {
        println("Укажите существующую директорию и положительный таймаут в секундах")
        return@runBlocking
    }
    val result =
        withTimeoutOrNull((seconds * 1000).toLong()) {
            val files =
                withContext(Dispatchers.IO) {
                    dir.walkTopDown()
                        .onEnter {
                            ensureActive()
                            true
                        }
                        .filter { it.isFile && it.extension.equals("json", true) }
                        .toList()
                }
            val semaphore = Semaphore(4)
            files
                .map { f ->
                    async {
                        semaphore.withPermit {
                            try {
                                sha256(f) to f.canonicalPath
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                println("Не удалось прочитать ${f.name}: ${e.message}")
                                null
                            }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
        }
    if (result == null) println("Поиск прерван по таймауту")
    else if (result.isEmpty()) println("Дубликаты не найдены")
    else
        result.forEach { (hash, files) -> println("SHA-256: $hash\n${files.joinToString("\n")}\n") }
}
