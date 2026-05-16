# Bounded-Pruned Yen + A\* — interactive visualization

**Live:** https://seilat.github.io/jgrapht/visualizations/

A single self-contained HTML page that animates and compares three pairs of
shortest-path algorithms, side by side:

| Mode | Left pane | Right pane |
|------|-----------|------------|
| **K-shortest paths (Yen)** | Vanilla Yen + Dijkstra | Bounded-pruned Yen + A\* (this PR's algorithm) |
| **Single shortest path** | Standard Dijkstra | A\* with reverse-Dijkstra heuristic |
| **All paths — sandwich prune** | `AllDirectedPaths` baseline (backward BFS only) | `AllDirectedPaths` with forward+backward sandwich prune |

Step counters, per-event explanations, and a glossary of expansion / relaxation
/ heuristic / admissibility terms accompany the animation.

The page reproduces the algorithms exactly as they're implemented in JGraphT
on the [`bounded-pruned-yen`][bpy] and [`alldirectedpaths-source-sandwich-prune`][adp]
branches.

[bpy]: https://github.com/seilat/jgrapht/tree/bounded-pruned-yen
[adp]: https://github.com/seilat/jgrapht/tree/alldirectedpaths-source-sandwich-prune

## Running

There are three ways to view the visualization. The HTML file is fully
self-contained (no external JS or CSS), so any static file server works.

### 1. Open the file directly (zero infra)

Open `index.html` in a modern browser. That's it.

Some Chromium policies disable `file://` page features; if anything looks
broken, fall back to one of the local server options below.

### 2. Local Python HTTP server (developer convenience)

```sh
cd visualizations
python -m http.server 8080
# then visit http://localhost:8080/
```

### 3. Docker (the "deploy anywhere" option)

A multi-stage-free, ~20 MB nginx-alpine image is provided.

#### Build

```sh
cd visualizations
docker build -t jgrapht-viz:latest .
```

#### Run

```sh
docker run --rm -p 8080:8080 --name jgrapht-viz jgrapht-viz:latest
# then visit http://localhost:8080/
```

Stop it with `Ctrl-C` (or `docker stop jgrapht-viz` from another shell).

#### docker-compose

```sh
docker compose up -d
docker compose down
```

The image runs nginx as a non-root user on port 8080 and exposes a basic
healthcheck (`HEAD /`). No persistent volumes, no network egress.

## What's inside

| File | Purpose |
|------|---------|
| `index.html` | The whole visualization (HTML + inline CSS + inline JS, no external deps). |
| `Dockerfile` | nginx-alpine image, runs as non-root on port 8080. |
| `nginx.conf` | Minimal server block: serves the one static file, no autoindex, no server tokens. |
| `docker-compose.yml` | One-service compose file (build + port mapping). |
| `vercel.json` | Static-routing config for the matching Vercel deployment. |

## Controls (in-app)

- **Mode** — switches between the three algorithm pairs above.
- **Example** — graph preset (mode-aware: grid / chain / wiki for shortest-path
  modes; garden / tight-budget diamond for sandwich mode).
- **K** — number of paths to enumerate (Yen mode only).
- **Max length** — path-length budget for the sandwich prune (sandwich mode only).
- **Run** — load graph + start animation.
- **Play / Pause / Step / Reset** — animation controls.
- **Speed** — animation step interval.

Each step explains itself in plain English in the box under each canvas (e.g.
"Spur task starts at `1,1` — banned edges shown in red, lower bound is
`prefixCost (3) + h[spurNode] = 7`; the bounded driver only ran it because no
cheaper candidate is known yet.").

## Algorithm correctness

Both panes in every mode produce the same set of optimal paths or the same
decoration outcome on every supplied example. Where the two panes show
different alternates for K ≥ 2 in Yen mode, those alternates are still
equal-cost — the algorithms tie-break differently among multiple optimal
paths, which is a property of Yen with weighted ties, not a bug.

Numerical wins (Chrome verification, weighted grid 6×5, K=3):

| Variant | Spur tasks | Node expansions |
|--------|-----------|------------------|
| Vanilla Yen + Dijkstra | 18 | 280 |
| Bounded-pruned Yen + A\* | 9 | 141 |

Sandwich prune (garden example, budget=4):

| Variant | Edges retained | Edges considered | Vertices marked | Edges dropped |
|---------|---------------|------------------|-----------------|---------------|
| Baseline | 6 | 6 | 6 (backward) | 0 |
| Sandwich | 4 | 5 | 5 (forward) | 1 |

The sandwich variant also avoids ever queueing the orphan branch, so its
total work is strictly less than the considered-count suggests.
