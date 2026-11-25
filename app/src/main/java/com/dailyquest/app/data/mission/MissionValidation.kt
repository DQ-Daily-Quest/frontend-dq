package com.dailyquest.app.data.mission

/** 서버 CreateMemberMissionRequest 기준: content 2~100(trim), rewardCoins 10~500 · 10 단위, dueAt 미래, 본인 지정 불가. */
object MissionValidation {
    const val CONTENT_MIN = 2
    const val CONTENT_MAX = 100
    const val REWARD_MIN = 10
    const val REWARD_MAX = 500
    const val REWARD_STEP = 10
    const val DEFAULT_REWARD = 50

    fun contentError(raw: String): String? {
        val n = raw.trim()
        return when {
            n.isEmpty() -> "미션 내용을 입력해 주세요."
            n.length !in CONTENT_MIN..CONTENT_MAX -> "미션 내용은 ${CONTENT_MIN}~${CONTENT_MAX}자로 입력해 주세요."
            else -> null
        }
    }

    fun rewardError(coins: Int): String? =
        if (coins !in REWARD_MIN..REWARD_MAX || coins % REWARD_STEP != 0) {
            "보상 코인은 ${REWARD_MIN}~${REWARD_MAX}코인, ${REWARD_STEP}코인 단위로 정해 주세요."
        } else null

    /** 서버(v0.13): 멤버 미션 마감은 미래이면서 30일 이내. */
    const val DUE_MAX_DAYS = 30L

    fun dueError(dueAtMs: Long, nowMs: Long): String? = when {
        dueAtMs <= nowMs -> "마감 시간은 현재보다 이후여야 해요."
        dueAtMs > nowMs + DUE_MAX_DAYS * 86_400_000L -> "마감 시간은 ${DUE_MAX_DAYS}일 이내로 정해 주세요."
        else -> null
    }

    fun assigneeError(assigneeUserId: Long?, myUserId: Long?): String? =
        if (assigneeUserId != null && assigneeUserId == myUserId) "본인을 대상으로 미션을 보낼 수 없어요." else null

    fun stepReward(current: Int, delta: Int): Int =
        (current + delta).coerceIn(REWARD_MIN, REWARD_MAX)
}
