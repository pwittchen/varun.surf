Recommended Logo Specifications
===============================

## EN

Logo requirements for varun.surf Sponsors

## Dimensions
- Maximum width: **200px** (150px on mobile)  
- Maximum height: **60px** (50px on mobile)  
- Recommended aspect ratio: **3:1 or 16:9** (horizontal logos look best)

## File Format
- **PNG with transparent background** (preferred)  
- **SVG** (best for scalability and sharpness)  
- **JPG** (acceptable if transparency is not required)

## Quality
- **2× resolution** (e.g., provide a 400×120px file for a 200×60px display size)  
- **File size under 50KB** after optimization  

## Color Versions
The site supports both light and dark themes. You can provide:

- **Option 1 (recommended):** two theme-specific versions  
  - `logoLight` – for light background  
  - `logoDark` – for dark background  
- **Option 2:** a single universal logo (`logo`) that works on both backgrounds  

## Design Tips
- Use **bold, readable fonts**  
- Avoid **thin lines** and **vertical layouts**  
- Ensure **good contrast** on both light and dark backgrounds  

---

## PL

Zalecane Specyfikacje logo dla Sponsorów varun.surf

## Wymiary
- Maksymalna szerokość: **200px** (na mobile: 150px)  
- Maksymalna wysokość: **60px** (na mobile: 50px)  
- Zalecane proporcje: **3:1 lub 16:9** (poziome logo działa najlepiej)

## Format pliku
- **PNG z przezroczystym tłem** (zalecany)  
- **SVG** (najlepszy dla ostrości i skalowalności)  
- **JPG** (dopuszczalny, jeśli nie ma przezroczystości)

## Jakość
- **2× rozdzielczość** (np. 400×120px)  
- **Rozmiar pliku do 50KB** po kompresji  

## Kolory
Strona obsługuje jasny i ciemny motyw. Możliwe są dwie opcje:

- **Opcja 1 (zalecana):** dwa warianty logo  
  - `logoLight` – na jasne tło  
  - `logoDark` – na ciemne tło  
- **Opcja 2:** jedno uniwersalne logo (`logo`), które działa na obu tłach  

## Wskazówki projektowe
- Używaj **czytelnych i pogrubionych fontów**  
- Unikaj **cienkich linii** i **pionowych układów**  
- Zapewnij **dobry kontrast** z jasnym i ciemnym tłem  

---

### Technical details regarding adding sponsor's logo to the website (internal)

Put logos into the `src/resources/static/img/sponsors/` dir.
Put the id according to the spot id or 0 i it's main sponsor.
Set `main` to true, if the logo should be displayed on the main page.

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
