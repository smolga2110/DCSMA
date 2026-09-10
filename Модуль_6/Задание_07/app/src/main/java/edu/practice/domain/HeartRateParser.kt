package edu.practice.domain
object HeartRateParser {
    fun parse(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        val flags = bytes[0].toInt() and 255
        return if (flags and 1 == 0) bytes[1].toInt() and 255
        else if (bytes.size >= 3) (bytes[1].toInt() and 255) or ((bytes[2].toInt() and 255) shl 8)
        else null
    }
}
