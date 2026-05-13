#!/usr/bin/env python3
"""
Render the Andorra OSM top-k shortest paths benchmark plots embedded by the
wiki page.

Inputs are the JMH summary text files written by AndorraBenchmarkRunner to
target/jmh-andorra/yen.txt. The numbers are also reproduced inline below so
this script works without an actual JMH run; pass --from-target to re-parse
the JMH text file instead.

Outputs are PNGs written under docs/wiki/images/:
    k_shortest_andorra.png         - log-scale bar chart over all three algorithms
    k_shortest_andorra_scaled.png  - linear-scale zoom on the BoundedPrunedYen pair

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


# Default numbers measured on the maintenance branch (post-#1341 master) on
# AMD x86-64 / 32 GB DDR4 / Windows 11 Pro / Eclipse Temurin JDK 21.0.9 /
# JMH 1.37 with 3 random source-sink pairs (seed = 7), 2 warm-up iterations of
# 5 s and 3 measurement iterations of 10 s.
DEFAULT_YEN: list[YenRow] = [
    YenRow("Yen", 1, 548.129, 543.865),
    YenRow("Yen", 5, 2538.576, 3050.256),
    YenRow("BPYen+Dijkstra", 1, 67.233, 51.000),
    YenRow("BPYen+Dijkstra", 5, 1578.425, 283.738),
    YenRow("BPYen+A*", 1, 26.897, 8.300),
    YenRow("BPYen+A*", 5, 411.600, 436.627),
]


YEN_TXT_LINE = re.compile(
    r"^AndorraBoundedPrunedYenBench\.(\S+)\s+(\d+)\s+\S+\s+\d+\s+([\d.]+)\s+\D+\s+([\d.]+)"
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


def _grouped_bar_data(
    rows: list[YenRow], algos: list[str]
) -> tuple[list[int], list[list[float]], list[list[float]]]:
    ks = sorted({r.k for r in rows})
    means = [
        [next((r.mean_ms for r in rows if r.algorithm == a and r.k == k), 0.0) for k in ks]
        for a in algos
    ]
    errs = [
        [next((r.err_ms for r in rows if r.algorithm == a and r.k == k), 0.0) for k in ks]
        for a in algos
    ]
    return ks, means, errs


def render_yen_main(rows: list[YenRow], out: Path) -> None:
    """Log-scale bar chart over all three algorithms."""
    algos = ["Yen", "BPYen+Dijkstra", "BPYen+A*"]
    colors = {"Yen": "#cc3333", "BPYen+Dijkstra": "#3366cc", "BPYen+A*": "#22aa55"}
    ks, means, errs = _grouped_bar_data(rows, algos)

    fig, ax = plt.subplots(figsize=(8, 5))
    x_positions = list(range(len(ks)))
    bar_width = 0.25
    for i, algo in enumerate(algos):
        offsets = [x + (i - 1) * bar_width for x in x_positions]
        ax.bar(
            offsets,
            means[i],
            bar_width,
            label=algo,
            color=colors[algo],
            yerr=errs[i],
            capsize=4,
            edgecolor="black",
            linewidth=0.5,
        )
        for off, m in zip(offsets, means[i]):
            ax.text(off, m, f"{m:.0f}", ha="center", va="bottom", fontsize=8)

    ax.set_xticks(x_positions)
    ax.set_xticklabels([f"k = {k}" for k in ks])
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(
        "Top-k shortest paths on roadmap of Andorra"
    )
    ax.set_yscale("log")
    ax.set_ylim(bottom=10)
    ax.grid(True, axis="y", which="both", linestyle=":", alpha=0.4)
    ax.legend(loc="upper left")
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)
    logger.info("wrote %s", out)


def render_yen_scaled(rows: list[YenRow], out: Path) -> None:
    """Linear-scale variant comparing only the BoundedPrunedYen spur engines.

    Error bars are intentionally omitted here: classical Yen's variance at k = 5
    on Andorra dominates the main chart, and the scaled view exists to highlight
    the Dijkstra-vs-A* difference at the mean level. The error bars remain
    visible on the main chart for the full picture.
    """
    algos = ["BPYen+Dijkstra", "BPYen+A*"]
    colors = {"BPYen+Dijkstra": "#3366cc", "BPYen+A*": "#22aa55"}
    ks, means, _ = _grouped_bar_data(rows, algos)

    fig, ax = plt.subplots(figsize=(8, 5))
    x_positions = list(range(len(ks)))
    bar_width = 0.32
    for i, algo in enumerate(algos):
        offsets = [x + (i - 0.5) * bar_width for x in x_positions]
        ax.bar(
            offsets,
            means[i],
            bar_width,
            label=algo,
            color=colors[algo],
            edgecolor="black",
            linewidth=0.5,
        )
        for off, m in zip(offsets, means[i]):
            ax.text(off, m, f"{m:.0f}", ha="center", va="bottom", fontsize=8)

    ax.set_xticks(x_positions)
    ax.set_xticklabels([f"k = {k}" for k in ks])
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(
        "Top-k shortest paths on roadmap of Andorra (scaled, BoundedPrunedYen only)"
    )
    ax.set_ylim(bottom=0)
    ax.grid(True, axis="y", linestyle=":", alpha=0.4)
    ax.legend(loc="upper left")
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

    render_yen_main(yen_rows, OUT_DIR / "k_shortest_andorra.png")
    render_yen_scaled(yen_rows, OUT_DIR / "k_shortest_andorra_scaled.png")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
