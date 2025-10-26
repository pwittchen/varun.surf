package com.github.pwittchen.varun.model.sponsor;

public record Sponsor(
        int id,
        boolean main,
        String name,
        String link,
        String logo,
        String logoDark,
        String logoLight
) {
    public Sponsor {
        // If theme-specific logos are not provided, use the main logo for both themes
        if (logoDark == null || logoDark.isEmpty()) {
            logoDark = logo;
        }
        if (logoLight == null || logoLight.isEmpty()) {
            logoLight = logo;
        }
    }
}