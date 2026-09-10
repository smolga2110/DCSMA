package edu.practice.data

import edu.practice.domain.*
import edu.practice.security.Passwords
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

class PostgresPrizeRepository(url: String, seeds: List<Prize>) : PrizeRepository {
    private val jdbc: String
    private val properties: Properties

    init {
        val uri = URI(url)
        require(uri.scheme in listOf("postgres", "postgresql")) { "Ожидается строка PostgreSQL" }
        val userInfo =
            uri.rawUserInfo?.split(':', limit = 2) ?: error("В строке подключения нет пользователя")
        properties =
            Properties().apply {
                setProperty("user", URLDecoder.decode(userInfo[0], "UTF-8"))
                setProperty("password", URLDecoder.decode(userInfo.getOrElse(1) { "" }, "UTF-8"))
                setProperty("sslmode", "verify-full")
                setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory")
                setProperty("connectTimeout", "15")
                setProperty("socketTimeout", "30")
            }
        jdbc = "jdbc:postgresql://${uri.host}:${if(uri.port==-1)5432 else uri.port}${uri.path}"
        connection().use { c ->
            c.autoCommit = false
            try {
                val schema =
                    Thread.currentThread().contextClassLoader.getResource("schema.sql")!!.readText()
                c.createStatement().use { s ->
                    schema.split(';').filter { it.isNotBlank() }.forEach { s.execute(it) }
                }
                c.prepareStatement(
                        "INSERT INTO users(username,password_hash,role) VALUES(?,?,?) ON CONFLICT(username) DO NOTHING"
                    )
                    .use { s ->
                        s.setString(1, "student")
                        s.setString(2, Passwords.hash("Student123!"))
                        s.setString(3, "student")
                        s.executeUpdate()
                    }
                seeds.forEach { p ->
                    c.prepareStatement(
                            "INSERT INTO prizes(id,award_year,category) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING"
                        )
                        .use { s ->
                            s.setString(1, p.id)
                            s.setInt(2, p.year)
                            s.setString(3, p.category)
                            s.executeUpdate()
                        }
                    p.laureates.forEach { l ->
                        c.prepareStatement(
                                "INSERT INTO laureates(prize_id,id,full_name,motivation,portion,birth_country,portrait_url) VALUES(?,?,?,?,?,?,?) ON CONFLICT(prize_id,id) DO NOTHING"
                            )
                            .use { s ->
                                s.setString(1, p.id)
                                s.setString(2, l.id)
                                s.setString(3, l.fullName)
                                s.setString(4, l.motivation)
                                s.setString(5, l.portion)
                                s.setString(6, l.birthCountry)
                                s.setString(7, l.portraitUrl)
                                s.executeUpdate()
                            }
                    }
                }
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(jdbc, properties)

    override fun prizes(): List<Prize> =
        connection().use { c ->
            c.createStatement().use { s ->
                s.executeQuery(
                        "SELECT id,award_year,category FROM prizes ORDER BY award_year DESC,category"
                    )
                    .use { rs ->
                        buildList {
                            while (rs.next()) {
                                val id = rs.getString(1)
                                add(Prize(id, rs.getInt(2), rs.getString(3), laureates(c, id)))
                            }
                        }
                    }
            }
        }

    private fun laureates(c: Connection, id: String): List<Laureate> =
        c.prepareStatement(
                "SELECT id,full_name,motivation,portion,birth_country,portrait_url FROM laureates WHERE prize_id=? ORDER BY id"
            )
            .use { s ->
                s.setString(1, id)
                s.executeQuery().use { r ->
                    buildList {
                        while (r.next()) add(
                            Laureate(
                                r.getString(1),
                                r.getString(2),
                                r.getString(3),
                                r.getString(4),
                                r.getString(5),
                                r.getString(6),
                            )
                        )
                    }
                }
            }

    override fun credentials(username: String): Credentials? =
        connection().use { c ->
            c.prepareStatement("SELECT id,username,role,password_hash FROM users WHERE username=?")
                .use { s ->
                    s.setString(1, username)
                    s.executeQuery().use { r ->
                        if (r.next())
                            Credentials(
                                User(r.getLong(1), r.getString(2), r.getString(3)),
                                r.getString(4),
                            )
                        else null
                    }
                }
        }

    override fun user(id: Long): User? =
        connection().use { c ->
            c.prepareStatement("SELECT id,username,role FROM users WHERE id=?").use { s ->
                s.setLong(1, id)
                s.executeQuery().use { r ->
                    if (r.next()) User(r.getLong(1), r.getString(2), r.getString(3)) else null
                }
            }
        }

    override fun favorites(userId: Long): List<Prize> {
        val ids =
            connection().use { c ->
                c.prepareStatement("SELECT prize_id FROM user_prizes WHERE user_id=?").use { s ->
                    s.setLong(1, userId)
                    s.executeQuery().use { r -> buildSet { while (r.next()) add(r.getString(1)) } }
                }
            }
        return prizes().filter { it.id in ids }
    }

    override fun addFavorite(userId: Long, id: String): Boolean {
        if (prizes().none { it.id == id }) return false
        connection().use { c ->
            c.prepareStatement(
                    "INSERT INTO user_prizes(user_id,prize_id) VALUES(?,?) ON CONFLICT DO NOTHING"
                )
                .use { s ->
                    s.setLong(1, userId)
                    s.setString(2, id)
                    s.executeUpdate()
                }
        }
        return true
    }

    override fun removeFavorite(userId: Long, id: String): Boolean =
        connection().use { c ->
            c.prepareStatement("DELETE FROM user_prizes WHERE user_id=? AND prize_id=?").use { s ->
                s.setLong(1, userId)
                s.setString(2, id)
                s.executeUpdate() > 0
            }
        }
}
