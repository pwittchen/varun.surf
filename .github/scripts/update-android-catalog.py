#!/usr/bin/env python3
"""Bump android/gradle/libs.versions.toml from a ben-manes dependencyUpdates report.

The catalog pins versions by key ([versions]) and the report lists artifacts
(group:name), so each outdated artifact is traced back through [libraries] and
[plugins] to the key it reads. A key shared by several artifacts (lifecycle,
room, agp) takes the lowest version offered for it, so none of them is moved
past what has been released for all of them. Only the version string on the
key's own line is rewritten, which keeps the file's layout and comments.

Usage: update-android-catalog.py <report.json> <libs.versions.toml>
Prints one "- key: old -> new" line per bumped key.
"""
import json
import re
import sys
import tomllib


def version_tuple(version):
    return [int(part) if part.isdigit() else part for part in re.split(r"[.\-]", version)]


def artifact_keys(catalog):
    keys = {}
    for library in catalog.get("libraries", {}).values():
        if not isinstance(library, dict) or "ref" not in library.get("version", {}):
            continue
        module = library.get("module") or f"{library.get('group')}:{library.get('name')}"
        keys[module] = library["version"]["ref"]
    for plugin in catalog.get("plugins", {}).values():
        if isinstance(plugin, dict) and "ref" in plugin.get("version", {}):
            keys[f"{plugin['id']}:{plugin['id']}.gradle.plugin"] = plugin["version"]["ref"]
    return keys


def main(report_path, catalog_path):
    with open(report_path) as f:
        report = json.load(f)
    with open(catalog_path, "rb") as f:
        catalog = tomllib.load(f)
    with open(catalog_path) as f:
        text = f.read()

    versions = catalog.get("versions", {})
    keys = artifact_keys(catalog)

    offers = {}
    for dep in report.get("outdated", {}).get("dependencies", []):
        key = keys.get(f"{dep['group']}:{dep['name']}")
        available = dep.get("available", {})
        new = available.get("release") or available.get("milestone")
        if key is None or not new or versions.get(key) != dep["version"]:
            continue
        offers.setdefault(key, []).append(new)

    for key, candidates in sorted(offers.items()):
        old = versions[key]
        new = min(candidates, key=version_tuple)
        text, count = re.subn(
            rf'^({re.escape(key)}\s*=\s*")({re.escape(old)})(")',
            rf"\g<1>{new}\g<3>",
            text,
            count=1,
            flags=re.MULTILINE,
        )
        if count:
            print(f"- {key}: {old} -> {new}")

    with open(catalog_path, "w") as f:
        f.write(text)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
