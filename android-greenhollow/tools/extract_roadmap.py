#!/usr/bin/env python3
"""Writes docs/EMBERFALL_ROADMAP.md from the roadmap message in the chat history.

Read-only on .rork/history. Picture links are dropped and blank runs collapsed.
Run from the project root: python3 android-greenhollow/tools/extract_roadmap.py
"""
import glob, json, re

def texts(o):
    if isinstance(o, str):
        yield o
    elif isinstance(o, dict):
        for v in o.values():
            yield from texts(v)
    elif isinstance(o, list):
        for v in o:
            yield from texts(v)

best = ""
for f in sorted(glob.glob(".rork/history/main/*_user.json")):
    for t in texts(json.load(open(f))):
        if "Project Emberfall" in t and len(t) > len(best):
            best = t
if not best:
    raise SystemExit("Roadmap message not found")
lines = [l.rstrip() for l in best.split("\n") if not l.lstrip().startswith("![")]
body = re.sub(r"\n{2,}", "\n\n", "\n".join(lines)).strip()
body = re.sub(r"(\n\* [^\n]*)\n\n(?=\* )", r"\1\n", body)
body = re.sub(r"(\n\|[^\n]*)\n\n(?=\|)", r"\1\n", body)
head = (
    "<!-- Reference copy of the Project Emberfall roadmap as written by the project owner.\n"
    "     The app keeps the name Greenhollow; Emberfall is the codename.\n"
    "     Status: Phases 1 and 2 built (see .rork/plans). Later phases not started. -->\n\n"
)
open("docs/EMBERFALL_ROADMAP.md", "w").write(head + body + "\n")
print(len(body), "characters written")
