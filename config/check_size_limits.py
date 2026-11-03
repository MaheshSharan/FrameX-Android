#!/usr/bin/env python3
"""AGENTS.md size gate: functions <= 60 lines, files <= 500 lines.

Existing offenders are listed in config/size-baseline.txt and tolerated; anything new fails.
Run with --update to rewrite the baseline after reducing offenders.
"""
import os
import re
import sys

ROOT = "app/src/main/java"
BASELINE = "config/size-baseline.txt"
MAX_FUN, MAX_FILE = 60, 500
FUN = re.compile(r"(\s*)(?:(?:private|internal|public|override|suspend|inline|open)\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w.<>?]+\.)?(\w+)")


def offenders():
    found = set()
    for root, _, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(root, name)
            lines = open(path, encoding="utf-8").read().split("\n")
            rel = path.split("com/framex/app/")[-1]
            if len(lines) > MAX_FILE:
                found.add(f"file {rel}")
            for i, line in enumerate(lines):
                m = FUN.match(line)
                if not m or "@Composable" in line or (i > 0 and "@Composable" in lines[i - 1]):
                    continue
                j = i
                while j < len(lines) and not re.search(r"(\{|=)\s*$", lines[j]) and not re.search(r"\)\s*(:\s*[^={]+)?\s*[={]", lines[j]):
                    j += 1
                if j >= len(lines) or not lines[j].rstrip().endswith("{"):
                    continue
                k = j + 1
                while k < len(lines) and not (lines[k].startswith(m.group(1) + "}") and len(lines[k].rstrip()) == len(m.group(1)) + 1):
                    k += 1
                if k - i + 1 > MAX_FUN:
                    found.add(f"fun {rel} {m.group(2)}")
    return found


def main():
    current = offenders()
    if "--update" in sys.argv:
        open(BASELINE, "w").write("\n".join(sorted(current)) + "\n")
        return 0
    allowed = set(open(BASELINE).read().split("\n")) if os.path.exists(BASELINE) else set()
    new = sorted(current - allowed)
    for item in new:
        print(f"over the size limit: {item}", file=sys.stderr)
    return 1 if new else 0


sys.exit(main())
