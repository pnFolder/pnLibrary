# Metrics credentials

`MetricsCredentials<T>` provides typed access to provider-specific credentials:

- bStats uses `MetricsCredentials<Int>`: a public positive project ID.
- FastStats uses `MetricsCredentials<CharArray>`: a sensitive project token.

```kotlin
val bStats: MetricsCredentials<Int> = MetricsCredentials.BStats(12345)
bStats.useValue { projectId -> println(projectId) }

val token = "example-token".toCharArray()
val fastStats: MetricsCredentials<CharArray> = MetricsCredentials.FastStats(token)
token.fill('\u0000')

try {
    fastStats.useValue { characters ->
        // Use the token here. Do not log or retain this array.
    }
} finally {
    fastStats.close()
}
```

FastStats credentials copy the input array. Each `useValue` call lends a separate copy
and erases it in `finally`, including when the callback throws. Closing credentials
erases the owned buffer. Metrics sessions close their owned credentials automatically.

`SecretValue.useSecret` provides the same scoped character-buffer access for other
secrets. A returned borrowed array is already erased when the call finishes.

This is memory-lifetime management, not encryption. Existing String configuration
and the native FastStats SDK's String token cannot be reliably erased by these classes.
`useToken` converts to String only at that SDK boundary. Token textual representations
are redacted; callers must also avoid placing secrets in logs or reports.
