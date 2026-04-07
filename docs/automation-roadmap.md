# Automation roadmap

This document is the current maintainer-facing backlog for SoulFire-native survival automation.

Scope:

- SoulFire-native automation only.
- No port, shim, or code reuse from AltoClef.
- Goal state is broader than "beat the game once": it includes reliable parallel runs, operator controls, settings, protocol support, client visibility, and documentation.

This repository currently contains the CLI and server implementation. GUI client work mentioned here belongs in the separate `SoulFireClient` repository, but it is still part of the end-to-end automation feature surface.

## Current baseline

SoulFire now has:

- A native automation controller and shared team coordinator.
- Item acquisition, crafting, smelting, looting, bartering, and beat-game phase orchestration.
- Shared world memory, shared exploration claims, stronghold estimates, and team status reporting.
- Native portal construction and portal casting paths.
- Basic end-fight targeting, ranged attacks, and death/stall recovery hooks.

That is enough for a prototype automation stack. It is not yet the same as a production-grade "10 bots beat the game in parallel" system, and it is still short of the broader feature surface associated with AltoClef-style survival automation.

## P0: reliability for 10 parallel beat-game bots

These are the gaps that still directly block reliable unattended parallel wins.

### Progression robustness

- Harden portal casting and return-portal recovery.
- Add better ruined-portal recognition and completion logic.
- Add safer lava-source selection and retry logic for portal casting.
- Remember portal ownership and last-good portals per bot and per team.
- Add fallback paths when a portal is blocked, griefed, trapped, or links poorly.
- Improve fortress solving beyond marker following and coarse exploration.
- Add better blaze-spawner camping behavior and safer rod farming loops.
- Improve piglin barter loops, bartering safety, and barter completion detection.
- Improve stronghold solving beyond eye samples and layered exploration.
- Add a real portal-room solver and controlled dig-down logic.
- Add stronger activation logic for fragmented portal-room discovery.
- Improve win detection beyond "dragon/crystal not visible for N ticks".

### End-fight tactics

- Add tower-specific crystal handling.
- Add logic for enclosed-cage crystals.
- Add safer target prioritization between crystals, dragon, and survival recovery.
- Add spawn-platform recovery when a bot enters the End in a bad state.
- Add dragon breath avoidance and lingering-cloud avoidance.
- Add better void-edge safety and recentering.
- Add perch detection and perch-specific damage logic.
- Decide and implement bed strategy policy explicitly.
- Decide and implement melee-only fallback policy explicitly.
- Add cooperative end-fight behavior so not all bots do the same thing at once.

### Recovery and anti-looping

- Distinguish between path stall, combat stall, container stall, and progression stall.
- Add recovery strategies specific to each stall class.
- Add grave-item recovery policies and abandonment thresholds.
- Add re-gear loops after repeated deaths.
- Add explicit "give up on this structure/area" backoff rules.
- Track repeated failed actions and failed locations over longer horizons.
- Add run budgets and timeout ceilings per phase.
- Add bot quarantine logic for repeatedly failing bots during a team run.

### Soak testing and validation

- Add repeatable multi-bot integration scenarios for overworld, nether, stronghold, and End.
- Add a soak-test harness for 10-bot runs across multiple seeds.
- Record completion rate, mean run time, phase failure rate, and death rate.
- Run automated regression suites after major automation changes.
- Build a world-fixture library for edge cases: bad portals, lava caves, split strongholds, partial fortresses, bad End spawn.

## P1: broader automation parity and feature surface

These are the major gaps between the current automation stack and a fuller survival automation system.

### Task catalogue breadth

- Generalized `goto` tasks for blocks, entities, structures, and coordinates.
- Follow/protect/escort tasks for players or other bots.
- Deposit, stash, and withdraw workflows.
- Inventory cleanup and loadout normalization tasks.
- Generalized kill/hunt tasks with entity-specific policies.
- Sleep/bed usage tasks.
- Generalized build/place/activate tasks.
- Farming/replanting tasks.
- Animal breeding and food-production tasks.
- Resource gathering policies that prefer villages, chests, structures, or mob drops when appropriate.

### Planner and recipe depth

- Expand recipe coverage far beyond the current hardcoded set.
- Add alternate acquisition strategies for the same target item.
- Add tool-upgrade planning beyond pickaxe-only progression.
- Add armor planning and shield replacement planning.
- Add explicit bow/ammo replenishment planning.
- Add richer food strategy selection by environment and phase.
- Add planner cost models for danger, travel time, and contention.
- Add better substitute handling for grouped requirements.
- Add planner support for rare or optional end-fight utilities.

### World tracking and memory

- Track more structure hints and progression-relevant landmarks.
- Track richer dropped-item state and ownership contention.
- Track "last inspected" and "likely refilled" state for containers.
- Track partial structure observations and infer likely structure centers.
- Improve memory expiration rules for far-away high-value targets.
- Persist selected automation memory across reconnects or restarts.
- Add per-bot and per-team blacklists for bad targets and bad routes.

### Building and construction

- Finish the currently stubbed schematic/building controller.
- Add native structure placement/build tasks for shelters, bridges, scaffolds, and simple defensive placements.
- Add safer temporary-block management and cleanup.
- Add better support for deliberate block placement during combat and traversal.

### Interaction surface parity

- Add a butler-style chat control layer if that remains in scope for SoulFire.
- Add automation-aware chat triggers and safety filters.
- Add better integration between visual scripting and automation tasks.
- Add plugin APIs for custom automation goals, strategies, and planners.

## P2: team coordination and collaboration controls

The current team coordinator is useful, but still too fixed and too implicit.

### Team strategy

- Replace static role assignment based on bot ordering with configurable role policy.
- Support "independent runners" mode versus "fully collaborative team" mode.
- Support subteams, for example separate nether and overworld squads.
- Allow dynamic reassignment when bots die, disconnect, or finish their phase.
- Add explicit team-wide quotas and shared goals that are configurable.
- Add shared item handoff, chest dropoff, and rendezvous logic.
- Add task reservation for structures, chests, spawners, portals, and fight targets.
- Add stronger collision avoidance between bots working in the same area.

### Collaboration settings

- Add a top-level automation settings page.
- Include a simple on/off toggle for team collaboration.
- Add toggles for role specialization, shared looting, shared structures, and shared End entry.
- Add caps for how many bots may enter the nether, stronghold, and End at once.
- Add settings for shared exploration spacing and structure claim lease time.
- Add settings for whether death recovery is attempted.
- Add settings for whether beds are used in the End.
- Add settings for whether bow combat is preferred, required, or optional.
- Add settings for portal strategy selection: existing portal only, build-only, cast-only, mixed.
- Add settings for aggression and safety thresholds.

## P3: settings, protocol, commands, and client surface

There is currently no automation settings object registered alongside Bot, Account, Proxy, AI, and Pathfinding settings.

### Server settings and config model

- Add `AutomationSettings` as a first-class instance settings page.
- Define stable namespaces and keys for automation configuration.
- Separate team-level settings from per-bot settings where appropriate.
- Support presets for common modes: solo survival, team beat-game, resource farming, structure search.
- Document defaults and safe ranges for each setting.

### Commands

- Expand the automation command set beyond `beat`, `get`, `status`, `teamstatus`, and `stop`.
- Add `pause`, `resume`, `restart-phase`, and `abort-phase`.
- Add commands to set roles and objectives manually.
- Add commands to claim or release targets manually.
- Add commands to inspect planner decisions and current requirement queues.
- Add commands to dump or reset automation memory.

### gRPC, MCP, and protocol surface

- Add explicit automation RPCs instead of relying on generic command dispatch.
- Add automation status streaming and event streaming.
- Expose the automation phase, current action, planner queue, role, objective, and recovery state over RPC.
- Expose team summaries, claims, and shared memory snapshots over RPC.
- Add automation actions to MCP tooling so external agents can inspect and control automation directly.
- Add versioned proto messages for automation settings and telemetry.
- Add automation-specific audit log events.

### GUI client and operator experience

The official GUI client is in a different repository, but the following features are still needed:

- Dedicated automation settings page.
- Dedicated automation dashboard per instance.
- Per-bot automation panels showing phase, task tree, planner queue, deaths, and last recovery.
- Team view showing roles, quotas, structure targets, and shared objective.
- Map or world overlay for shared claims, portals, fortress hints, stronghold estimate, and portal-room estimate.
- Controls to pause, resume, reprioritize, or remove bots from a collaborative run.
- Controls to toggle collaboration on and off.
- Better surfacing of why a bot is stuck or what it is waiting on.
- Run history and post-run summaries.

## P4: observability, metrics, and operator tooling

- Add automation-specific metrics beyond general instance metrics.
- Track phase durations, retries, stalls, deaths, path failures, and completion outcomes.
- Track structure-search efficiency and false-positive rates.
- Track handoff success, portal success, and recovery success.
- Add automation event logs suitable for replaying a run timeline.
- Add structured "why this plan was chosen" traces for debugging planner behavior.
- Add operator-visible alerts for repeated failures or low completion probability.
- Add exportable run reports for comparing seeds, settings, and versions.

## P5: documentation and discoverability

Current public documentation focuses on installation, usage, commands, plugins, and general operation. Automation-specific documentation is still missing.

### User-facing docs

- Add automation command documentation.
- Add automation settings reference documentation.
- Add a guide for running a collaborative beat-game team.
- Add safety guidance for using automation on allowed/private servers only.
- Add troubleshooting docs for common automation failure modes.
- Add a glossary for automation terms such as role, objective, claim, shared memory, and recovery.

### Maintainer docs

- Document the automation architecture.
- Document the planner model and requirement normalization rules.
- Document the shared-coordinator lifecycle and invariants.
- Document the RPC and settings model for automation.
- Document expected test strategy and soak-test methodology.
- Maintain a versioned roadmap like this one as the implementation evolves.

## P6: cross-version and protocol coverage

SoulFire supports many Minecraft versions and both Java and Bedrock accounts. Automation coverage needs to be treated as a compatibility surface, not a single-version feature.

- Define an automation support matrix by protocol/version.
- Test automation against multiple versions, not just the current native one.
- Audit container, interaction, and entity behavior differences that affect automation.
- Audit dimension, portal, and combat differences across supported versions.
- Decide what automation behavior is supported for Bedrock connections and what is not.
- Surface unsupported automation modes clearly in settings and UI.

## P7: polish and long-tail improvements

- Better loadout preferences and cosmetic user preferences for automation runs.
- More nuanced anti-cheat-aware motion and input jitter for automation-specific actions.
- Better coexistence with other plugins and scripted behaviors.
- Better persistence and resumption when the server or SoulFire restarts mid-run.
- Better modularization so automation pieces can be reused outside the beat-game flow.
- Better separation between experimental automation features and stable ones.

## Suggested implementation order

If the goal is "10 SoulFire bots reliably beat the game in parallel", the highest-value order is:

1. Soak tests, failure classification, and recovery hardening.
2. Stronger fortress, stronghold, and portal-room solving.
3. End-fight safety and cooperative tactics.
4. Configurable collaboration and automation settings.
5. Automation RPCs and operator visibility.
6. GUI client dashboards and controls.
7. Broader task catalogue and parity work beyond beat-game.
8. Public documentation and long-tail polish.

## Notable currently missing product pieces

These are worth calling out explicitly because they are easy to overlook:

- No automation settings page is registered yet.
- No collaboration toggle exists yet.
- No automation-specific proto or gRPC service exists yet.
- No dedicated GUI client automation dashboard exists in this repository.
- No automation user documentation exists yet.
- No 10-bot soak-test suite exists yet.
- General survival automation breadth is still narrower than a mature AltoClef-like system.
