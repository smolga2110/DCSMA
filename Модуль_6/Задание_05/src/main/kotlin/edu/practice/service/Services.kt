package edu.practice.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import edu.practice.domain.*
import edu.practice.security.Passwords
import java.util.Date

class AuthService(private val repo: PrizeRepository, private val secret: String) {
    fun login(username: String, password: String): String? {
        val c = repo.credentials(username) ?: return null
        if (!Passwords.verify(password, c.hash)) return null
        return JWT.create()
            .withIssuer("nobel-practice")
            .withAudience("nobel-client")
            .withSubject(c.user.id.toString())
            .withClaim("username", c.user.username)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 30 * 60 * 1000))
            .sign(Algorithm.HMAC256(secret))
    }
}

class PrizeService(private val repo: PrizeRepository) {
    fun list(year: String?, category: String?): List<Prize> {
        require(year.isNullOrBlank() || year.toIntOrNull() != null) { "Некорректный год" }
        return repo.prizes().filter {
            (year.isNullOrBlank() || it.year == year.toInt()) &&
                (category.isNullOrBlank() || it.category.equals(category, true))
        }
    }

    fun detail(year: Int, category: String) =
        repo.prizes().find { it.year == year && it.category.equals(category, true) }

    fun profile(id: Long) = repo.user(id)

    fun favorites(id: Long) = repo.favorites(id)

    fun add(id: Long, prizeId: String) = repo.addFavorite(id, prizeId)

    fun remove(id: Long, prizeId: String) = repo.removeFavorite(id, prizeId)
}
