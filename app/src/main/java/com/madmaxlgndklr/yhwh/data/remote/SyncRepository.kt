package com.madmaxlgndklr.yhwh.data.remote

import android.util.Log
import com.madmaxlgndklr.yhwh.persistence.SaveData
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── Remote DTO ────────────────────────────────────────────────────────────────

@Serializable
data class RemoteSaveRow(
    @SerialName("user_id")       val userId: String,
    @SerialName("save_json")     val saveJson: String,
    val tick: Long,
    val epoch: String,
    @SerialName("last_saved_at") val lastSavedAt: Long
)

// ── State types ───────────────────────────────────────────────────────────────

sealed class ConflictState {
    object None : ConflictState()
    data class Pending(val local: SaveData, val cloud: RemoteSaveRow) : ConflictState()
    object Resolved : ConflictState()
}

sealed class SyncResult {
    object NoAction : SyncResult()
    data class CloudRestoreAvailable(val savedData: SaveData) : SyncResult()
    object PushedToCloud : SyncResult()
    data class ConflictDetected(val local: SaveData, val cloud: RemoteSaveRow) : SyncResult()
}

// ── Repository ────────────────────────────────────────────────────────────────

class SyncRepository(
    private val authRepository: AuthRepository,
    private val saveManager: SaveManager
) {
    private val pg = SupabaseModule.client.postgrest
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Pull cloud save, compare with local, and return what the caller should do.
     * Never throws — returns [SyncResult.NoAction] on network failure.
     */
    suspend fun syncOnOpen(): SyncResult {
        val uid = authRepository.currentUserId() ?: return SyncResult.NoAction
        return try {
            val cloudRow = pg.from("game_saves")
                .select { filter { eq("user_id", uid) } }
                .decodeSingleOrNull<RemoteSaveRow>()

            val localSave = saveManager.load()

            when {
                cloudRow == null && localSave == null -> SyncResult.NoAction
                cloudRow == null && localSave != null -> {
                    pushSave(localSave)
                    SyncResult.PushedToCloud
                }
                cloudRow != null && localSave == null ->
                    SyncResult.CloudRestoreAvailable(decodeSaveData(cloudRow.saveJson))
                else ->
                    SyncResult.ConflictDetected(localSave!!, cloudRow!!)
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "syncOnOpen failed", e)
            SyncResult.NoAction
        }
    }

    /**
     * Upsert [data] to the cloud. Swallows network errors — the local save is
     * always the source of truth; the cloud copy is best-effort.
     */
    suspend fun pushSave(data: SaveData) {
        val uid = authRepository.currentUserId() ?: return
        try {
            val encoded = json.encodeToString(data)
            pg.from("game_saves").upsert(buildJsonObject {
                put("user_id", uid)
                put("save_json", encoded)
                put("tick", data.snapshot.tick)
                put("epoch", data.snapshot.epoch.name)
                put("last_saved_at", data.lastTickTimestamp)
            })
        } catch (e: Exception) {
            Log.e("SyncRepository", "pushSave failed", e)
        }
    }

    /** Deserialize a raw JSON string from the cloud into a [SaveData]. */
    fun decodeSaveData(rawJson: String): SaveData =
        json.decodeFromString<SaveData>(rawJson)
}
