---
name: core-data-repository
description: Define a repository plus its data source in the :core:data module — domain model, wire→domain mapper, remote data source over :core:network, and the public repository contract :feature consumes. Invoke explicitly with /core-data-repository when adding or modifying a repository, data source, domain model, or mapper in this repo. Encodes the module-boundary rule that :core:data exists to enforce.
disable-model-invocation: true
---

# Repository + data source in `:core:data`

`:core:data` is a **boundary keeper**. Its job is not storing data — it is making sure `:feature`
never sees a network type. Everything here follows from that.

Read `@CLAUDE.md` first — it owns the architecture and toolchain rules. This skill adds what is
specific to the data layer.

## Before you write code

Check what actually exists below you, then pick the contract from this table. **Never fill a gap
by writing another module's code here.**

| `:core:common` has `Result`/`NetworkException`/`DispatcherProvider`? | `:core:network` has `<X>Api`/`<X>Dto`/`safeApiCall`? | Build |
| --- | --- | --- |
| yes | yes | **Canonical** — full stack, §"The pieces" |
| yes | no | Repository returns `Result`, but the data source is a **seam** (§"Missing layers") |
| no | no | Plain domain return types + seam; failures propagate as exceptions (§"Missing layers") |

Verify by looking, not by assuming — `ls core/common/src/main/java/com/sample/demo/core/common/`
and the `:core:network` equivalent. Both hold only `.gitkeep` until someone implements them.

Also check whether the repository already exists before adding a second one for the same domain.

## File layout

```
core/data/src/main/java/com/sample/demo/core/data/
├── model/<X>.kt                     # domain model — public
├── mapper/<X>Mapper.kt              # internal <X>Dto.toDomain()
├── datasource/<X>RemoteDataSource.kt # internal interface + internal impl over <X>Api
└── repository/
    ├── <X>Repository.kt             # public interface + public factory function
    └── <X>RepositoryImpl.kt         # internal constructor
```

`core/data/src/test/java/com/sample/demo/core/data/` gets `<X>MapperTest` and
`<X>RepositoryImplTest`. They are not optional — mapping and failure classification are exactly
the logic worth testing.

Load `references/templates.md` for the skeletons and `references/testing.md` before writing tests.

## The pieces

**Domain model** (`model/`) — shaped by what the app needs, **not** by the JSON. Drop fields the
app never reads, resolve wire nullability here (so the domain model has no pointless `?`),
normalise raw types (date string → parsed type). Public, and free of every network type.

**Mapper** (`mapper/`) — `internal fun <X>Dto.toDomain(): <X>`. All null handling and normalisation
happens in this one function. It must be `internal`: its signature names `<X>Dto`.

**Remote data source** (`datasource/`) — the only place that touches `<X>Api` and `safeApiCall`.
Interface + implementation, both `internal`, both free to speak in `<X>Dto`. It answers "where do
the bytes come from"; it does no mapping and holds no domain types.

Why a data source at all, when the repository could call the `Api` directly: it is the seam every
test fakes (a hand-written `internal` fake, never a mocking library), and it is where a second
source — cache, local store — would slot in without touching the repository's contract.

**Repository interface** (`repository/`) — the module's *entire* public surface, and the only thing
`:feature` ever names (via `wire-feature-to-data`). Its signatures may name only `:core:common`
types and this module's domain models:

```kotlin
interface PostRepository {
    suspend fun getPosts(): Result<List<Post>>
}
```

**Repository impl** — orchestrates: call the data source, `map` the wire models to domain, run that
work on `dispatchers.io`. It **branches on `Result`**; it does not `try`/`catch` — `safeApiCall`
already folded every failure into a value. Its constructor takes the data source and is therefore
`internal`. Provide one public, network-free way to build it, next to the interface:

```kotlin
fun PostRepository(): PostRepository = PostRepositoryImpl(PostRemoteDataSourceImpl(NetworkModule.createPostApi()))
```

Pick that factory-function style **or** a public no-arg secondary constructor — one of them, used
consistently across the module.

## The boundary rule — this module's whole reason to exist

`:core:data` depends on `:core:network` with `implementation`, not `api`. That keeps Retrofit off
`:feature`'s compile classpath, but **Gradle will not catch a leak**: an `internal` that should have
stayed `internal` still compiles fine as long as `:feature` never names the type. The rule is held
while writing, not by the compiler.

Before you finish, walk every `public` declaration in the module and confirm none of these appear in
its signature — parameters, return types, or type arguments:

`<X>Dto` · `Response` · `Retrofit` · `OkHttp*` · `Call` · `@SerializedName` · anything from
`com.sample.demo.core.network`

If one does, the fix is `internal` plus a public factory — never widening `:core:network` to `api`.

## Missing layers: build the contract, fabricate nothing

Same rule as `compose-feature-screen`, one layer down — that skill leaves a seam pointing down at
this module, this one leaves a seam pointing down at `:core:network`, and `wire-feature-to-data`
closes the one in between once both sides exist. If `:core:network` has no API yet, **do not
invent one** — no `<X>Dto`, no Retrofit interface, no `NetworkModule`, no hardcoded sample list, no
in-memory `Impl` pretending to be a cache. And if `:core:common` has no `Result`, **do not define
`Result`, `NetworkException`, or `DispatcherProvider` here** — that is `:core:common`'s code sitting
in the wrong module, which is the exact mistake this rule exists to prevent.

Build what is genuinely `:core:data`'s own — the domain model, the repository interface, the impl,
the tests — and leave the missing source as a **seam**: a constructor parameter whose type is a
function, defaulting to "nothing to load".

```kotlin
internal class PostRepositoryImpl(
    // :core:network has no PostApi yet, so there is nothing to fetch and nothing is provided.
    // When it lands, replace this with PostRemoteDataSource and map PostDto -> Post here.
    private val fetchPosts: suspend () -> List<Post> = { emptyList() },
) : PostRepository
```

- The default supplies **no data** — `{ emptyList() }`, never sample content.
- The domain model is still yours to define. It is this module's concern, not a fabricated wire model.
- Without `Result`, the interface returns the domain type directly and a failing seam throws. Say in
  a comment that this becomes `Result` once `:core:common` has it.
- **Report it.** Name which layers were absent and what you therefore did not build.
- Do not add dependencies to `core/data/build.gradle.kts` to compensate. Retrofit is deliberately
  invisible here except through `:core:network`.

## Non-negotiables in this repo

- **No new coordinates.** `core/data/build.gradle.kts` declares no production library of its own by
  design — coroutines arrive via `api(:core:common)`, Retrofit via `implementation(:core:network)`;
  only `junit` and `kotlinx-coroutines-test` are declared, for tests. Adding a dependency (Room,
  DataStore, a mocking library) changes the repo's shape: ask first.
- **No DI framework** — default constructor arguments, not a stateful global singleton.
- **No Compose, no `android.*` UI types, no `ViewModel`** in this module.
- **Errors are values.** No `try`/`catch` in a repository when `Result` exists.
- **A local/offline source needs a new dependency**, so it is out of scope by default. Say so rather
  than faking one with an in-memory map.

## Verification — required, and show the output

```powershell
.\gradlew.bat :core:data:testDebugUnitTest      # mapper + repository logic
.\gradlew.bat :core:data:lintDebug
.\gradlew.bat :app:assembleDebug                # whole chain still links
```

Report the commands' actual output. "Should work" is not verification.
