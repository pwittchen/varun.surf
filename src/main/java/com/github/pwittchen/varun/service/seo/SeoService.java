package com.github.pwittchen.varun.service.seo;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds search-engine metadata: per-spot and per-country {@code <head>} tags
 * (title, description, canonical, Open Graph, Twitter Card, JSON-LD) injected
 * into the static HTML templates, plus a dynamic XML sitemap covering every
 * spot and country page.
 */
@Service
public class SeoService {

    private static final String BASE_URL = "https://varun.surf";
    private static final String DEFAULT_IMAGE = BASE_URL + "/logo.png";
    private static final int MAX_DESCRIPTION_LENGTH = 160;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("(?is)<title>.*?</title>");
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("(?is)<meta\\s+name=(?:\"description\"|description)[^>]*>");

    /**
     * Injects spot-specific SEO tags into the spot page template.
     */
    public String injectSpotSeo(String template, Spot spot) {
        String name = nullSafe(spot.name());
        String country = nullSafe(spot.country());
        String url = BASE_URL + "/spot/" + spot.wgId();
        String title = name + ", " + country
                + " – Kitesurfing Wind Forecast & Live Conditions | VARUN.SURF";
        String description = buildSpotDescription(spot, name, country);
        String image = spotImage(spot);

        StringBuilder head = new StringBuilder();
        head.append(metaName("description", description));
        head.append(metaName("keywords", buildSpotKeywords(name, country)));
        head.append(link("canonical", url));
        head.append(og("og:type", "website"));
        head.append(og("og:url", url));
        head.append(og("og:site_name", "VARUN.SURF"));
        head.append(og("og:title", title));
        head.append(og("og:description", description));
        head.append(og("og:image", image));
        head.append(metaName("twitter:card", "summary_large_image"));
        head.append(metaName("twitter:title", title));
        head.append(metaName("twitter:description", description));
        head.append(metaName("twitter:image", image));
        head.append(spotJsonLd(spot, name, country, description, url, image));

        return transform(template, title, head.toString());
    }

    /**
     * Injects country-specific SEO tags into the index template.
     */
    public String injectCountrySeo(String template, String country, int spotCount) {
        String safeCountry = nullSafe(country);
        String url = BASE_URL + "/country/" + normalizeCountry(safeCountry);
        String title = "Kitesurfing in " + safeCountry
                + " – Kite Spots, Wind Forecast & Live Conditions | VARUN.SURF";
        String description = "Live wind conditions and weather forecast for "
                + spotCount + " kitesurfing " + (spotCount == 1 ? "spot" : "spots")
                + " in " + safeCountry
                + ". Wind speed, gusts, direction and hourly forecast for kiteboarding, windsurfing and wingfoil.";
        description = truncate(description, MAX_DESCRIPTION_LENGTH);

        StringBuilder head = new StringBuilder();
        head.append(metaName("description", description));
        head.append(link("canonical", url));
        head.append(og("og:type", "website"));
        head.append(og("og:url", url));
        head.append(og("og:site_name", "VARUN.SURF"));
        head.append(og("og:title", title));
        head.append(og("og:description", description));
        head.append(og("og:image", DEFAULT_IMAGE));
        head.append(metaName("twitter:card", "summary_large_image"));
        head.append(metaName("twitter:title", title));
        head.append(metaName("twitter:description", description));

        return transform(template, title, head.toString());
    }

    /**
     * Builds an XML sitemap listing the homepage and every spot and country page.
     */
    public String buildSitemap(List<Spot> spots) {
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(sb, BASE_URL + "/", today, "hourly", "1.0");

        spots.stream()
                .map(Spot::country)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .forEach(country -> appendUrl(sb,
                        BASE_URL + "/country/" + normalizeCountry(country), today, "daily", "0.6"));

        spots.stream()
                .sorted(Comparator.comparingInt(Spot::wgId))
                .forEach(spot -> appendUrl(sb,
                        BASE_URL + "/spot/" + spot.wgId(), today, "hourly", "0.8"));

        sb.append("</urlset>\n");
        return sb.toString();
    }

    private void appendUrl(StringBuilder sb, String loc, String lastmod, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(xml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private String transform(String template, String title, String headFragment) {
        String result = TITLE_PATTERN.matcher(template)
                .replaceFirst("<title>" + java.util.regex.Matcher.quoteReplacement(html(title)) + "</title>");
        result = DESCRIPTION_PATTERN.matcher(result).replaceFirst("");
        return result.replaceFirst("(?i)</head>",
                java.util.regex.Matcher.quoteReplacement(headFragment) + "</head>");
    }

    private String buildSpotDescription(Spot spot, String name, String country) {
        SpotInfo info = spot.spotInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("Live wind & weather forecast for kitesurfing at ")
                .append(name).append(", ").append(country).append(". ");
        if (info != null && info.bestWind() != null && !info.bestWind().isBlank()) {
            sb.append("Best wind: ").append(info.bestWind().trim()).append(". ");
        }
        sb.append("Real-time wind speed, gusts, direction & hourly forecast.");
        return truncate(sb.toString(), MAX_DESCRIPTION_LENGTH);
    }

    private String buildSpotKeywords(String name, String country) {
        return name + " kitesurfing, " + name + " wind forecast, " + name + " windguru, "
                + "kitesurfing " + country + ", kite spot " + name + ", "
                + name + " live wind, " + name + " kiteboarding";
    }

    private String spotImage(Spot spot) {
        String photo = spot.spotPhotoUrl();
        if (photo != null && photo.startsWith("http")) {
            return photo;
        }
        return DEFAULT_IMAGE;
    }

    private String spotJsonLd(Spot spot, String name, String country,
                              String description, String url, String image) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/ld+json\">");
        sb.append("{\"@context\":\"https://schema.org\",\"@type\":\"SportsActivityLocation\",");
        sb.append("\"name\":\"").append(json(name)).append("\",");
        sb.append("\"description\":\"").append(json(description)).append("\",");
        sb.append("\"url\":\"").append(json(url)).append("\",");
        sb.append("\"image\":\"").append(json(image)).append("\",");
        sb.append("\"sport\":[\"Kitesurfing\",\"Windsurfing\",\"Wingfoil\"],");
        sb.append("\"address\":{\"@type\":\"PostalAddress\",\"addressCountry\":\"")
                .append(json(country)).append("\"}");
        if (spot.coordinates() != null) {
            sb.append(",\"geo\":{\"@type\":\"GeoCoordinates\",\"latitude\":")
                    .append(String.format(Locale.ROOT, "%.5f", spot.coordinates().lat()))
                    .append(",\"longitude\":")
                    .append(String.format(Locale.ROOT, "%.5f", spot.coordinates().lon()))
                    .append("}");
        }
        sb.append("}</script>");
        return sb.toString();
    }

    private String metaName(String name, String content) {
        return "<meta name=\"" + name + "\" content=\"" + html(content) + "\">";
    }

    private String og(String property, String content) {
        return "<meta property=\"" + property + "\" content=\"" + html(content) + "\">";
    }

    private String link(String rel, String href) {
        return "<link rel=\"" + rel + "\" href=\"" + html(href) + "\">";
    }

    /**
     * Normalizes a country name to the URL slug used by the frontend router
     * (lowercase, whitespace removed, non-letter characters stripped).
     */
    public String normalizeCountry(String country) {
        return country.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z]", "");
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1).trim() + "…";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private String html(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String json(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
