
package com.dailyquest.app.data.model

data class Room(
    val roomId: Long,
    val name: String,
    val memberCount: Long,
    val isPublic: Boolean,
    val description: String? = null,
    val maxMembers: Int = 10,
    val thumbnailUrl: String? = null,
    val isHot: Boolean = false,
    val joined: Boolean = false,
    val todayMissionDone: Boolean? = null,
    val pendingProofCount: Long? = null
)
