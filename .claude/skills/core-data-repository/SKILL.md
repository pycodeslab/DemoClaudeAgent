---
name: core-data-repository
description: Define a repository plus its data source in the :core:data module — domain model, wire→domain mapper, internal remote data source, and the public repository contract :feature consumes. Invoke explicitly with /core-data-repository when adding or modifying a repository, data source, domain model, or mapper in this repo. Takes an optional domain name as argument (`/core-data-repository Post`) which becomes the model and repository name; without it, plain prompting works exactly as before. Encodes the module-boundary rule that :core:data exists to enforce.
argument-hint: "[DomainName] [deskripsi repository bebas]"
disable-model-invocation: true
---

# Repository + data source in `:core:data`

`:core:data` is a **boundary keeper**. Its job is not storing data — it is making sure `:feature`
never sees a network type. Everything here follows from that.

`CLAUDE.md` is already in context and owns the architecture, toolchain, and testing rules — on any
conflict it wins. This skill adds only what is specific to the data layer.

## Arguments

> Arguments passed: `$ARGUMENTS`

Everything typed after `/core-data-repository` is interpolated into the quoted line above, verbatim —
that line is the single source of truth for what the user passed; the rest of this section only says
how to read it. **Empty backticks there mean no argument was given**, which is the ordinary case and
not an error: go to step 2 of "Resolving the name" and behave exactly as this skill did before the
argument existed.

`argument-hint` advertises the shape as `[DomainName] [deskripsi repository bebas]` — both halves
optional and independent:

```
/core-data-repository                                    # nothing passed — usual behaviour
/core-data-repository Post                               # name only
/core-data-repository user-profile                       # any casing, normalised below
/core-data-repository Post ambil daftar post dari API    # name + free-form prompt
/core-data-repository "blog post" simpan judul dan body  # quote a multi-word name
/core-data-repository buatkan repository untuk komentar  # no name — all of it is prompt
```

### Splitting the argument

It arrives as one string, so split it yourself. It carries a name **only when it starts with one**:

- Starts with a quoted string → that is the name, the rest is prompt.
- Otherwise the first whitespace-separated token is the name only if it reads as an identifier:
  one word, letters/digits/`-`/`_`, nothing else.
- An ordinary request word in that position (`buatkan`, `bikin`, `tambahkan`, `add`, `create`,
  `make`, `repository`, `repo`, `data`, `a`, `the`) is **not** a name — the whole string is prompt.
- Everything not consumed as the name is the prompt, and it is a full instruction, not a footnote:
  read it for the model's fields, the operations the contract needs, and failure behaviour just as
  you would a normal request.

So the last two examples split differently: `"blog post"` is a name, `buatkan` is not. If a bare
first token is genuinely ambiguous, prefer reading it as prose and confirm the domain name in your
first sentence rather than silently naming a public contract after it.

### Resolving the name

1. **The argument starts with a name** → that is the name. It wins over a name guessed from the
   prose, so `/core-data-repository Post buatkan repository komentar` builds `Post` — but when the
   prose contradicts the argument that plainly, say which one you used in your first sentence.
2. **No name in it** (empty, or prose only) → take the name from the request as usual
   (`"buatkan repository untuk komentar"` → `Comment`). This is the pre-existing behaviour and stays
   fully supported.
3. **Neither** → ask for the name before writing files. Do not invent one, and do not fall back to
   `Item`, `Data` or `Sample` — `PostRepository` is this module's *public* surface, so renaming it
   later means touching every `:feature` call site as well as the files here.

### Normalising the name

One name yields the forms below, and every template placeholder is one of them:

| Form | Rule | `posts` | `user-profile` | `"blog post"` |
| --- | --- | --- | --- | --- |
| `<X>` — domain model, file names | PascalCase **singular**, separators dropped | `Post` | `UserProfile` | `BlogPost` |
| Public contract | `<X>Repository` | `PostRepository` | `UserProfileRepository` | `BlogPostRepository` |
| Collection accessor | `get<X>s`, natural English plural | `getPosts()` | `getUserProfiles()` | `getBlogPosts()` |

Accept `PascalCase`, `camelCase`, `kebab-case`, `snake_case` and quoted multi-word input; they all
normalise to the same thing. Strip a trailing `Repository`/`Repo`/`DataSource`/`Dto`/`Model`/`Entity`
the user typed (`PostRepository` → `Post`) so the templates do not produce `PostRepositoryRepository.kt`,
and singularise a plural (`Posts` → `Post`) — the domain model names one thing even when the
repository returns a list of them.

Unlike `compose-feature-screen` there is **no per-domain package segment**: `:core:data` is organised
by layer (`model/`, `mapper/`, `datasource/`, `repository/`), so the name only ever appears in class
and file names. Do not create `com.sample.demo.core.data.post`.

Then substitute those forms into §"File layout" and into `references/templates.md` and
`references/testing.md`, whose `Post`/`post` examples are placeholders for `<X>`.

**Before creating anything, check whether that repository already exists** — look for
`repository/<X>Repository.kt` under `core/data/src/main/java/com/sample/demo/core/data/`. If it does,
modify it — a name in the argument is not an instruction to create a second contract for the same
domain.

## Before you write code

Check what actually exists below you, then pick the contract from this table. **Never fill a gap
by writing another module's code here.**

| `:core:common` has `Result`/`NetworkException`/`DispatcherProvider`? | This module has an HTTP client + `<X>Api`/`<X>Dto`? | Build |
| --- | --- | --- |
| yes | yes | **Canonical** — full stack, §"The pieces" |
| yes | no | Repository returns `Result`, but the data source is a **seam** (§"Missing layers") |
| no | no | Plain domain return types + seam; failures propagate as exceptions (§"Missing layers") |

Verify by looking, not by assuming — `ls core/common/src/main/java/com/sample/demo/core/common/`
and `ls core/data/src/main/java/com/sample/demo/core/data/`. Both hold only `.gitkeep` until
someone implements them, so today the answer to both columns is *no*.

There is no `:core:network` module — it was deliberately removed. The wire layer, when it arrives,
lives **inside** `:core:data` in an `internal` `remote/` package. Adding an HTTP client here means
adding a coordinate to `core/data/build.gradle.kts`, which is a repo-shape change: ask first (see
§"Non-negotiables"). Do not recreate `:core:network`.

## File layout

`<X>` is the normalised form from §"Arguments" — for `/core-data-repository Post` this layout reads
`model/Post.kt`, `repository/PostRepository.kt`, and so on:

```
core/data/src/main/java/com/sample/demo/core/data/
├── model/<X>.kt                     # domain model — public
├── remote/<X>Api.kt, <X>Dto.kt      # internal wire layer — only once an HTTP client exists
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

**Wire layer** (`remote/`) — `<X>Api`, `<X>Dto`, and the `safeApiCall` helper that folds a failure
into a `Result`. Every declaration here is `internal`, without exception: this package is the reason
the rest of the module can stay honest.

**Remote data source** (`datasource/`) — the only place that touches `<X>Api` and `safeApiCall`.
Interface + implementation, both `internal`, both free to speak in `<X>Dto`. It answers "where do
the bytes come from"; it does no mapping and holds no domain types.

Why a data source at all, when the repository could call the `Api` directly: it is the seam every
test fakes, and it is where a second source — cache, local store — would slot in without touching
the repository's contract.

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
fun PostRepository(): PostRepository = PostRepositoryImpl(PostRemoteDataSourceImpl(PostApi.create()))
```

Pick that factory-function style **or** a public no-arg secondary constructor — one of them, used
consistently across the module.

## The boundary rule — this module's whole reason to exist

The wire layer lives in this module now, so **nothing in the build file protects the boundary** —
there is no separate module to keep on an `implementation` edge. Kotlin `internal` is the only thing
standing between `:feature` and a `Response<PostDto>`, and Gradle will not catch a leak: a `public`
that should have been `internal` compiles fine right up until someone names it from `:feature`. The
rule is held while writing, not by the compiler.

`:feature` also consumes this module through `implementation(project(":core:data"))`, so anything
this module marks `api(...)` — today just `:core:common` — is transitively visible up there. Keep
any HTTP client on `implementation`.

Before you finish, walk every `public` declaration in the module and confirm none of these appear in
its signature — parameters, return types, or type arguments:

`<X>Dto` · `Response` · `Retrofit` · `OkHttp*` · `Call` · `@SerializedName` · anything from the
`remote/` package

If one does, the fix is `internal` plus a public factory — never widening the wire layer's visibility.

## Missing layers: build the contract, fabricate nothing

Same rule as `compose-feature-screen`, one layer down — that skill leaves a seam pointing down at
this module, this one leaves a seam pointing down at a wire layer that does not exist yet, and
`wire-feature-to-data` closes the one in between once both sides exist. With no HTTP client in the
build file, **do not invent one** — no `<X>Dto`, no Retrofit interface, no hand-rolled
`HttpURLConnection`, no hardcoded sample list, no in-memory `Impl` pretending to be a cache. And if
`:core:common` has no `Result`, **do not define
`Result`, `NetworkException`, or `DispatcherProvider` here** — that is `:core:common`'s code sitting
in the wrong module, which is the exact mistake this rule exists to prevent.

Build what is genuinely `:core:data`'s own — the domain model, the repository interface, the impl,
the tests — and leave the missing source as a **seam**: a constructor parameter whose type is a
function, defaulting to "nothing to load".

```kotlin
internal class PostRepositoryImpl(
    // No HTTP client in this module yet, so there is nothing to fetch and nothing is provided.
    // Once remote/PostApi lands, replace this with PostRemoteDataSource and map PostDto -> Post here.
    private val fetchPosts: suspend () -> List<Post> = { emptyList() },
) : PostRepository
```

- The default supplies **no data** — `{ emptyList() }`, never sample content.
- The domain model is still yours to define. It is this module's concern, not a fabricated wire model.
- Without `Result`, the interface returns the domain type directly and a failing seam throws. Say in
  a comment that this becomes `Result` once `:core:common` has it.
- **Report it.** Name which layers were absent and what you therefore did not build.
- Do not add dependencies to `core/data/build.gradle.kts` to compensate. Retrofit sits in
  `gradle/libs.versions.toml` unused, on purpose — reaching for it is the decision to ask about, not
  the workaround.

## Non-negotiables in this repo

- **No new coordinates.** `core/data/build.gradle.kts` declares no production library of its own by
  design — coroutines arrive via `api(project(":core:common"))`, and only `junit` plus
  `kotlinx-coroutines-test` are declared, for tests. Adding a dependency (Retrofit, Room, DataStore,
  a mocking library) changes the repo's shape: ask first.
- **No Compose, no `android.*` UI types, no `ViewModel`** in this module.
- **A local/offline source needs a new dependency**, so it is out of scope by default. Say so rather
  than faking one with an in-memory map.

## Verification — required, and show the output

```powershell
.\gradlew.bat :core:data:testDebugUnitTest      # mapper + repository logic
.\gradlew.bat :core:data:lintDebug
.\gradlew.bat :app:assembleDebug                # whole chain still links
```

Report the commands' actual output. "Should work" is not verification.
