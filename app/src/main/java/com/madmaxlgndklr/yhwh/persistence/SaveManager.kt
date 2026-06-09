package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SaveManager(private val saveFile: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(snapshot: GameSnapshot, overrideTimestamp: Long? = null) {
        val data = SaveData(
            lastTickTimestamp = overrideTimestamp ?: System.currentTimeMillis(),
            snapshot = snapshot
        )
        saveFile.writeText(json.encodeToString(data))
    }

    fun load(): SaveData? {
        if (!saveFile.exists()) return null
        return try {
            json.decodeFromString<SaveData>(saveFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun computeMissedTicks(
        lastTickTimestamp: Long,
        tickIntervalMs: Long = 1000L,
        maxTicks: Long = 28_800L
    ): Long {
        val elapsed = System.currentTimeMillis() - lastTickTimestamp
        val missed = elapsed / tickIntervalMs
        return missed.coerceAtMost(maxTicks)
    }
}
