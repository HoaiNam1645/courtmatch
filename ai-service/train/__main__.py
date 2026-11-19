"""Chay:  uv run python -m train partner|team [--seed 42] [--database-url ...]

Doc thang cac bang cua he thong bang ket noi chi-doc, huan luyen, danh gia, in ket qua va ghi artifact vao artifacts/{version}/.
"""

import argparse
import json
import os
import sys

from courtly_ai import config, trainers


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="train", description=__doc__)
    parser.add_argument("kind", choices=["partner", "team"])
    parser.add_argument("--seed", type=int, default=config.RANDOM_SEED)
    parser.add_argument("--database-url")
    args = parser.parse_args(argv)
    if args.database_url:
        os.environ["DATABASE_URL"] = args.database_url
    result = (trainers.train_partner if args.kind == "partner" else trainers.train_team)(seed=args.seed)
    meta = result["metadata"]
    print(f"Model: {result['version']}")
    print(json.dumps({"gate": meta["gate"], "model": meta["metrics"]["model"]["overall"],
                      "baseline": meta["metrics"]["baseline"]["overall"]}, ensure_ascii=False, indent=1))
    print("Dat dieu kien kich hoat" if meta["gate"]["passes"] else "KHONG dat: " + "; ".join(meta["gate"]["reasons"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
