#!/usr/bin/env python3

"""Aggregate per-run search latency summaries across replica counts."""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path
from typing import Sequence


INPUT_COLUMNS = (
    "bucket",
    "bucket_label",
    "bucket_min_docs",
    "bucket_max_docs",
    "cache_mode",
    "n",
    "min_ms",
    "median_ms",
    "mean_ms",
    "p95_ms",
    "p99_ms",
    "max_ms",
)

OUTPUT_COLUMNS = (
    "replica_count",
    "fault_count",
    "bucket",
    "bucket_label",
    "bucket_min_docs",
    "bucket_max_docs",
    "cache_mode",
    "n",
    "min_ms",
    "median_ms",
    "mean_ms",
    "p95_ms",
    "p99_ms",
    "max_ms",
    "source_run",
)

RUN_DIR_PATTERN = re.compile(r"_r(?P<replicas>[0-9]+)_f(?P<faults>[0-9]+)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Aggregate search latency TSV summaries across replica counts.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to the aggregated TSV output.",
    )
    parser.add_argument(
        "inputs",
        nargs="+",
        help="Per-run search_latency_by_docs_summary.tsv files.",
    )
    return parser.parse_args()


def run_metadata(input_path: Path) -> tuple[int, int, str]:
    run_dir = input_path.parent.name
    match = RUN_DIR_PATTERN.search(run_dir)
    if match is None:
        raise ValueError(f"Cannot extract replica/fault counts from run directory: {run_dir}")

    return int(match.group("replicas")), int(match.group("faults")), run_dir


def validate_header(fieldnames: Sequence[str], input_path: Path) -> None:
    missing = [column for column in INPUT_COLUMNS if column not in fieldnames]
    if missing:
        raise ValueError(f"{input_path} is missing required columns: {', '.join(missing)}")


def read_rows(input_path: Path) -> list[dict[str, str]]:
    if not input_path.is_file():
        raise FileNotFoundError(f"Input TSV not found: {input_path}")

    replica_count, fault_count, source_run = run_metadata(input_path)
    rows: list[dict[str, str]] = []

    with input_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        validate_header(reader.fieldnames or (), input_path)

        for row in reader:
            output_row = {
                "replica_count": str(replica_count),
                "fault_count": str(fault_count),
                "source_run": source_run,
            }
            for column in INPUT_COLUMNS:
                output_row[column] = (row.get(column) or "").strip()
            rows.append(output_row)

    if not rows:
        raise ValueError(f"Input TSV has no data rows: {input_path}")
    return rows


def sort_key(row: dict[str, str]) -> tuple[int, int, int, int, str]:
    cache_order = {"fresh": 0, "cached": 1}
    return (
        int(row["replica_count"]),
        int(row["fault_count"]),
        int(row["bucket_min_docs"] or "0"),
        cache_order.get(row["cache_mode"], 99),
        row["cache_mode"],
    )


def write_rows(output_path: Path, rows: Sequence[dict[str, str]]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, delimiter="\t", fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        for row in sorted(rows, key=sort_key):
            writer.writerow(row)


def main() -> int:
    args = parse_args()
    output_path = Path(args.output)

    try:
        rows: list[dict[str, str]] = []
        for raw_input in args.inputs:
            rows.extend(read_rows(Path(raw_input)))
        write_rows(output_path, rows)
    except Exception as exc:  # pragma: no cover - CLI error path
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print(f"Wrote {len(rows)} aggregated rows to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
