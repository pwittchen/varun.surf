package surf.varun.core.network.session

/**
 * Where the SESSION cookie value survives process death. The interface keeps the
 * session logic testable without Android.
 */
interface SessionStore {
    fun load(): String?

    fun save(value: String?)
}

class InMemorySessionStore(private var value: String? = null) : SessionStore {
    override fun load(): String? = value

    override fun save(value: String?) {
        this.value = value
    }
}
