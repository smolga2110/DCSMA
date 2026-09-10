package edu.practice.data

import edu.practice.domain.*
import edu.practice.security.Passwords
import java.util.concurrent.ConcurrentHashMap

class MemoryPrizeRepository(private val rows: List<Prize>) : PrizeRepository {
    private val student = Credentials(User(1, "student"), Passwords.hash("Student123!"))
    private val favorites = ConcurrentHashMap<Long, MutableSet<String>>()

    override fun prizes() = rows

    override fun credentials(username: String) = student.takeIf { it.user.username == username }

    override fun user(id: Long) = student.user.takeIf { it.id == id }

    override fun favorites(userId: Long) =
        rows.filter { favorites[userId]?.contains(it.id) == true }

    override fun addFavorite(userId: Long, id: String): Boolean {
        if (rows.none { it.id == id }) return false
        favorites.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(id)
        return true
    }

    override fun removeFavorite(userId: Long, id: String) = favorites[userId]?.remove(id) ?: false
}
