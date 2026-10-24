package surf.varun.core.network.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Persists the cookie in its own Preferences DataStore. Reads are served from
 * memory after the first one (OkHttp asks for cookies synchronously on its own
 * threads); writes go through asynchronously.
 */
class DataStoreSessionStore(private val context: Context) : SessionStore {
    private val key = stringPreferencesKey("session_cookie")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    override fun load(): String? {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    cached = runCatching { runBlocking { context.sessionDataStore.data.first()[key] } }.getOrNull()
                    loaded = true
                }
            }
        }
        return cached
    }

    override fun save(value: String?) {
        cached = value
        loaded = true
        scope.launch {
            context.sessionDataStore.edit { prefs ->
                if (value == null) prefs.remove(key) else prefs[key] = value
            }
        }
    }
}
