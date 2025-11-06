# Kitesurfing Spot Generator - LLM Prompt Template

## CLI Usage with Claude Code

**Use this prompt directly from CLI without copying:**

```bash
# Generate spots for a specific region
claude "Generate 10 kitesurfing spots for Greek Islands following the schema in SPOT_GENERATOR_PROMPT.md"

# Or for different regions
claude "Generate 5 spots for Vietnam following SPOT_GENERATOR_PROMPT.md"
claude "Generate 15 spots for Caribbean Islands following SPOT_GENERATOR_PROMPT.md"
```

**How it works:**
1. Claude Code will read this file automatically when referenced
2. Follow the schema and guidelines below
3. Output will be validated JSON ready to merge into spots.json
4. Claude Code can help merge and test the new spots

---

## Manual Instructions (for other LLMs)

1. **Copy the prompt below** (everything under "PROMPT TEMPLATE" section)
2. **Replace the placeholders** with your desired location/region:
   - `[COUNTRY/REGION]` - e.g., "Greece", "Caribbean Islands", "California, USA"
   - `[NUMBER]` - e.g., "5", "10", "15"
3. **Paste into your LLM** (Claude, ChatGPT, etc.)
4. **Review and validate** the generated spots (check URLs, coordinates, facts)
5. **Merge into spots.json** by copying the generated JSON array items
6. **Test the application** to ensure new spots load correctly

---

## PROMPT TEMPLATE

You are a kitesurfing spot data researcher. Generate **[NUMBER]** kitesurfing spots for **[COUNTRY/REGION]** in valid JSON format following the exact schema below.

### Requirements

1. **All fields must be filled** - no null or empty strings (use "" for optional URLs if unavailable)
2. **Real, accurate data** - use actual Windguru URLs, real coordinates, and factual spot information
3. **Valid URLs only** - ensure Windguru, Windfinder, and location URLs are real and accessible
4. **Accurate coordinates** - locationUrl should point to the actual spot launch area
5. **Both English and Polish** - provide spotInfo (English) and spotInfoPL (Polish translations)
6. **No LLM context comments** - avoid adding llmComment unless the spot has unique AI-relevant context

### JSON Schema

```json
{
  "name": "Spot Name",
  "country": "Country Name",
  "windguruUrl": "https://www.windguru.cz/[ID]",
  "windfinderUrl": "https://www.windfinder.com/forecast/[spot-name]",
  "icmUrl": "https://www.meteo.pl/um/metco/mgram_pict.php?ntype=0u&row=XXX&col=XXX&lang=pl",
  "webcamUrl": "https://example.com/webcam",
  "locationUrl": "https://maps.app.goo.gl/[shortcode]",
  "spotInfo": {
    "type": "Lagoon/Beach/Bay/Open Sea/Wave spot/etc.",
    "bestWind": "N, NE, E, SE, S, SW, W, NW (pick best directions)",
    "waterTemp": "XX-XX°C (seasonal range)",
    "experience": "Beginner/Intermediate/Advanced/Expert or combinations",
    "launch": "Sandy Beach/Rocky Shore/Grass/Concrete Ramp/etc.",
    "hazards": "Shallow areas/Rocks/Currents/Traffic/Crowds/etc.",
    "season": "Month-Month (best season)",
    "description": "2-3 sentence description of the spot's characteristics, location, and appeal."
  },
  "spotInfoPL": {
    "type": "[Polish translation of type]",
    "bestWind": "[same cardinal directions]",
    "waterTemp": "[same temperature range]",
    "experience": "[Polish translation]",
    "launch": "[Polish translation]",
    "hazards": "[Polish translation]",
    "season": "[Polish months]",
    "description": "[Polish translation of description]"
  }
}
```

### Field Guidelines

#### Core Fields
- **name**: Official spot name (city/beach name)
- **country**: Full country name
- **windguruUrl**: Search Windguru for the spot and use the actual station URL
- **windfinderUrl**: Use Windfinder's URL format (all lowercase, hyphens for spaces)
- **icmUrl**: Only for Polish spots (ICM model), use "" for others
- **webcamUrl**: Real webcam URL if available, otherwise ""
- **locationUrl**: Google Maps shortened URL (maps.app.goo.gl format preferred)

#### spotInfo Fields
- **type**: Water conditions (Lagoon, flat water, choppy, waves, reef, etc.)
- **bestWind**: Cardinal directions that work best (based on geography)
- **waterTemp**: Seasonal range in Celsius
- **experience**: Target rider level(s)
- **launch**: Type of launch area
- **hazards**: Safety concerns (rocks, shallow water, currents, crowds, boats, etc.)
- **season**: Best months for kitesurfing
- **description**: Engaging 2-3 sentence overview

#### spotInfoPL (Polish Translation)
Translate all spotInfo fields to Polish:
- "Lagoon" → "Laguna"
- "Beginner" → "Początkujący"
- "Intermediate" → "Średniozaawansowany"
- "Advanced" → "Zaawansowany"
- "Sandy Beach" → "Plaża piaszczysta"
- "May-September" → "Maj-Wrzesień"

### Optional Field (Advanced)
- **llmComment**: Add only if spot has unique AI-relevant context (e.g., "thermal winds in afternoon", "tide-dependent", "requires local knowledge")

### Example Output Format

Return only a valid JSON array of spots (no markdown code blocks, no extra text):

```json
[
  {
    "name": "Example Spot",
    "country": "Greece",
    ...
  },
  {
    "name": "Another Spot",
    "country": "Greece",
    ...
  }
]
```

### Research Tips

1. **Find Windguru IDs**: Search windguru.cz for "[spot name] kite" or "[city name] wind"
2. **Verify coordinates**: Use Google Maps to get exact launch coordinates
3. **Check local sources**: Look for kitesurfing schools, forums, or local guides for accurate spot info
4. **Validate wind directions**: Consider geography (coastline orientation, thermal winds)
5. **Seasonal accuracy**: Research typical kitesurfing season for the region

### Quality Checklist

- [ ] All URLs are real and accessible
- [ ] Windguru URLs point to actual stations near the spot
- [ ] Location URLs point to the correct coordinates
- [ ] Water temperature ranges are realistic for the region
- [ ] Best wind directions match the coastline geography
- [ ] Hazards are accurate and relevant
- [ ] Descriptions are informative and engaging
- [ ] Polish translations are accurate
- [ ] JSON is valid and properly formatted

---

## Post-Generation Steps

1. **Validate JSON**: Use a JSON validator (https://jsonlint.com)
2. **Verify URLs**: Click through Windguru and Google Maps links
3. **Cross-check facts**: Verify spot details with kitesurfing forums/guides
4. **Test in app**: Add spots to spots.json and run `./build.sh --run`
5. **Check frontend**: Ensure new spots display correctly with forecasts

## Example Workflow

```bash
# 1. Generate spots using this prompt with your LLM
# 2. Save output to a temporary file
cat > new_spots.json << 'EOF'
[
  { ... generated spots ... }
]
EOF

# 3. Validate JSON
cat new_spots.json | jq .

# 4. Backup current spots
cp src/main/resources/spots.json src/main/resources/spots.json.backup

# 5. Merge manually (open in editor and append to array)
# OR use jq to merge:
jq -s '.[0] + .[1]' src/main/resources/spots.json new_spots.json > merged.json
mv merged.json src/main/resources/spots.json

# 6. Test
./build.sh --run

# 7. Verify in browser
open http://localhost:8080
```

## Common Mistakes to Avoid

1. **Fake Windguru IDs**: Don't invent URLs - search windguru.cz for real stations
2. **Wrong coordinates**: Ensure locationUrl points to the actual launch area
3. **Generic descriptions**: Make descriptions specific to the spot
4. **Missing Polish translations**: Don't forget spotInfoPL
5. **Invalid JSON**: Always validate before merging
6. **Inconsistent formatting**: Follow the exact schema structure
7. **Empty required fields**: Fill all fields (use "" for optional URLs)

## Tips for Different Regions

### Mediterranean (Greece, Spain, Italy, Turkey)
- Water temp: 15-28°C
- Season: April-October
- Common wind: Meltemi, Mistral, Thermal winds
- Types: Bay, beach, thermal spot

### Caribbean
- Water temp: 26-29°C
- Season: Year-round (November-July best)
- Common wind: Trade winds
- Types: Flat water, wave spots, reef breaks

### North Europe (Baltic, North Sea)
- Water temp: 8-20°C
- Season: May-September
- Common wind: Western systems
- Types: Lagoon, beach, exposed coast

### Brazil
- Water temp: 23-28°C
- Season: September-March (summer trades)
- Common wind: Trade winds
- Types: Lagoon, downwinders, wave spots

---

**Last updated**: 2025-01-06
**Compatible with**: varun.surf backend (Spring Boot, spots.json schema)