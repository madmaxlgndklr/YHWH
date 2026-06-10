package com.madmaxlgndklr.yhwh.data.remote

import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.SaveData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SyncRepositoryTypesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun minimalSaveData(tick: Long = 1L) = SaveData(
        lastTickTimestamp = 9999L,
        snapshot = GameSnapshot(
            tick = tick,
            epoch = EpochType.COSMOLOGY,
            resources = mapOf(ResourceType.ENERGY.name to BigDouble.of(1.0)),
            generators = emptyList(),
            upgrades = emptyList(),
            epochProgress = 0f,
            events = emptyList()
        )
    )

    @Test fun `ConflictState None is not Pending or Resolved`() {
        val state: ConflictState = ConflictState.None
        assertTrue(state is ConflictState.None)
        assertFalse(state is ConflictState.Resolved)
    }

    @Test fun `ConflictState Resolved is not None`() {
        val state: ConflictState = ConflictState.Resolved
        assertTrue(state is ConflictState.Resolved)
        assertFalse(state is ConflictState.None)
    }

    @Test fun `SyncResult types are distinct`() {
        val noAction: SyncResult = SyncResult.NoAction
        val pushed: SyncResult = SyncResult.PushedToCloud
        assertTrue(noAction is SyncResult.NoAction)
        assertTrue(pushed is SyncResult.PushedToCloud)
        assertFalse(noAction is SyncResult.PushedToCloud)
    }

    @Test fun `SyncResult CloudRestoreAvailable holds SaveData`() {
        val data = minimalSaveData(tick = 42L)
        val result: SyncResult = SyncResult.CloudRestoreAvailable(data)
        assertTrue(result is SyncResult.CloudRestoreAvailable)
        assertEquals(42L, (result as SyncResult.CloudRestoreAvailable).savedData.snapshot.tick)
    }

    @Test fun `RemoteSaveRow holds expected fields`() {
        val row = RemoteSaveRow(
            userId = "uid-123",
            saveJson = "{}",
            tick = 500L,
            epoch = "COSMOLOGY",
            lastSavedAt = 100000L
        )
        assertEquals("uid-123", row.userId)
        assertEquals(500L, row.tick)
        assertEquals("COSMOLOGY", row.epoch)
    }

    @Test fun `SaveData round-trips through JSON correctly`() {
        val original = minimalSaveData(tick = 77L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SaveData>(encoded)
        assertEquals(original.lastTickTimestamp, decoded.lastTickTimestamp)
        assertEquals(original.snapshot.tick, decoded.snapshot.tick)
        assertEquals(original.snapshot.epoch, decoded.snapshot.epoch)
    }
}
