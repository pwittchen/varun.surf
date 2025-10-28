package com.github.pwittchen.varun.model.sponsor;

public record Sponsor(
        int id,
        boolean main,
        String name,
        String link
) {
}