package com.madmaxlgndklr.yhwh.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MetaSaveManager(private val metaFile: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): MetaSave {
        if (!metaFile.exists()) return MetaSave()
        return try {
            json.decodeFromString<MetaSave>(metaFile.readText())
        } catch (e: Exception) {
            MetaSave()
        }
    }

    fun save(meta: MetaSave) {
        metaFile.writeText(json.encodeToString(meta))
    }
}
