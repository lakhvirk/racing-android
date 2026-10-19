package com.lakh.racing.data

import android.content.Context
import android.net.Uri
import com.lakh.racing.data.db.CheckInEntity
import com.lakh.racing.data.db.OfflineState
import com.lakh.racing.data.db.RaceDao
import com.lakh.racing.data.db.RaceEntity
import com.lakh.racing.data.importer.RaceFormatException
import com.lakh.racing.data.importer.RaceParser
import com.lakh.racing.data.model.Race
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class RaceSummary(
    val id: String,
    val name: String,
    val description: String,
    val offlineState: OfflineState,
    val offlineBytes: Long,
)

class RaceRepository(private val context: Context, private val dao: RaceDao) {

    val races: Flow<List<RaceSummary>> = dao.observeAll().map { list -> list.map { it.summary() } }

    fun observeSummary(id: String): Flow<RaceSummary?> = dao.observe(id).map { it?.summary() }

    fun observeCheckIns(raceId: String): Flow<List<CheckInEntity>> = dao.observeCheckIns(raceId)

    suspend fun race(id: String): Race? = dao.get(id)?.let { RaceParser.json.decodeFromString<Race>(it.json) }

    suspend fun checkedIn(raceId: String): Set<String> = dao.checkIns(raceId).map { it.checkpointId }.toSet()

    suspend fun checkIn(raceId: String, checkpointId: String) =
        dao.addCheckIn(CheckInEntity(raceId, checkpointId, System.currentTimeMillis()))

    suspend fun resetProgress(raceId: String) = dao.clearCheckIns(raceId)

    suspend fun setOffline(raceId: String, state: OfflineState, bytes: Long = 0) = dao.setOffline(raceId, state, bytes)

    suspend fun delete(raceId: String) {
        dao.clearCheckIns(raceId)
        dao.delete(raceId)
    }

    suspend fun importSample(): Race = save(
        withContext(Dispatchers.IO) {
            context.assets.open("sample_race.json").bufferedReader().use { it.readText() }
        },
        "Sample race",
    )

    suspend fun importFromUri(uri: Uri): Race {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } ?: throw RaceFormatException("Couldn't read the selected file")
        return save(text, uri.lastPathSegment ?: "Imported race")
    }

    suspend fun importFromUrl(url: String): Race {
        val text = withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) {
                    throw RaceFormatException("Download failed: HTTP ${conn.responseCode}")
                }
                val bytes = conn.inputStream.use { it.readNBytesCompat(MAX_FILE_BYTES + 1) }
                if (bytes.size > MAX_FILE_BYTES) throw RaceFormatException("Race file is too large")
                bytes.decodeToString()
            } finally {
                conn.disconnect()
            }
        }
        return save(text, url.substringAfterLast('/').ifEmpty { "Imported race" })
    }

    private suspend fun save(text: String, fallbackName: String): Race {
        val race = withContext(Dispatchers.Default) { RaceParser.parse(text, fallbackName) }
        val existing = dao.get(race.id)
        dao.upsert(
            RaceEntity(
                id = race.id,
                name = race.name,
                description = race.description,
                json = RaceParser.json.encodeToString(Race.serializer(), race),
                importedAt = System.currentTimeMillis(),
                // Re-importing the same race keeps its downloaded map state.
                offlineState = existing?.offlineState ?: OfflineState.NONE,
                offlineBytes = existing?.offlineBytes ?: 0,
            ),
        )
        return race
    }

    private fun RaceEntity.summary() = RaceSummary(id, name, description, offlineState, offlineBytes)

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
