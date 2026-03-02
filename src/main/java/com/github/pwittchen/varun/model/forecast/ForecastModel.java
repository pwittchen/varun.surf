package com.github.pwittchen.varun.model.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The ForecastModel enum represents different weather forecast models,
 * which are used to generate meteorological data and predictions.
 * <p>
 * More details: <a href="https://micro.windguru.cz/help.php">micro windguru help</a>
 * All models available at Windguru are listed here.
 */
public enum ForecastModel {

    // ── Global models ──────────────────────────────────────────────────
    GFS("gfs", "GFS 13 km"),
    IFS("ifs", "IFS 9 km"),
    ICON("icon", "ICON 13 km"),
    GEM("gem", "GEM 25 km"),
    GEOS5("geos5", "GEOS5 25 km"),
    ACCESS_G("access_g", "ACCESS-G 12 km"),

    // ── Europe ─────────────────────────────────────────────────────────
    ICON_EU("iconeu", "ICON-EU 7 km"),
    ICON_D2("icond2", "ICON-D2 2 km"),
    AROME("arome", "AROME 1 km"),
    AROME_HD("aromehd", "AROME HD 0.5 km"),
    ARPEGE("arpege", "ARPEGE 11 km"),
    ARPEGE_EU("arpegeeu", "ARPEGE-EU 7 km"),
    HARM_EU("harmeu", "HARMONIE 5 km (EU)"),
    HARM_DINI("harmdini", "HARMONIE-DINI 2 km"),
    HIRLAM("hirlam", "HIRLAM 7 km"),
    ICON_EU_WAVE("iconewam", "ICON-EU Wave"),
    UKV("ukv", "UKV 2 km"),
    GALWEM("galwem", "GALWEM 17 km"),
    COSMO2I("cosmo2i", "COSMO-2I 2 km"),

    // ── Mediterranean / Africa ─────────────────────────────────────────
    ICON_MENA("iconmena", "ICON 13 km (MENA)"),

    // ── Asia-Pacific ───────────────────────────────────────────────────
    ICON_ASIA("iconasia", "ICON 13 km (Asia)"),
    ACCESS_R("access_r", "ACCESS-R 12 km (AU)"),
    KMAP("kma_gdps", "KMA GDPS 12 km"),
    KMA_RDPS("kma_rdps", "KMA RDPS 3 km"),
    JMA("jma", "JMA MSM 5 km"),

    // ── Americas ───────────────────────────────────────────────────────
    NAM("nam", "NAM 12 km"),
    NAM_CONUS("namconus", "NAM CONUS 3 km"),
    HRRR("hrrr", "HRRR 3 km"),
    RAP("rap", "RAP 13 km"),
    WRF_9("wrfnam9", "WRF 9 km (NAM)"),
    WRF_4("wrfnam4", "WRF 4 km (NAM)"),
    GEM_REGIONAL("gemreg", "GEM Regional 10 km"),

    // ── French Territories ────────────────────────────────────────────
    AROME_ANTILLES("aromeantilles", "AROME 2 km (Antilles)"),
    AROME_GUYANE("aromeguyane", "AROME 2 km (Guyane)"),
    AROME_REUNION("aromereunion", "AROME 2 km (Reunion)"),
    AROME_NCAL("aromencal", "AROME 2 km (N-Cal)"),
    AROME_POLYNESIE("aromepolynesie", "AROME 2 km (Polynesie)"),

    // ── Wave models ────────────────────────────────────────────────────
    WW3("ww3", "WW3 28 km"),
    GWAM("gwam", "GWAM 25 km"),
    EWAM("ewam", "EWAM 10 km"),
    MFWAM("mfwam", "MFWAM 10 km"),

    // ── Additional global / regional ──────────────────────────────────
    FV3("fv3", "FV3 13 km"),
    ICONWAVE("iconwave", "ICON Wave 28 km"),
    GFS_WAVE("gfswave", "GFS Wave 28 km"),
    GFSENS("gfsens", "GFS Ens 25 km"),
    CMCENS("cmcens", "CMC Ens 25 km"),
    IFSENS("ifsens", "IFS Ens 18 km");

    private static final Logger log = LoggerFactory.getLogger(ForecastModel.class);

    private static final Map<String, ForecastModel> BY_MODEL_KEY = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(ForecastModel::modelKey, m -> m));

    private final String modelKey;
    private final String displayName;

    ForecastModel(String modelKey, String displayName) {
        this.modelKey = modelKey;
        this.displayName = displayName;
    }

    public String modelKey() {
        return modelKey;
    }

    public String displayName() {
        return displayName;
    }

    public static ForecastModel fromModelKey(String key) {
        if (key == null || key.isEmpty()) {
            return GFS;
        }
        ForecastModel model = BY_MODEL_KEY.get(key.toLowerCase());
        if (model != null) {
            return model;
        }
        log.error("Unknown forecast model modelKey: {}", key);
        log.error("Fallback to default model: {}", GFS);
        return GFS;
    }

    public static ForecastModel valueOfGracefully(String model) {
        return fromModelKey(model);
    }
}
