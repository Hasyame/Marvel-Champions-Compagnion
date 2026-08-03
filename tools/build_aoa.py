# -*- coding: utf-8 -*-
"""Builds the Age of Apocalypse campaign template.

Written as a script rather than by hand so the repetitive parts — the mission
and overseer setup that every scenario shares — are written once and cannot
drift between scenarios.
"""
import json, io

MISSIONS = ["45166a", "45167a", "45168a", "45169a"]      # 1/5 - 4/5
PROTECT_THE_PROFESSOR = "45170a"                          # 5/5, scenario 5 only
OVERSEERS = ["45179a", "45180a", "45181a", "45182a", "45183a"]
AOA_MODULAR = ["45164", "45165"]                          # Age of Apocalypse set
MISSION_TEAM = "45171a"
PURSUED_BY_THE_PAST = "45075a"                            # Standard III permanent


def t(fr, en):
    return {"fr": fr, "en": en}


def standard_iii_step():
    return {
        "text": t(
            "Choisir Standard I ou Standard III (identique pour tous les scénarios)",
            "Choose Standard I or Standard III (same for every scenario)",
        ),
    }


def mission_steps(scenario_number):
    """The side-mission setup, identical for scenarios 1 to 4."""
    return [
        {
            "text": t(
                "Mélanger le set modulaire Age of Apocalypse dans le deck rencontre",
                "Shuffle the Age of Apocalypse modular set into the encounter deck",
            ),
        },
        {
            "text": t(
                "Jouer avec cette mission et suivre sa mise en place dans le journal de campagne",
                "Play with this MISSION side scheme and follow its setup in the campaign log",
            ),
            "draw": {"id": "mission", "from": MISSIONS, "excluding": "missionsUsed"},
        },
        {
            "text": t(
                "Ajouter cet Overseer à la zone de mission et poser la carte Mission Rules à côté",
                "Add this OVERSEER minion to the mission area and put Mission Rules beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend le contrôle de ce soutien, face MISSION visible",
                "First player takes control of this support card, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et le prend en main",
                "Each player searches their deck for an ally and takes it into hand",
            ),
        },
    ]


def expert_steps(first_scenario=False):
    """Hit points carried between scenarios, on an Expert campaign only."""
    steps = []
    if not first_scenario:
        steps.append({
            "text": t(
                "Expert : régler les points de vie de chaque héros sur la valeur ci-dessous",
                "Expert: set each hero's hit points to the value below",
            ),
            "when": {"difficulty": "expert"},
            "showCounter": "hp",
        })
        steps.append({
            "text": t(
                "Expert : placer 3 menaces sur la mission pour soigner un héros à fond",
                "Expert: place 3 threat on the mission to heal a hero to full",
            ),
            "when": {"difficulty": "expert"},
            "action": {
                "id": "heal",
                "label": t("Soigner", "Heal"),
                "perHero": True,
                "effects": [{"op": "setHeroCounter", "counter": "hp", "value": 999}],
            },
        })
    return steps


def mission_outcome_prompts(scenario_number):
    """What the campaign log needs recorded after scenarios 1 to 4.

    The app already knows which mission and overseer were in play — it drew
    them — so it only asks what it cannot know. Whether the mission fell is
    recorded for the campaign summary; its reward and its penalty are both
    printed on the back of the card and resolve inside the scenario, so nothing
    carries forward from it.
    """
    return [
        {
            "id": "missionDefeated",
            "type": "boolean",
            "label": t(
                "La mission a-t-elle été vaincue ?",
                "Was the MISSION side scheme defeated?",
            ),
        },
        {
            "id": "overseerDefeated",
            "type": "boolean",
            "label": t(
                "L'Overseer a-t-il été vaincu ?",
                "Was the OVERSEER minion defeated?",
            ),
        },
        {
            "id": "hpPerHero",
            "type": "perHeroNumber",
            "label": t("Points de vie restants", "Remaining hit points"),
            "when": {"difficulty": "expert"},
        },
    ]


def mission_outcome_effects():
    return [
        # Struck whether or not it was defeated: a mission that was attempted is
        # spent. Whether it fell only decides which of its two backs resolves,
        # and that happens inside the scenario.
        {"op": "addDrawnCard", "cardList": "missionsUsed", "from": "mission"},
        # An overseer is struck only when it fell, so one that survived stays in
        # the pool for the next scenario.
        {
            "op": "addDrawnCard",
            "cardList": "overseersDefeated",
            "from": "overseer",
            "when": {"answer": "overseerDefeated"},
        },
        {
            "op": "setHeroCounter",
            "counter": "hp",
            "from": "hpPerHero",
            "when": {"difficulty": "expert"},
        },
    ]


scenarios = []

# ---------------------------------------------------------------- scenario 1
scenarios.append({
    "id": "s1_unus",
    "name": t("Unus", "Unus"),
    "flavour": t(
        "X-Force est bloquée dans une ligne temporelle où Apocalypse règne, et le prélat Unus "
        "les a déjà trouvés.",
        "X-Force is stranded in a timeline ruled by Apocalypse, and the prelate Unus has "
        "already found them.",
    ),
    "victoryLabel": t("Unus est vaincu !", "Unus is beaten!"),
    "defeatLabel": t("Unus vous a vaincus !", "Unus has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45059", "45060"], "expert": ["45060", "45061"]},
        "mainScheme": ["45062a"],
        "encounterSets": ["unus", "infinites", "dystopian_nightmare", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Mettre Gene Pool en jeu et y placer la menace de la difficulté choisie :\n"
                "  Escarmouche : 0\n"
                "  Standard : 1 par joueur\n"
                "  Expert : 2 par joueur\n"
                "  Héroïque : 3 par joueur",
                "Put Gene Pool into play and place the threat for your difficulty on it:\n"
                "  Skirmish: 0\n"
                "  Standard: 1 per player\n"
                "  Expert: 2 per player\n"
                "  Heroic: 3 per player",
            ),
            "cards": ["45071"],
        },
    ] + [{"include": "missions"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(1),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s2_four_horsemen"}],
    },
    "onDefeat": {"next": [{"goto": "s1_unus"}]},
})

# ---------------------------------------------------------------- scenario 2
scenarios.append({
    "id": "s2_four_horsemen",
    "name": t("Les Quatre Cavaliers", "Four Horsemen"),
    "flavour": t(
        "Magneto accueille X-Force au quartier général des X-Men. Apocalypse le trouve aussitôt "
        "et lance ses quatre cavaliers.",
        "Magneto takes X-Force into the X-Men's headquarters. Apocalypse finds it at once and "
        "sends his four horsemen.",
    ),
    "victoryLabel": t("Les cavaliers sont vaincus !", "The horsemen are beaten!"),
    "defeatLabel": t("Les cavaliers vous ont vaincus !", "The horsemen have beaten you!"),
    "baseSetup": {
        # One card each: side A for Skirmish and Standard, side B for Expert and
        # Heroic, so the codes do not change with difficulty.
        "villainDeck": {
            "standard": ["45081a", "45082a", "45083a", "45084a"],
            "expert": ["45081a", "45082a", "45083a", "45084a"],
        },
        "mainScheme": ["45085a"],
        "encounterSets": ["four_horsemen", "hounds", "dystopian_nightmare", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Aligner ces quatre vilains dans cet ordre, chacun avec son compteur de points de vie",
                "Set these four villains in a row in this order, each with its own hit point dial",
            ),
            "draw": {
                "id": "horsemen",
                "from": ["45081a", "45082a", "45083a", "45084a"],
                "count": 4,
            },
        },
        {
            "text": t(
                "Face A en Escarmouche et Standard, face B en Expert et Héroïque",
                "Use side A for Skirmish and Standard, side B for Expert and Heroic",
            ),
        },
        {
            "text": t(
                "Donner le marqueur d'activation au vilain le plus à gauche",
                "Give the active counter to the leftmost villain",
            ),
        },
    ] + [{"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(2),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s3_apocalypse"}],
    },
    "onDefeat": {"next": [{"goto": "s2_four_horsemen"}]},
})

# ---------------------------------------------------------------- scenario 3
scenarios.append({
    "id": "s3_apocalypse",
    "name": t("Apocalypse", "Apocalypse"),
    "flavour": t(
        "Les X-Men marchent sur la citadelle d'Apocalypse, à travers le cœur de son empire et "
        "ses prélats les plus solides.",
        "The X-Men march on Apocalypse's citadel, through the heart of his empire and his "
        "strongest prelates.",
    ),
    "victoryLabel": t("Apocalypse est vaincu !", "Apocalypse is beaten!"),
    "defeatLabel": t("Apocalypse vous a vaincus !", "Apocalypse has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45101a", "45102a"], "expert": ["45102a"]},
        "mainScheme": ["45103a"],
        "encounterSets": [
            "apocalypse", "overseer", "dark_riders", "infinites", "standard",
        ],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Commencer Apocalypse sur la face (II) — face (I) pour une partie plus facile",
                "Start Apocalypse on side (II) — side (I) for an easier game",
            ),
            "cards": ["45101a"],
        },
    ] + [{"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(3),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s4_dark_beast"}],
    },
    "onDefeat": {"next": [{"goto": "s3_apocalypse"}]},
})

# ---------------------------------------------------------------- scenario 4
scenarios.append({
    "id": "s4_dark_beast",
    "name": t("Dark Beast", "Dark Beast"),
    "flavour": t(
        "Sous la citadelle se trouve une machine à voyager dans le temps, et le Fauve qui la "
        "manœuvre n'est pas le leur.",
        "Beneath the citadel is a time machine, and the Beast operating it is not theirs.",
    ),
    "victoryLabel": t("Dark Beast est vaincu !", "Dark Beast is beaten!"),
    "defeatLabel": t("Dark Beast vous a vaincus !", "Dark Beast has beaten you!"),
    "baseSetup": {
        "villainDeck": {"standard": ["45118", "45119"], "expert": ["45119", "45120"]},
        "mainScheme": ["45121a"],
        "encounterSets": [
            "dark_beast", "blue_moon", "genosha", "savage_land",
            "dystopian_nightmare", "standard",
        ],
    },
    "campaignSetup": [{"include": "standardIII"}, {"include": "missions"}, {"include": "expertHp"}],
    "onVictory": {
        "prompts": mission_outcome_prompts(4),
        "effects": mission_outcome_effects(),
        "next": [{"goto": "s5_en_sabah_nur"}],
    },
    "onDefeat": {"next": [{"goto": "s4_dark_beast"}]},
})

# ---------------------------------------------------------------- scenario 5
scenarios.append({
    "id": "s5_en_sabah_nur",
    "name": t("En Sabah Nur", "En Sabah Nur"),
    "flavour": t(
        "De retour dans leur passé, l'équipe doit arracher à Apocalypse l'antidote qui sauvera "
        "le Professeur X.",
        "Back in their own past, the team must take from Apocalypse the antidote that will save "
        "Professor X.",
    ),
    "victoryLabel": t("Apocalypse est vaincu !", "Apocalypse is beaten!"),
    "defeatLabel": t("Apocalypse vous a vaincus !", "Apocalypse has beaten you!"),
    "baseSetup": {
        # The three-sided villain: BIOMORPH is the starting form.
        "villainDeck": {"standard": ["45184a", "45185a"], "expert": ["45185a", "45186a"]},
        "mainScheme": ["45147a", "45148a"],
        "encounterSets": ["en_sabah_nur", "celestial_tech", "clan_akkaba", "standard"],
    },
    "campaignSetup": [
        {"include": "standardIII"},
        {
            "text": t(
                "Mettre Ancient Ritual en jeu",
                "Put Ancient Ritual into play",
            ),
            "cards": ["45163"],
        },
        {
            "text": t(
                "Commencer Apocalypse sur sa face BIOMORPH",
                "Start Apocalypse on his BIOMORPH side",
            ),
            "cards": ["45184a"],
        },
        {
            "text": t(
                "Le Professeur X ne peut pas entrer en jeu cette partie",
                "Professor X cannot enter play this game",
            ),
        },
        {
            "text": t(
                "Mélanger le set modulaire Age of Apocalypse dans le deck rencontre",
                "Shuffle the Age of Apocalypse modular set into the encounter deck",
            ),
        },
        {
            "text": t(
                "Jouer avec cette mission et suivre sa mise en place dans le journal de campagne",
                "Play with this MISSION side scheme and follow its setup in the campaign log",
            ),
            "cards": [PROTECT_THE_PROFESSOR],
        },
        {
            "text": t(
                "Ajouter cet Overseer à la zone de mission et poser la carte Mission Rules à côté",
                "Add this OVERSEER minion to the mission area and put Mission Rules beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend le contrôle de ce soutien, face MISSION visible",
                "First player takes control of this support card, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et le prend en main",
                "Each player searches their deck for an ally and takes it into hand",
            ),
        },
    ] + [{"include": "expertHp"}],
    "onVictory": {
        "prompts": [
            {
                "id": "professorSaved",
                "type": "boolean",
                "label": t(
                    "Protect the Professor a-t-elle été vaincue ?",
                    "Was Protect the Professor defeated?",
                ),
            },
        ],
        "effects": [
            {"op": "setFlag", "flag": "professorSaved", "from": "professorSaved"},
        ],
        "next": [{"end": True}],
    },
    "onDefeat": {"next": [{"goto": "s5_en_sabah_nur"}]},
})

template = {
    "_note": (
        "Mechanics only, written for this app: no rules text and no text from the campaign book. "
        "Card codes are MarvelCDB codes for pack 'aoa'; names are resolved from the card database "
        "at runtime so they appear in the player's language."
    ),
    "id": "aoa",
    "schemaVersion": 1,
    "name": t("L'Ère d'Apocalypse", "Age of Apocalypse"),
    "packCode": "aoa",
    "difficulties": ["standard", "expert"],
    "counters": [
        {
            "id": "hp",
            "scope": "hero",
            "initial": 0,
            "maxFrom": "heroCard.health",
            "activeWhen": {"difficulty": "expert"},
        },
    ],
    "flagSets": [{"id": "professorSaved", "scope": "campaign"}],
    "cardLists": [
        {"id": "missionsUsed", "scope": "campaign"},
        {"id": "overseersDefeated", "scope": "campaign"},
    ],
    # Written once and included where they belong: the side-mission setup is
    # word for word the same in five scenarios, and five copies of it would be
    # five things to keep in step.
    "setupFragments": {
        "standardIII": [standard_iii_step()],
        "missions": mission_steps(0),
        "expertHp": expert_steps(),
    },
    "startScenarioId": "s1_unus",
    "scenarios": scenarios,
}

with io.open("app/src/main/assets/campaigns/aoa.json", "w", encoding="utf-8") as f:
    json.dump(template, f, ensure_ascii=False, indent=1)
    f.write("\n")

print("scenarios:", len(scenarios))
for s in scenarios:
    print(" ", s["id"], "| setup steps:", len(s["campaignSetup"]),
          "| prompts:", len(s["onVictory"].get("prompts", [])))
