#!/usr/bin/env python3
"""
Turn a world-atlas topology (https://github.com/topojson/world-atlas, the same
outlines the web globe loads from unpkg) into the compact binary the Android
globe reads from its assets.

    curl -sSfLO https://cdn.jsdelivr.net/npm/world-atlas@2.0.2/countries-110m.json
    python3 globe_outlines.py countries-110m.json ../src/main/assets/globe/outlines-110m.bin

Format (big endian, read by GlobeOutlines.kt):
    int land ring count,   then per ring: int n, n x (short lon, short lat)
    int border line count, then per line: int n, n x (short lon, short lat)
lon is quantised as lon / 180 * 32767 and lat as lat / 90 * 32767.

Land rings keep the topology's winding (d3's spherical convention: exterior
rings clockwise), which the globe's horizon clipping relies on. Borders are
the arcs shared by two different countries, like topojson.mesh(a !== b).
"""
import json
import struct
import sys


def decode_arcs(topology):
    scale = topology["transform"]["scale"]
    translate = topology["transform"]["translate"]
    arcs = []
    for arc in topology["arcs"]:
        x = y = 0
        points = []
        for dx, dy in arc:
            x += dx
            y += dy
            points.append((x * scale[0] + translate[0], y * scale[1] + translate[1]))
        arcs.append(points)
    return arcs


def arc_points(arcs, index):
    return arcs[index] if index >= 0 else list(reversed(arcs[~index]))


def ring(arcs, indices):
    points = []
    for i, index in enumerate(indices):
        part = arc_points(arcs, index)
        points.extend(part if i == 0 else part[1:])
    return points


def polygons(geometry):
    if geometry["type"] == "Polygon":
        return [geometry["arcs"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["arcs"]
    if geometry["type"] == "GeometryCollection":
        return [p for g in geometry["geometries"] for p in polygons(g)]
    return []


def arc_ids(geometry):
    return {a if a >= 0 else ~a for polygon in polygons(geometry) for r in polygon for a in r}


def quantise(lon, lat):
    return (
        max(-32767, min(32767, round(lon / 180 * 32767))),
        max(-32767, min(32767, round(lat / 90 * 32767))),
    )


def write_lines(out, lines):
    out.extend(struct.pack(">i", len(lines)))
    for line in lines:
        out.extend(struct.pack(">i", len(line)))
        for lon, lat in line:
            out.extend(struct.pack(">hh", *quantise(lon, lat)))


def main(source, target):
    with open(source) as f:
        topology = json.load(f)
    arcs = decode_arcs(topology)

    land = [ring(arcs, r) for polygon in polygons(topology["objects"]["land"]) for r in polygon]

    users = {}
    for country in topology["objects"]["countries"]["geometries"]:
        for arc in arc_ids(country):
            users[arc] = users.get(arc, 0) + 1
    borders = [arcs[a] for a in sorted(users) if users[a] > 1]

    out = bytearray()
    write_lines(out, land)
    write_lines(out, borders)
    with open(target, "wb") as f:
        f.write(out)
    points = sum(map(len, land)) + sum(map(len, borders))
    print(f"{target}: {len(land)} land rings, {len(borders)} border arcs, {points} points, {len(out)} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
