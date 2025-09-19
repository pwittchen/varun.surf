package com.github.pwittchen.varun.model.windguru;

public record WeatherForecastWindguru(
        String label,
        int wspd_kn,
        int gust_kn,
        String wdir_compass,
        int wdir_deg,
        int temp_c,
        int slp_hpa,
        int cloud_high_pct,
        int cloud_mid_pct,
        int cloud_low_pct,
        int apcp_mm_3h,
        int apcp_mm_1h,
        int rh_pct
) {
}
