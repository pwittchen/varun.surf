Recommended Logo Specifications
===============================

## EN

Optimal Dimensions:

- Maximum Height: 60px (50px on mobile)
- Maximum Width: 200px (150px on mobile)
- Recommended Aspect Ratio: 16:9 or 3:1 (horizontal/wide logos work best)

File Format:

- PNG with transparent background (preferred)
- SVG (best for scalability and sharpness)
- WebP (modern format, good compression)
- JPG (acceptable, but avoid if logo has transparency)

Resolution:

- 2x resolution for retina displays (e.g., 400x120px at 2x scale)
- Minimum 72 DPI (96 DPI preferred)

File Size:

- Keep under 50KB for fast loading
- Optimize/compress before providing

Color Considerations:

Since your site has dark/light theme support, sponsors can provide:
- **Option 1 (Recommended)**: Theme-specific logos
  - Dark background version (logo with light/white elements) - for dark mode (`logoDark`)
  - Light background version (logo with dark elements) - for light mode (`logoLight`)
- **Option 2**: Single universal logo with good contrast that works on both backgrounds (`logo`)

If only one logo is provided, it will automatically be used for both themes.

Example Specifications Template:

Logo Requirements for varun.surf Sponsors:
- Dimensions: 200px × 60px (or proportional, max 200px wide)
- Format: PNG with transparent background (preferred) or SVG
- Resolution: 2x retina ready (400px × 120px actual size)
- File size: Under 50KB
- Color: Provide version that works on both light and dark backgrounds
- File name: company-name-logo.png

Design Tips for Sponsors:

1. Horizontal logos work best in the layout
2. Avoid tall/vertical logos (they won't fit well)
3. Ensure a good contrast with both light and dark backgrounds
4. Use solid/bold fonts for better readability at small sizes
5. Avoid very thin lines that might not render well at 60px height

The CSS uses object-fit: contain, so logos maintain their aspect ratio and won't be distorted, but they'll be constrained within the 200px × 60px box (150px × 50px on mobile).

Exemplary entries in the `sponsors.json` file:

**Option 1: Single universal logo (works for both themes)**
```json
  {
    "id": 0,
    "main": true,
    "name": "Sponsor1",
    "link": "https://sponsor1.pl",
    "logo": "sponsor1.png"
  }
```

**Option 2: Theme-specific logos (recommended for best appearance)**
```json
  {
    "id": 1,
    "main": true,
    "name": "Sponsor2",
    "link": "https://sponsor2.pl",
    "logo": "sponsor2.png",
    "logoDark": "sponsor2-dark.png",
    "logoLight": "sponsor2-light.png"
  }
```

**Note**: When using theme-specific logos:
- The `logo` field serves as a fallback
- `logoDark` is displayed when the site is in dark mode
- `logoLight` is displayed when the site is in light mode
- If `logoDark` or `logoLight` is not provided, the system will use the `logo` field for both themes

## PL

Zalecane Specyfikacje Logo

Optymalne Wymiary:

- Maksymalna Wysokość: 60px (50px na urządzeniach mobilnych)
- Maksymalna Szerokość: 200px (150px na urządzeniach mobilnych)
- Zalecane Proporcje: 16:9 lub 3:1 (poziome/szerokie logo działa najlepiej)

Format Pliku:

- PNG z przezroczystym tłem (preferowany)
- SVG (najlepszy dla skalowalności i ostrości)
- WebP (nowoczesny format, dobra kompresja)
- JPG (dopuszczalny, ale unikaj jeśli logo ma przezroczystość)

Rozdzielczość:

- Rozdzielczość 2x dla wyświetlaczy retina (np. 400x120px w skali 2x)
- Minimum 72 DPI (preferowane 96 DPI)

Rozmiar Pliku:

- Poniżej 50KB dla szybkiego ładowania
- Zoptymalizuj/skompresuj przed dostarczeniem

Kwestie Kolorystyczne:

Ponieważ strona obsługuje motyw jasny/ciemny, sponsorzy mogą dostarczyć:
- **Opcja 1 (Zalecana)**: Logo dedykowane dla każdego motywu
  - Wersja dla ciemnego tła (logo z jasnymi/białymi elementami) - dla trybu ciemnego (`logoDark`)
  - Wersja dla jasnego tła (logo z ciemnymi elementami) - dla trybu jasnego (`logoLight`)
- **Opcja 2**: Jedno uniwersalne logo z dobrym kontrastem, które działa na obu tłach (`logo`)

Jeśli dostarczono tylko jedno logo, zostanie ono automatycznie użyte dla obu motywów.

Przykładowy Szablon Specyfikacji:

Wymagania dotyczące logo dla sponsorów varun.surf:
- Wymiary: 200px × 60px (lub proporcjonalne, maks. 200px szerokości)
- Format: PNG z przezroczystym tłem (preferowane) lub SVG
- Rozdzielczość: 2x retina ready (400px × 120px rzeczywisty rozmiar)
- Rozmiar pliku: Poniżej 50KB
- Kolor: Dostarcz wersję, która działa na jasnym i ciemnym tle
- Nazwa pliku: nazwa-firmy-logo.png

Wskazówki Projektowe dla Sponsorów:

1. Poziome logo działa najlepiej w tym układzie
2. Unikaj wysokich/pionowych logo (nie będą dobrze pasować)
3. Zapewnij dobry kontrast z jasnym i ciemnym tłem
4. Używaj solidnych/pogrubionych czcionek dla lepszej czytelności w małych rozmiarach
5. Unikaj bardzo cienkich linii, które mogą nie renderować się dobrze przy wysokości 60px

CSS używa object-fit: contain, więc logo zachowują swoje proporcje i nie będą zniekształcone, ale będą ograniczone do ramki 200px × 60px (150px × 50px na urządzeniach mobilnych).

Przykładowe wpisy w pliku `sponsors.json`:

**Opcja 1: Jedno uniwersalne logo (działa dla obu motywów)**
```json
  {
    "id": 0,
    "main": true,
    "name": "Sponsor1",
    "link": "https://sponsor1.pl",
    "logo": "sponsor1.png"
  }
```

**Opcja 2: Logo dedykowane dla każdego motywu (zalecane dla najlepszego wyglądu)**
```json
  {
    "id": 1,
    "main": true,
    "name": "Sponsor2",
    "link": "https://sponsor2.pl",
    "logo": "sponsor2.png",
    "logoDark": "sponsor2-dark.png",
    "logoLight": "sponsor2-light.png"
  }
```

**Uwaga**: Przy użyciu logo dedykowanych dla motywów:
- Pole `logo` służy jako rezerwowe
- `logoDark` jest wyświetlane, gdy strona jest w trybie ciemnym
- `logoLight` jest wyświetlane, gdy strona jest w trybie jasnym
- Jeśli `logoDark` lub `logoLight` nie jest podane, system użyje pola `logo` dla obu motywów

