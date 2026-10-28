# Android Releases

Every Google Play release of the app (`surf.varun.app`), newest first, with the "What's new"
text it shipped with (`distribution/whatsnew`). The version name comes from the
`android-vX.Y.Z` tag and the version code is the run number of the ANDROID workflow run that
built the tag (`.github/workflows/android.yml`). Dates are when the release was prepared
(the commit with its release notes), in local time.

Maintained by the `release-notes-android` skill.

## 1.1.0

- Version name: `1.1.0`
- Version code: `pending`
- Date: 2026-10-28 14:27 +01:00
- Tag: `android-v1.1.0`

**EN:** The spot forecast now suggests a kite size for every hour from the weight and skill saved in the calculator, and can fade hours blowing from outside the spot's best directions (see Settings). The table opens on the current hour and a bell on the spot page sets up a wind alert. Map clusters show their strongest wind, forecast days are named by weekday, and storms above 35 kts no longer top the list of spots firing now.

**PL:** Prognoza spotu podpowiada teraz rozmiar latawca na każdą godzinę - na podstawie wagi i poziomu zapisanych w kalkulatorze - i może przygaszać godziny z wiatrem spoza najlepszych kierunków spotu (zobacz Ustawienia). Tabela otwiera się na bieżącej godzinie, a dzwonek na stronie spotu ustawia alert wiatrowy. Klastry na mapie pokazują najsilniejszy wiatr, kolejne dni prognozy mają nazwy dni tygodnia, a sztorm powyżej 35 kn nie trafia już na szczyt listy spotów, gdzie teraz wieje.

## 1.0.2

- Version name: `1.0.2`
- Version code: `28`
- Date: 2026-10-28 08:38 +01:00
- Tag: `android-v1.0.2`

**EN:** The hourly forecast and live history tables on the spot page are now compact by default - shorter rows, narrower columns and wind and gust cells coloured edge to edge, so more hours fit on screen at a glance. Prefer the roomier layout? Switch it back under Settings - Table density.

**PL:** Tabele prognozy godzinowej i historii pomiarów na stronie spotu są teraz domyślnie kompaktowe - niższe wiersze, węższe kolumny i komórki wiatru oraz porywów pokolorowane na całej szerokości, dzięki czemu na ekranie mieści się więcej godzin. Wolisz luźniejszy układ? Przywrócisz go w Ustawieniach - Gęstość tabel.

## 1.0.1

- Version name: `1.0.1`
- Version code: `25`
- Date: 2026-10-27 20:11 +01:00
- Tag: `android-v1.0.1`

**EN:** Forecast models now show a loading indicator while they are being fetched, and the ICM forecast is loaded automatically with the other models - no separate button needed. Removed the outdated ICM meteogram link.

**PL:** Modele prognozy pokazują wskaźnik ładowania podczas pobierania, a prognoza ICM wczytuje się automatycznie razem z innymi modelami - bez osobnego przycisku. Usunięto nieaktualny link do meteogramu ICM.

## 1.0.0

- Version name: `1.0.0`
- Version code: `1`
- Date: 2026-10-25 22:53 +01:00
- Tag: none (the AAB was uploaded to Play Console by hand; live on Google Play since 2026-10-27)

**EN:** First release: live wind and forecasts for 800+ kitesurfing spots, wind map, favourites, home screen widgets, wind alerts and a kite size calculator.

**PL:** Pierwsze wydanie: wiatr na żywo i prognozy dla ponad 800 spotów kitesurfingowych, mapa wiatru, ulubione, widżety, alerty wiatrowe i kalkulator rozmiaru latawca.
