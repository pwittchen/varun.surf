# Kitesurfing Spot Generator - LLM Prompt Template

## CLI Usage with Claude Code

**Use this prompt directly from CLI without copying:**

```bash
# Generate spots for a specific region
claude "Generate 10 kitesurfing spots for Greek Islands following the schema in new-kite-spot.md"

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
2. **Real, accurate data** - use actual Windguru URLs and factual spot information
3. **Valid Windguru URLs only** - ensure Windguru URLs are real and accessible (verify on windguru.cz)
4. **Leave location URLs blank** - set locationUrl to "" (will be added manually later with correct coordinates)
5. **Leave webcam URLs blank** - set webcamUrl to "" (will be added manually later if available)
6. **Both English and Polish** - provide spotInfo (English) and spotInfoPL (Polish translations)
7. **No LLM context comments** - avoid adding llmComment unless the spot has unique AI-relevant context

### JSON Schema

```json
{
  "name": "Spot Name",
  "country": "Country Name",
  "windguruUrl": "https://www.windguru.cz/[ID]",
  "windfinderUrl": "https://www.windfinder.com/forecast/[spot-name]",
  "icmUrl": "",
  "webcamUrl": "",
  "locationUrl": "",
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
- **windguruUrl**: Search Windguru for the spot and use the actual station URL (REQUIRED - verify on windguru.cz)
- **windfinderUrl**: Use Windfinder's URL format (all lowercase, hyphens for spaces)
- **icmUrl**: Only for Polish spots (ICM model), use "" for all other countries
- **webcamUrl**: ALWAYS set to "" (do not generate - will be added manually later)
- **locationUrl**: ALWAYS set to "" (do not generate - will be added manually later with correct coordinates)

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

1. **Find Windguru IDs**: Search windguru.cz for "[spot name] kite" or "[city name] wind" - VERIFY the URL exists!
2. **Check local sources**: Look for kitesurfing schools, forums, or local guides for accurate spot info
3. **Validate wind directions**: Consider geography (coastline orientation, thermal winds)
4. **Seasonal accuracy**: Research typical kitesurfing season for the region
5. **Leave URLs blank**: Set locationUrl and webcamUrl to "" - they will be added manually later

### Quality Checklist

- [ ] Windguru URLs are real and verified (search on windguru.cz before adding)
- [ ] Windfinder URLs follow correct format (lowercase, hyphens)
- [ ] webcamUrl is set to "" (do not generate)
- [ ] locationUrl is set to "" (do not generate)
- [ ] icmUrl is "" for all non-Polish spots
- [ ] Water temperature ranges are realistic for the region
- [ ] Best wind directions match the coastline geography
- [ ] Hazards are accurate and relevant
- [ ] Descriptions are informative and engaging
- [ ] Polish translations are accurate
- [ ] JSON is valid and properly formatted

---

## Post-Generation Steps

1. **Validate JSON**: Use a JSON validator or `cat spots.json | jq .`
2. **Verify Windguru URLs**: Click through to ensure they're real stations
3. **Cross-check facts**: Verify spot details with kitesurfing forums/guides
4. **Add location URLs manually**: Find exact coordinates on Google Maps and add shortened URLs
5. **Add webcam URLs manually**: Search for webcams and add if available
6. **Test in app**: Add spots to spots.json and run `./build.sh --run`
7. **Check frontend**: Ensure new spots display correctly with forecasts

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

1. **Fake Windguru IDs**: Don't invent URLs - search windguru.cz for real stations (MOST IMPORTANT)
2. **Generating location/webcam URLs**: ALWAYS set locationUrl and webcamUrl to "" - these were frequently wrong in the past
3. **Generic descriptions**: Make descriptions specific to the spot
4. **Missing Polish translations**: Don't forget spotInfoPL (all fields must be translated)
5. **Invalid JSON**: Always validate before merging (use jq or jsonlint)
6. **Inconsistent formatting**: Follow the exact schema structure
7. **Wrong icmUrl for non-Polish spots**: Set to "" for all countries except Poland

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

**Last updated**: 2025-11-10
**Compatible with**: varun.surf backend (Spring Boot, spots.json schema)

## Changelog

**2025-11-10**:
- Removed requirement to generate `locationUrl` (Google Maps) - frequently generated incorrect URLs
- Removed requirement to generate `webcamUrl` - frequently generated incorrect URLs
- Both fields should now always be set to "" and added manually later
- Updated all sections to reflect this change (Requirements, Schema, Field Guidelines, Research Tips, Quality Checklist, Common Mistakes)