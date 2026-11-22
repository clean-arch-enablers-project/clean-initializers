<p align="center">CAE Implementation</p>

---

<br>

### Clean Initializers for Java

Clean Initializers provides a standalone lazy value for Java 25. Its only runtime dependency is Clean Mapped Exceptions.

<br>

#### Dependency

```xml
<dependency>
    <groupId>com.clean-arch-enablers</groupId>
    <artifactId>clean-initializers</artifactId>
    <version>1.0.0-RC1</version>
</dependency>
```

<br>

#### Usage

```java
import com.cae.clean_initializers.Lazy;
import java.net.http.HttpClient;

Lazy<HttpClient> client = Lazy.loadingOf("billing-http-client", HttpClient::newHttpClient);
boolean initialized = client.isInitialized(); // false
HttpClient first = client.get();
HttpClient same = client.get(); // same instance as first

client.reset();
HttpClient replacement = client.get();
```

<br>

#### Retention

Each initializer retains its own result. A supplier that returns `null` has initialized successfully, so subsequent calls return that same result until reset. This differs from the original CAE `Lazy`, which ran the supplier again after a null result.

<br>

#### Failure diagnostics

When a supplier throws a runtime exception, `get()` throws `LazyInitializationMappedException` with that exception as its cause. The next access tries again. JVM errors propagate directly and leave the initializer ready for another attempt. A null supplier, recursive access, or reset from within the same supplier is an internal configuration or execution error.

Use `Lazy.loadingOf(name, supplier)` to identify a lazy value in diagnostics, especially when multiple values use the same factory. The exception's `getDetails()` also reports the first frame of the supplier exception, including source file and line number when the JVM provides them. The public message stays generic.

<br>

#### Concurrent access and reset

Only one thread can attempt creation at a time for a given initializer. Waiting callers may reuse a successful value or try creation after a failure. A concurrent reset waits for the current attempt to finish, then clears the retained value. State queries return immediately and report false while creation is in progress.

Reset leaves existing references usable. The application owns disposal and coordinates concurrent use of the returned resource.

<br>

#### Verification and publishing

From the repository root, run `mvn -f implementations/java/pom.xml clean verify` with Java 25. The build checks compiler warnings, tests, per-class coverage, and Javadoc.

GitHub Actions verifies Java changes on pull requests to `main` and publishes new Java versions from `main`. See the [publishing guide](../../.github/PUBLISHING.md) for the release rules and required secrets.

<br>
<br>

---

<p align="center">clean software, made easy.</p>
