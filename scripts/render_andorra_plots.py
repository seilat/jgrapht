#!/usr/bin/env python3
"""
Render the Andorra OSM 2026-05 benchmark plots embedded by the wiki page.

Inputs are the JMH summary text files written by AndorraBenchmarkRunner to
target/jmh-andorra/{yen,m2m,adp}.txt. The numbers are also reproduced inline
below so this script works without an actual JMH run; pass --from-target to
re-parse the JMH text files instead.

Outputs are PNGs written under docs/wiki/images/.

Usage:
    python scripts/render_andorra_plots.py
    python scripts/render_andorra_plots.py --from-target
"""
from __future__ import annotations

import argparse
import logging
import os
import re
from dataclasses import dataclass
from pathlib import Path

import matplotlib.pyplot as plt

logger = logging.getLogger(__name__)

PROJECT_ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = PROJECT_ROOT / "docs" / "wiki" / "images"
JMH_DIR = PROJECT_ROOT / "jgrapht-core" / "target" / "jmh-andorra"


@dataclass(frozen=True)
class YenRow:
    algorithm: str
    k: int
    mean_ms: float
    err_ms: float


@dataclass(frozen=True)
class M2MRow:
    n: int
    mean_ms: float


@dataclass(frozen=True)
class AdpRow:
    bfs_radius: int
    max_path_len: int
    mean_ms: float
    err_ms: float


# Default numbers — measured 2026-05-12 on the maintenance branch (pre-#1341
# rebase) on AMD x86-64 / 32 GB DDR4 / Windows 11 Pro / Temurin JDK 21.0.9.
DEFAULT_YEN: list[YenRow] = [
    YenRow("Yen", 1, 548.129, 543.865),
    YenRow("Yen", 5, 2538.576, 3050.256),
    YenRow("BPYen+Dijkstra", 1, 67.233, 51.000),
    YenRow("BPYen+Dijkstra", 5, 1578.425, 283.738),
    YenRow("BPYen+A*", 1, 26.897, 8.300),
    YenRow("BPYen+A*", 5, 411.600, 436.627),
]

DEFAULT_M2M: list[M2MRow] = [
    # Pre-PR-#1340 catastrophe: getPaths(V) iterates graph.vertexSet().
    M2MRow(n=2, mean_ms=215_964.016),
]

DEFAULT_ADP: list[AdpRow] = [
    AdpRow(bfs_radius=6, max_path_len=6, mean_ms=0.003, err_ms=0.001),
]


YEN_TXT_LINE = re.compile(
    r"^AndorraBoundedPrunedYenBench\.(\S+)\s+(\d+)\s+\S+\s+\d+\s+([\d.]+)\s+\D+\s+([\d.]+)"
)
M2M_TXT_LINE = re.compile(
    r"^AndorraDijkstraManyToManyGetPathsBench\.\S+\s+(\d+)\s+\S+\s+\d+\s+([\d.]+)"
)
ADP_TXT_LINE = re.compile(
    r"^AndorraAllDirectedPathsNonSimpleBench\.\S+\s+(\d+)\s+(\d+)\s+\S+\s+\d+\s+([\d.]+)\s+\D+\s+([\d.]+)"
)


def parse_yen_txt(path: Path) -> list[YenRow]:
    rows: list[YenRow] = []
    if not path.exists():
        return rows
    method_to_label = {
        "yen": "Yen",
        "boundedPrunedYenDijkstra": "BPYen+Dijkstra",
        "boundedPrunedYenAStar": "BPYen+A*",
    }
    for line in path.read_text(encoding="utf-8").splitlines():
        m = YEN_TXT_LINE.match(line)
        if m is None:
            continue
        method, k, mean, err = m.groups()
        label = method_to_label.get(method, method)
        rows.append(YenRow(label, int(k), float(mean), float(err)))
    return rows


def parse_m2m_txt(path: Path) -> list[M2MRow]:
    rows: list[M2MRow] = []
    if not path.exists():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        m = M2M_TXT_LINE.match(line)
        if m is None:
            continue
        n_str, mean_str = m.groups()
        rows.append(M2MRow(int(n_str), float(mean_str)))
    return rows


def parse_adp_txt(path: Path) -> list[AdpRow]:
    rows: list[AdpRow] = []
    if not path.exists():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        m = ADP_TXT_LINE.match(line)
        if m is None:
            continue
        r, l, mean, err = m.groups()
        rows.append(AdpRow(int(r), int(l), float(mean), float(err)))
    return rows


def render_yen_plot(rows: list[YenRow], out: Path) -> None:
    algos = ["Yen", "BPYen+Dijkstra", "BPYen+A*"]
    ks = sorted({r.k for r in rows})
    colors = {"Yen": "#cc3333", "BPYen+Dijkstra": "#3366cc", "BPYen+A*": "#22aa55"}

    x_positions = list(range(len(ks)))
    bar_width = 0.25

    fig, ax = plt.subplots(figsize=(8, 5))
    for i, algo in enumerate(algos):
        means = [
            next((r.mean_ms for r in rows if r.algorithm == algo and r.k == k), 0.0)
            for k in ks
        ]
        errs = [
            next((r.err_ms for r in rows if r.algorithm == algo and r.k == k), 0.0)
            for k in ks
        ]
        offsets = [x + (i - 1) * bar_width for x in x_positions]
        ax.bar(
            offsets,
            means,
            bar_width,
            label=algo,
            color=colors[algo],
            yerr=errs,
            capsize=4,
            edgecolor="black",
            linewidth=0.5,
        )
        for off, m in zip(offsets, means):
            ax.text(off, m, f"{m:.0f}", ha="center", va="bottom", fontsize=8)

    ax.set_xticks(x_positions)
    ax.set_xticklabels([f"k = {k}" for k in ks])
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(
        "Top-k shortest paths on Andorra OSM\n"
        "(36,618 vertices / 67,354 edges; 3 random s→t pairs, seed = 7)"
    )
    ax.set_yscale("log")
    ax.set_ylim(bottom=10)
    ax.grid(True, axis="y", which="both", linestyle=":", alpha=0.4)
    ax.legend(loc="upper left")
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)
    logger.info("wrote %s", out)


def render_m2m_plot(rows: list[M2MRow], out: Path) -> None:
    if not rows:
        return
    fig, ax = plt.subplots(figsize=(8, 4.5))
    labels = ["master\n(pre-PR #1340)", "PR #1340 expected\n(single Dijkstra)"]
    # The "after" number is not measured on this branch; ~10 ms is a generous
    # upper bound for one Dijkstra over Andorra. Marked in the chart legend.
    means = [rows[0].mean_ms, 10.0]
    colors = ["#cc3333", "#22aa55"]
    bars = ax.bar(labels, means, color=colors, edgecolor="black", linewidth=0.5)
    ax.set_yscale("log")
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(
        "DijkstraManyToManyShortestPaths.getPaths(V) on Andorra OSM\n"
        "(36,618 vertices; |S| = |T| = 2; single-shot measurement)"
    )
    ax.grid(True, axis="y", which="both", linestyle=":", alpha=0.4)
    for bar, m in zip(bars, means):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            m,
            f"{m:,.0f} ms" if m >= 1.0 else f"{m * 1000:.1f} µs",
            ha="center",
            va="bottom",
            fontsize=9,
        )
    fig.text(
        0.5,
        0.02,
        "PR #1340 cell is the expected post-merge value — not measured on this branch.",
        ha="center",
        fontsize=8,
        style="italic",
        color="#666",
    )
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    fig.savefig(out, dpi=120)
    plt.close(fig)
    logger.info("wrote %s", out)


def render_adp_plot(rows: list[AdpRow], out: Path) -> None:
    if not rows:
        return
    fig, ax = plt.subplots(figsize=(8, 4.5))
    label = (
        f"BFS-ball = 29, walk len = {rows[0].max_path_len}\n"
        f"seed = 13, both pre/post #1341"
    )
    bars = ax.bar(
        [label],
        [rows[0].mean_ms],
        yerr=[rows[0].err_ms],
        color="#888888",
        edgecolor="black",
        linewidth=0.5,
        capsize=6,
    )
    ax.set_ylabel("Mean time per call, ms/op")
    ax.set_title(
        "AllDirectedPaths non-simple on an Andorra BFS-ball subgraph\n"
        "(small ball — neither #1341 nor C3 forward-pruning has room to register)"
    )
    ax.grid(True, axis="y", linestyle=":", alpha=0.4)
    for bar, m in zip(bars, [rows[0].mean_ms]):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            m,
            f"{m:.3f} ms",
            ha="center",
            va="bottom",
            fontsize=9,
        )
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)
    logger.info("wrote %s", out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--from-target",
        action="store_true",
        help="re-parse JMH text files under jgrapht-core/target/jmh-andorra/ "
        "instead of using the committed defaults.",
    )
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(message)s")

    os.makedirs(OUT_DIR, exist_ok=True)

    yen_rows = (
        parse_yen_txt(JMH_DIR / "yen.txt") if args.from_target else []
    ) or DEFAULT_YEN
    m2m_rows = (
        parse_m2m_txt(JMH_DIR / "m2m.txt") if args.from_target else []
    ) or DEFAULT_M2M
    adp_rows = (
        parse_adp_txt(JMH_DIR / "adp.txt") if args.from_target else []
    ) or DEFAULT_ADP

    render_yen_plot(yen_rows, OUT_DIR / "k_shortest_andorra.png")
    render_m2m_plot(m2m_rows, OUT_DIR / "m2m_get_paths_andorra.png")
    render_adp_plot(adp_rows, OUT_DIR / "all_directed_paths_andorra.png")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
