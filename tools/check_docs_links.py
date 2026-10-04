#!/usr/bin/env python3
"""Vérifie que les liens relatifs des fichiers Markdown pointent vers un fichier existant.

Les URL http(s)/mailto et les ancres seules sont ignorées (pas de réseau, pas de faux échecs).
Usage : python3 tools/check_docs_links.py   (depuis n'importe où ; code de sortie 1 si lien cassé)
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
LINK = re.compile(r"\]\(<?([^)>\s]+)>?(?:\s+\"[^\"]*\")?\)")
FENCE = re.compile(r"^\s*(```|~~~)")


def markdown_files():
    out = subprocess.check_output(["git", "ls-files", "*.md"], cwd=ROOT, text=True)
    return [ROOT / p for p in out.splitlines()]


def broken_links(path):
    in_code = False
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE.match(line):
            in_code = not in_code
        if in_code:
            continue
        for m in LINK.finditer(line):
            target = m.group(1)
            if re.match(r"^([a-z][a-z0-9+.-]*:|#)", target, re.I):
                continue
            file_part = target.split("#", 1)[0].split("?", 1)[0]
            if file_part and not (path.parent / file_part).resolve().exists():
                yield n, target


def main():
    bad = 0
    for f in markdown_files():
        for n, target in broken_links(f):
            print(f"{f.relative_to(ROOT)}:{n}: lien cassé -> {target}")
            bad += 1
    print(f"{bad} lien(s) cassé(s)" if bad else "Liens relatifs OK")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
