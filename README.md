# CodeverseAPI

The shared contract for Codeverse network plugins.

Identity, trust tiers, voice restrictions, account linking and events, declared
once so that every plugin agrees on what they mean.

Compiled for **Java 21** so consumers are not forced onto 25, even though the
plugins implementing it are.

## Why this exists

Before this artifact, the authentication plugin held trust tiers as an enum
while the voice plugin compared configured strings. Both were correct in
isolation and nothing kept them in step: adding a tier on one side silently
changed behaviour on the other, and the failure appeared as a permission bug
rather than as a compile error.

An unenforced contract between two plugins is a bug waiting for a deploy. This
makes it a compile time dependency instead.

## Design

**The interface is the contract, the implementation varies by where it runs.**
On the proxy, the authentication plugin answers identity lookups from memory. On
a backend, the voice plugin answers them from the shared database. Consumers
call the same interface and never learn which they received, which is what keeps
the cross process problem out of consumer code.

**Async by default.** Anything that may touch storage returns a
`CompletableFuture` and must not be joined on a server thread. Methods prefixed
with `cached` are the only ones safe from a tick; they read an in memory view
and return empty rather than blocking. Placeholders and scoreboards are the
reason that distinction exists.

**Empty and exceptional mean different things.** An empty optional means the
thing does not exist. An exceptional future means the question could not be
answered. Conflating them is how a storage outage quietly becomes an
authorisation decision.

**Events are informational and cannot be cancelled.** By the time one is
published the decision has been persisted; offering cancellation would let a
listener leave storage and behaviour disagreeing.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `api` | `api` | interfaces and value types, no dependencies at all |
| `jdbc` | `jdbc` | an `IdentityService` that reads the shared database directly |

The `api` module deliberately has no dependencies. Anything added there lands on
the classpath of every plugin consuming the contract, whether they use it or not.

The `jdbc` module exists because a backend has no authentication plugin to ask.
It reads the same rows the proxy writes, behind the same interface, so consumer
code cannot tell which implementation it received. Burying it inside one plugin
would put it out of reach of every other.

## Using it

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.CodeVerseHub-Minecraft.CodeverseAPI:api:0.2.0")

    // Only on servers that resolve identity from the database themselves,
    // which means anything that is not the proxy.
    implementation("com.github.CodeVerseHub-Minecraft.CodeverseAPI:jdbc:0.2.0")
}
```

`compileOnly` for `api` is correct: a providing plugin supplies the
implementation at runtime, and shading the contract would give you a second copy
of every interface that is not the one the provider registered.

`implementation` for `jdbc` is correct for the opposite reason: it is an
implementation you ship yourself.

### Note on coordinates

Version `0.1.0` was a single module published as
`com.github.CodeVerseHub-Minecraft:CodeverseAPI`. From `0.2.0` the group carries
the repository name and each module is its own artifact, which is how JitPack
addresses a multi module build. Nothing consumed `0.1.0`, so nothing breaks, but
the coordinates are not interchangeable.

```java
CodeverseApiProvider.find().ifPresent(api -> {
    IdentityService identity = api.requireIdentity();

    identity.byMinecraftId(player.getUniqueId()).thenAccept(found -> {
        found.ifPresent(id -> {
            // Key your own data on the internal id, never the Minecraft uuid.
            // A person with linked Java and Bedrock accounts is one person.
            store(id.internalId(), someValue);
        });
    });

    // Safe from a tick, unlike the call above.
    identity.cachedTier(player.getUniqueId())
            .filter(TrustTier::eligibleForElevatedPermissions)
            .ifPresent(tier -> showStaffTools(player));
});
```

Use `find()` rather than `get()` during your own startup: plugin load order is
not guaranteed, and an absent API at that point is normal.

### Resolving identity on a backend

```java
JdbcIdentityService identities = new JdbcIdentityService(
        dataSource, executor, "codeverse_accounts", Duration.ofMinutes(5));

if (!identities.probe()) {
    logger.error("The accounts table could not be read. Restrictions will be keyed to "
            + "individual accounts rather than to people, so switching accounts sheds them.");
}
```

The table needs one column beyond what the authentication plugin created
originally:

```sql
ALTER TABLE codeverse_accounts
  ADD COLUMN discord_id VARCHAR(32) NULL,
  ADD KEY idx_discord (discord_id);
```

## The one rule worth repeating

`internalId()` is who someone is. `minecraftId()` is where their packets go.

Store data against the internal id. Keying on the Minecraft uuid means a person
with a linked Java and Bedrock account appears as two people, and any
restriction placed on one is shed by connecting with the other.

## Stability

Version `0.x`. The shape is informed by two consumers, which is enough to know
it is not obviously wrong and not enough to know it is right. It will be
declared stable once a third consumer exists, which is expected to be the
minigame framework.

Until then, minor versions may break source compatibility. They will not do so
quietly: breaking changes get a release note explaining the migration.

## Building

```bash
./gradlew build
```

Produces the API jar alongside sources and javadoc jars, since an API that
cannot be read from an editor is one that gets used incorrectly.

## License

MIT. See [LICENSE](LICENSE).

## About

Built and maintained by the **CodeVerseHub-Minecraft Subteam**.

We work alongside the wider CodeVerseHub community but operate as a separate
team; CodeVerseHub is not responsible for this project.
