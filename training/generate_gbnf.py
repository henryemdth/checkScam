#!/usr/bin/env python3
"""Generate a llama.cpp GBNF grammar from the FraudSynthesized Pydantic schema.

Export procedure (AGENTS.md Section 6/7):
  1. Load `training/schema.py` (single source of truth).
  2. `FraudSynthesized.model_json_schema()` produces the JSON schema.
  3. Resolve $defs inline and emit a deterministic GBNF file whose start symbol
     is `root`, feedable to `llama_sampler_init_grammar`.

The whitespace / string rules mirror llama.cpp's canonical `grammars/json.gbnf`.

Usage:
    python generate_gbnf.py [--output DIR] [--write-json]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from schema import FraudSynthesized

RULE_HEADER = """# GBNF grammar generated from training/schema.py (FraudSynthesized.model_json_schema()).
# Do not edit by hand: regenerate with `python generate_gbnf.py`.
# Start symbol: root
"""

WS_RULE = 'ws ::= | " " | "\\n" [ \\t]{0,20}'
BOOL_RULE = 'boolean ::= ("true" | "false") ws'
STRING_RULE = (
    'string ::= "\\"" ( [^"\\\\\\x7F\\x00-\\x1F] | '
    '"\\\\" (["\\\\bfnrt"] | "u" [0-9a-fA-F]{4}) )* "\\"" ws'
)


def _escape_literal(value: str) -> str:
    """Escape a string so it is emitted inside a GBNF quoted literal."""
    return value.replace("\\", "\\\\").replace('"', '\\"')


def resolve_defs(schema: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Resolve top-level $refs into $defs, returning a mutable copy per property."""
    defs = schema.get("$defs", {})
    resolved: dict[str, dict[str, Any]] = {}
    for key, prop in schema["properties"].items():
        ref = prop.get("$ref")
        if ref is not None:
            name = ref.rsplit("/", 1)[-1]
            if name not in defs:
                raise ValueError(f"Unresolved $ref in schema: {ref}")
            resolved[key] = dict(defs[name])
        else:
            resolved[key] = dict(prop)
    return resolved


def build_grammar(schema: dict[str, Any]) -> str:
    """Assemble the full GBNF document (deterministic, one rule per line)."""
    properties = resolve_defs(schema)

    if not properties:
        raise ValueError("Schema has no properties")

    rules = [RULE_HEADER.rstrip(), WS_RULE, BOOL_RULE, STRING_RULE]

    pairs: list[str] = []
    for key, prop in properties.items():
        ptype = prop.get("type")
        if ptype == "string" and "enum" in prop:
            # Rule names must not contain '_' (llama.cpp's is_word_char excludes it).
            ref = "enum" + "".join(part.capitalize() for part in key.split("_"))
            alternatives = " | ".join(
                f'"\\"{_escape_literal(value)}\\""' for value in prop["enum"]
            )
            rules.append(f"{ref} ::= ({alternatives}) ws")
        elif ptype == "boolean":
            ref = "boolean"
        elif ptype == "string":
            ref = "string"
        else:
            raise ValueError(f"Unsupported type in schema: {ptype!r} for {key!r}")
        pairs.append(f'"\\"{key}\\"" ws ":" ws {ref}')

    sep = ' "," ws '.join(pairs)
    rules.append(f'object ::= "{{" ws {sep} ws "}}"')
    rules.append("root ::= object")

    return "\n".join(rules) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent)
    parser.add_argument("--write-json", action="store_true", default=True)
    args = parser.parse_args()

    schema = FraudSynthesized.model_json_schema()
    grammar = build_grammar(schema)

    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    (out / "scam_schema.gbnf").write_text(grammar, encoding="utf-8")

    if args.write_json:
        (out / "scam_schema.json").write_text(
            json.dumps(schema, indent=2, ensure_ascii=False), encoding="utf-8"
        )

    print(f"GBNF written to {out / 'scam_schema.gbnf'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())