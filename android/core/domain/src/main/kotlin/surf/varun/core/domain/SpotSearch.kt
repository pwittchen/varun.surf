package surf.varun.core.domain

import surf.varun.core.model.Spot
import java.text.Normalizer

/**
 * Local, instant spot search: name and country (English and Polish names), with
 * diacritics folded so "Leba" finds "Łeba".
 */
object SpotSearch {
    private val MARKS = Regex("\\p{M}+")

    fun fold(text: String): String {
        val replaced = text.lowercase()
            .replace('ł', 'l').replace('đ', 'd').replace('ø', 'o').replace('æ', 'a')
            .replace('ß', 's').replace('ı', 'i')
        return MARKS.replace(Normalizer.normalize(replaced, Normalizer.Form.NFD), "")
    }

    data class Index(val entries: List<Pair<Spot, String>>)

    fun index(spots: List<Spot>): Index = Index(
        spots.map { spot ->
            val country = Countries.of(spot.country)
            spot to fold("${spot.name} ${country.name} ${country.namePl}")
        },
    )

    /**
     * Spots matching every word of [query]. Name matches rank before country-only
     * matches, prefix matches before infix ones, then alphabetically.
     */
    fun search(index: Index, query: String, limit: Int = Int.MAX_VALUE): List<Spot> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return index.entries
            .filter { (_, haystack) -> words.all { haystack.contains(it) } }
            .sortedWith(
                compareBy<Pair<Spot, String>> { (spot, _) ->
                    val name = fold(spot.name)
                    when {
                        name.startsWith(words.first()) -> 0
                        words.all { name.contains(it) } -> 1
                        else -> 2
                    }
                }.thenBy { it.first.name },
            )
            .take(limit)
            .map { it.first }
    }
}
