#!/usr/bin/env python3

"""Generate update payload NDJSON files for SSE update latency benchmarks."""

from __future__ import annotations

import argparse
import json
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


DEFAULT_SEED = 42


@dataclass(frozen=True)
class UpdatePayloadConfig:
    output_path: Path
    warmup_count: int
    warmup_associations: int
    warmup_keywords_per_payload: int
    measure_associations: int
    measure_keywords_per_payload: int
    seed: int

    @property
    def payload_count(self) -> int:
        return self.warmup_count + 1

    @property
    def association_count(self) -> int:
        return self.warmup_count * self.warmup_associations + self.measure_associations


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Generate NDJSON update payloads with controlled keyword-docId "
            "association counts."
        ),
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to the generated NDJSON file.",
    )
    parser.add_argument(
        "--warmup-count",
        required=True,
        type=parse_non_negative_int,
        help="Number of warmup payloads to generate. Can be 0.",
    )
    parser.add_argument(
        "--warmup-associations",
        required=True,
        type=parse_positive_int,
        help="Number of keyword-doc associations in each warmup payload.",
    )
    parser.add_argument(
        "--warmup-keywords-per-payload",
        required=True,
        type=parse_positive_int,
        help="Number of keywords used inside each warmup payload.",
    )
    parser.add_argument(
        "--measure-associations",
        required=True,
        type=parse_positive_int,
        help="Number of keyword-doc associations in the measured payload.",
    )
    parser.add_argument(
        "--measure-keywords-per-payload",
        required=True,
        type=parse_positive_int,
        help="Number of keywords used inside the measured payload.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=DEFAULT_SEED,
        help=f"Random seed reserved for reproducible payload generation. Default: {DEFAULT_SEED}.",
    )
    return parser.parse_args(argv)


def parse_non_negative_int(raw_value: str) -> int:
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"must be a valid integer: {raw_value}") from exc

    if value < 0:
        raise argparse.ArgumentTypeError("must be 0 or greater")
    return value


def parse_positive_int(raw_value: str) -> int:
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"must be a valid integer: {raw_value}") from exc

    if value <= 0:
        raise argparse.ArgumentTypeError("must be greater than 0")
    return value


def build_config(args: argparse.Namespace) -> UpdatePayloadConfig:
    config = UpdatePayloadConfig(
        output_path=Path(args.output),
        warmup_count=args.warmup_count,
        warmup_associations=args.warmup_associations,
        warmup_keywords_per_payload=args.warmup_keywords_per_payload,
        measure_associations=args.measure_associations,
        measure_keywords_per_payload=args.measure_keywords_per_payload,
        seed=args.seed,
    )
    validate_config(config)
    return config


def validate_config(config: UpdatePayloadConfig) -> None:
    if config.output_path.exists() and config.output_path.is_dir():
        raise ValueError(f"Output path is a directory: {config.output_path}")

    if config.warmup_count > 0 and config.warmup_keywords_per_payload > config.warmup_associations:
        raise ValueError("warmup-keywords-per-payload cannot be greater than warmup-associations")

    if config.measure_keywords_per_payload > config.measure_associations:
        raise ValueError("measure-keywords-per-payload cannot be greater than measure-associations")


def generate_payloads(config: UpdatePayloadConfig) -> None:
    random_generator = random.Random(config.seed)

    config.output_path.parent.mkdir(parents=True, exist_ok=True)
    with config.output_path.open("w", encoding="utf-8", newline="\n") as handle:
        line_count = 0
        for payload_index in range(1, config.warmup_count + 1):
            payload = build_payload(
                phase="warmup",
                payload_index=payload_index,
                association_count=config.warmup_associations,
                keywords_per_payload=config.warmup_keywords_per_payload,
                random_generator=random_generator,
            )
            write_payload(handle, payload)
            line_count += 1

        measure_payload = build_payload(
            phase="measure",
            payload_index=1,
            association_count=config.measure_associations,
            keywords_per_payload=config.measure_keywords_per_payload,
            random_generator=random_generator,
        )
        write_payload(handle, measure_payload)
        line_count += 1

    expected_line_count = config.payload_count
    if line_count != expected_line_count:
        raise RuntimeError(f"Generated {line_count} lines, expected {expected_line_count}")


def build_payload(
    phase: str,
    payload_index: int,
    association_count: int,
    keywords_per_payload: int,
    random_generator: random.Random,
) -> dict[str, object]:
    payload_id = format_payload_id(phase, payload_index)
    doc_counts_by_keyword = split_associations(association_count, keywords_per_payload)
    updates = []
    next_doc_index = 1

    for keyword_index, doc_count in enumerate(doc_counts_by_keyword, start=1):
        doc_ids = [
            format_doc_id(phase, payload_index, doc_index)
            for doc_index in range(next_doc_index, next_doc_index + doc_count)
        ]
        random_generator.shuffle(doc_ids)
        updates.append(
            {
                "keyword": format_keyword(phase, payload_index, keyword_index),
                "docIds": doc_ids,
            }
        )
        next_doc_index += doc_count

    return {
        "phase": phase,
        "payloadId": payload_id,
        "associationCount": association_count,
        "updates": updates,
    }


def split_associations(association_count: int, keywords_per_payload: int) -> list[int]:
    base_count = association_count // keywords_per_payload
    remainder = association_count % keywords_per_payload
    return [
        base_count + (1 if keyword_index < remainder else 0)
        for keyword_index in range(keywords_per_payload)
    ]


def format_payload_id(phase: str, payload_index: int) -> str:
    return f"{phase}_{payload_index:03d}"


def format_keyword(phase: str, payload_index: int, keyword_index: int) -> str:
    return f"upd_{phase}_{payload_index:03d}_kw_{keyword_index:03d}"


def format_doc_id(phase: str, payload_index: int, doc_index: int) -> str:
    return f"upd_{phase}_{payload_index:03d}_doc_{doc_index:06d}"


def write_payload(handle, payload: dict[str, object]) -> None:
    handle.write(json.dumps(payload, separators=(",", ":")))
    handle.write("\n")


def print_summary(config: UpdatePayloadConfig) -> None:
    print("Generated update payloads:")
    print(f"  output: {config.output_path}")
    print(f"  payloads: {config.payload_count}")
    print(f"  warmup payloads: {config.warmup_count}")
    print(f"  warmup associations per payload: {config.warmup_associations}")
    print(f"  warmup keywords per payload: {config.warmup_keywords_per_payload}")
    print(f"  measure associations: {config.measure_associations}")
    print(f"  measure keywords per payload: {config.measure_keywords_per_payload}")
    print(f"  total keyword-doc associations: {config.association_count}")
    print(f"  seed: {config.seed}")


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        config = build_config(args)
        generate_payloads(config)
    except Exception as exc:  # pragma: no cover - CLI error path
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print_summary(config)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
