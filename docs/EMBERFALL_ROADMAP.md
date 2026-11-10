<!-- Reference copy of the Project Emberfall roadmap as written by the project owner.
     The app keeps the name Greenhollow; Emberfall is the codename.
     Status: Phases 1 and 2 built (see .rork/plans). Later phases not started. -->

### Project Emberfall

### Game Design Philosophy & Development Roadmap

Project foundation: Greenhollow Genre: First-person open-world fantasy RPG and living-world simulation Platform: Android, with mobile performance as a primary design constraint Technical foundation: Existing Greenhollow Kotlin/Android codebase, progressively expanded and refactored.

### Part I — Design Philosophy

### 1\. The vision

We are building an open-world fantasy RPG where the player enters a world that feels alive, mysterious, welcoming, and occasionally dangerous.

The player should be able to explore ancient ruins, become a respected blacksmith, join a faction, learn magic, establish relationships, hunt dangerous creatures, uncover forgotten histories, or simply spend their days living in a village.

The world should not exist exclusively to serve the player. Its inhabitants should have their own routines, ambitions, occupations, relationships, problems, and histories.

The ultimate objective is to create a world that feels as though it would continue to exist even if the player never arrived.

This is not simply a conventional RPG with additional crafting and NPC interactions. It is a combination of open-world adventure, systemic simulation, and fantasy life simulation.

### 2\. Three defining influences

Our creative direction combines three complementary qualities.

The Elder Scrolls — Depth and exploration

The freedom to explore, discover locations, uncover lore, develop a character, pursue different paths, join factions, and become part of a vast fantasy world.

We want meaningful exploration and the feeling that something interesting could exist beyond the next hill.

Fable — Atmosphere and personality

Whimsical medieval fantasy, colorful environments, charming settlements, unusual characters, eccentric humor, folklore, and occasional darkness.

We want the world to have personality rather than feeling like a generic medieval setting.

Our own direction — A living simulation

Autonomous inhabitants, meaningful professions, physical crafting, persistent settlements, evolving relationships, economies, and systems that interact without requiring player involvement.

This is the defining feature that will distinguish our game.

These influences are design references, not templates to reproduce. The game needs its own identity, history, setting, visual language, and mechanics.

### 3\. The world must feel alive

This is our most important design principle.

NPCs should be characters rather than decorations.

A blacksmith should have a home, a workplace, a schedule, personal needs, professional skills, and relationships. A merchant should have goods to sell, economic motivations, and reasons to travel or remain in a particular settlement.

A villager might:

* Wake up and prepare for work.
* Visit the market.

* Purchase supplies.
* Socialize with neighbors.

* Develop friendships or rivalries.
* Return home in the evening.

* React to dangerous events.
* Change occupations or circumstances over time.

Not every NPC needs equally complex intelligence. We should use different levels of simulation depending on relevance and distance.

However, every important NPC should have a coherent identity and persistent history.

The player should discover stories emerging from the simulation, not only stories explicitly scripted for them.

### 4\. Player freedom

We should not force the player into a predefined identity.

The player could become:

* A wandering adventurer.
* A master blacksmith.

* A merchant.
* A hunter.

* A mage.
* A warrior.

* A thief.
* A scholar.

* A faction leader.
* A landowner.

* An ordinary resident.

These identities should be supported by actual gameplay systems.

A player who spends most of their time crafting should have a rewarding experience. A player who prefers exploration should have an equally meaningful experience.

There should be no single mandatory way to play.

### 5\. Consequences and persistence

The world should remember meaningful actions.

Examples:

* Killing a dangerous creature makes its territory safer.
* Helping a settlement increases its prosperity.

* Neglecting a settlement can create economic difficulties.
* Building relationships changes how characters respond to the player.

* Joining a faction changes available opportunities and relationships.
* Selling large quantities of goods affects local supply.

* A crafted weapon becomes a unique item with its own history.
* Major events can influence the future of a region.

Not every action needs a dramatic consequence. The important thing is that the systems are interconnected and that meaningful events are retained.

### 6\. Atmosphere and environmental identity

The game should be colorful, inviting, and mysterious without becoming uniformly cheerful.

We want:

* Sunlit medieval villages.
* Lush green valleys.

* Ancient forests.
* Strange ruins.

* Cozy taverns.
* Mountain settlements.

* Forgotten temples.
* Dangerous caves.

* Eccentric inhabitants.
* Local legends.

* Unsettling wilderness.
* Dark and tragic discoveries.

There should be contrast between safety and danger, humor and seriousness, beauty and decay.

The world should feel like a fairytale that has a real history behind it.

### 7\. Systems should interact

We should avoid isolated mechanics that have no meaningful relationship with each other.

For example, consider blacksmithing:

* The player or NPC mines ore.
* Ore is transported to a settlement.

* A smelter produces usable metal.
* A blacksmith uses that metal to manufacture equipment.

* The equipment enters the local economy.
* Adventurers purchase it.

* A faction might order weapons.
* The local economy responds to material shortages.

The same principle should apply to wildlife, agriculture, settlements, professions, faction relationships, and exploration.

Our goal is systemic depth rather than a large collection of disconnected features.

### Part II — Core Gameplay Pillars

### 8\. Open-world exploration

Exploration should be one of the primary reasons to play.

The world should contain:

* Multiple distinct regions.
* Forests, mountains, plains, swamps, and coastlines.

* Settlements and remote homesteads.
* Caves and underground areas.

* Ancient ruins and forgotten civilizations.
* Dungeons and hidden locations.

* Rare creatures and unique resources.
* Environmental stories and discoveries.

We should reward curiosity.

Not every location should be marked on the map. Some discoveries should come from rumors, exploration, books, conversations, and environmental clues.

### 9\. Living NPCs and society

NPCs should have several layers of simulation.

| Layer         | Intended functionality                         |
| ------------- | ---------------------------------------------- |

| Identity      | Name, appearance, age, background, personality |
| Needs         | Hunger, fatigue, safety, social interaction    |

| Profession    | Skills, workplace, duties, production          |
| Schedule      | Daily and seasonal routines                    |

| Relationships | Family, friendship, rivalry, romance           |
| Memory        | Player interactions and important events       |

| Goals         | Short-term and long-term ambitions             |
| Society       | Factions, reputation, community participation  |

| Persistence   | Continued existence and evolving circumstances |

Important NPCs should be capable of more complex behavior than ordinary background inhabitants.

We should also support family structures, generational changes, and meaningful relationships as the simulation matures.

### 10\. Physical crafting and professions

Crafting should be an actual activity rather than a simple button that converts materials into equipment.

The player should be able to develop expertise in multiple professions.

Planned professions:

* Blacksmithing.
* Mining.

* Smelting.
* Woodworking.

* Carpentry.
* Leatherworking.

* Tailoring.
* Alchemy.

* Cooking.
* Herbalism.

* Hunting.
* Fishing.

* Enchanting.

Different materials, techniques, tools, and skill levels should influence the results.

The existing Greenhollow forging system is our starting point.

### 11\. Combat and magic

Combat should reward character development, equipment, preparation, and player skill.

Planned combat elements:

* One-handed weapons.
* Two-handed weapons.

* Daggers.
* Bows.

* Shields.
* Blocking and parrying.

* Dodging.
* Weapon-specific attacks.

* Armor and equipment properties.
* Enemy behavior and attack patterns.

* Weapon condition and maintenance.

Magic should be a complete progression path rather than a small collection of special abilities.

Potential magical disciplines include:

* Elemental magic.
* Restoration.

* Conjuration.
* Illusion.

* Destruction.
* Enchantment.

* Ancient or forbidden magic.

Magic should have meaningful costs, progression, applications, and interactions with the world.

### 12\. Factions, quests, and relationships

Factions should have actual interests, resources, members, leadership, territories, and internal conflicts.

The player should be able to join organizations, earn trust, make enemies, advance through ranks, and influence their direction.

Quest design should combine authored stories with emergent situations.

We should have:

* Main storyline.
* Regional storylines.

* Faction questlines.
* Personal quests.

* Profession-related opportunities.
* Random encounters.

* Dynamic requests.
* Rumors and discoveries.

Not every quest should be a traditional objective marker. Some should emerge from the state of the world.

### Part III — Technical Design Philosophy

### 13\. Greenhollow remains our foundation

We will continue developing the existing Android codebase.

We are not starting over with an unrelated engine.

The current project already provides useful systems:

* Kotlin and Android application structure.
* Jetpack Compose interface.

* Custom 3D rendering.
* Game simulation loop.

* NPC behavior.
* Inventory and equipment.

* Content registry.
* JSON content packs.

* Save system.
* Weapon forging.

These will be retained and expanded where technically appropriate.

The custom renderer will initially remain part of the project. We will assess its limitations as world complexity increases rather than committing to a premature renderer replacement.

### 14\. Modular architecture

The existing `GameEngine` should evolve into a coordinator for specialized systems.

Each subsystem should have clear responsibilities and controlled access to world state.

We should avoid turning the game into one enormous source file.

The architecture should support:

* Independent feature development.
* Reusable gameplay logic.

* Data-driven definitions.
* Predictable system updates.

* Clear dependencies.
* Testable mechanics.

* Safe future expansion.

### 15\. Data-driven development

Items, weapons, materials, creatures, NPCs, professions, locations, and other content should be defined through data wherever practical.

The existing JSON content-pack system should become the backbone of content creation.

This will make it easier to expand the world without constantly rewriting core engine logic.

Content should support:

* Namespaced IDs.
* Validation.

* Dependencies.
* Version compatibility.

* Override rules.
* Asset references.

* Migration.
* Optional expansions.

### 16\. Deterministic simulation and persistence

The world needs to remain coherent across save and load cycles.

We should establish:

* Stable entity IDs.
* Seeded random systems.

* Persisted random state where necessary.
* Versioned save schemas.

* Atomic save operations.
* Backup rotation.

* Region-level persistence.
* NPC state persistence.

* Event history.
* Simulation recovery.

We should also distinguish between active simulation and simplified background simulation.

A player should not need to keep the application running continuously for the world to remain believable.

### 17\. Mobile-first performance

Android is the primary platform.

We must design around realistic mobile constraints rather than assuming desktop hardware.

Our principles:

* Adaptive rendering quality.
* Efficient geometry and draw calls.

* Spatial partitioning.
* Region streaming.

* Simulation LOD.
* NPC update scheduling.

* Memory-conscious assets.
* Controlled background processing.

* Reliable lifecycle handling.
* Performance testing on actual devices.

We should target a scalable architecture rather than promise that every device can run every visual feature at maximum quality.

### Part IV — Development Roadmap

The roadmap is organized around functional milestones. A milestone is complete when its systems work together, not merely when the relevant source files exist.

### Phase 1 — Greenhollow foundation

Starting point

Objective: Turn the existing prototype into a stable, expandable RPG foundation.

Work:

* Establish a verified build baseline.
* Audit existing source and dependencies.

* Fix persistence and content-loading weaknesses.
* Establish regression tests.

* Refactor `GameEngine` into modular systems.
* Formalize world state and entity identity.

* Improve error reporting and diagnostics.
* Preserve all working gameplay features.

Completion: A stable Greenhollow application with a documented, extensible architecture.

### Phase 2 — Core RPG systems

Objective: Turn the prototype into a genuine role-playing game.

Work:

* Character attributes.
* Skill progression.

* Equipment slots and item properties.
* Weapon and armor definitions.

* Experience and progression.
* Dialogue framework.

* Interaction framework.
* Player character customization.

* Journal and discovery tracking.

Completion: The player can create a character, explore, develop skills, equip items, and interact with the world.

### Phase 3 — Living-world simulation\*\* Objective: Make the inhabitants independent participants in the world.Work:Completion: NPCs exhibit believable lives and continue operating independently of the player.Phase 4 — Expand the worldObjective: Transform the small Greenhollow environment into an explorable fantasy world.Work:Completion: The player can travel between distinct regions with persistent world state.Phase 5 — Combat and magicObjective: Build the central action-RPG experience.Work:Completion: Combat and magic support multiple meaningful character builds.Phase 6 — Professions and economyObjective: Expand physical crafting into a connected economic simulation.Work:Completion: Players and NPCs can participate in an interconnected production economy.Phase 7 — Factions, quests, and world storiesObjective: Give the world deeper social and narrative structure.Work:Completion: Player actions influence meaningful world relationships and story outcomes.Phase 8 — Atmosphere and presentationObjective: Establish the distinctive identity of the game.Work:Completion: The world has a recognizable and consistent artistic identity.Phase 9 — Long-term persistence and optimizationObjective: Prepare the complete experience for sustained play on Android.Work:Completion: A stable, optimized game capable of supporting long-term world progression.

### Part V — Long-Term Features

These are ambitious features that should be developed after the underlying simulation and world architecture are mature.

| System                 | Long-term vision                                              |
| ---------------------- | ------------------------------------------------------------- |

| Settlement development | Villages grow, prosper, decline, and change                   |
| Ownership              | Buy property, own businesses, and acquire land                |

| Construction           | Build homes, workshops, and settlements                       |
| Family simulation      | Relationships, marriages, children, and generations           |

| Dynamic politics       | Leadership, alliances, disputes, and territorial control      |
| Advanced economy       | Production chains, trade routes, shortages, and wealth        |

| Wildlife ecosystems    | Territory, hunting, population changes, and predator behavior |
| World history          | Persistent records of important events                        |

| Advanced modding       | Community content packs and expansions                        |
| Emergent storytelling  | Interconnected events that produce unscripted narratives      |

These features should not become mandatory requirements for the first playable release. They are the longer-term direction.

### Part VI — Project Rules

### 18\. Non-negotiable development principles

* Preserve working functionality. New systems must not casually break existing ones.
* No superficial implementations. A feature should have meaningful functionality, not just a screen or placeholder.

* Modular architecture. Avoid monolithic systems and uncontrolled dependencies.
* Data-driven content. Prefer definitions and configuration over hardcoded content.

* Persistent consequences. Important world changes should survive saving and loading.
* Mobile performance first. Performance and memory are architectural concerns from the beginning.

* Test as we build. Important simulation and persistence systems require regression tests.
* Incremental development. Implement, verify, and stabilize before expanding.

* Distinctive world identity. Do not imitate another franchise's world, characters, or lore.
* Systems over feature counts. A smaller number of deeply interconnected systems is better than dozens of shallow mechanics.

### 19\. Definition of success

The project will be successful when a player can enter the world and pursue an entirely self-directed experience.

They could spend an afternoon exploring ancient ruins, return to a village, sell their discoveries, develop a friendship with a local blacksmith, spend several days improving their craft, join a faction, and eventually discover that events elsewhere have changed the region.

The world should support that experience naturally.

It should feel like an adventure, a place to live, and a world worth returning to.

That is the central ambition of Project Emberfall: an open-world fantasy RPG where the adventure belongs to the player, but the world belongs to itself.
