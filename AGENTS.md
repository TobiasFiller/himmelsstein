# AGENTS.md

Guidance for AI agents working in this repository.

## Working Agreement: the human decides, the agent implements

The human owns design and scope decisions; the agent executes them. Do not silently pick an
interpretation when a request leaves room for one.

- **Ask first, then implement.** Whenever there is a real choice — naming, player-facing texts,
  advancement frame/icon/background, balance numbers (upgrade chances, max levels, stat scaling,
  drop rates), recipes, particles/look & feel, or which of several technical approaches to use —
  present 2-4 concrete options with trade-offs and let the human pick before writing code.
- **Confirm the scope of a multi-part request** (e.g. "implement the upgrade stones") with a short
  list of what will and will not be included.
- **Small, unambiguous fixes** (compile errors, typos, a clearly wrong path or value, a crash)
  may be done directly, but call them out in the summary.
- **Do not expand scope.** If you spot an unrelated bug or improvement while working, report it
  and ask — do not fold it into the current change.
- **Outline larger refactors** (new event systems, API changes, moving packages) and wait for
  approval before starting.
- If something is ambiguous, prefer one short clarifying question over guessing and redoing work
  afterwards.
- Still verify your own work: `gradlew build` and, for gameplay changes, `gradlew
  runGameTestServer` before reporting back.

## Project

**Himmelsstein** (`himmelsstein`) is a Minecraft mod for **NeoForge 1.21.1** written in Java 21.
It adds an equipment upgrade system inspired by *Last Chaos*: players use stones and runes to
upgrade weapons and armour up to **+30**, with a risk of destroying the item on failure.

- Mod ID: `himmelsstein`
- Root package: `net.tobiasfiller.himmelsstein`
- Build system: Gradle with `net.neoforged.moddev` 2.0.148
- Versions live in `gradle.properties` (single source of truth). Do not hardcode MC/NeoForge versions elsewhere.
- License: All Rights Reserved (`mod_license` in `gradle.properties`). `TEMPLATE_LICENSE.txt`
  covers only the MDK template files.

The repository is currently the **NeoForge MDK template** (`example_block`, `example_item`,
`example_tab`). The upgrade system is not implemented yet; the example content is placeholder
scaffolding and may be replaced as the real feature set lands.

## Design & Concept (Obsidian Vault)

Feature design and concept work does **not** live in this repo. It lives in the "Zweiter-Kopf"
Obsidian vault at:

`C:\Users\tfill\Documents\Zweiter-Kopf\Minecraft Stuff\Himmelsstein`

Consult this note before designing or implementing a feature, so the code matches the intended
design. Read-only by default — do not modify the vault unless explicitly asked.

- [Himmelsstein](<C:/Users/tfill/Documents/Zweiter-Kopf/Minecraft Stuff/Himmelsstein/0 Himmelsstein.md>)
  — concept note: upgrade system, stones (Himmelstein, Kraftstein, Glücksstein), protection runes,
  upgrade chance tables, and inspiration/sources
- [Offene Punkte](<C:/Users/tfill/Documents/Zweiter-Kopf/Minecraft Stuff/Himmelsstein/2 Offene Punkte.md>)
  — collection of design questions that are deliberately still undecided

Design questions the human does not want to decide right now go **into `2 Offene Punkte.md`**, not
silently into a design note or into code. Number them (`O1`, `O2`, …), state status
(offen / vertagt / entschieden) and the options, and link the affected note. When a question becomes
blocking during implementation, point back to its entry and ask instead of choosing.

The note is the source of truth for balance values. When a feature is implemented or its design
changes, suggest updating the note (but leave the actual edit to the user). Do not invent balance
numbers that are not in the note — ask.

## Build & Run

Use the Gradle wrapper. On Windows use `gradlew.bat`, otherwise `./gradlew`.

```bash
./gradlew build                # compile + jar
./gradlew runClient            # dev client
./gradlew runServer            # dev server
./gradlew runData              # datagen -> src/generated/resources
./gradlew runGameTestServer    # headless game tests
```

- Game tests are enabled for the `himmelsstein` namespace in the `client`, `server` and
  `gameTestServer` runs.
- The `data` run writes to `src/generated/resources`, which is registered as a resource source set.
- `gradle.properties` currently enables the Gradle daemon, parallel builds, build caching and the
  configuration cache. Do not change these build settings as part of a feature without asking.

## Source Layout

```
src/main/java/net/tobiasfiller/himmelsstein/
  Himmelsstein.java             mod entry point; registers DeferredRegisters and the creative tab
  HimmelssteinClient.java       client-only setup
  Config.java                   common config (still MDK example values)

src/main/
  templates/META-INF/neoforge.mods.toml   mod metadata template (versions expanded from gradle.properties)
  resources/assets/himmelsstein/lang/     translations
  resources/data/himmelsstein/            recipes, loot tables, tags, worldgen (not created yet)
```

As the mod grows, follow the Milten Magic layout unless there is a reason not to:

```
  common/block/                 Block subclasses
  common/blockentity/           BlockEntity subclasses
  common/item/                  custom items, upgrade stones, runes
  core/event/                   @EventBusSubscriber game-bus handlers
  core/gametest/                GameTest registration + tests
  core/registry/                DeferredRegister holders per registry type
  core/utility/                 shared helpers
  client/                       client-only setup and renderers
```

## Conventions

- **Java 21**, 4-space indentation, one public class per file.
- Keep a `common/` vs `client/` split. Anything referencing client-only classes
  (`net.minecraft.client.*`, renderers) belongs in `client/` or must be guarded by
  `Dist.CLIENT` / `level.isClientSide`.
- **Registration is centralized**: add every new registry entry to a dedicated class in
  `core/registry/` once that package exists. Do not scatter ad-hoc `DeferredRegister`s.
- Use `DeferredRegister` / `DeferredHolder`; reference registered objects via `.get()`.
- Event handlers use `@EventBusSubscriber(modid = Himmelsstein.MODID, bus = ...)` with
  `static @SubscribeEvent` methods. Mod-bus lifetime events go in `client/` or a `Bus.MOD`
  subscriber; gameplay events use `Bus.GAME`.
- All player-facing text is translatable: add keys to `assets/himmelsstein/lang/en_us.json` and
  build them with `Component.translatable("tooltip." + Himmelsstein.MODID + ...)`.
- Recipe, loot table, blockstate and model JSON follow the vanilla 1.21 folder layout and use the
  `himmelsstein` namespace.
- Upgrade level and other per-item data must be stored in item data components, never in mutable
  item fields (items are singletons, stacks are not).

## 1.21.1 / NeoForge Pitfalls (important)

1. **Item data is not raw NBT.** Arbitrary item tags live in
   `DataComponents.CUSTOM_DATA` (`CustomData.of(tag)` / `stack.get(DataComponents.CUSTOM_DATA)`).
   `ItemStack#get` returns a detached copy — modify the copy, then write it back. For first-class
   data, prefer a custom `DataComponentType` (e.g. `upgrade_level`) over stuffing NBT.
2. **Items are singletons, stacks are not.** Never keep per-stack mutable state (counters,
   "armed" flags, upgrade levels) in an item field. `inventoryTick` runs once *per stack*.
3. **Tooltips and item names are client-visible.** Apply the `+N` suffix through the item's name
   component/tooltip hooks, not by mutating stacks on the server only. Renaming must not erase the
   upgrade level.
4. **Attribute modifiers are the right way to scale damage/armour.** Add/remove modifiers keyed by
   a stable `ResourceLocation` so re-equipping or reloading does not stack them. Do not hardcode
   final damage values into `Item` subclasses.
5. **Never destroy an item on the client.** Upgrade rolls happen server-side; sync only the
   result. Failure that destroys the stack must be handled in a server-authoritative code path
   (command, menu click, or item use), and the result must be validated.
6. **Container/menu logic needs a `Menu` + `MenuType` + server-side `Container`.** If upgrades use a
   GUI dialog, do not trust client-provided slot contents; re-validate the item and stones on the
   server.
7. **Worldgen registries cannot be filled from code** in 1.21. Configured/placed features go in
   `data/himmelsstein/worldgen/...` and are attached via
   `data/himmelsstein/neoforge/biome_modifier/...`.
8. **Registry-dependent logic belongs in events**, not in `static` initializers, so it does not run
   before registries are frozen.
9. Render types for cutout/translucent blocks are set in client setup
   (`ItemBlockRenderTypes.setRenderLayer`).

## Adding Things

**New item / stone / rune:** create the class (or use a data-driven item) → register it → add
`assets/himmelsstein/models/item/<name>.json` + texture → add `item.himmelsstein.<name>` to
`en_us.json`. Add it to the mod's creative tab.

**New upgrade mechanic:** check the concept note for the chance table, max level and failure
behaviour first. Put the roll in a server-side helper (e.g. `core/utility/UpgradeHelper`) so it can
be unit-tested and reused by commands and menus. Cover the behaviour with a GameTest.

**New command:** register it in a `RegisterCommandsEvent` handler on the game bus (the concept
mentions `/equipment-upgrade [lvl]` as a development command). Commands must be server-side and
respect the configured max level.

**New block:** create the class in `common/block/` → register block and `BlockItem` → add
blockstate, block model, item model → add loot table under `data/himmelsstein/loot_tables/blocks/`
→ add `block.himmelsstein.<name>` translation. Add a `BlockEntity` if it needs logic/ticking.

## Testing & Verification

- Run `./gradlew build` before considering a change done.
- For gameplay logic (upgrade rolls, item destruction, protection runes, attribute scaling,
  command behaviour), add a `@GameTest` and run `./gradlew runGameTestServer`.
- Upgrade chance logic should be deterministic under test (inject/seed the `RandomSource` or test
  the boundary conditions) rather than relying on luck.
- Do not "fix" tests by weakening assertions; tests document regressions that were actually reported.

## Repository Hygiene

- **Do not edit generated/legacy directories:** `build/`, `.gradle/`, `run/`, `runs/`,
  `archive/`, `src/generated/resources/` (regenerate with `runData`).
- Do not commit IDE metadata (`.idea/`, `.vscode/`, `*.iml`, `*.ipr`, `*.iws`).
- Use short, lowercase, imperative-ish commit summaries (e.g. `add himmelstein`, `port to neoforge`).
  Keep changes focused per commit.
- Match surrounding style rather than reformatting unrelated code.
