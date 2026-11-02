# Known Issues

Sammelstelle für offene Punkte aus Code-Reviews, die bewusst **nicht** sofort behoben wurden.
Zweck: In einer späteren Session direkt hier weiterarbeiten, ohne das Review zu wiederholen.

## Kontext

- **Stand:** 2026-11-02
- **Basis:** Patch `add_weapon_upgrade_visual_effects` (aus dem Vault: `_assets/add_weapon_upgrade_visual_effects.patch`)
  ist angewendet, aber **noch nicht committet**. Alle Zeilenangaben unten beziehen sich auf diesen
  Arbeitsstand.
- **Verifiziert:** `gradlew build` grün, `gradlew runGameTestServer` 15/15 grün.
- **Nicht verifiziert:** das eigentliche Aussehen im Client (`gradlew runClient`). Die Mixins sind
  client-only, der GameTest-Server lädt sie nie.

Betroffene Dateien (neu, aus dem Patch):

```
src/main/java/net/tobiasfiller/himmelsstein/client/UpgradeGlowRenderer.java
src/main/java/net/tobiasfiller/himmelsstein/client/UpgradeRenderTypes.java
src/main/java/net/tobiasfiller/himmelsstein/client/UpgradeLightning.java
src/main/java/net/tobiasfiller/himmelsstein/client/UpgradeEffectBuffers.java
src/main/java/net/tobiasfiller/himmelsstein/client/UpgradeGlowEvents.java
src/main/java/net/tobiasfiller/himmelsstein/core/utility/UpgradeGlow.java
src/main/java/net/tobiasfiller/himmelsstein/mixin/client/*.java
src/main/resources/assets/himmelsstein/shaders/**
```

## Übersicht

| ID   | Titel                                              | Schwere  | Status |
|------|----------------------------------------------------|----------|--------|
| KI-1 | `maxLevelThisFrame` ist global                     | Mittel   | offen  |
| KI-2 | Post-Chain: 8 Fullscreen-Pässe pro Frame           | Mittel   | offen  |
| KI-3 | NPE-Risiko bei Quads ohne Direction                | Mittel   | offen  |
| KI-4 | `activeCapture` ist ein statisches Einzelfeld      | Niedrig  | offen  |
| KI-5 | Depth-Test bleibt nach `composite()` aus           | Niedrig  | offen  |
| KI-6 | Halo liegt über den Screen-Effekten                | Niedrig  | offen  |
| KI-7 | Keine Effekte in GUI / Item-Frame / Boden-Items    | Doku     | offen  |
| KI-8 | `required: true` + `defaultRequire: 1`             | Niedrig  | offen  |
| KI-9 | `ColorModulator` nicht explizit zurückgesetzt      | Niedrig  | offen  |
| KI-10| Allokationen pro Frame in `UpgradeLightning`       | Niedrig  | offen  |
| KI-11| RenderType-Caches werden nie geleert               | Niedrig  | offen  |
| KI-12| Balance-Werte nur im Code, nicht in der Vault-Note | Doku     | offen  |
| KI-13| `UpgradeGlowRenderer` macht vier Dinge (571 Zeilen)| Refactor | offen  |

---

## KI-1 – `maxLevelThisFrame` ist global

**Schwere:** Mittel · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:215`, `:224`, `:530`

**Symptom:** Halo-Radius und die „weite Aura“ werden aus dem **höchsten** Upgrade-Level aller in
diesem Frame gerenderten Items berechnet. Ein +30-Item im Bild gibt damit auch einem +5-Item den
vollen Radius und die weite Aura.

**Ursache:** `maxLevelThisFrame = Math.max(maxLevelThisFrame, level)` in `markRecorded()` und
`UpgradeGlow.progress(maxLevelThisFrame)` / `hasWideAura(maxLevelThisFrame)` in `composite()`.

**Fix-Idee:** Radius/Aura pro Item statt pro Frame. Entweder eigene Targets pro Level-Band, oder
die Silhouetten mit bereits eingerechneter Breite emittieren (dann ist der Blur-Radius konstant).
Achtung: `Radius` ist ein Uniform, das `PostChain.setUniform()` auf **alle** Pässe setzt.

**Entscheidung nötig:** Ist das überhaupt störend? Wenn immer nur der Spieler selbst ein
aufgewertetes Item hält, fällt es nicht auf.

---

## KI-2 – Post-Chain: 8 Fullscreen-Pässe pro Frame

**Schwere:** Mittel · **Status:** offen

**Ort:** `resources/assets/himmelsstein/shaders/post/upgrade_glow.json` (8 Pässe),
`client/UpgradeGlowRenderer.java:205-232`

**Symptom:** Sobald ein aufgewertetes Item sichtbar ist, laufen 8 Blur-Pässe über die komplette
Framebuffer-Auflösung (bei 4K ~8 Mio. Pixel, je bis zu ~16 Taps). Das ist der teuerste Teil des
Features.

**Fix-Idee:** Targets mit halber Auflösung. `PostChain` unterstützt `{"name": "...", "width": ...,
"height": ...}` pro Target – **aber** `PostChain.resize()` skaliert nur Targets, die in
`fullSizedTargets` liegen (also exakt Screen-Größe hatten). Halb aufgelöste Targets würden bei
Fenstergrößenänderung nicht mitwachsen → eigenes Resize-Handling nötig.

**Vorher:** Messen (F3 oder Spark), ob es auf der Zielhardware überhaupt relevant ist.

---

## KI-3 – NPE-Risiko bei Quads ohne Direction

**Schwere:** Mittel · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:295`

```java
if (outline != null && quad.getDirection().getAxis() != Direction.Axis.Z) {
```

**Symptom:** `BakedQuad.getDirection()` kann bei modellierten/moddeten Modellen `null` sein →
NPE. Betroffen ist nur der Pfad „Lightning + PROJECTILE“ (`outline != null`, also Bogen/Armbrust
ab +15).

**Fix-Idee:** `quad.getDirection() != null && quad.getDirection().getAxis() != Direction.Axis.Z`.

**Aufwand:** Einzeiler.

---

## KI-4 – `activeCapture` ist ein statisches Einzelfeld

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:119`, `:324`, `:338`

**Symptom:** Wenn ein Custom-Renderer verschachtelt ein weiteres Item rendert (Shield/Banner,
moddete Items mit Inhalt), überschreibt der innere `wrapCustomRenderer()` die äußere Capture. Das
äußere `finishCustomRenderer()` macht dann nichts → Glow/Glint fehlen für das äußere Item. Kein
Crash.

**Fix-Idee:** `Deque<CustomCapture>` statt eines Feldes (push in `wrapCustomRenderer`, pop in
`finishCustomRenderer`).

---

## KI-5 – Depth-Test bleibt nach `composite()` aus

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:219-229`

**Symptom:** `RenderTarget._blitToScreen()` ruft `_disableDepthTest()` und stellt den State nicht
wieder her. `composite()` läuft am TAIL von `renderLevel`, also **vor** der GUI. Die
Vanilla-GUI-RenderTypes setzen ihren Depth-State selbst, deshalb praktisch unkritisch – Mod-GUIs,
die das voraussetzen, könnten aber betroffen sein.

**Fix-Idee:** `RenderSystem.enableDepthTest()` am Ende von `composite()`.

**Aufwand:** Einzeiler.

---

## KI-6 – Halo liegt über den Screen-Effekten

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:205-232` in Zusammenspiel mit
`GameRenderer.renderItemInHand` (Screen-Effekte werden nach den Händen gerendert)

**Symptom:** Vignette, Wasser- und Feuer-Overlay werden vor `composite()` gezeichnet, der Halo
wird darüber addiert. Kosmetisch.

**Fix-Idee:** Composite früher (z. B. direkt nach dem Hand-Flush) oder die Screen-Effekte
akzeptieren.

---

## KI-7 – Keine Effekte in GUI / Item-Frame / Boden-Items

**Schwere:** Doku · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java:237-239` (`replacesFoil`), `:533-543`
(`phaseTypesFor`)

**Verhalten:** Effekte gibt es nur in `THIRD_PERSON_*` und `FIRST_PERSON_*`. Items im Inventar, im
Item-Frame, auf dem Boden und in der Hand eines anderen Spielers (außer über die Hand-Renderer)
bekommen nichts; im Inventar bleibt der Vanilla-Glint (falls das Item `foil` hat), sonst ist das
Upgrade dort unsichtbar.

**Zu tun:** Als bewusste Einschränkung im Javadoc von `UpgradeGlowRenderer` festhalten, damit es
nicht wie ein Bug wirkt. Oder Feature-Wunsch daraus machen (Item-Frame/Boden).

---

## KI-8 – `required: true` + `defaultRequire: 1`

**Schwere:** Niedrig · **Status:** offen

**Ort:** `resources/himmelsstein.mixins.json`

**Symptom:** Bei jedem Minecraft-Update crasht das Spiel hart, wenn `renderItemInHand`,
`renderByItem` oder `popPose` wandern.

**Fix-Idee:** Für Release `require = 0` + Logging erwägen (Feature degradiert statt Crash). Für
Dev ist der harte Fehler hilfreich – Entscheidung offen.

---

## KI-9 – `ColorModulator` nicht explizit zurückgesetzt

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeRenderTypes.java:118-136` (Bolt-RenderTypes nutzen
`RENDERTYPE_LIGHTNING_SHADER`)

**Symptom:** Der Lightning-Shader multipliziert mit dem globalen `ColorModulator`. Der Patch setzt
ihn nicht; normalerweise ist er an dieser Stelle weiß, ein anderer Mod könnte ihn aber verändert
hinterlassen → eingefärbte Blitze.

**Fix-Idee:** Vor dem Flush `RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)`.

---

## KI-10 – Allokationen pro Frame in `UpgradeLightning`

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeLightning.java:60-118` (`Frame.fromPoints`), `:143-159` (`orbitArcs`),
`:202-242` (`jagged`, `Painter.segment`)

**Symptom:** Pro Item und Frame entstehen viele kleine `Vector3f`/Arrays; `fromPoints` iteriert bis
zu 16 384 Punkte und allokiert pro Punkt. Bei 1–2 Items egal, bei vielen Items mit Blitzen
GC-Druck.

**Fix-Idee:** Reuse-Buffer / mutable Vektoren, Punkte im `CustomCapture` bereits in ein
`float[]`-Array schreiben.

---

## KI-11 – RenderType-Caches werden nie geleert

**Schwere:** Niedrig · **Status:** offen

**Ort:** `client/UpgradeRenderTypes.java:71-72` (`WORLD_TEXTURED`/`HAND_TEXTURED`),
`client/UpgradeEffectBuffers.java:40` (`entries`)

**Symptom:** Beide Maps wachsen pro gesehener Textur/RenderType und werden nie geleert (bounded,
kein echter Leak). Nach Resource-Reload sind die Einträge zusätzlich veraltet.

**Fix-Idee:** `clear()` in `UpgradeGlowRenderer.onResourcesReloaded()` mit aufrufen.

---

## KI-12 – Balance-Werte nur im Code, nicht in der Vault-Note

**Schwere:** Doku · **Status:** offen

**Ort:** `core/utility/UpgradeGlow.java:9-29`, festgeschrieben in
`core/gametest/UpgradeGameTests.java` (`glowColorBlendsBetweenAnchors`,
`glowIntensityRisesUntilPeakThenStays`, `glowEffectsUnlockAtTheirLevels`)

**Werte:**

- Farbanker: +1 Weiß, +6 Gelb, +11 Gold/Orange, +16 Rot, +21 Magenta, +26 Gold (bis +30 konstant)
- Intensität: 2 % bei +1, linear bis 20 % bei +15, danach konstant 20 %
- Weite Aura ab +5, Blitze am Griff ab +15, Blitze an der Klinge ab +20

**Zu tun:** In `0 Himmelsstein.md` (Vault) nachtragen – laut `AGENTS.md` ist die Note die Quelle
der Wahrheit für Balance-Werte. Alternativ Konstanten nach `UpgradeTables` verschieben, wo
`MAX_LEVEL` schon liegt.

---

## KI-13 – `UpgradeGlowRenderer` macht vier Dinge

**Schwere:** Refactor · **Status:** offen

**Ort:** `client/UpgradeGlowRenderer.java` (571 Zeilen)

**Inhalt:** Frame-Lifecycle, Baked-Model-Emission, Custom-Renderer-Capture, Post-Chain-Verwaltung.

**Fix-Idee:** Split in z. B. `UpgradeGlowPostChain`, `UpgradeGlowCapture` und den Renderer.
Laut `AGENTS.md` vorher mit dem Menschen abstimmen (größerer Refactor).

---

## Bereits geprüft – kein Issue

Damit das in der nächsten Session nicht erneut untersucht wird:

- **Mixin-Descriptor `@ModifyArg`** auf `renderByItem`: korrekt. Der Handler spiegelt die Args der
  *aufgerufenen* Methode (6 Args), nicht die von `render()`. `ModifyArgInjector` verlangt exakt
  diese Args (`Arrays.equals(args, methodArgs)`).
- **Bus-Routing:** `@EventBusSubscriber` ohne `bus` ist in 1.21.1 richtig – `bus` ist deprecated,
  FML routet pro Methode anhand `IModBusEvent` automatisch. `RegisterShadersEvent` und
  `RegisterClientReloadListenersEvent` landen auf dem Mod-Bus, `RenderLevelStageEvent` auf dem
  Game-Bus.
- **Reihenfolge:** `AFTER_LEVEL` feuert vor `renderItemInHand` (GameRenderer:1279 vs. 1283) →
  World-Flush vor Hand-Phase.
- **Buffer-Lifecycle:** `ByteBufferBuilder` pro RenderType wird korrekt wiederverwendet;
  `RenderType.draw(MeshData)` → `VertexBuffer.upload()` schließt das `MeshData` und gibt die Slice
  zurück. Kein Leak, keine Doppel-Freigabe.
- **Depth:** `copyDepthFrom` + `LEQUAL` für World, `NO_DEPTH_TEST` für Hand ist korrekt, weil
  `renderHandsWithItems` vorher `GL_DEPTH_BUFFER_BIT` cleart.
- **Shader-Pfade:** `shaders/program/upgrade_glow_blur.json` mit `"vertex": "blur"` löst auf die
  Vanilla-`program/blur.vsh` auf (liefert `sampleStep`/`texCoord`).
- **Uniforms:** `FogStart`/`FogEnd`/`GlintAlpha` werden von `ShaderInstance.apply()` automatisch
  gesetzt – der Glint wird nicht weggefadet. `safeGetUniform` liefert `DUMMY_UNIFORM` statt `null`.
- **Accessor-Mixins:** `CompositeState.textureState` ist ein echtes Feld, `cutoutTexture()` ist
  `protected` → `@Invoker` ok; `textureOf()` ist mit `instanceof` abgesichert.

## Offene Aufgabe: visueller Test

Noch nie im Client gesehen. Vorgehen:

```bash
./gradlew runClient
```

Dann in die dritte Person (F5) und:

```
/summon minecraft:zombie ~ ~ ~ {NoAI:1b,Silent:1b,PersistenceRequired:1b,HandItems:[{id:"minecraft:iron_sword",count:1,components:{"himmelsstein:upgrade_level":30}},{id:"minecraft:air"}],HandDropChances:[0.0f,0.0f]}
```

Vergleichswerte: `5`, `15`, `20`, `30` (weite Aura ab 5, Griff-Blitze ab 15, Klingen-Blitze ab 20).
Bei Problemen im Log nach `Failed to load upgrade glow post chain` suchen.

## Empfohlene Reihenfolge in der nächsten Session

1. Visueller Test im Client (siehe oben) – entscheidet, ob KI-1/KI-2 überhaupt relevant sind.
2. KI-3 (Einzeiler) und KI-5 (Einzeiler) direkt mitnehmen.
3. KI-1 entscheiden (Design) – danach ggf. KI-2 messen.
4. KI-12 (Vault-Note) nachziehen, wenn die Balance-Werte bestätigt sind.
5. Commit erst nach dem visuellen Test.
