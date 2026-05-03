#!/usr/bin/env python3

"""Aggregate update latency association summaries across replica counts."""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path
from typing import Sequence


INPUT_COLUMNS = (
    "replica_count",
    "fault_count",
    "target_db_associations",
    "associations_per_update",
    "keyword_count",
    "doc_id_count",
    "payload_id",
    "latency_ns",
    "latency_ms",
    "source_run",
    "source_csv",
)

OUTPUT_COLUMNS = INPUT_COLUMNS + ("source_summary",)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Aggregate update latency TSV summaries across replica counts.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to the aggregated replica sweep TSV output.",
    )
    parser.add_argument(
        "inputs",
        nargs="+",
        help="Per-replica-count update_latency_by_associations_summary.tsv files.",
    )
    return parser.parse_args()


def validate_header(fieldnames: Sequence[str], input_path: Path) -> None:
    if tuple(fieldnames) != INPUT_COLUMNS:
        raise ValueError(f"{input_path} has unexpected TSV header")


def read_rows(input_path: Path) -> list[dict[str, str]]:
    if not input_path.is_file():
        raise FileNotFoundError(f"Input TSV not found: {input_path}")

    rows: list[dict[str, str]] = []
    with input_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        validate_header(reader.fieldnames or (), input_path)

        for row in reader:
            output_row = {column: (row.get(column) or "").strip() for column in INPUT_COLUMNS}
            validate_row(output_row, input_path)
            output_row["source_summary"] = str(input_path)
            rows.append(output_row)

    if not rows:
        raise ValueError(f"Input TSV has no data rows: {input_path}")
    return rows


def validate_row(row: dict[str, str], input_path: Path) -> None:
    for field_name in (
        "replica_count",
        "fault_count",
        "target_db_associations",
        "associations_per_update",
        "keyword_count",
        "doc_id_count",
        "latency_ns",
    ):
        parse_positive_int(row[field_name], field_name, input_path)

    parse_float(row["latency_ms"], "latency_ms", input_path)

    if row["payload_id"] == "":
        raise ValueError(f"{input_path} has empty payload_id")
    if row["source_run"] == "":
        raise ValueError(f"{input_path} has empty source_run")
    if row["source_csv"] == "":
        raise ValueError(f"{input_path} has empty source_csv")


def parse_positive_int(raw_value: str, field_name: str, input_path: Path) -> int:
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise ValueError(f"{input_path} field {field_name} must be a valid integer") from exc

    if value <= 0:
        raise ValueError(f"{input_path} field {field_name} must be greater than 0")
    return value


def parse_float(raw_value: str, field_name: str, input_path: Path) -> float:
    try:
        return float(raw_value)
    except ValueError as exc:
        raise ValueError(f"{input_path} field {field_name} must be a valid float") from exc


def sort_key(row: dict[str, str]) -> tuple[int, int, int]:
    return (
        int(row["replica_count"]),
        int(row["target_db_associations"]),
        int(row["associations_per_update"]),
    )


def write_rows(output_path: Path, rows: Sequence[dict[str, str]]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, delimiter="\t", fieldnames=OUTPUT_COLUMNS, lineterminator="\n")
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
