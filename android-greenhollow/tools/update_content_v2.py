#!/usr/bin/env python3
"""
Upgrades the built-in content packs to pack format 2 (Emberfall phases 1 and 2).

  python3 android-greenhollow/tools/update_content_v2.py

core.json is updated in place (existing entries are kept and extended); emberfall.json is
rewritten. The script can be run again safely: entries are matched by id.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
CONTENT = os.path.join(HERE, "..", "app", "src", "main", "assets", "content")
SECTIONS = [
    "materials", "items", "races", "backgrounds", "professions", "npcs", "population", "creatures",
    "buildings", "locations", "spots", "resources", "interactables", "rumours", "dialogues",
]


def dump(pack, path):
    out = ["{", '  "manifest": ' + json.dumps(pack["manifest"], ensure_ascii=False, indent=2).replace("\n", "\n  ") + ","]
    keys = [k for k in SECTIONS if pack.get(k)]
    for n, key in enumerate(keys):
        out.append('  "%s": [' % key)
        rows = pack[key]
        for i, row in enumerate(rows):
            out.append("    " + json.dumps(row, ensure_ascii=False) + ("," if i < len(rows) - 1 else ""))
        out.append("  ]" + ("," if n < len(keys) - 1 else ""))
    out.append("}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")


def upsert(rows, entry):
    for i, row in enumerate(rows):
        if row["id"] == entry["id"]:
            rows[i] = entry
            return
    rows.append(entry)


def by_id(rows, ident):
    return next(r for r in rows if r["id"] == ident)


# ---------------------------------------------------------------- dialogue helpers

def C(kind, key="", min=None, max=None, negate=False):
    c = {"type": kind}
    if key:
        c["key"] = key
    if min is not None:
        c["min"] = min
    if max is not None:
        c["max"] = max
    if negate:
        c["negate"] = True
    return c


def O(kind, key="", amount=0, text=""):
    o = {"type": kind}
    if key:
        o["key"] = key
    if amount:
        o["amount"] = amount
    if text:
        o["text"] = text
    return o


def R(text, goto="", answer="", once=False, cond=None, out=None):
    r = {"text": text}
    if cond:
        r["conditions"] = cond
    if out:
        r["outcomes"] = out
    if goto:
        r["goto"] = goto
    if once:
        r["once"] = True
    if answer:
        r["answer"] = answer
    return r


def L(text, *cond):
    return {"text": text, "conditions": list(cond)}


def N(ident, text, replies, lines=None):
    n = {"id": ident, "text": text}
    if lines:
        n["lines"] = lines
    n["replies"] = replies
    return n


TALK = R("Talk a while.", out=[O("small_talk")])
BYE = R("Farewell.", out=[O("end")])
NIGHT = C("hour", min=21, max=5)


def trade(text, answer):
    return R(text, answer=answer, out=[O("trade")])


# ---------------------------------------------------------------- core

def update_core():
    path = os.path.join(CONTENT, "core.json")
    with open(path, encoding="utf-8") as f:
        core = json.load(f)

    core["manifest"]["version"] = "0.2.0"
    core["manifest"]["schemaVersion"] = 2

    items = core["items"]
    gear = {
        "ironwood_longsword": {"slot": "main_hand", "weaponType": "one_handed", "speed": 1.0, "durability": 300},
        "hunting_knife": {"slot": "main_hand", "weaponType": "dagger", "speed": 1.5, "durability": 200},
        "forged_blade": {"slot": "main_hand", "weaponType": "one_handed", "speed": 1.0, "durability": 260},
        "leather_cap": {"armorClass": "light", "durability": 160},
        "padded_jerkin": {"armorClass": "light", "durability": 220},
        "oak_shield": {"slot": "off_hand", "armorClass": "medium", "durability": 240},
    }
    for ident, extra in gear.items():
        by_id(items, ident).update(extra)
    by_id(items, "book_vale").update({
        "description": "A worn chronicle of the vale, kept by the first innkeeper of the Gilded Stag.",
        "text": "Greenhollow was founded beneath the white ash by seven families fleeing the Greyback mines. "
                "They brought picks, seed barley and one anvil, and swore never again to dig for another man's profit.\n\n"
                "The ash still marks every door. When a child of the vale comes of age, a leaf is pressed into the lintel of the house they were born in.\n\n"
                "Of the old mines little is written, save that the deepest gallery was sealed in a single night and that nobody who did the sealing would say why.",
        "rumour": "founding",
    })

    def weapon(ident, name, kind, damage, speed, reach, weight, value, durability, description, req=None, bonus=None):
        e = {"id": ident, "name": name, "type": "weapon", "slot": "main_hand", "weaponType": kind, "weight": weight,
             "value": value, "stackable": False, "icon": "sword", "damage": damage, "speed": speed, "reach": reach,
             "durability": durability, "description": description}
        if req:
            e["requirements"] = req
        if bonus:
            e["bonuses"] = bonus
        return e

    def armor(ident, name, slot, value_armor, klass, weight, value, durability, description, icon="armor", req=None, bonus=None):
        e = {"id": ident, "name": name, "type": "armor", "slot": slot, "weight": weight, "value": value,
             "stackable": False, "icon": icon, "armor": value_armor, "armorClass": klass, "durability": durability,
             "description": description}
        if req:
            e["requirements"] = req
        if bonus:
            e["bonuses"] = bonus
        return e

    def trinket(ident, name, value, description, bonus, req=None):
        e = {"id": ident, "name": name, "type": "trinket", "slot": "trinket", "weight": 0.1, "value": value,
             "stackable": False, "icon": "gem", "description": description, "bonuses": bonus}
        if req:
            e["requirements"] = req
        return e

    new_items = [
        weapon("steel_dagger", "Steel Dagger", "dagger", 9, 1.6, 1.3, 0.5, 34, 260,
               "Narrow, quick and easy to hide. Favoured by people who do not want a fair fight.", req={"agility": 6}),
        weapon("woodcutter_axe", "Woodcutter's Axe", "two_handed", 17, 0.8, 1.9, 2.6, 38, 320,
               "Made for oak, but it does not much care what it bites. Needs both hands.", req={"strength": 6}),
        weapon("vale_greatsword", "Vale Greatsword", "two_handed", 23, 0.7, 2.3, 3.4, 120, 380,
               "As long as a tall man's reach. Slow to swing and slow to stop.", req={"strength": 8}),
        armor("iron_helm", "Iron Helm", "head", 5, "heavy", 1.6, 46, 320,
              "A riveted pot helm with a nose guard. Rings like a bell when struck.", icon="helmet", req={"strength": 7}),
        armor("mail_hauberk", "Mail Hauberk", "chest", 11, "heavy", 7.5, 150, 420,
              "Ten thousand rings, each one closed by hand. Heavy on the shoulders, kind to the ribs.",
              req={"strength": 8, "endurance": 6}),
        armor("work_gloves", "Work Gloves", "hands", 1, "light", 0.3, 6, 140,
              "Thick hide gloves, scorched at the fingertips."),
        armor("smith_gauntlets", "Smith's Gauntlets", "hands", 3, "heavy", 1.1, 40, 300,
              "Plated across the knuckles, for holding hot work and taking hard knocks.",
              req={"strength": 6}, bonus={"strength": 1}),
        armor("leather_leggings", "Leather Leggings", "legs", 3, "light", 1.4, 22, 200,
              "Oiled against the wet and laced to the knee."),
        armor("travel_boots", "Travelling Boots", "feet", 1, "light", 0.9, 18, 220,
              "Double-soled and already broken in. The road feels shorter in them.", bonus={"agility": 1}),
        armor("iron_greaves", "Iron Greaves", "feet", 3, "heavy", 2.2, 44, 320,
              "Shin plates on stout boots. Nothing bites through them.", req={"strength": 7}),
        trinket("ash_charm", "White Ash Charm", 26, "A leaf of the founders' ash sealed in beeswax. It steadies the mind.", {"willpower": 1}),
        trinket("silver_ring", "Silver Ring", 48, "Plain silver, well polished. People notice it.", {"charm": 1}),
        trinket("tusk_talisman", "Tusk Talisman", 32, "A boar tusk bound in copper wire. Hunters wear them for nerve.", {"strength": 1}),
        trinket("river_stone", "Silverrun Stone", 30, "A river-worn pebble with a hole clean through it. Said to keep a traveller on their feet.", {"endurance": 1}),
        trinket("scholars_lens", "Scholar's Lens", 70, "A ground crystal on a cord. Small print and small lies both come clear through it.",
                {"intellect": 2}, req={"intellect": 6}),
    ]
    for e in new_items:
        upsert(items, e)

    races = core["races"]
    by_id(races, "human")["attributes"] = {"charm": 1, "endurance": 1}
    by_id(races, "ashen")["attributes"] = {"intellect": 2, "willpower": 1, "endurance": -1}
    by_id(races, "stoneborn")["attributes"] = {"strength": 2, "endurance": 1, "agility": -1}

    def give(background, ident):
        if not any(s["item"] == ident for s in background["items"]):
            background["items"].append({"item": ident})

    bgs = core["backgrounds"]
    apprentice = by_id(bgs, "apprentice")
    give(apprentice, "work_gloves")
    apprentice["equip"] = ["ironwood_longsword", "work_gloves"]
    hunter = by_id(bgs, "hunter")
    hunter["skills"] = {"blades": 12, "hunting": 10}
    give(hunter, "travel_boots")
    hunter["equip"] = ["hunting_knife", "padded_jerkin", "leather_cap", "travel_boots"]
    wanderer = by_id(bgs, "wanderer")
    wanderer["skills"] = {"speech": 12, "trading": 8}
    give(wanderer, "ash_charm")
    wanderer["equip"] = ["ironwood_longsword", "oak_shield", "ash_charm"]

    def stock(profession, ident, count=1):
        goods = by_id(core["professions"], profession)["goods"]
        if not any(g["item"] == ident for g in goods):
            goods.append({"item": ident, "count": count})

    for ident in ["steel_dagger", "vale_greatsword", "iron_helm", "smith_gauntlets", "iron_greaves", "mail_hauberk"]:
        stock("blacksmith", ident)
    for ident in ["leather_leggings", "travel_boots", "work_gloves", "woodcutter_axe", "silver_ring"]:
        stock("merchant", ident)
    stock("alchemist", "ash_charm")

    for ident, text in {
        "wolf": "Lean grey hunters of the Whisper Woods. They circle before they bite, and they seldom hunt alone.",
        "boar": "Short-tempered and thick-skulled. A tusker charges first and thinks never.",
        "deer": "Shy grazers of the meadow. They bolt at a snapped twig, so come at them from downwind.",
    }.items():
        by_id(core["creatures"], ident)["description"] = text

    for ident, text in {
        "greenhollow": "A ring of timbered houses around the square and the Old Well, with a gate to the north and one to the south.",
        "greenhollow_road": "The cart road south of the village. Every traveller to the vale arrives by it.",
        "whisper_woods": "Old trees west of the road, named for the sound the wind makes in them. Wolf country.",
        "east_thicket": "Bramble and scrub oak east of the village, rooted up by the boar that give it its name.",
        "stag_meadow": "Open grass to the south-east where the deer herds bed down. The hunters work it at dawn.",
        "silverrun": "The cold river north of the village. Trout in the shallows, yarrow on the banks.",
        "greyback_dig": "A scar of red earth where the miners still cut ore from the foot of the Greybacks.",
    }.items():
        by_id(core["locations"], ident)["description"] = text

    core["rumours"] = [
        {"id": "founding", "title": "The sealed gallery", "location": "greyback_dig",
         "text": "The founders of Greenhollow fled the Greyback mines, and the deepest gallery was sealed in a single night. Nobody wrote down why."},
        {"id": "old_seam", "title": "A second seam", "location": "greyback_dig",
         "text": "The miners whisper of a richer seam somewhere under the old dig. Dunstan knows more than he says."},
        {"id": "wolves", "title": "Wolves at the wood's edge", "location": "whisper_woods",
         "text": "The wolves have come down early this year. They are seen along the edge of the Whisper Woods after dusk."},
        {"id": "white_stag", "title": "The white stag", "location": "stag_meadow",
         "text": "A white stag has been seen on the meadow at dawn. Garrick swears to it, and Garrick does not like to be laughed at."},
        {"id": "tuskers", "title": "Boar in the thicket", "location": "east_thicket",
         "text": "Big boar root in the thicket east of the village. Their tusks fetch a fair price, if you come back with them."},
        {"id": "river_box", "title": "The peddler's strongbox", "location": "silverrun",
         "text": "A peddler lost a strongbox fording the Silverrun last spring. It should still lie somewhere near the fishing bank."},
        {"id": "river_yarrow", "title": "Yarrow on the north bank", "location": "silverrun",
         "text": "The best yarrow grows where the ground stays wet, on the bank of the Silverrun north of the village."},
        {"id": "east_vein", "title": "Unclaimed iron",
         "text": "There is iron lying open in the hills north-east of the village, past the thicket. Nobody claims it."},
        {"id": "hunters_cache", "title": "The hunters' cache", "location": "stag_meadow",
         "text": "The hunters keep a lean-to on Stag Meadow with a cot and a cache. Anyone caught out at night may use it, so long as they leave it tidy."},
    ]

    core["interactables"] = [
        {"id": "notice_board", "name": "the notice board", "kind": "sign", "x": -5.5, "z": 8.0,
         "text": "NOTICES OF GREENHOLLOW\n\nWOLVES. Keep children and goats inside the gates after dusk. By order of the hunters.\n\n"
                 "ORE WANTED. Bram at the smithy pays in good bar iron for red ore from the Greybacks.\n\n"
                 "LOST. One strongbox, iron-bound, somewhere in the Silverrun. A reward to the finder, says the peddler, though the peddler has not been seen since.",
         "rumour": "river_box"},
        {"id": "south_gate_sign", "name": "the gate post", "kind": "sign", "x": 3.5, "z": 54.0,
         "text": "GREENHOLLOW\n\nFounded beneath the white ash.\nTravellers welcome. Swords sheathed inside the gate."},
        {"id": "road_milestone", "name": "the old milestone", "kind": "milestone", "x": 4.0, "z": 98.0,
         "prompt": "Examine the old milestone",
         "text": "The stone is older than the village. One face reads GREENHOLLOW, half a league. "
                 "Another, almost worn away, points west to THE GREYBACK WORKS and has been deliberately chiselled across.",
         "rumour": "founding", "skillXp": {"speech": 2}},
        {"id": "hilde_cot", "name": "the cot at Hilde's door", "kind": "bed", "x": 22.0, "z": 25.0, "sleep": True,
         "owner": "hilde", "accessAffinity": 50, "restore": {"comfort": 15}},
        {"id": "meadow_lean_to", "name": "the hunters' lean-to", "kind": "bed", "x": 84.0, "z": 98.0, "sleep": True},
        {"id": "meadow_cache", "name": "the hunters' cache", "kind": "container", "x": 86.5, "z": 99.5, "respawnHours": 72,
         "loot": [{"item": "venison", "chance": 1.0}, {"item": "rope", "chance": 0.4}, {"item": "healing_draught", "chance": 0.35}],
         "rumour": "hunters_cache"},
        {"id": "bram_scrap_bin", "name": "Bram's scrap bin", "kind": "container", "x": -33.5, "z": 5.5, "respawnHours": 96,
         "owner": "bram", "accessAffinity": 60,
         "loot": [{"item": "iron_ingot", "chance": 1.0, "count": 2}, {"item": "bronze_ingot", "chance": 0.4}]},
        {"id": "river_strongbox", "name": "a half-buried strongbox", "kind": "container", "x": -6.0, "z": -74.0,
         "loot": [{"item": "silver_ring", "chance": 1.0}, {"item": "amethyst", "chance": 1.0}, {"item": "river_stone", "chance": 1.0}],
         "skillXp": {"foraging": 6}},
        {"id": "woods_stash", "name": "a hollow log", "kind": "container", "x": -82.0, "z": 60.0, "respawnHours": 120,
         "loot": [{"item": "yarrow", "chance": 1.0, "count": 2}, {"item": "tusk_talisman", "chance": 0.3}, {"item": "apple", "chance": 0.6, "count": 2}]},
        {"id": "herbal_lectern", "name": "Mira's herbal", "kind": "book", "x": -27.8, "z": 18.8,
         "text": "A COMMON HERBAL, copied in Mira's hand and chained to the lectern.\n\n"
                 "YARROW. White flower, feathered leaf. Crush and bind to a wound. Best gathered where the ground stays wet.\n\n"
                 "Three sprigs steeped overnight make one draught. Bring them fresh and I will do the steeping.\n\n"
                 "Do not eat anything from this book on the strength of a drawing.",
         "rumour": "river_yarrow", "skillXp": {"foraging": 4, "restoration": 2}},
        {"id": "wayside_shrine", "name": "the wayside shrine", "kind": "shrine", "x": -9.0, "z": 78.0,
         "prompt": "Rest a moment at the wayside shrine", "restore": {"magic": 40, "comfort": 10}, "cooldownHours": 24,
         "text": "A cairn of river stones under a sapling ash. Travellers leave a pebble and take a breath. You do the same, and your head feels clearer.",
         "skillXp": {"restoration": 3}},
    ]

    core["dialogues"] = [
        {"id": "bram_talk", "npc": "bram", "nodes": [
            N("start", "Mind the sparks, {player}. What do you need hammered?", [
                trade("Show me what you have.", "Ingots on the left, finished work on the right. Touch nothing that glows."),
                R("Any work for an apprentice?", goto="work"),
                R("What do the miners say about the Greybacks?", once=True, cond=[C("affinity", min=50)], out=[O("rumour", "old_seam")],
                  answer="Dunstan swears there is a second seam under the old dig, richer than the first. He will not say where. Miners."),
                R("You taught me well.", once=True, cond=[C("skill", "smithing", min=25)],
                  out=[O("affinity", amount=4), O("give_item", "smith_gauntlets"), O("memory", text="Gave {player} a pair of gauntlets")],
                  answer="Ha. You have stopped burning the steel, at least. Here. These were mine when I was your size."),
                TALK, BYE,
            ], lines=[
                L("Say your piece and mind the anvil.", C("affinity", max=24)),
                L("The forge is banked for the night, {player}. Make it quick.", NIGHT),
            ]),
            N("work", "Bring me red ore from the Greybacks and I will pay in bar iron. Three lumps, two bars. Fair?", [
                R("Here are three lumps.", goto="work", cond=[C("item", "iron_ore", min=3)],
                  out=[O("take_item", "iron_ore", 3), O("give_item", "iron_ingot", 2), O("affinity", amount=1), O("skill_xp", "trading", 1)],
                  answer="Good red ore. Two bars, as promised. Bring more and the offer stands."),
                R("I will keep an eye out.", answer="Do. The pile is low."),
            ]),
        ]},
        {"id": "elsa_talk", "npc": "elsa", "nodes": [
            N("start", "Sit, sit. The stew is still warm.", [
                trade("What is in the pot?", "Stag stew, barley loaf, and apples if you must be virtuous."),
                R("Heard anything lately?", goto="gossip"),
                R("A bowl for a friend?", once=True, cond=[C("affinity", min=70)], out=[O("give_item", "stew")],
                  answer="On the house. Do not tell Oswin, he will want one too."),
                TALK, BYE,
            ], lines=[
                L("Evening, {player}. The common room is loud tonight. Find a stool.", C("hour", min=19, max=2)),
            ]),
            N("gossip", "Always. What sort of thing?", [
                R("Trouble on the roads?", goto="gossip", once=True, out=[O("rumour", "wolves")],
                  answer="Tomas says the wolves have come down to the wood's edge early this year. Keep to the road after dusk."),
                R("Anything worth coin?", goto="gossip", once=True, cond=[C("affinity", min=55)], out=[O("rumour", "river_box")],
                  answer="A peddler lost a strongbox fording the Silverrun last spring. Never found it. Near the fishing bank, he said."),
                R("Anything strange?", goto="gossip", once=True, out=[O("rumour", "white_stag")],
                  answer="Garrick came in swearing he saw a white stag on the meadow. He was three cups deep, mind."),
                R("Enough gossip.", answer="Suit yourself. There will be more tomorrow."),
            ]),
        ]},
        {"id": "tomas_talk", "npc": "tomas", "nodes": [
            N("start", "Quiet. You will scare everything from here to the river.", [
                R("Where is the hunting good?", once=True, out=[O("rumour", "tuskers")],
                  answer="Boar, in the thicket east of the village. Big ones. Do not go with a knife."),
                R("Teach me to track.", once=True, cond=[C("affinity", min=60)],
                  out=[O("skill_xp", "hunting", 8), O("memory", text="Taught {player} to read a trail")],
                  answer="Look for bent grass, not prints. Prints lie. Grass does not. And stay downwind, always."),
                R("I brought you a wolf pelt.", cond=[C("item", "wolf_pelt")],
                  out=[O("take_item", "wolf_pelt", 1), O("gold", amount=16), O("affinity", amount=2), O("skill_xp", "trading", 1)],
                  answer="Clean cut. Sixteen, and my thanks. One less at the wood's edge."),
                R("May I use the lean-to on the meadow?", once=True, out=[O("rumour", "hunters_cache")],
                  answer="Anyone may. There is a cot, and a cache we keep stocked. Take what you need and leave it tidy."),
                TALK, BYE,
            ], lines=[
                L("You again. Keep your voice down.", C("affinity", max=29)),
            ]),
        ]},
        {"id": "mira_talk", "npc": "mira", "nodes": [
            N("start", "Do not touch the blue jar. Or the green one.", [
                trade("I need a remedy.", "Draughts for the body, tonics for the head. Do not mix them."),
                R("Do you need herbs?", goto="herbs"),
                R("What is yarrow good for?", once=True, out=[O("rumour", "river_yarrow"), O("skill_xp", "foraging", 4)],
                  answer="It staunches bleeding. The best of it grows on the bank of the Silverrun, where the ground stays wet."),
                R("Could you teach me a little of the craft?", once=True, cond=[C("attribute", "intellect", min=7)],
                  out=[O("skill_xp", "restoration", 10), O("affinity", amount=3)],
                  answer="You ask better questions than most. Mending is attention, nothing more. Hold the hurt in your mind and do not look away."),
                TALK, BYE,
            ], lines=[
                L("The shop is shut. Unless somebody is bleeding?", NIGHT),
            ]),
            N("herbs", "Always. Three fresh sprigs of yarrow and I will give you a draught for them.", [
                R("Three sprigs, as asked.", goto="herbs", cond=[C("item", "yarrow", min=3)],
                  out=[O("take_item", "yarrow", 3), O("give_item", "healing_draught", 1), O("affinity", amount=2)],
                  answer="Fresh. Good. One draught, already steeped."),
                R("Later, then.", answer="I will be here. I am always here."),
            ]),
        ]},
        {"id": "oswin_talk", "npc": "oswin", "nodes": [
            N("start", "Everything has a price, friend. Mine are merely fair.", [
                trade("Let me see your goods.", "Look freely. Looking is the only thing I do not charge for."),
                R("Can you do better on price?", goto="haggle"),
                R("Where do your goods come from?", once=True, out=[O("rumour", "east_vein")],
                  answer="Carters, mostly. One told me there is iron lying open in the hills north-east, past the thicket. Nobody claims it. Nobody sensible goes there, either."),
                TALK, BYE,
            ], lines=[
                L("Ah, {player}. My favourite customer. Almost.", C("affinity", min=60)),
            ]),
            N("haggle", "Better? These prices are already a kindness.", [
                R("I am a good customer.", once=True, cond=[C("skill", "trading", min=20)], out=[O("affinity", amount=5)],
                  answer="Hm. You are, at that. I shall remember it at the till."),
                R("A little silver for your trouble. (20 gold)", once=True, cond=[C("gold", min=20)],
                  out=[O("gold", amount=-20), O("affinity", amount=8), O("memory", text="Took a sweetener from {player}")],
                  answer="Now that is a language I speak fluently."),
                R("Never mind.", answer="As you like. The prices will still be here."),
            ]),
        ]},
        {"id": "hilde_talk", "npc": "hilde", "nodes": [
            N("start", "You look thin. Are you eating?", [
                R("I am hungry, Aunt.", once=True, out=[O("give_item", "bread", 2), O("give_item", "apple", 2)],
                  answer="I knew it. Take these, and do not argue with me."),
                R("May I sleep here?", answer="The cot by the door is always yours. Family does not ask."),
                R("How is the farm?", goto="farm"),
                TALK, BYE,
            ], lines=[
                L("You should be abed, {player}. The cot by the door is yours, you know that.", NIGHT),
            ]),
            N("farm", "The barley is in, but the apples are dropping faster than I can gather them.", [
                R("I brought apples.", goto="farm", cond=[C("item", "apple", min=3)],
                  out=[O("take_item", "apple", 3), O("gold", amount=4), O("affinity", amount=2), O("skill_xp", "foraging", 2)],
                  answer="Bless you. Here, a few coppers, and do not tell me you will not take them."),
                R("Tell me about the founding.", goto="farm", once=True, out=[O("rumour", "founding")],
                  answer="Seven families came down from the Greyback mines, my grandmother's among them. They sealed the deep gallery behind them in one night. She never would say why."),
                R("I will let you work.", answer="Come by for supper."),
            ]),
        ]},
        {"id": "wyl_talk", "npc": "wyl", "nodes": [
            N("start", "Heading to the woods? Take me along next time!", [
                R("Tell me a tale.", goto="tale"),
                R("Come to the woods with me.", answer="Aunt Hilde would skin us both. ...Next time?"),
                TALK, BYE,
            ]),
            N("tale", "Which one? I know two good ones.", [
                R("The white stag.", goto="tale", once=True, out=[O("rumour", "white_stag")],
                  answer="It comes to the meadow at dawn, white as milk, and no arrow has ever touched it. Garrick has seen it. He goes red if you ask."),
                R("The miners' secret.", goto="tale", once=True, out=[O("rumour", "old_seam")],
                  answer="There is a second seam under the old dig. The miners found it and covered it up again. That is what I heard, anyway."),
                R("Another time.", answer="You always say that."),
            ]),
        ]},
        {"id": "garrick_talk", "npc": "garrick", "nodes": [
            N("start", "Still playing at swords? Stay off my trails.", [
                R("I did not come to quarrel.", once=True, cond=[C("affinity", max=49)], out=[O("affinity", amount=3)],
                  answer="No? Then you are smarter than you look."),
                R("A drink to mend things? (8 gold)", once=True, cond=[C("affinity", max=49), C("gold", min=8)],
                  out=[O("gold", amount=-8), O("affinity", amount=6), O("memory", text="Shared a drink with {player}")],
                  answer="...One drink. And you are buying the second."),
                R("I will out-hunt you yet.", once=True, cond=[C("skill", "hunting", min=20)],
                  out=[O("affinity", amount=6), O("rumour", "white_stag"), O("memory", text="Was challenged by {player}")],
                  answer="Ha! Big words. East side of the meadow, at dawn. That is where the white one shows. Prove it."),
                TALK, BYE,
            ], lines=[
                L("Hm. You again. ...Sit, if you must.", C("affinity", min=50)),
            ]),
        ]},
        {"id": "dunstan_talk", "npc": "dunstan", "nodes": [
            N("start", "Bram's little helper. Hmph.", [
                R("What is under the old dig?", once=True, cond=[C("affinity", min=40)], out=[O("rumour", "old_seam")],
                  answer="A seam. A good one. And something past it that made my father put his pick down and never lift it again. Leave it be."),
                R("I can swing a pick.", once=True, cond=[C("attribute", "strength", min=8)],
                  out=[O("affinity", amount=6), O("give_item", "work_gloves")],
                  answer="Hmph. With arms like that, maybe you can. Take these. You will wreck your hands otherwise."),
                R("Will you buy my ore?", cond=[C("item", "iron_ore", min=2)],
                  out=[O("take_item", "iron_ore", 2), O("gold", amount=8), O("skill_xp", "trading", 1)],
                  answer="Eight for the pair. Bram would cheat you worse."),
                TALK, BYE,
            ], lines=[
                L("You. Hm. You are not as soft as you look.", C("affinity", min=50)),
            ]),
        ]},
        {"id": "isolde_talk", "npc": "isolde", "nodes": [
            N("start", "Stand still. That sleeve is a disgrace.", [
                R("Can you mend it? (6 gold)", once=True, cond=[C("gold", min=6)],
                  out=[O("gold", amount=-6), O("affinity", amount=3), O("memory", text="Mended a sleeve for {player}")],
                  answer="There. Now you look like someone's child instead of someone's scarecrow."),
                R("I have a hide for you.", cond=[C("item", "deer_hide")],
                  out=[O("take_item", "deer_hide", 1), O("gold", amount=12), O("affinity", amount=2), O("skill_xp", "trading", 1)],
                  answer="Supple. Twelve, and bring me more like it."),
                R("Make me something to walk in. (2 hides, 10 gold)", once=True, cond=[C("item", "deer_hide", min=2), C("gold", min=10)],
                  out=[O("take_item", "deer_hide", 2), O("gold", amount=-10), O("give_item", "travel_boots")],
                  answer="I had a pair cut already. A few stitches... there. Walk gently for a day."),
                TALK, BYE,
            ]),
        ]},
        {"id": "fisher_talk", "profession": "fisher", "nodes": [
            N("start", "Shh. They are biting.", [
                R("Sell me a trout? (6 gold)", cond=[C("gold", min=6)], out=[O("gold", amount=-6), O("give_item", "river_trout")],
                  answer="Fresh out of the Silverrun. Mind the bones."),
                R("Anything odd in the river?", once=True, out=[O("rumour", "river_box")],
                  answer="Something iron-bound, down by the bank west of the ford. My line snagged it twice. Too heavy to land."),
                TALK, BYE,
            ], lines=[
                L("Nothing rises this late. Go home.", NIGHT),
            ]),
        ]},
    ]

    dump(core, path)
    return core


# ---------------------------------------------------------------- emberfall sample pack

def write_emberfall():
    pack = {
        "manifest": {
            "id": "emberfall", "name": "Emberfall Relics", "version": "1.1.0", "author": "Greenhollow",
            "description": "A sample content pack: a new forging metal, a wandering relic trader with her own conversation, a rare beast, a rumour and things to find. Adds content without touching engine code.",
            "schemaVersion": 2, "dependencies": ["core>=0.2.0"], "loadOrder": 50,
        },
        "materials": [
            {"id": "emberglass", "name": "Emberglass", "category": "relic", "density": 6.1, "hardness": 88, "toughness": 46,
             "flexibility": 30, "workability": 34, "heatResistance": 92, "edgeRetention": 90, "forgeTemp": 1280,
             "color": "#D9772B", "itemId": "emberglass_shard"},
        ],
        "items": [
            {"id": "emberglass_shard", "name": "Emberglass Shard", "type": "material", "weight": 0.4, "value": 45, "icon": "gem",
             "description": "Volcanic glass that never quite cools. Light, wickedly hard, and brittle."},
            {"id": "ember_pelt", "name": "Cinder Pelt", "type": "misc", "weight": 1.4, "value": 55, "icon": "pelt",
             "description": "Fur the colour of banked coals."},
            {"id": "ember_band", "name": "Emberglass Band", "type": "trinket", "slot": "trinket", "weight": 0.1, "value": 90,
             "stackable": False, "icon": "gem", "description": "A ring of dark glass, warm against the skin. The warmth does not fade.",
             "bonuses": {"willpower": 1, "intellect": 1}, "requirements": {"willpower": 6}},
        ],
        "professions": [
            {"id": "relic_trader", "name": "Relic Trader", "workplace": "plaza", "workStart": 9, "workEnd": 19, "wage": 5,
             "activity": "Hawking relics in the square", "trader": True, "cloth": "#8A2F22", "hat": True,
             "goods": [{"item": "emberglass_shard", "count": 9}, {"item": "mind_tonic", "count": 2}, {"item": "ember_band", "count": 1}]},
        ],
        "npcs": [
            {"id": "vesna", "name": "Vesna", "profession": "relic_trader", "age": 41, "race": "ashen", "home": "inn",
             "skin": "#D9D2C4", "hair": "#B8402A", "longHair": True, "relation": "stranger", "affinity": 38, "gold": 160,
             "greeting": "From the Emberfall, all of it. Careful, it bites.", "likes": "old ruins", "fears": "deep water",
             "personality": {"ambition": 0.8, "courage": 0.7, "sociability": 0.6, "generosity": 0.3, "greed": 0.7,
                             "aggression": 0.3, "curiosity": 0.9, "loyalty": 0.3}},
        ],
        "creatures": [
            {"id": "cinder_wolf", "name": "Cinder Wolf", "health": 70, "damage": 12, "speed": 5.6, "aggressive": True,
             "color": "#B8402A", "size": 1.25, "territory": "whisper_woods", "count": 1,
             "description": "A wolf with a coat like banked coals. Where it beds down the leaf litter is scorched black.",
             "loot": [{"item": "ember_pelt", "chance": 1.0}, {"item": "emberglass_shard", "chance": 0.6, "count": 2}]},
        ],
        "interactables": [
            {"id": "scorched_stone", "name": "a scorched standing stone", "kind": "marker", "x": -96.0, "z": 44.0,
             "prompt": "Examine the scorched stone",
             "text": "The moss on this stone is burned away in the shape of something that lay against it. The rock is still faintly warm, and flecks of dark glass glitter in the cracks.",
             "rumour": "cinder_tracks", "loot": [{"item": "emberglass_shard", "chance": 1.0}], "skillXp": {"hunting": 4}},
            {"id": "relic_crate", "name": "Vesna's relic crate", "kind": "container", "x": 6.5, "z": 9.0, "owner": "vesna",
             "accessAffinity": 55, "respawnHours": 120, "loot": [{"item": "emberglass_shard", "chance": 1.0}]},
        ],
        "rumours": [
            {"id": "cinder_tracks", "title": "Scorched tracks", "location": "whisper_woods",
             "text": "Something in the Whisper Woods leaves burned leaf litter where it sleeps. Vesna pays well for its pelt."},
        ],
        "dialogues": [
            {"id": "vesna_talk", "npc": "vesna", "nodes": [
                N("start", "From the Emberfall, all of it. Careful, it bites.", [
                    trade("Show me the relics.", "Look with your eyes first. The glass is sharper than it seems."),
                    R("What is the Emberfall?", goto="emberfall"),
                    R("I found your wolf.", cond=[C("item", "ember_pelt")],
                      out=[O("take_item", "ember_pelt", 1), O("gold", amount=70), O("affinity", amount=8),
                           O("memory", text="Bought a cinder pelt from {player}")],
                      answer="So it is real. Seventy, and you have made a friend today. Do not spend either carelessly."),
                    R("You trust me with your crate?", once=True, cond=[C("affinity", min=55)],
                      answer="With the top layer. Take a shard when you need one. I count them, mind."),
                    TALK, BYE,
                ], lines=[
                    L("{player}. Good. I was hoping for someone who can tell glass from gravel.", C("affinity", min=55)),
                ]),
                N("emberfall", "A valley three weeks east where the ground never cooled. The glass grows there like frost.", [
                    R("Does anything live there?", goto="emberfall", once=True, out=[O("rumour", "cinder_tracks")],
                      answer="Things that followed me here, I fear. There are scorched tracks in your Whisper Woods. Bring me the pelt and I will pay well."),
                    R("Could I forge with it?", goto="emberfall", once=True, cond=[C("skill", "smithing", min=15)],
                      out=[O("skill_xp", "smithing", 6)],
                      answer="If your fire is hot enough. Hotter than iron wants, and do not quench it fast or it will shatter in your tongs."),
                    R("Enough of far places.", answer="As you wish. The relics are here either way."),
                ]),
            ]},
        ],
    }
    dump(pack, os.path.join(CONTENT, "emberfall.json"))
    return pack


if __name__ == "__main__":
    core = update_core()
    ember = write_emberfall()
    for name in ("core.json", "emberfall.json"):
        with open(os.path.join(CONTENT, name), encoding="utf-8") as f:
            json.load(f)
    print("core: %d items, %d dialogues, %d objects, %d rumours" % (
        len(core["items"]), len(core["dialogues"]), len(core["interactables"]), len(core["rumours"])))
    print("emberfall: %d items, %d dialogues, %d objects" % (
        len(ember["items"]), len(ember["dialogues"]), len(ember["interactables"])))
