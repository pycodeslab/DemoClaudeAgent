# Testing `:core:data`

`core/data/build.gradle.kts` provides `junit` and `kotlinx-coroutines-test`. **There is no mocking
library and you may not add one** — every collaborator in this module is an interface or a function
type, so a hand-written fake is shorter than the mock would have been.

Unit tests compile against the same module, so they see `internal` declarations: a test can
construct `PostRepositoryImpl` and implement `PostRemoteDataSource` directly.

## What to test

| Class | Cases that matter |
| --- | --- |
| `<X>Mapper` | a complete DTO maps field-for-field; a DTO with every nullable field `null` yields sensible defaults instead of throwing |
| `<X>RepositoryImpl` | success maps DTO → domain; a `Failure` is passed through **unchanged**, not re-wrapped or swallowed |

Do not test the domain `data class` itself, and do not re-test `safeApiCall` — that is
`:core:network`'s test.

## Fake data source

```kotlin
private class FakePostRemoteDataSource(
    private val result: Result<List<PostDto>>,
) : PostRemoteDataSource {
    override suspend fun getPosts(): Result<List<PostDto>> = result
}
```

For the seam variant (§B of `templates.md`) there is nothing to fake — pass the lambda:

```kotlin
PostRepositoryImpl(fetchPosts = { listOf(Post(1, "a", "b")) })
PostRepositoryImpl(fetchPosts = { throw IOException("offline") })
```

## Dispatchers

Inject through `DispatcherProvider` so no rule or Robolectric is needed. `runTest`'s scheduler backs
every dispatcher, so the test stays deterministic:

```kotlin
@Test
fun `success maps to domain`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val repository = PostRepositoryImpl(
        remote = FakePostRemoteDataSource(Result.Success(listOf(PostDto(1, "Judul", "Isi")))),
        dispatchers = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
        },
    )

    val result = repository.getPosts()

    assertEquals(Result.Success(listOf(Post(1, "Judul", "Isi"))), result)
}
```

Assert on values, not on call counts. If a test needs to know *how many times* the data source was
called, that is usually a caching concern that does not exist in this module yet.

## Failure passes through

The point of the repository is that it adds mapping without touching classification:

```kotlin
@Test
fun `failure is forwarded unchanged`() = runTest {
    val failure = Result.Failure(NetworkException.Http(500, "boom"))
    val repository = PostRepositoryImpl(FakePostRemoteDataSource(failure), dispatchers)

    assertSame(failure, repository.getPosts())
}
```

## Run it

```powershell
.\gradlew.bat :core:data:testDebugUnitTest
.\gradlew.bat :core:data:testDebugUnitTest --tests "*PostRepositoryImplTest"
```

A green run is the evidence — quote it. If a test fails, fix the cause; never loosen the assertion.
