package com.caterktor.live.registry

import com.caterktor.live.domain.TopicInfo

object TopicRegistry {
    val all: List<TopicInfo> = listOf(
        TopicInfo("orders",     "o", supportsSnapshot = true, supportsReplay = true,  supportsStream = true, requiresAuth = true),
        TopicInfo("deliveries", "d", supportsSnapshot = true, supportsReplay = true,  supportsStream = true, requiresAuth = true),
        TopicInfo("prices",     "p", supportsSnapshot = true, supportsReplay = false, supportsStream = true, requiresAuth = false),
        TopicInfo("incidents",  "i", supportsSnapshot = true, supportsReplay = true,  supportsStream = true, requiresAuth = false),
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): TopicInfo? = byName[name]
    fun exists(name: String): Boolean = byName.containsKey(name)
}
