package wtf.buddyworks.iperfclient.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wtf.buddyworks.iperfclient.iperf.Json
import wtf.buddyworks.iperfclient.iperf.asList
import wtf.buddyworks.iperfclient.iperf.asObj
import java.io.File
import java.util.UUID

/** Servers, test options and history, kept in SharedPreferences and a JSON file. */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("iperf", Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, "history.json")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null) ?: _servers.value.firstOrNull()?.id)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _options = MutableStateFlow(
        prefs.getString(KEY_OPTIONS, null)?.let { runCatching { TestOptions.fromJson(Json.parse(it).asObj()) }.getOrNull() } ?: TestOptions(),
    )
    val options: StateFlow<TestOptions> = _options.asStateFlow()

    private val _history = MutableStateFlow<List<TestRecord>>(emptyList())
    val history: StateFlow<List<TestRecord>> = _history.asStateFlow()

    init {
        io.launch {
            val loaded = if (historyFile.exists()) TestRecord.listFromJson(historyFile.readText()) else emptyList()
            _history.update { current -> (current + loaded).distinctBy { it.id }.sortedByDescending { it.id } }
        }
    }

    val selectedServer: Server?
        get() = _servers.value.firstOrNull { it.id == _selectedId.value } ?: _servers.value.firstOrNull()

    private fun loadServers(): List<Server> {
        val saved = prefs.getString(KEY_SERVERS, null) ?: return Server.Public
        return runCatching { Json.parse(saved).asList().mapNotNull { Server.fromJson(it.asObj()) } }.getOrDefault(Server.Public)
    }

    private fun saveServers(list: List<Server>) {
        _servers.value = list
        prefs.edit { putString(KEY_SERVERS, Json.write(list.map { it.toJson() })) }
    }

    fun select(id: String) {
        _selectedId.value = id
        prefs.edit { putString(KEY_SELECTED, id) }
    }

    /** Adds or replaces [server] and selects it. */
    fun upsert(server: Server): Server {
        val s = if (server.id.isBlank()) server.copy(id = UUID.randomUUID().toString()) else server
        val list = _servers.value
        saveServers(if (list.any { it.id == s.id }) list.map { if (it.id == s.id) s else it } else listOf(s) + list)
        select(s.id)
        return s
    }

    fun delete(id: String) {
        saveServers(_servers.value.filterNot { it.id == id })
        if (_selectedId.value == id) _servers.value.firstOrNull()?.let { select(it.id) }
    }

    fun restorePublic() {
        val list = _servers.value
        saveServers(list + Server.Public.filter { p -> list.none { it.id == p.id } })
    }

    fun setOptions(options: TestOptions) {
        _options.value = options
        prefs.edit { putString(KEY_OPTIONS, Json.write(options.toJson())) }
    }

    fun addRecord(record: TestRecord) {
        _history.update { (listOf(record) + it).take(MAX_HISTORY) }
        persistHistory()
    }

    fun deleteRecord(id: Long) {
        _history.update { list -> list.filterNot { it.id == id } }
        persistHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persistHistory()
    }

    private fun persistHistory() {
        val snapshot = _history.value
        io.launch {
            synchronized(historyFile) {
                val tmp = File(historyFile.parentFile, "history.json.tmp")
                tmp.writeText(Json.write(snapshot.map { it.toJson() }))
                tmp.renameTo(historyFile)
            }
        }
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_SELECTED = "selected"
        const val KEY_OPTIONS = "options"
        const val MAX_HISTORY = 300
    }
}
