#!/usr/bin/env python3

"""Aggregate single-run update latency CSV files into one TSV."""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path
from typing import Sequence


INPUT_COLUMNS = (
    "scenario",
    "operation",
    "run",
    "phase",
    "associations_per_update",
    "keyword_count",
    "doc_id_count",
    "payload_id",
    "latency_ns",
)

OUTPUT_COLUMNS = (
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

RUN_DIR_PATTERN = re.compile(
    r"_quinta_r(?P<replicas>[0-9]+)_f(?P<faults>[0-9]+)_db(?P<db>[0-9]+)_upd(?P<upd>[0-9]+)_kw(?P<keywords>[0-9]+)$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Aggregate update latency CSV files produced by single measurement runs.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to the aggregated TSV output.",
    )
    parser.add_argument(
        "inputs",
        nargs="+",
        help="Single-run update_latency_by_associations.csv files.",
    )
    return parser.parse_args()


def run_metadata(input_path: Path) -> tuple[int, int, int, int, int, str]:
    run_dir = input_path.parent.name
    match = RUN_DIR_PATTERN.search(run_dir)
    if match is None:
        raise ValueError(f"Cannot extract metadata from run directory: {run_dir}")

    return (
        int(match.group("replicas")),
        int(match.group("faults")),
        int(match.group("db")),
        int(match.group("upd")),
        int(match.group("keywords")),
        run_dir,
    )


def validate_header(fieldnames: Sequence[str], input_path: Path) -> None:
    if tuple(fieldnames) != INPUT_COLUMNS:
        raise ValueError(f"{input_path} has unexpected CSV header")


def read_row(input_path: Path) -> dict[str, str]:
    if not input_path.is_file():
        raise FileNotFoundError(f"Input CSV not found: {input_path}")

    (
        replica_count,
        fault_count,
        target_db_associations,
        expected_update_associations,
        expected_keyword_count,
        source_run,
    ) = run_metadata(input_path)

    with input_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        validate_header(reader.fieldnames or (), input_path)
        rows = list(reader)

    if len(rows) != 1:
        raise ValueError(f"{input_path} must contain exactly one measured row, found {len(rows)}")

    row = {key: (value or "").strip() for key, value in rows[0].items()}
    validate_update_row(row, input_path, expected_update_associations, expected_keyword_count)

    latency_ns = int(row["latency_ns"])
    return {
        "replica_count": str(replica_count),
        "fault_count": str(fault_count),
        "target_db_associations": str(target_db_associations),
        "associations_per_update": row["associations_per_update"],
        "keyword_count": row["keyword_count"],
        "doc_id_count": row["doc_id_count"],
        "payload_id": row["payload_id"],
        "latency_ns": row["latency_ns"],
        "latency_ms": f"{latency_ns / 1_000_000:.6f}",
        "source_run": source_run,
        "source_csv": str(input_path),
    }


def validate_update_row(
    row: dict[str, str],
    input_path: Path,
    expected_update_associations: int,
    expected_keyword_count: int,
) -> None:
    if row["scenario"] != "update-latency-by-associations":
        raise ValueError(f"{input_path} has unexpected scenario: {row['scenario']}")
    if row["operation"] != "UPDATE":
        raise ValueError(f"{input_path} has unexpected operation: {row['operation']}")
    if row["run"] != "1":
        raise ValueError(f"{input_path} has unexpected run: {row['run']}")
    if row["phase"] != "measure":
        raise ValueError(f"{input_path} has unexpected phase: {row['phase']}")

    associations_per_update = parse_positive_int(row["associations_per_update"], "associations_per_update", input_path)
    if associations_per_update != expected_update_associations:
        raise ValueError(
            f"{input_path} associations_per_update {associations_per_update} does not match run directory "
            f"value {expected_update_associations}"
        )

    keyword_count = parse_positive_int(row["keyword_count"], "keyword_count", input_path)
    if keyword_count != expected_keyword_count:
        raise ValueError(
            f"{input_path} keyword_count {keyword_count} does not match run directory "
            f"value {expected_keyword_count}"
        )

    parse_positive_int(row["doc_id_count"], "doc_id_count", input_path)
    parse_positive_int(row["latency_ns"], "latency_ns", input_path)

    if row["payload_id"] == "":
        raise ValueError(f"{input_path} has empty payload_id")


def parse_positive_int(raw_value: str, field_name: str, input_path: Path) -> int:
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise ValueError(f"{input_path} field {field_name} must be a valid integer") from exc

    if value <= 0:
        raise ValueError(f"{input_path} field {field_name} must be greater than 0")
    return value


def sort_key(row: dict[str, str]) -> tuple[int, int, int, int]:
    return (
        int(row["replica_count"]),
        int(row["fault_count"]),
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
        rows = [read_row(Path(raw_input)) for raw_input in args.inputs]
        write_rows(output_path, rows)
    except Exception as exc:  # pragma: no cover - CLI error path
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print(f"Wrote {len(rows)} aggregated rows to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
