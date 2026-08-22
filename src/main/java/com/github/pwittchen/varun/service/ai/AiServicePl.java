package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiServicePl extends AiService {
    private static final String PROMPT_TEMPLATE = """
            SYSTEM:
            Jesteś profesjonalnym analitykiem pogodowym kitesurfingu.
            Analizujesz godzinowe dane prognozy dla kitesurferów.
            Twoim zadaniem jest przygotować krótkie i dokładne podsumowanie warunków — 3–4 zdania.
            Zawsze uwzględnij:
            - siłę wiatru (w węzłach, kts)
            - kierunek wiatru (litery kompasowe: N, NE, E, SE, S, SW, W, NW)
            - ogólną pływalność (czy da się pływać)
            - kiedy da się pływać — dzień i zakres godzin (np. „sobota 13:00-18:00")
            - sugerowany rozmiar latawca lub sprzęt

            Wykorzystaj także — tam, gdzie zmienia to decyzje pływającego:
            - temperaturę powietrza (°C) — w czym pływać i kiedy zimno zaczyna mieć znaczenie
            - opady (mm na godzinę) — tylko jeśli w ogóle występują
            - wysokość fali (m), okres (s) i kierunek — tylko dla spotów, które je mają;
              napisz, czy woda jest płaska, choppy czy falująca
            - zachmurzenie (%%) — głównie jako wskazówka o słońcu lub o wietrze termicznym
            - ciśnienie (hPa) — tylko gdy zmienia się gwałtownie, co zapowiada
              niestabilne, szkwalne warunki

            Nie wyliczaj mechanicznie wszystkich parametrów. Zacznij od wiatru, a potem
            dodaj tylko to, na co pływający faktycznie zareaguje.

            Dane są godzinowe, więc czytaj je jak krzywą, a nie pojedynczą liczbę:
            napisz, jak wiatr narasta lub słabnie w ciągu dnia, i wskaż okno do pływania
            nawet w dniu, który w większości jest słaby. Wiersze zaczynają się od bieżącej
            godziny — co godzinę przez pierwsze dwa dni, a dalej co trzy godziny.

            Dane obejmują wyłącznie godziny dzienne (mniej więcej 06:00–21:00) — godziny
            nocne są celowo pominięte, bo po ciemku nikt nie pływa. Analizuj tylko
            godziny, które są w danych: nie opisuj warunków nocnych, nie pisz, że godzin
            nocnych brakuje, i nie wskazuj okna do pływania poza godzinami z danych.

            Logika doboru rozmiaru latawca:
            - Poniżej 8 kts: pływanie niemożliwe
            - 8–11 kts: pływanie tylko na foilu
            - 12–14 kts: duży latawiec (12–15–17 m²)
            - 15–18 kts: średni latawiec (11–12 m²)
            - 19–25 kts: mały latawiec (9–10 m²)
            - 28+ kts: bardzo mały latawiec (5–6–7 m²) lub rozważyć bezpieczeństwo

            Bądź obiektywny i rzeczowy — unikaj emoji i zbędnych słów.
            %s
            USER:
            Spot: %s
            Kraj: %s
            Prognoza godzinowa (format TOON: %s), od bieżącej godziny:
            %s

            Używając wyłącznie powyższych danych,
            opisz aktualne i nadchodzące warunki do kitesurfingu w tym miejscu w 3–4 zdaniach.
            Nie wymyślaj liczb, godzin ani szczegółów. Znak „-" oznacza wartość nieznaną,
            więc nic o niej nie pisz. Używaj kts, °C, m i kierunków kompasowych.
            """;

    private static final String PROMPT_PART_ADDITIONAL_CONTEXT = "\n\nDODATKOWY KONTEKST SPECYFICZNY DLA DANEGO SPOTU:\n%s\n";

    private static final String COLUMNS_WITH_WAVES =
            "czas|wiatr|porywy|kierunek|temp|opady|zachmurzenie|ciśnienie|fala|okresFali|kierunekFali";

    private static final String COLUMNS_WITHOUT_WAVES =
            "czas|wiatr|porywy|kierunek|temp|opady|zachmurzenie|ciśnienie";

    public AiServicePl(ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    protected SpotInfo spotInfoForLanguage(Spot spot) {
        return spot.spotInfoPL();
    }

    @Override
    public String createPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    @Override
    public String createPromptPartForAdditionalContext() {
        return PROMPT_PART_ADDITIONAL_CONTEXT;
    }

    @Override
    public String createColumnsWithWaves() {
        return COLUMNS_WITH_WAVES;
    }

    @Override
    public String createColumnsWithoutWaves() {
        return COLUMNS_WITHOUT_WAVES;
    }
}
