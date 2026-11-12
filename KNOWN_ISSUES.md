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
- **Nachtrag 2026-11-12:** KI-14 (Artefakte mit Shaderpack) und KI-16 (fehlende First-Person-Effekte
  mit Shaderpack) behoben, KI-15 neu aufgenommen. Die Zeilenangaben der übrigen Einträge stammen
  weiterhin vom 2026-11-02 und haben sich durch die Fixes um einige Zeilen verschoben – im Zweifel
  über die genannten Methodennamen suchen.

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
| KI-14| Shaderpack: Effekte „wild in der Luft“             | Hoch     | behoben |
| KI-15| Effekte umgehen das Shaderpack-Post-Processing     | Doku     | offen   |
| KI-16| Shaderpack: keine Effekte in der First Person      | Hoch     | behoben |

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

## KI-14 – Shaderpack: Effekte „wild in der Luft“ (behoben)

**Schwere:** Hoch · **Status:** behoben am 2026-11-12

**Ort:** `client/IrisCompat.java` (neu), `client/UpgradeGlowRenderer.java` (`replacesFoil`, `render`,
`wrapCustomRenderer`)

**Symptom:** Mit aktivem Shaderpack (Iris/Oculus) erschienen Glow, Glint und Blitze aufgewerteter
Items nicht am Schwert, sondern als losgelöste Geometrie irgendwo in der Welt. Ohne Shaderpack trat
es nie auf.

**Ursache:** Shaderpacks rendern das Level ein zweites Mal in die Shadow-Map und rufen dabei den
Vanilla-Item-Renderer für gehaltene Items erneut auf — mit der **Shadow-Kamera-Matrix** in der
Item-Pose. `ItemRendererMixin` reichte beide Aufrufe ungefiltert an `UpgradeGlowRenderer` weiter, der
die Eckpunkte munter aufzeichnete. Geflusht wird aber nur einmal, im Haupt-Pass (`AFTER_LEVEL`), und
zwar mit den **Hauptkamera-Matrizen**. Die Shadow-Pass-Vertices landeten dadurch in einem völlig
falschen Raum.

**Fix:** `client/IrisCompat.isShadowPass()` liest per Reflection `ShadowRenderer.ACTIVE` (sowohl
`net.irisshaders.iris.shadows.ShadowRenderer` für Iris/NeOculus als auch das alte
`net.coderbot.iris.pipeline.ShadowRenderer` für Oculus). Keine Build-Abhängigkeit zu Iris; fehlt die
Klasse, greift der Guard einfach nicht und es bleibt beim alten Verhalten. `replacesFoil`, `render`
und `wrapCustomRenderer` brechen ab, solange der Shadow-Pass läuft. Haupt-Pass und First-Person-Hand
sind unverändert, die Effekte bleiben also mit Shaderpack sichtbar.

**Verifikation:** `gradlew build` grün. Dritte Person im Client mit Shaderpack am 2026-11-12 bestätigt.
Die Egoperspektive war ein eigener Fehler – siehe KI-16.

**Restrisiko:** Das Flag ist ein Implementierungsdetail von Iris und kann in einer künftigen Version
umbenannt oder verschoben werden. Dann fällt das Feature still auf das alte, fehlerhafte Verhalten
zurück (kein Crash). Bei erneutem Auftreten zuerst prüfen, ob `ShadowRenderer.ACTIVE` noch existiert.

---

## KI-15 – Effekte umgehen das Shaderpack-Post-Processing

**Schwere:** Doku · **Status:** offen

**Ort:** `client/UpgradeGlowEvents.java` / `GameRendererMixin` (`AFTER_LEVEL` → `composite()`)

**Verhalten:** Iris ruft sein `finalizeLevelRendering` (Final-Composite inkl. eigener Blur-, Bloom- und
Tonemapping-Pässe) **vor** dem NeoForge-Event `AFTER_LEVEL` auf. `composite()` läuft damit erst
danach und wird von der Shaderpack-Kette nicht mehr erfasst: Halo, weite Aura und Hand-Mist werden
roh auf den fertigen Frame addiert.

**Folgen:**

- Shaderpack-Effekte (Bloom, Farbkorrektur, Nebel, Wassercaustics, Tonemapping) wirken nicht auf die
  Upgrade-Effekte. Sie können dadurch deutlich „flacher“ oder farblich daneben wirken als vanilla.
- Das Glühen kann über Wasser-/Nebelkulissen liegen, wo es eigentlich dahinter gehören würde.
- Der Depth-Test bleibt korrekt: Iris nutzt für sein Gbuffer denselben Depth-Texture wie das
  Haupt-Target. `copyDepthFrom(main)` liefert also weiterhin gültige Werte, Verdeckung durch
  Weltgeometrie funktioniert.
- Umgekehrt gilt dasselbe ohne Shaderpack für Vanilla-Post-Effekte (siehe KI-6).

**Optionen:**

1. **Akzeptieren und dokumentieren.** Kein Aufwand, kein Kompatibilitätsrisiko. Effekte sind sichtbar,
   nur nicht vom Shaderpack „eingefärbt“.
2. **Früher compositen**, z. B. direkt nach dem Hand-Flush statt am Frame-Ende. Löst nur den
   Hand-Teil und verschiebt das Problem (Halo läge dann unter den Screen-Effekten, siehe KI-6).
3. **In die Iris-Pipeline einhaken** (eigener Pass vor dem Final-Composite). Liefert das beste Bild,
   ist aber versionsfragil und widerspricht dem bisherigen Ansatz, Iris nicht als Abhängigkeit zu
   führen. Größerer Eingriff — laut `AGENTS.md` vorher abstimmen.

**Zu tun:** Entscheidung steht aus. Vorher im Client mit Shaderpack vergleichen, wie störend es
tatsächlich ist.

---

## KI-16 – Shaderpack: keine Effekte in der First Person (behoben)

**Schwere:** Hoch · **Status:** behoben am 2026-11-12

**Ort:** `client/UpgradeGlowRenderer.java` (`phaseTypesFor`, `captureDeferredHandState`, `markRecorded`,
`flushWorld`, `renderHandAura`)

**Symptom:** Nach dem Fix von KI-14 war die dritte Person korrekt, in der Egoperspektive aber **gar
nichts** mehr zu sehen: kein Glint, kein Halo, keine Blitze, keine Aura.

**Ursache:** Ein Shaderpack rendert die Hand nicht über `GameRenderer.renderItemInHand`, sondern
**innerhalb des Level-Passes**: `MixinLevelRenderer.iris$beginTranslucents` ruft
`HandRenderer.renderSolid`, `iris$endLevelRender` ruft `renderTranslucent`; der Vanilla-Aufruf in
`ItemInHandRenderer` wird per `MixinGameRenderer` abgeschaltet. Daraus folgen zwei Probleme:

1. Zu dem Zeitpunkt ist unsere Phase noch `WORLD`. `phaseTypesFor` akzeptierte `FIRST_PERSON_*` aber
   nur in `Phase.HAND` → es wurde nichts aufgezeichnet, und der spätere `flushHand()` lief leer.
2. Selbst mit Aufzeichnung wäre ein Flush direkt nach der Hand falsch: `finalizeLevelRendering`
   (Iris-Composite) läuft erst danach und überschreibt den Frame. Das Material muss also bis nach dem
   Level warten – und zwar mit **den Matrizen des Hand-Passes**, denn `HandRenderer` setzt eine eigene
   Projektion (`0.125 * handProjection`) und eine eigene Modelview (Bob, ohne inverse Kamerarotation).

**Fix:**

- `phaseTypesFor` behandelt `FIRST_PERSON_*` auch in `Phase.WORLD` als Handgeometrie. Das kann in
  Vanilla nicht auftreten, dort rendert die Hand erst nach dem Level.
- Beim ersten Zugriff kopiert `captureDeferredHandState()` Modelview und Projektion des Passes;
  `markRecorded(level, hand)` merkt sich getrennt, dass Handgeometrie aus dem Level-Pass stammt.
- `flushWorld` zeichnet nach dem Welt-Flush zusätzlich die Handpuffer mit diesen Matrizen – also nach
  dem Iris-Composite, im `AFTER_LEVEL`-Event.
- `renderHandAura` bildet in diesem Fall die Bildschirmachsen über die gespeicherte Modelview ab
  (`M^-1 * X/Y`), weil der Hand-Pass ohne inverse Kamerarotation arbeitet. Der Vanilla-Pfad behält die
  Weltkamera-Achsen.

**Verifikation:** `gradlew build` grün. Visueller Test mit Shaderpack in der Egoperspektive offen.

**Restrisiko:** Der Fix hängt an der Reihenfolge „Iris-Composite vor `AFTER_LEVEL`“ (verifiziert für
Iris 1.8.14) und daran, dass der Hand-Pass im Level-Pass bleibt. Ändert eine künftige Iris-Version
das, fehlen die Effekte in der Egoperspektive wieder – ohne Crash.

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

Zweiter Durchlauf **mit Shaderpack** (KI-14): prüfen, dass die Effekte am Item bleiben und nicht als
losgelöste Geometrie in der Luft hängen. Sinnvoll sind ein Pack mit Shadow-Map (die meisten) und
zusätzlich die Egoperspektive, weil der Hand-Flush einen anderen Pfad nimmt.

## Empfohlene Reihenfolge in der nächsten Session

1. Visueller Test im Client (siehe oben) – inkl. Durchlauf mit Shaderpack (bestätigt KI-14/KI-16 und
   entscheidet, ob KI-15 störend ist). Er entscheidet außerdem, ob KI-1/KI-2 überhaupt relevant sind.
2. KI-3 (Einzeiler) und KI-5 (Einzeiler) direkt mitnehmen.
3. KI-1 entscheiden (Design) – danach ggf. KI-2 messen.
4. KI-12 (Vault-Note) nachziehen, wenn die Balance-Werte bestätigt sind.
5. Commit erst nach dem visuellen Test.
