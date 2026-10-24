package surf.varun.core.domain

/**
 * Countries as the server names them (English), with the Polish name and ISO code
 * the web carries in `translations.js` / `flags.js`.
 */
object Countries {
    data class Country(val name: String, val namePl: String, val iso: String) {
        /** Regional-indicator flag emoji built from the ISO code. */
        val flag: String
            get() = iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

        fun localizedName(polish: Boolean): String = if (polish) namePl else name
    }

    private val ALL = listOf(
        Country("Poland", "Polska", "PL"), Country("Czech Republic", "Czechy", "CZ"),
        Country("Austria", "Austria", "AT"), Country("Belgium", "Belgia", "BE"),
        Country("Switzerland", "Szwajcaria", "CH"), Country("Latvia", "Łotwa", "LV"),
        Country("Lithuania", "Litwa", "LT"), Country("Estonia", "Estonia", "EE"),
        Country("Denmark", "Dania", "DK"), Country("Sweden", "Szwecja", "SE"),
        Country("Norway", "Norwegia", "NO"), Country("Iceland", "Islandia", "IS"),
        Country("Spain", "Hiszpania", "ES"), Country("Portugal", "Portugalia", "PT"),
        Country("Italy", "Włochy", "IT"), Country("Greece", "Grecja", "GR"),
        Country("France", "Francja", "FR"), Country("Germany", "Niemcy", "DE"),
        Country("Netherlands", "Holandia", "NL"), Country("Croatia", "Chorwacja", "HR"),
        Country("Slovenia", "Słowenia", "SI"), Country("Serbia", "Serbia", "RS"),
        Country("Montenegro", "Czarnogóra", "ME"), Country("Albania", "Albania", "AL"),
        Country("Macedonia", "Macedonia", "MK"), Country("Bulgaria", "Bułgaria", "BG"),
        Country("Romania", "Rumunia", "RO"), Country("Malta", "Malta", "MT"),
        Country("Ireland", "Irlandia", "IE"), Country("United Kingdom", "Wielka Brytania", "GB"),
        Country("UK", "Anglia", "GB"), Country("Turkey", "Turcja", "TR"),
        Country("Morocco", "Maroko", "MA"), Country("Egypt", "Egipt", "EG"),
        Country("Cape Verde", "Cabo Verde", "CV"), Country("Mauritius", "Mauritius", "MU"),
        Country("Brazil", "Brazylia", "BR"), Country("Peru", "Peru", "PE"),
        Country("Chile", "Chile", "CL"), Country("USA", "USA", "US"),
        Country("Namibia", "Namibia", "NA"), Country("Mexico", "Meksyk", "MX"),
        Country("Costa Rica", "Kostaryka", "CR"), Country("South Africa", "RPA", "ZA"),
        Country("Tanzania", "Tanzania", "TZ"), Country("Sri Lanka", "Sri Lanka", "LK"),
        Country("Vietnam", "Wietnam", "VN"), Country("Turks and Caicos", "Turks and Caicos", "TC"),
        Country("Philippines", "Filipiny", "PH"), Country("Thailand", "Tajlandia", "TH"),
        Country("Indonesia", "Indonezja", "ID"), Country("Taiwan", "Tajwan", "TW"),
        Country("India", "Indie", "IN"), Country("Oman", "Oman", "OM"),
        Country("United Arab Emirates", "Zjednoczone Emiraty Arabskie", "AE"),
        Country("Canada", "Kanada", "CA"), Country("Dominican Republic", "Dominikana", "DO"),
        Country("Aruba", "Aruba", "AW"), Country("Colombia", "Kolumbia", "CO"),
        Country("Venezuela", "Wenezuela", "VE"), Country("Ecuador", "Ekwador", "EC"),
        Country("Argentina", "Argentyna", "AR"), Country("Australia", "Australia", "AU"),
    ).associateBy { it.name }

    /** Known country, or one built from the server's name when the app predates it. */
    fun of(name: String): Country = ALL[name] ?: Country(name, name, "")

    /**
     * Country from the slug the web uses in `/country/{name}` URLs: lowercased with
     * spaces as hyphens ("czech-republic"), matched leniently.
     */
    fun fromSlug(slug: String, known: Collection<String>): String? {
        val normalized = slug.trim().lowercase().replace('_', '-').replace(' ', '-')
        return known.firstOrNull { slugOf(it) == normalized }
    }

    fun slugOf(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), "-")
}
