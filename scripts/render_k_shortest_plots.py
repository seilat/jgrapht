#!/usr/bin/env python3
"""
Render the top-k shortest paths benchmark plots embedded by the wiki page.

Inputs are the JMH summary text files written by AndorraBenchmarkRunner to
target/jmh-andorra/yen.txt. The numbers are also reproduced inline below so
this script works without an actual JMH run; pass --from-target to re-parse
the JMH text file instead.

Outputs are PNGs written under docs/wiki/images/:
    k_shortest_andorra.png         - log-scale bar chart, Andorra road graph
    k_shortest_andorra_scaled.png  - linear-scale zoom on the BPYen pair
    k_shortest_gnp.png             - log-scale bar chart, dense G(n,p) graph
    k_shortest_gnp_scaled.png      - linear-scale zoom on the BPYen pair

Usage:
    python scripts/render_k_shortest_plots.py
    python scripts/render_k_shortest_plots.py --from-target
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
    graph_type: str
    k: int
    mean_ms: float
    err_ms: float


# Algorithms in the order we want them to appear in the legend / bar groups.
ALGOS: list[str] = ["Yen", "Yen+A* (no BP)", "BPYen+Dijkstra", "BPYen+A*", "Eppstein"]
COLORS: dict[str, str] = {
    "Yen": "#cc3333",
    "Yen+A* (no BP)": "#dd9933",
    "BPYen+Dijkstra": "#3366cc",
    "BPYen+A*": "#22aa55",
    "Eppstein": "#9933cc",
}
METHOD_TO_LABEL: dict[str, str] = {
    "yen": "Yen",
    "yenAStarNoBoundedPrune": "Yen+A* (no BP)",
    "boundedPrunedYenDijkstra": "BPYen+Dijkstra",
    "boundedPrunedYenAStar": "BPYen+A*",
    "eppstein": "Eppstein",
}

# Default numbers as a placeholder so the script renders without a JMH run.
# Replaced with the actual results after the bench completes.
DEFAULT_YEN: list[YenRow] = [
    YenRow("Yen", "andorra", 1, 3174.0, 1144.0),
    YenRow("Yen", "andorra", 5, 11564.0, 3211.0),
    YenRow("Yen", "andorra", 25, 45808.0, 9406.0),
    YenRow("BPYen+Dijkstra", "andorra", 1, 281.0, 189.0),
    YenRow("BPYen+Dijkstra", "andorra", 5, 11399.0, 4268.0),
    YenRow("BPYen+Dijkstra", "andorra", 25, 36748.0, 6818.0),
    YenRow("BPYen+A*", "andorra", 1, 114.0, 41.0),
    YenRow("BPYen+A*", "andorra", 5, 2048.0, 590.0),
    YenRow("BPYen+A*", "andorra", 25, 10720.0, 2979.0),
]


# Matches a JMH text row when the bench class declares two @Param fields
# (graphType then k) and the layout is "method  (graphType)  (k)  Mode  Cnt  Score  +/-  Error  Units".
YEN_TXT_LINE = re.compile(
    r"^KShortestPathBench\.(\S+)\s+(\S+)\s+(\d+)\s+\S+\s+\d+\s+([\d.]+)\s+\D+\s+([\d.]+)"
)


def parse_yen_txt(path: Path) -> list[YenRow]:
    rows: list[YenRow] = []
    if not path.exists():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        m = YEN_TXT_LINE.match(line)
        if m is None:
            continue
        method, gt, k, mean, err = m.groups()
        label = METHOD_TO_LABEL.get(method, method)
        rows.append(YenRow(label, gt, int(k), float(mean), float(err)))
    return rows


def _grouped_bar_data(
    rows: list[YenRow], algos: list[str], graph_type: str
) -> tuple[list[int], list[list[float]], list[list[float]]]:
    rows_g = [r for r in rows if r.graph_type == graph_type]
    ks = sorted({r.k for r in rows_g})
    means = [
        [next((r.mean_ms for r in rows_g if r.algorithm == a and r.k == k), 0.0) for k in ks]
        for a in algos
    ]
    errs = [
        [next((r.err_ms for r in rows_g if r.algorithm == a and r.k == k), 0.0) for k in ks]
        for a in algos
    ]
    return ks, means, errs


def render_main(rows: list[YenRow], graph_type: str, title: str, out: Path) -> None:
    """Log-scale grouped bar chart over all algorithms present in the data."""
    present_algos = [a for a in ALGOS if any(r.algorithm == a and r.graph_type == graph_type for r in rows)]
    ks, means, errs = _grouped_bar_data(rows, present_algos, graph_type)
    if not ks or not present_algos:
        logger.warning("no data for graph_type=%s, skipping %s", graph_type, out)
        return

    fig, ax = plt.subplots(figsize=(9, 5))
    n_algos = len(present_algos)
    x_positions = list(range(len(ks)))
    bar_width = 0.85 / n_algos
    for i, algo in enumerate(present_algos):
        offsets = [x + (i - (n_algos - 1) / 2) * bar_width for x in x_positions]
        ax.bar(
            offsets,
            means[i],
            bar_width,
            label=algo,
            color=COLORS[algo],
            yerr=errs[i],
            capsize=3,
            edgecolor="black",
            linewidth=0.4,
        )
        for off, m in zip(offsets, means[i]):
            if m > 0:
                ax.text(off, m, f"{m:.0f}", ha="center", va="bottom", fontsize=7)

    ax.set_xticks(x_positions)
    ax.set_xticklabels([f"k = {k}" for k in ks])
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(title)
    ax.set_yscale("log")
    ax.set_ylim(bottom=max(1, min(m for row in means for m in row if m > 0) / 4))
    ax.grid(True, axis="y", which="both", linestyle=":", alpha=0.4)
    ax.legend(loc="upper left", fontsize=8)
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)
    logger.info("wrote %s", out)


def render_scaled(rows: list[YenRow], graph_type: str, title: str, out: Path) -> None:
    """Linear-scale variant for the BoundedPrunedYen pair. Error bars omitted to
    keep the y-axis honest on the fast variants."""
    algos = ["BPYen+Dijkstra", "BPYen+A*"]
    present_algos = [a for a in algos if any(r.algorithm == a and r.graph_type == graph_type for r in rows)]
    ks, means, _ = _grouped_bar_data(rows, present_algos, graph_type)
    if not ks or not present_algos:
        logger.warning("no data for graph_type=%s, skipping %s", graph_type, out)
        return

    fig, ax = plt.subplots(figsize=(8, 5))
    n_algos = len(present_algos)
    x_positions = list(range(len(ks)))
    bar_width = 0.7 / n_algos
    for i, algo in enumerate(present_algos):
        offsets = [x + (i - (n_algos - 1) / 2) * bar_width for x in x_positions]
        ax.bar(
            offsets,
            means[i],
            bar_width,
            label=algo,
            color=COLORS[algo],
            edgecolor="black",
            linewidth=0.5,
        )
        for off, m in zip(offsets, means[i]):
            if m > 0:
                ax.text(off, m, f"{m:.0f}", ha="center", va="bottom", fontsize=8)

    ax.set_xticks(x_positions)
    ax.set_xticklabels([f"k = {k}" for k in ks])
    ax.set_ylabel("Mean time per call, ms/op (lower is better)")
    ax.set_title(title)
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

    rows = (parse_yen_txt(JMH_DIR / "yen.txt") if args.from_target else []) or DEFAULT_YEN

    render_main(
        rows, "andorra",
        "Top-k shortest paths on roadmap of Andorra",
        OUT_DIR / "k_shortest_andorra.png",
    )
    render_scaled(
        rows, "andorra",
        "Top-k shortest paths on roadmap of Andorra (scaled, BoundedPrunedYen only)",
        OUT_DIR / "k_shortest_andorra_scaled.png",
    )
    render_main(
        rows, "gnp",
        "Top-k shortest paths on dense G(n=500, p=0.3) random graph",
        OUT_DIR / "k_shortest_gnp.png",
    )
    render_scaled(
        rows, "gnp",
        "Top-k shortest paths on G(n=500, p=0.3) (scaled, BoundedPrunedYen only)",
        OUT_DIR / "k_shortest_gnp_scaled.png",
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
