package com.dailyquest.app.data.mission

/**
 * minSdk 24에서는 java.time을 쓸 수 없고 별도 라이브러리도 추가하지 않으므로, 서버의 ISO-8601 시각을 직접 다룬다.
 * 화면에 표시하는 날짜·시각은 서버 정책과 같은 KST(UTC+9) 기준이다.
 */
object MissionTime {
    private const val KST_OFFSET_MS = 9L * 3_600_000
    private const val DAY_MS = 86_400_000L
    private val ISO = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?""")
    private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 파싱할 수 없으면 null. 오프셋이 없으면 UTC로 본다(서버 Instant는 항상 Z). */
    fun parse(iso: String?): Long? {
        val m = ISO.matchEntire(iso?.trim().orEmpty()) ?: return null
        val g = m.groupValues
        val days = daysFromCivil(g[1].toInt(), g[2].toInt(), g[3].toInt())
        val millis = g[7].padEnd(3, '0').take(3).ifEmpty { "0" }.toLong()
        var t = days * DAY_MS + g[4].toLong() * 3_600_000 + g[5].toLong() * 60_000 + (g[6].toLongOrNull() ?: 0) * 1000 + millis
        val off = g[8]
        if (off.isNotEmpty() && off != "Z") {
            val sign = if (off[0] == '-') -1 else 1
            val d = off.drop(1).replace(":", "")
            t -= sign * (d.take(2).toLong() * 60 + d.drop(2).toLong()) * 60_000
        }
        return t
    }

    fun toIso(epochMs: Long): String {
        val days = Math.floorDiv(epochMs, DAY_MS)
        val rem = Math.floorMod(epochMs, DAY_MS)
        val (y, mo, d) = civilFromDays(days)
        val s = rem / 1000
        return "%04d-%02d-%02dT%02d:%02d:%02dZ".format(y, mo, d, s / 3600, s % 3600 / 60, s % 60)
    }

    /** "4월 17일 (목)" */
    fun kstDateLabel(epochMs: Long): String {
        val days = Math.floorDiv(epochMs + KST_OFFSET_MS, DAY_MS)
        val (_, mo, d) = civilFromDays(days)
        return "${mo}월 ${d}일 (${DAYS[Math.floorMod(days + 3, 7L).toInt()]})"
    }

    /** "4월 17일 23:59" */
    fun kstDateTimeLabel(epochMs: Long): String {
        val local = epochMs + KST_OFFSET_MS
        val (_, mo, d) = civilFromDays(Math.floorDiv(local, DAY_MS))
        val min = Math.floorMod(local, DAY_MS) / 60_000
        return "%d월 %d일 %02d:%02d".format(mo, d, min / 60, min % 60)
    }

    /** 마감까지 남은 시간. 이미 지났으면 "마감". */
    fun remainingLabel(dueMs: Long, nowMs: Long): String {
        val left = dueMs - nowMs
        if (left <= 0) return "마감"
        val min = left / 60_000
        return when {
            min < 1 -> "1분 이내 마감"
            min < 60 -> "${min}분 남음"
            min < 24 * 60 -> "${min / 60}시간 남음"
            else -> "${min / (24 * 60)}일 남음"
        }
    }

    // Howard Hinnant의 civil 날짜 알고리즘 (1970-01-01 = 0일)
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097 + doe - 719468
    }

    private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719468
        val era = Math.floorDiv(z, 146097L)
        val doe = (z - era * 146097).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = (yoe + era * 400).toInt() + if (m <= 2) 1 else 0
        return Triple(y, m, d)
    }
}
