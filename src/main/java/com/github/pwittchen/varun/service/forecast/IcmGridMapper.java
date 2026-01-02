package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.IcmGrid;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IcmGridMapper {
    private static final Logger log = LoggerFactory.getLogger(IcmGridMapper.class);
    private static final String ICM_URL_FORMAT = "https://www.meteo.pl/um/metco/mgram_pict.php?ntype=0u&row=%d&col=%d&lang=pl";

    // Empirically fitted for Poland + the Czech Republic (UM 4 km grid)
    private static final double ROW_A = -27.52;
    private static final double ROW_B = 1844.0;
    private static final double COL_A = 18.47;
    private static final double COL_B = -132.42;

    // Valid meteograms are larger than 10KB, error images are ~360 bytes
    private static final int MIN_VALID_IMAGE_SIZE = 10000;
    // Search radius for finding a valid grid point (ICM grid spacing is irregular)
    private static final int SEARCH_RADIUS = 8;

    private final OkHttpClient httpClient;
    // Cache validated grid points to avoid repeated HTTP checks
    private final ConcurrentMap<String, Optional<IcmGrid>> validatedGridCache = new ConcurrentHashMap<>();

    public IcmGridMapper(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Converts lat/lon coordinates to ICM meteogram URL.
     * Snaps to the nearest valid grid point since the ICM grid is sparse.
     */
    public Optional<String> toIcmUrl(double lat, double lon, String country) {
        // ICM grid is only available for Poland and the Czech Republic, so we can use it only for those countries
        if (!country.equals("Poland") && !country.equals("Czech Republic")) {
            return Optional.empty();
        }
        final IcmGrid approximateGrid = toRowCol(lat, lon);
        String cacheKey = approximateGrid.row() + ":" + approximateGrid.col();

        Optional<IcmGrid> validGrid = validatedGridCache.computeIfAbsent(cacheKey,
                k -> findNearestValidGrid(approximateGrid));

        return validGrid.map(grid -> String.format(ICM_URL_FORMAT, grid.row(), grid.col()));
    }

    private IcmGrid toRowCol(double lat, double lon) {
        int row = (int) Math.round(ROW_A * lat + ROW_B);
        int col = (int) Math.round(COL_A * lon + COL_B);
        return new IcmGrid(row, col);
    }

    /**
     * Search in expanding squares around the approximate point to find a valid meteogram.
     */
    private Optional<IcmGrid> findNearestValidGrid(IcmGrid approximate) {
        // First, check the exact point
        if (isValidMeteogram(approximate.row(), approximate.col())) {
            return Optional.of(approximate);
        }

        // Search in an expanding radius
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            for (int dr = -radius; dr <= radius; dr++) {
                for (int dc = -radius; dc <= radius; dc++) {
                    // Only check points on the perimeter of this radius
                    if (Math.abs(dr) == radius || Math.abs(dc) == radius) {
                        int row = approximate.row() + dr;
                        int col = approximate.col() + dc;
                        if (isValidMeteogram(row, col)) {
                            log.debug("Found valid ICM grid at ({}, {}) for approximate ({}, {})",
                                    row, col, approximate.row(), approximate.col());
                            return Optional.of(new IcmGrid(row, col));
                        }
                    }
                }
            }
        }

        log.warn("No valid ICM grid found near ({}, {})", approximate.row(), approximate.col());
        return Optional.empty();
    }

    private boolean isValidMeteogram(int row, int col) {
        String url = String.format(ICM_URL_FORMAT, row, col);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (var response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                long contentLength = response.body().contentLength();
                if (contentLength > 0) {
                    return contentLength > MIN_VALID_IMAGE_SIZE;
                }
                // If content-length unknown, read the body
                byte[] bytes = response.body().bytes();
                return bytes.length > MIN_VALID_IMAGE_SIZE;
            }
        } catch (IOException e) {
            log.trace("Failed to check ICM grid ({}, {}): {}", row, col, e.getMessage());
        }
        return false;
    }
}