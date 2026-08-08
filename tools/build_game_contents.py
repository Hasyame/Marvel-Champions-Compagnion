# -*- coding: utf-8 -*-
"""Builds docs/game_contents.json from a list of what the boxes contain.

The structure comes from the boxes, which is the whole point: MarvelCDB is
missing seventeen scenarios, and it cannot say which pack a difficulty arrived
in at all. Codes and French names are filled in from the MarvelCDB API where
they exist and left null where they do not, so the gaps are visible rather than
papered over with the English name.

Usage:
  python tools/build_game_contents.py
"""
import json
import io
import urllib.request

EN = "https://marvelcdb.com/api/public/cards/?encounter=1"
FR = "https://fr.marvelcdb.com/api/public/cards/?encounter=1"

# --- the boxes ---------------------------------------------------------------
# (name, code, type, scenarios, modular sets, difficulties the box adds)
# Names are English; the French comes from the API below.
WAVES = [
    (0, [
        ("Core Set", "core", "core",
         ["Rhino", "Klaw", "Ultron"],
         ["Bomb Scare", "Masters of Evil", "Under Attack", "Legions of Hydra",
          "The Doomsday Chair"],
         ["Standard I", "Expert I"]),
    ]),
    (1, [
        ("The Green Goblin", "gob", "scenario_pack",
         ["Risky Business", "Mutagen Formula"],
         ["Goblin Gimmicks", "A Mess of Things", "Power Drain",
          "Running Interference"],
         []),
        ("The Wrecking Crew", "twc", "scenario_pack",
         ["Wrecking Crew"], [], []),
        ("The Rise of Red Skull", "trors", "campaign_box",
         ["Crossbones", "Absorbing Man", "Taskmaster", "Zola", "Red Skull"],
         ["Hydra Assault", "Weapon Master", "Hydra Patrol"],
         []),
    ]),
    (2, [
        ("The Once and Future Kang", "toafk", "scenario_pack",
         ["Kang"],
         ["Temporal", "Anachronauts", "Master of Time"],
         []),
        ("Galaxy's Most Wanted", "gmw", "campaign_box",
         ["Brotherhood of Badoon", "Infiltrate the Museum", "Escape the Museum",
          "Nebula", "Ronan"],
         ["Band of Badoon", "Galactic Artifacts", "Kree Militants",
          "Menagerie Medley", "Space Pirates", "Badoon Headhunter",
          "Ship Command"],
         []),
    ]),
    (3, [
        ("The Mad Titan's Shadow", "mts", "campaign_box",
         ["Ebony Maw", "Tower Defense", "Thanos", "Hela", "Loki"],
         ["The Black Order", "Armies of Titan", "Children of Thanos",
          "Infinity Gauntlet", "Legions of Hel", "Frost Giants", "Enchantress"],
         []),
    ]),
    (4, [
        ("The Hood", "hood", "scenario_pack",
         ["The Hood"],
         ["Beasty Boys", "Brothers Grimm", "Mister Hyde", "Wrecking Crew",
          "Sinister Syndicate", "Crossfire's Crew", "Ransacked Armory",
          "State of Emergency", "Streets of Mayhem"],
         ["Standard II", "Expert II"]),
        ("Sinister Motives", "sm", "campaign_box",
         ["Sandman", "Venom", "Mysterio", "Sinister Six", "Venom Goblin"],
         ["City in Chaos", "Down to Earth", "Goblin Gear", "Guerilla Tactics",
          "Osborn Tech", "Personal Nightmare", "Sinister Assault",
          "Symbiotic Strength", "Whispers of Paranoia"],
         []),
    ]),
    (5, [
        ("Mutant Genesis", "mut_gen", "campaign_box",
         ["Sabretooth", "Project Wideawake", "Master Mold", "Mansion Attack",
          "Magneto"],
         ["Mystique", "Brotherhood", "Operation Zero Tolerance", "Sentinels",
          "Acolytes", "Future Past"],
         []),
    ]),
    (6, [
        ("MojoMania", "mojo", "scenario_pack",
         ["Magog", "Spiral", "Mojo"],
         ["Crime", "Fantasy", "Horror", "Sci-Fi", "Sitcom", "Western"],
         []),
        ("NeXt Evolution", "next_evol", "campaign_box",
         ["Morlock Siege", "On the Run", "Juggernaut", "Mister Sinister",
          "Stryfe"],
         [],
         []),
    ]),
    (7, [
        ("The Age of Apocalypse", "aoa", "campaign_box",
         ["Unus", "Four Horsemen", "Apocalypse", "Dark Beast", "En Sabah Nur"],
         ["Infinites", "Dystopian Nightmare", "Hounds", "Dark Riders",
          "Savage Land", "Genosha", "Blue Moon", "Celestial Tech",
          "Clan Akkaba", "Age of Apocalypse"],
         ["Standard III"]),
    ]),
    (8, [
        ("Agents of S.H.I.E.L.D.", "aos", "campaign_box",
         ["Black Widow", "Batroc", "M.O.D.O.K.", "Thunderbolts", "Baron Zemo"],
         ["A.I.M. Abduction", "A.I.M. Science", "Batroc's Brigade",
          "Scientist Supreme", "S.H.I.E.L.D."],
         []),
    ]),
    (9, [
        ("Trickster Takeover", "tt", "scenario_pack",
         ["Enchantress", "Loki, God of Lies"],
         ["Trickster Magic"],
         []),
        ("Civil War", "cw", "campaign_box",
         ["Resistance: Captain Marvel", "Resistance: Iron Man",
          "Resistance: Spider-Woman", "Resistance: Captain America",
          "Registration: Captain Marvel", "Registration: Iron Man",
          "Registration: Spider-Woman", "Registration: Captain America"],
         ["Dangerous Recruits", "Mighty Avengers", "Heroes For Hire", "Paladin",
          "Cape-Killer", "The Initiative", "Martial Law", "Maria Hill",
          "New Avengers", "Secret Avengers", "Namor", "Atlanteans",
          "Spider-Man", "Defenders", "Hell's Kitchen"],
         []),
    ]),
    (10, [
        ("She-Hulk", "shulk", "scenario_pack",
         ["Resistance: She-Hulk", "Registration: She-Hulk"],
         ["S.H.I.E.L.D. Ops", "Thunderbolts", "Taskmaster", "Deadly Duo",
          "Vision", "Young Avengers", "Scarlet Twins", "Moon Knight",
          "Royal Guard"],
         []),
        ("Fear No Evil", "fne", "campaign_box",
         ["The Getaway", "Protection Racket", "The Raft Breakout", "Kingpin"],
         ["Bullseye", "Electro"],
         []),
    ]),
    (11, [
        ("Shadowland", "shadowland", "scenario_pack",
         ["Shadows in the Night", "Shadow Labyrinth", "Heart of Shadow"],
         [],
         []),
    ]),
]

# Civil War and She-Hulk share one restricted modular pool and draw more sets
# than anything else does. The randomiser does not know either rule yet.
SPECIAL = {
    "cw": {"modularCount": [3, 4], "modularsRestrictedTo": ["cw", "shulk"]},
    "shulk": {"modularCount": [3, 4], "modularsRestrictedTo": ["cw", "shulk"]},
}


# Where the box and MarvelCDB spell the same scenario differently. Kept
# explicit rather than fuzzy-matched: a near-miss matcher would happily bind
# "Sinister Six" to "Mister Sinister", and a wrong code is worse than a gap.
ALIASES = {
    "ronan": "ronan the accuser",
    "sinister six": "the sinister six",
    "m.o.d.o.k.": "m.o.d.o.k",
    "loki, god of lies": "god of lies",
}


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode("utf-8"))


def english_index(cards, kind):
    """Lowercased set name to (code, name), for one card set type."""
    out = {}
    for card in cards:
        if card.get("card_set_type_name_code") != kind:
            continue
        code, name = card.get("card_set_code"), card.get("card_set_name")
        if code and name:
            out[name.strip().lower()] = (code, name)
    return out


def french_names(cards, kind):
    out = {}
    for card in cards:
        if card.get("card_set_type_name_code") == kind and card.get("card_set_code"):
            out[card["card_set_code"]] = card.get("card_set_name")
    return out


def main():
    en_cards, fr_cards = fetch(EN), fetch(FR)
    en_villain = english_index(en_cards, "villain")
    en_modular = english_index(en_cards, "modular")
    fr_villain = french_names(fr_cards, "villain")
    fr_modular = french_names(fr_cards, "modular")

    def resolve(name, index, french):
        key = name.strip().lower()
        hit = index.get(ALIASES.get(key, key)) or index.get(key)
        if not hit:
            return {"en": name, "code": None, "fr": None}
        code, en_name = hit
        translated = french.get(code)
        return {
            "en": en_name,
            "code": code,
            # null rather than the English name when MarvelCDB has not
            # translated it: a gap you can see is a gap somebody can fill.
            "fr": translated if translated and translated != en_name else None,
        }

    waves = []
    for wave, packs in WAVES:
        entries = []
        for name, code, kind, scenarios, modulars, difficulties in packs:
            entry = {
                "name": name,
                "code": code,
                "type": kind,
                "scenarios": [resolve(s, en_villain, fr_villain) for s in scenarios],
                "modularSets": [resolve(m, en_modular, fr_modular) for m in modulars],
                "difficultiesAdded": difficulties,
            }
            if code in SPECIAL:
                entry["special"] = SPECIAL[code]
            entries.append(entry)
        waves.append({"wave": wave, "packs": entries})

    document = {
        "_note": (
            "What the boxes contain, listed by somebody holding them. The structure is "
            "authoritative. Codes and French names come from MarvelCDB and are null where "
            "MarvelCDB has nothing, which is how you find what still needs translating or "
            "entering. Regenerate with tools/build_game_contents.py."
        ),
        "generatedFrom": [EN, FR],
        "waves": waves,
    }

    with io.open("docs/game_contents.json", "w", encoding="utf-8", newline="\n") as out:
        json.dump(document, out, ensure_ascii=False, indent=2)
        out.write("\n")

    scenarios = [s for w in waves for p in w["packs"] for s in p["scenarios"]]
    modulars = [m for w in waves for p in w["packs"] for m in p["modularSets"]]
    print(
        "scenarios:", len(scenarios),
        "| not in MarvelCDB:", sum(1 for s in scenarios if not s["code"]),
        "| no French name:", sum(1 for s in scenarios if not s["fr"]),
    )
    print(
        "modular sets:", len(modulars),
        "| not in MarvelCDB:", sum(1 for m in modulars if not m["code"]),
        "| no French name:", sum(1 for m in modulars if not m["fr"]),
    )
    print("difficulties by pack:", {
        d: p["code"] for w in waves for p in w["packs"] for d in p["difficultiesAdded"]
    })


main()
