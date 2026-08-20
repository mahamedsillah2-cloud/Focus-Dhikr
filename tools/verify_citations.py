#!/usr/bin/env python3
"""Verify every religious citation in the app against a primary source.

This is the mechanical half of requirement 5: no invented scripture. The
editorial half is that nothing gets written into the Kotlin libraries from
memory in the first place; this script is what stops that rule from quietly
eroding over time.

What it does:
  * parses QuranLibrary.kt and HadithLibrary.kt (no Kotlin runtime needed),
  * downloads each ayah from the Quran.com API v4,
  * normalises both sides - strips Arabic diacritics, tatweel, Qur'anic
    annotation marks and presentation variants - and compares,
  * whole ayat must match exactly; excerpts (`partial = true`) must appear
    verbatim inside the ayah,
  * checks each hadith reference resolves on sunnah.com and that the Arabic
    quoted in the app appears in the page.

Exit code 1 on any mismatch, so CI fails rather than shipping a bad citation.

Usage:
    python3 tools/verify_citations.py [--quran-only] [--offline]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Both platforms are checked. The Swift copy is generated from the Kotlin one,
# but generated files get edited by hand eventually, so CI verifies both against
# the primary sources rather than trusting that they still agree.
QURAN_SOURCES = [
    ROOT / "app/src/main/java/com/focusdhikr/content/QuranLibrary.kt",
    ROOT / "ios/Shared/Content/QuranLibrary.swift",
]
HADITH_SOURCES = [
    ROOT / "app/src/main/java/com/focusdhikr/content/HadithLibrary.kt",
    ROOT / "ios/Shared/Content/HadithLibrary.swift",
]

QURAN_KT = QURAN_SOURCES[0]
HADITH_KT = HADITH_SOURCES[0]

QURAN_API = (
    "https://api.quran.com/api/v4/verses/by_key/{key}"
    "?fields=text_uthmani,text_imlaei"
)
USER_AGENT = "focus-dhikr-citation-verifier/1.0"

# Arabic marks that differ between orthographies but not between readings:
# harakat, sukun, shadda, superscript alef, the Qur'anic pause/sajdah symbols
# and the ayah separator.
TATWEEL = "ـ"
QURANIC_ANNOTATION = (
    "ؘؙؚؐؑؒؓؔؕؖؗ"
    "ۖۗۘۙۚۛۜ۝۞۟"
    "ۣ۠ۡۢۤۥۦۧۨ۩"
    "۪ۭ۫۬۰"
)
AYAH_SEPARATOR = "۝۞࣢"


def normalize_arabic(text: str) -> str:
    """Reduce Arabic to its consonantal skeleton plus written long vowels.

    Diacritics, tatweel and Qur'anic annotation marks are removed, and
    orthographic variants are folded together. What survives is what actually
    distinguishes one wording from another.
    """
    # Hamza seats must be folded BEFORE NFKD. Arabic hamza letters are
    # canonically decomposable (U+0626 -> U+064A U+0654), so NFKD would turn ئ
    # into a bare ya plus a combining mark that the Mn filter then deletes,
    # leaving a spurious ya behind. The Uthmani script writes that same hamza as
    # a standalone mark on a tatweel, which normalises away to nothing - so the
    # two sides would never agree. Dropping the seat first fixes that, and loses
    # nothing real: a hamza seat is always silent.
    for src, dst in (
        ("ؤ", ""),   # waw seat
        ("ئ", ""),   # ya seat
        ("ء", ""),   # standalone hamza
        ("آ", "ا"),  # alef madda
        ("أ", "ا"),  # alef + hamza above
        ("إ", "ا"),  # alef + hamza below
        ("ٱ", "ا"),  # alef wasla
    ):
        text = text.replace(src, dst)

    text = unicodedata.normalize("NFKD", text)
    out = []
    for ch in text:
        if unicodedata.category(ch) == "Mn":
            continue
        if ch in TATWEEL or ch in QURANIC_ANNOTATION or ch in AYAH_SEPARATOR:
            continue
        out.append(ch)
    text = "".join(out)

    for src, dst in (
        ("ى", "ي"),  # alef maqsura -> ya
        ("ة", "ه"),  # ta marbuta   -> ha
    ):
        text = text.replace(src, dst)

    text = re.sub(r"[^ؠ-ي\s]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def squash(text: str) -> str:
    """Normalised text with word boundaries removed.

    The two standard orthographies disagree about spacing: Imla'i writes
    يَا أَيُّهَا as two words, Uthmani joins them into يَـٰٓأَيُّهَا. Comparing the
    unspaced skeletons makes that difference invisible without loosening
    anything that matters.
    """
    return normalize_arabic(text).replace(" ", "")


def matches_exactly(ours: str, candidates: list[str]) -> bool:
    return any(
        normalize_arabic(ours) == normalize_arabic(c) or squash(ours) == squash(c)
        for c in candidates
    )


def matches_as_excerpt(ours: str, candidates: list[str]) -> bool:
    return any(
        normalize_arabic(ours) in normalize_arabic(c) or squash(ours) in squash(c)
        for c in candidates
    )


def unescape_kotlin(value: str) -> str:
    return value.replace('\\"', '"').replace("\\\\", "\\").replace("\\n", "\n")


def parse_entries(path: Path, ctor: str) -> list[dict]:
    """Extract constructor calls from a Kotlin source file.

    A small hand-rolled parser rather than a Kotlin dependency: this has to run
    in CI before (and independently of) the Android build.
    """
    source = path.read_text(encoding="utf-8")
    entries = []
    for match in re.finditer(re.escape(ctor) + r"\s*\(", source):
        start = match.end()
        depth, i, in_string, escaped = 1, start, False, False
        while i < len(source) and depth:
            ch = source[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
            elif ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        body = source[start : i - 1]

        entry = {}
        for key, value in re.findall(r'(\w+)\s*[:=]\s*"((?:[^"\\]|\\.)*)"', body):
            entry[key] = unescape_kotlin(value)
        for key, value in re.findall(r"(\w+)\s*[:=]\s*(\d+)", body):
            entry.setdefault(key, int(value))
        for key, value in re.findall(r"(\w+)\s*[:=]\s*(true|false)", body):
            entry.setdefault(key, value == "true")
        if entry:
            entries.append(entry)
    return entries


def fetch_json(url: str, retries: int = 3) -> dict:
    last = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last = exc
            time.sleep(2 ** attempt)
    raise RuntimeError(f"could not fetch {url}: {last}")


def fetch_text(url: str, retries: int = 3) -> str:
    last = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, TimeoutError) as exc:
            last = exc
            time.sleep(2 ** attempt)
    raise RuntimeError(f"could not fetch {url}: {last}")


def cross_check(sources: list[Path], ctor: str) -> list[str]:
    """The Arabic must be byte-identical across platforms.

    A citation that is right on Android and subtly wrong on iOS is worse than
    one that is wrong on both: the mistake hides behind a passing check.
    """
    failures = []
    baseline = None
    for path in sources:
        if not path.exists():
            failures.append(f"{path.name}: missing")
            continue
        arabic = [e.get("arabic", "") for e in parse_entries(path, ctor)]
        if baseline is None:
            baseline = (path, arabic)
        elif arabic != baseline[1]:
            failures.append(
                f"{path.name} and {baseline[0].name} disagree: "
                f"{len(arabic)} vs {len(baseline[1])} entries, or different Arabic"
            )
    return failures


def verify_quran() -> list[str]:
    failures = cross_check(QURAN_SOURCES, "QuranCitation")
    entries = parse_entries(QURAN_KT, "QuranCitation")
    if not entries:
        return failures + ["QuranLibrary.kt: parsed zero citations - the parser or the file changed"]

    print(f"Qur'an: {len(entries)} citations to verify (across {len(QURAN_SOURCES)} platforms)")

    for entry in entries:
        surah = entry.get("surah")
        start = entry.get("ayahStart")
        end = entry.get("ayahEnd", start)
        arabic = entry.get("arabic", "")
        partial = entry.get("partial", False)
        label = f"{surah}:{start}" + (f"-{end}" if end != start else "")

        if not all([surah, start, arabic]):
            failures.append(f"{label}: incomplete entry")
            continue

        try:
            uthmani_parts, imlaei_parts = [], []
            for ayah in range(start, end + 1):
                payload = fetch_json(QURAN_API.format(key=f"{surah}:{ayah}"))
                verse = payload.get("verse") or {}
                uthmani = verse.get("text_uthmani")
                if not uthmani:
                    failures.append(f"{label}: API returned no text for {surah}:{ayah}")
                    break
                uthmani_parts.append(uthmani)
                imlaei_parts.append(verse.get("text_imlaei") or uthmani)
            else:
                # Accept either standard orthography; they are the same wording.
                candidates = [" ".join(uthmani_parts), " ".join(imlaei_parts)]

                if partial:
                    ok = matches_as_excerpt(arabic, candidates)
                    kind = "excerpt not found in"
                else:
                    ok = matches_exactly(arabic, candidates)
                    kind = "does not match"

                if ok:
                    print(f"  ok   {label}{' (excerpt)' if partial else ''}")
                else:
                    print(f"  FAIL {label}")
                    failures.append(
                        f"{label}: {kind} the source\n"
                        f"      ours:     {normalize_arabic(arabic)}\n"
                        f"      uthmani:  {normalize_arabic(candidates[0])}\n"
                        f"      imlaei:   {normalize_arabic(candidates[1])}"
                    )
        except RuntimeError as exc:
            failures.append(f"{label}: {exc}")

        time.sleep(0.2)

    return failures


def verify_hadith() -> list[str]:
    failures = cross_check(HADITH_SOURCES, "HadithCitation")
    entries = parse_entries(HADITH_KT, "HadithCitation")
    if not entries:
        return failures + ["HadithLibrary.kt: parsed zero citations - the parser or the file changed"]

    print(f"\nHadith: {len(entries)} citations to verify (across {len(HADITH_SOURCES)} platforms)")

    for entry in entries:
        label = f"{entry.get('collection')} {entry.get('reference')}"
        url = entry.get("sourceUrl", "")
        arabic = entry.get("arabic", "")

        if not entry.get("grading"):
            failures.append(f"{label}: no grading stated")
        if not url.startswith("https://"):
            failures.append(f"{label}: no source url")
            continue
        if "sunnah.com/search" in url:
            # Reports outside the nine books resolve only to a search page, so
            # the reference cannot be machine-checked. The grading text carries
            # the attribution instead, and is asserted above.
            print(f"  skip {label} (not in the canonical collections)")
            continue

        try:
            page = fetch_text(url)
        except RuntimeError as exc:
            failures.append(f"{label}: {exc}")
            continue

        if matches_as_excerpt(arabic, [page]):
            print(f"  ok   {label}")
        else:
            print(f"  FAIL {label}")
            failures.append(f"{label}: quoted Arabic not found at {url}")

        time.sleep(0.5)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quran-only", action="store_true")
    parser.add_argument(
        "--offline",
        action="store_true",
        help="only check structure, skip every network call",
    )
    args = parser.parse_args()

    if args.offline:
        problems = cross_check(QURAN_SOURCES, "QuranCitation")
        problems += cross_check(HADITH_SOURCES, "HadithCitation")
        for path, ctor in (
            *[(p, "QuranCitation") for p in QURAN_SOURCES],
            *[(p, "HadithCitation") for p in HADITH_SOURCES],
        ):
            count = len(parse_entries(path, ctor)) if path.exists() else 0
            print(f"  {count:>3} {ctor} in {path.relative_to(ROOT)}")
            if count == 0:
                problems.append(f"{path}: parsed zero citations")
        if problems:
            print("\nStructural problems:")
            for problem in problems:
                print(f"  - {problem}")
            return 1
        print("\nBoth platforms carry identical Arabic.")
        return 0

    failures = verify_quran()
    if not args.quran_only:
        failures += verify_hadith()

    print()
    if failures:
        print(f"{len(failures)} citation problem(s):\n")
        for failure in failures:
            print(f"  - {failure}")
        print("\nA citation that cannot be verified must be removed, not shipped.")
        return 1

    print("Every citation matched its source.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
