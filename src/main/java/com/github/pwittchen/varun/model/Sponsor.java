package com.github.pwittchen.varun.model;

public record Sponsor(
        int id,
        boolean main,
        String name,
        String link,
        String logo
) {
}