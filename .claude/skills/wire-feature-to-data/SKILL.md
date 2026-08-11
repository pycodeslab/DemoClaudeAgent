---
name: wire-feature-to-data
description: Connect a :feature screen to a :core:data repository — replace the ViewModel's function-type seam with the real repository, map the domain model to the screen's UI model, and update the Factory and tests. Invoke explicitly with /wire-feature-to-data when joining the two layers in this repo. Owns the step that compose-feature-screen and core-data-repository each deliberately stop short of.
disable-model-invocation: true
---

# Wiring `:feature` → `:core:data`

`compose-feature-screen` builds the screen down to a seam. `core-data-repository` builds the
repository up to a seam. Neither closes the gap, by design — each refuses to fabricate the layer
below it. **This skill closes it**, once both sides genuinely exist.

Read `@CLAUDE.md` first. This skill adds only what is specific to the join.

## Before you write code

Look at both sides — do not assume either exists:

```powershell
ls core/data/src/main/java/com/sample/demo/core/data/repository/
ls core/common/src/main/java/com/sample/demo/core/common/    # .gitkeep only == no Result
```

| `:core:data` has the repository? | `:core:common` has `Result`? | Do this |
| --- | --- | --- |
| no | — | **Do not wire.** Stay on the seam — `compose-feature-screen` §"Missing layers". Building the repository first is `core-data-repository`'s job, not this skill's. |
| yes | no | **This repo today.** ViewModel takes the repository; the `try`/`catch` survives at that one call. §"The four steps" |
| yes | yes | Canonical. Same four steps, but `load()` branches on `Result` and the `try`/`catch` goes. §"When `Result` arrives" |

The middle row is the case no other skill documents. `compose-feature-screen`'s template §2b jumps
straight to the canonical row and will mislead you if `:core:common` is still empty.

## The four steps

Order matters: the mapper resolves the type mismatches, so it comes first. The `Factory` comes last
because it is what makes the wiring real for the running app.

### 1. Map domain → UI, in `:feature`

The two models will not line up — they are shaped by different concerns, which is the point.
`Post.id` is `Int` because that is what the domain says; `PostUiModel.id` is `Long` because that is
what the list needs. Every conversion, truncation and formatting decision lives in one `internal`
extension next to the UI model it produces:

```kotlin
// feature/postlist/PostListUiState.kt
internal fun Post.toUiModel(): PostUiModel = PostUiModel(
    id = id.toLong(),
    title = title,
    excerpt = body.take(EXCERPT_LENGTH),
)
```

Where it goes, and why not the two tempting alternatives:

| Location | Verdict |
| --- | --- |
| `feature/<screen>/` next to the UI model | **Correct.** The UI model is this screen's, so its mapper is too. |
| `feature/util/` | No. `util/` is framework-free *and* screen-agnostic; this names a domain model and one screen's UI model. |
| `:core:data/mapper/` | No. That package is for `<X>Dto → <X>` only. A UI model in `:core:data` inverts the dependency. |

### 2. Replace the seam with the repository

```kotlin
class PostListViewModel(
    private val repository: PostRepository = PostRepository(),
) : ViewModel()
```

The function-type seam was always meant to disappear here — `compose-feature-screen/SKILL.md`
calls it "collapses to one line when the repository arrives". Do not keep both: a lambda that
merely forwards to a repository is indirection with no remaining purpose.

Take the **interface**, default it to the **public factory function**. Never name
`<X>RepositoryImpl` — it is `internal` to `:core:data`, so this is compiler-enforced, but naming it
would also mean `:feature` knowing how the repository is assembled.

### 3. Update the `Factory` — the step that is silently skipped

```kotlin
companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { PostListViewModel() }
    }
}
```

If the default argument is the real repository, this line already works and needs no edit — but you
must **look at it and confirm** that. When the ViewModel needs something the default cannot supply,
the `Factory` is where it is constructed, and forgetting it is a silent failure: every test passes
against the injected fake while the running app still shows an empty screen.

### 4. Rewrite the tests around a hand-written fake

The lambda the tests passed is gone, so they need a fake repository. Write it by hand — there is no
mocking library in this repo and you may not add one:

```kotlin
private class FakePostRepository(
    private val posts: List<Post> = emptyList(),
    private val error: Throwable? = null,
) : PostRepository {
    var loadCount = 0
        private set

    override suspend fun getPosts(): List<Post> {
        loadCount++
        error?.let { throw it }
        return posts
    }
}
```

Keep every branch that existed before — success, failure, query filtering, refresh — and add one
that pins the mapper: a `Post` in produces the expected `PostUiModel` out. That test is the only
guard on the `Int`/`Long` and `body`/`excerpt` conversions.

Full skeletons in `references/templates.md`.

## Boundaries that must still hold afterwards

Wiring is where a clean module graph usually starts leaking. After the change, verify by reading —
Gradle will not catch any of these:

- **`:feature` names only the repository interface, its factory function, and domain models.** No
  `<X>Dto`, no Retrofit type, nothing from `com.sample.demo.core.network`. `:core:data` depends on
  `:core:network` with `implementation` precisely so this stays true. Check the imports, not every
  mention — `grep -rn "^import com.sample.demo.core.network" feature/src/` must come back empty. A
  plain `grep "core.network"` also hits the KDoc these skills tell you to write ("waiting on
  `:core:network`"), so it reports a leak that is not there.
- **`UiState` never holds a domain model.** `posts` stays `List<PostUiModel>`. Putting `List<Post>`
  in the state saves the mapper today and welds the screen to the domain shape forever.
- **The domain model does not grow a UI concern.** If the screen needs a formatted date or a
  truncated body, that is the mapper's output, not a new field on `Post`.
- **No new dependency in `feature/build.gradle.kts`.** `implementation(project(":core:data"))` is
  already there. Needing anything else means the mapping is happening in the wrong module.
- **Mapping stays out of the Composables.** The stateless screen receives finished UI models.

## When `Result` arrives

Once `:core:common` has `Result` and `NetworkException`, and `core-data-repository` has changed the
contract to `suspend fun getPosts(): Result<List<Post>>`:

Changes:

- `load()` swaps `try`/`catch` for `when (val result = repository.getPosts())`, with
  `is Result.Success` mapping `result.data` and `is Result.Error` reading the message from the
  exception. Errors cross boundaries as values — see `@CLAUDE.md`.
- Failure tests construct a `Result.Error` instead of throwing from the fake.

Does **not** change: the mapper, the `Factory`, the shape of `UiState`, the event interface, or the
screen. If a `Result` migration is pushing you to touch those, the wiring was wrong.

## Report honestly

A wired screen that still renders empty is the correct outcome when the layer *below* the repository
is a seam — in this repo `PostRepositoryImpl.fetchPosts` defaults to `{ emptyList() }` while
`:core:network` has no API. Say that in your summary. Do not "fix" it by giving the repository
sample data: that is the fabrication both other skills exist to prevent, and it would sit in
`:core:data` where it is hardest to find later.
