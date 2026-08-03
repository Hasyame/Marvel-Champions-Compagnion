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
            "Le Standard III peut remplacer le Standard : la trahison Shadow of the Past "
            "cède la place à cet environnement permanent",
            "Standard III may replace Standard: the Shadow of the Past treachery gives way "
            "to this permanent environment",
        ),
        "cards": [PURSUED_BY_THE_PAST],
    }


def mission_steps(scenario_number):
    """The side-mission setup, identical for scenarios 1 to 4."""
    return [
        {
            "text": t(
                "Mélanger le set Age of Apocalypse dans le deck rencontre",
                "Shuffle the Age of Apocalypse set into the encounter deck",
            ),
            "cards": AOA_MODULAR,
        },
        {
            "text": t(
                "Révéler cette mission dans la zone de mission (tirée parmi les disponibles)",
                "Reveal this MISSION side scheme in the mission area (drawn from those available)",
            ),
            "draw": {"id": "mission", "from": MISSIONS, "excluding": "missionsUsed"},
        },
        {
            "text": t(
                "Placer cet Overseer dans la zone de mission, puis y poser la carte Mission Rules",
                "Put this OVERSEER minion in the mission area, then put the Mission Rules card "
                "into play beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend ce soutien, face MISSION visible",
                "The first player takes this support, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et l'ajoute à sa main "
                "(il compte dans la taille de main)",
                "Each player searches their deck for an ally and adds it to their hand "
                "(it counts towards hand size)",
            ),
        },
    ]


def expert_steps(first_scenario=False):
    """Hit points carried between scenarios, on an Expert campaign only."""
    steps = []
    if not first_scenario:
        steps.append({
            "text": t(
                "Campagne Expert : reprendre les points de vie enregistrés au scénario précédent",
                "Expert campaign: set each hit point total to the value recorded last scenario",
            ),
            "when": {"difficulty": "expert"},
            "showCounter": "hp",
        })
        steps.append({
            "text": t(
                "Campagne Expert : placer 3 menaces sur la mission pour revenir aux points de vie imprimés",
                "Expert campaign: place 3 threat on the MISSION side scheme to heal to printed hit points",
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
                "Gene Pool commence en jeu et ne peut pas la quitter. Unus et l'Infinite Soldier "
                "changent de force selon la menace qui s'y trouve",
                "Gene Pool starts in play and cannot leave it. Unus and the Infinite Soldier get "
                "stronger or weaker with the threat on it",
            ),
            "cards": ["45071"],
        },
        {
            "text": t(
                "Difficulté ajustable : menace par joueur sur Gene Pool — 0 Escarmouche, "
                "1 Standard, 2 Expert, 3 Héroïque",
                "Adjustable difficulty: threat per player on Gene Pool — 0 Skirmish, "
                "1 Standard, 2 Expert, 3 Heroic",
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
                "Révéler les quatre vilains dans un ordre aléatoire et les aligner, chacun avec "
                "son propre compteur de points de vie",
                "Reveal all four villains in random order and set them in a row, each with its own "
                "hit point dial",
            ),
            "cards": ["45081a", "45082a", "45083a", "45084a"],
        },
        {
            "text": t(
                "Face A en Escarmouche et Standard, face B en Expert et Héroïque — un mélange des "
                "deux est permis",
                "Side A on Skirmish and Standard, side B on Expert and Heroic — a mix of the two "
                "is allowed",
            ),
        },
        {
            "text": t(
                "Donner le marqueur d'activation au vilain le plus à gauche : lui seul s'active, "
                "puis le marqueur passe au suivant vers la droite",
                "Give the active counter to the leftmost villain: only it activates, then the "
                "counter passes to the next villain to its right",
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
                "Commencer sur la face Apocalypse (II). Pour une partie plus facile, commencer "
                "sur la face (I)",
                "Begin on the Apocalypse (II) side. For an easier game, begin on side (I)",
            ),
            "cards": ["45101a"],
        },
        {
            "text": t(
                "Les prélats sont au dos des Overseers : vaincre un prélat ne retire pas son "
                "Overseer de la campagne",
                "The prelates are the reverse of the overseers: defeating a prelate does not "
                "remove its overseer from the campaign",
            ),
            "cards": OVERSEERS,
        },
        {
            "text": t(
                "Apocalypse ne peut être vaincu tant que No Longer Worthy, au dos de ce complot, "
                "ne lui est pas attaché",
                "Apocalypse cannot be defeated until No Longer Worthy, on the reverse of this "
                "side scheme, is attached to him",
            ),
            "cards": ["45105a"],
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
                "Mélanger le set Age of Apocalypse dans le deck rencontre",
                "Shuffle the Age of Apocalypse set into the encounter deck",
            ),
            "cards": AOA_MODULAR,
        },
        {
            "text": t(
                "Révéler cette mission : elle est réservée à ce scénario et décide de la campagne",
                "Reveal this MISSION side scheme: it is reserved for this scenario and decides "
                "the campaign",
            ),
            "cards": [PROTECT_THE_PROFESSOR],
        },
        {
            "text": t(
                "Placer cet Overseer dans la zone de mission, puis y poser la carte Mission Rules",
                "Put this OVERSEER minion in the mission area, then put the Mission Rules card "
                "into play beside it",
            ),
            "draw": {"id": "overseer", "from": OVERSEERS, "excluding": "overseersDefeated"},
        },
        {
            "text": t(
                "Le premier joueur prend ce soutien, face MISSION visible",
                "The first player takes this support, MISSION side faceup",
            ),
            "cards": [MISSION_TEAM],
        },
        {
            "text": t(
                "Chaque joueur cherche un allié dans son deck et l'ajoute à sa main "
                "(il compte dans la taille de main)",
                "Each player searches their deck for an ally and adds it to their hand "
                "(it counts towards hand size)",
            ),
        },
        {
            "text": t(
                "Le Professeur X ne peut pas entrer en jeu pendant cette partie",
                "Professor X cannot enter play during this game",
            ),
        },
        {
            "text": t(
                "Ancient Ritual commence en jeu et ne peut pas la quitter",
                "Ancient Ritual starts in play and cannot leave it",
            ),
            "cards": ["45163"],
        },
        {
            "text": t(
                "Apocalypse commence sous sa forme BIOMORPH. Changer de forme ne réinitialise "
                "ni ses points de vie ni ses attachements",
                "Apocalypse starts in his BIOMORPH form. Changing form resets neither his hit "
                "points nor his attachments",
            ),
            "cards": ["45184a"],
        },
    ] + [{"include": "expertHp"}],
    "onVictory": {
        "prompts": [
            {
                "id": "professorSaved",
                "type": "boolean",
                "label": t(
                    "Protect the Professor a-t-elle été vaincue ? Elle seule décide de la campagne",
                    "Was Protect the Professor defeated? It alone decides the campaign",
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
