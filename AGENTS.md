# AGENTS.md

Minecraft Forge **1.20.1** client-side mod "Just Enough Hiding" (JEH): records what hides JEI
content, reveals it, and applies the user's own hide list through JEI's visibility API.

## Commands (Windows / PowerShell)
- Build: `.\gradlew.bat build` → `build\libs\justenoughhiding-<version>.jar`
- Smoke test (always run after a build): `.\gradlew.bat runData`
- Skip optional deps for local runs: `-PnoJei`, `-PnoKubeJS`, `-PnoEmi`
- No unit tests and no `runClient` flow; testing is manual in the user's pack (see Testing).
- Needs JDK 17. This machine points Gradle at it through the global
  `~/.gradle/gradle.properties` (`org.gradle.java.home`).

## Versioning & commits
- Bump `mod_version` in `gradle.properties` on **every** change; keep the `-dev` suffix.
  Bugfix → patch, feature → minor (e.g. `0.14.2-dev`).
- Only commit when the user explicitly asks. Write the Chinese commit message to a UTF-8 file and
  `git commit -F <file>` (avoids mojibake); stage with `git add -A` first.
- Reply to the user in Chinese.

## Architecture (not obvious from filenames)
- The GUI must stay free of JEI/`ItemStack`. Viewer-specific code lives behind
  `client/viewer/ViewerAdapter` + `Adapters` (JEI impl: `client/viewer/jei/JeiAdapter`);
  `intent/` and `listehiding/` are viewer-agnostic. Do not import JEI into `client/gui`.
- `IntentTarget` is a sealed interface: `Ingredient` / `Recipe` / `RecipeCategory` / `Tag` /
  `Pattern` / `Unset`. `client/viewer/TargetKeys` (id, kind key, friendly label) and
  `TargetMatcher` (glob/regex) are the shared helpers — reuse them instead of re-parsing.
- Intent recording is done by mixins (`mixin/jei/intent/*`) calling `JeiIntentRecorder`. Any JEI
  mutation JEH makes itself must run inside `JeiIntentRecorder.runSuppressed(...)`, otherwise it is
  recorded with `source=unknown`.
- Reveal: `mixin/jei/RevealMixin` forces `IngredientVisibility.isIngredientVisible` to `true`.
  `client/jehide/JeHide` overrides that for its own targets via `JeHide.isHidden(...)`; JEHide is
  driven by both `ListEHiding` and recorded hide-intents.
- Config files live in `config/jeh/`: `client.toml` (`JehConfig`), `listehiding.json`
  (`ListEHidingStore`), `intentoverrides.json` (`IntentOverrides`). The mod never generates
  `listehiding.json` — it is hand-written/edited.
- New mixins must be registered in `src/main/resources/justenoughhiding.mixins.json` (`client`).
  `mixin/JehMixinPlugin` only applies `*.mixin.jei.*` when JEI is loaded, and `*.mixin.emi.*` when EMI is loaded.
- No JEI/EMI class may be referenced from code that runs unconditionally (`client/gui`,
  `client/jehide/JeHide`, `JehClientEvents`, KubeJS binding) — otherwise the mod hard-depends on that
  viewer. `client/jehide/JeHide` is a viewer-agnostic facade that only calls
  `Adapters.active().reapplyHides()/tickHides()`; the JEI engine lives in `client/jehide/JeiHide`.

## Optional integrations
- JEI: `compileOnly` API + `runtimeOnly` forge jar; `mods.toml` requires `[15.55.0,)`. The forge
  jar is runtime-only, so JEI internal classes (e.g. `IClientToggleState`, `IngredientFilter`) are
  **not** on the compile classpath — reach them with a mixin or reflection.
- KubeJS: `compileOnly`; discovered via `src/main/resources/kubejs.plugins.txt`
  (`<class> client`). Exposes the global `JEH` binding to `kubejs/client_scripts`
  (`integration/kubejs/`). Script changes are in-memory only until `JEH.save()`.
- EMI: `compileOnly`; discovered via `@dev.emi.emi.api.EmiEntrypoint`
  (`integration/emi/JehEmiPlugin`). `Adapters.active()` picks the highest `priority()`
  adapter, and EMI (100) outranks JEI (10) because EMI overrides the overlay. EMI has no
  runtime hide API: JEH expresses hiding as a predicate in `EmiStackList/EmiRecipes.invalidators`,
  re-added at `bake()` HEAD by `mixin/emi/EmiStackListBakeRevealMixin` /
  `EmiRecipesBakeRevealMixin` after clear. `client/jehide/EmiHide` computes the hidden id sets
  (list + hide-intents) and is EMI-type-free; `client/viewer/emi/EmiRevealSupport` bridges EMI
  `EmiStack`/`EmiRecipe` to it. Reveal lives in `mixin/emi/EmiStackListRevealMixin` and
  `mixin/emi/EmiHiddenRevealMixin` (force-hidden checks to false unless JEH hides it).
  EMI-native intents (plugin `removeEmiStacks` predicates, edit-mode `EmiHidden.setVisibility`)
  are recorded by `mixin/emi/EmiRegistryIntentMixin` / `EmiHiddenIntentMixin` through
  `integration/emi/EmiIntentRecorder`.

## JEI gotchas
- JEI caches its ingredient list; `hideIngredients`/`unhideIngredients` alone do not refresh it.
  After applying, force `IngredientFilter.updateHidden()` (reached via reflection; the internal
  field is `IngredientFilterApi.ingredientFilter`). Fallback: `Minecraft.reloadResourcePacks()`.
- Only `VanillaTypes.ITEM_STACK` and (via Forge) `ForgeTypes.FLUID_STACK` exist; there is no
  energy ingredient type. Enumerate dynamically via `getRegisteredIngredientTypes()`.

## Environment gotchas
- Edit source files with the dedicated file tools. PowerShell `Set-Content` writes ANSI by default
  and corrupts non-ASCII (e.g. Chinese in `mods.toml`).
- `mods.toml` `logoFile` must be a **square** PNG: HMCL rejects non-square mod icons
  (`|width-height| < 1`). Keep `src/main/resources/justenoughhiding.png` square.
- `reference/` is gitignored third-party mod source for reading only; never build or commit it.
- The mixin config `package` (`...justenhoughiding.mixin`) is **owned by Mixin**: classes under it
  may only be mixins listed in `justenoughhiding.mixins.json` (plus the config `plugin`). Put
  helper classes elsewhere (e.g. `client/viewer/...`), or the game crashes with
  `IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly`.

## Testing
- Build, then `.\gradlew.bat runData` as a smoke test.
- Hand-write test data to `build/libs/listehiding.json`, then copy it to
  `config/jeh/listehiding.json` in the user's test instance (a client profile with JEI installed).
- In-game entrypoints: `/jeh intents`, `/jeh list`; keybinds default unbound.
