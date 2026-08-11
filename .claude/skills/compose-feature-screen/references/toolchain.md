# Compose build wiring in this repo

This repo is unusual: **AGP 9 supplies Kotlin itself, there is no Kotlin Gradle plugin**, and
`@CLAUDE.md` warns that compiler-plugin libraries are ruled out. Compose is the one sanctioned
exception, and the wiring below is the exact shape that was verified to build here. Reproduce
it; do not improvise.

## What Compose needs that the rest of the repo does not

`buildFeatures { compose = true }` alone fails at **configuration time**:

```
A problem occurred configuring project ':feature'.
> Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required
  when compose is enabled.
```

The fix is the standalone Compose compiler plugin — **not** `org.jetbrains.kotlin.android`.
Applying KGP here is still wrong and still breaks the build's assumptions.

## Version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
composeBom = "2026.06.01"
composeCompiler = "2.4.10"     # must track the Kotlin version AGP bundles
activityCompose = "1.13.0"
lifecycle = "2.11.0"           # lifecycle-*-compose need >= 2.8

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }          # no version — BOM supplies it
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
# …ui-graphics, ui-tooling, ui-tooling-preview, ui-test-junit4, ui-test-manifest

[plugins]
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "composeCompiler" }
```

Compose artifacts carry **no** `version.ref` — the BOM decides. Adding one silently overrides
the BOM and is how versions drift apart.

## Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

## Module `build.gradle.kts` (any module that renders Compose)

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    buildFeatures {
        viewBinding = true   // existing XML screens still need it
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

`platform(...)` must be repeated for the `androidTest` configuration — BOM constraints do not
cross configurations.

## Which modules get this

Only `:feature` (and `:app` if it ever hosts a Composable directly). `:core:common`,
`:core:network` and `:core:data` stay Compose-free; a repository exposing `State` or a
`@Composable` breaks the layering that `@CLAUDE.md` describes.

## Version compatibility

`composeCompiler` must match the Kotlin version AGP bundles. Symptom of a mismatch is a
configuration-time error naming both versions. To move it:

1. Change only `composeCompiler` in the catalog.
2. `.\gradlew.bat :feature:assembleDebug` — configuration fails fast on a bad pair.
3. The error text states the Kotlin version AGP actually has; use that.

Verified working pair: **AGP 9.3.1 + compose compiler plugin 2.4.10 + compose-bom 2026.06.01**,
Gradle 9.5, configuration cache on, Java source/target 11, `minSdk` 24.

## Gotchas seen here

- **Configuration cache is on.** Build-file edits invalidate it; a stale-looking failure right
  after editing a `.gradle.kts` usually just needs the rerun it already does.
- **`FAIL_ON_PROJECT_REPOS`** — the compose compiler plugin resolves from `gradlePluginPortal()`
  already declared in `settings.gradle.kts`. Never add a `repositories {}` block to a module.
- **Material3 vs Material2** — this repo uses `androidx.compose.material3`. Importing
  `androidx.compose.material.*` pulls in a second design system; it is not in the catalog.
- **`minSdk` 24** — `dynamicColor` is API 31+; guard it with a `Build.VERSION.SDK_INT` check.
