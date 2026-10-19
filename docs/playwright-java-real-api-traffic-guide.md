# Playwright Java: Real API Traffic Guide

Oct 17, 2026 · @Dusmamat Karimov

## Scope and conventions

Use `waitForResponse` to sync each UI action with the real API call it triggers and to read that call's data. Use network events to record every call in the background for diagnostics and guards. Nothing here is mocked: every request reaches your real microservices.

| Tool | Job | Typical question it answers |
| --- | --- | --- |
| `waitForResponse` / `waitForRequest` | Synchronise + capture one call | "What ID did the POST return?" "Did the filter send the right params?" |
| Network events (`onRequestFinished`, `onRequestFailed`) | Record all calls passively | "Which call failed silently?" "How many times did search fire?" |

Conventions used in every snippet:

- Java 17, Playwright Java, Cucumber-JVM + PicoContainer, AssertJ, Jackson, Awaitility.
- `assertThat(...)` is AssertJ (static import). Playwright assertions are always written `PlaywrightAssertions.assertThat(...)` to avoid the name clash.
- `UiSession` is your per-scenario holder exposing `page()` and `context()`. The context is created with `setBaseURL(...)`, so relative paths work in `navigate` and `context().request()`.
- `/api/pricing-cases...` endpoints, JSON field names and page objects (`form`, `grid`, `details`) are placeholders. Swap in your real ones from DevTools → Network.

## Part 1: waitForResponse in depth

`waitForResponse` runs your action and returns once a matching response arrives. You get the real `Response` back: status, headers, body and the `Request` that produced it.

### 1.1 The golden rule: the action goes inside the callback

The waiter is registered before the callback runs, so a response that arrives during `click()` is caught. Waiting after the click is a race. A fast API answers during the click, the event is dispatched and gone, and you then wait 30 seconds for a response that has already happened.

```java
// WRONG: race condition, intermittent 30s timeouts
form.submit();
Response r = page.waitForResponse("**/api/pricing-cases", () -> {});

// RIGHT: waiter is armed before the click
Response r = page.waitForResponse("**/api/pricing-cases", () -> form.submit());
```

### 1.2 Three ways to match

| Matcher | Example | Use when |
| --- | --- | --- |
| Glob `String` | `"**/api/pricing-cases*"` | Quick one-off. Must match the whole URL; `*` stops at `/`. |
| `Pattern` | `Pattern.compile(".*/api/pricing-cases/\\d+$")` | Path has IDs or a variable query string. |
| `Predicate<Response>` | `r -> r.request().method().equals("POST") && ...` | Default choice: lets you match on method as well as path. |

Match on **method + path** with a small reusable predicate. The same URL is often hit by POST and then GET, and a path regex ignores the host and query string:

```java
static Predicate<Response> call(String method, String pathRegex) {
    Pattern path = Pattern.compile(pathRegex);
    return r -> r.request().method().equals(method)
             && path.matcher(URI.create(r.url()).getPath()).matches();
}

Response created = page.waitForResponse(
    call("POST", "/api/pricing-cases"),
    () -> form.submit());
```

Do **not** put the status in the predicate (`&& r.status() == 201`). If the API returns 500, the predicate never matches and you get a vague 30-second timeout. Match first, then assert the status, and you get an immediate failure that shows the real 500.

### 1.3 Reading the response

| Method | Returns |
| --- | --- |
| `status()`, `statusText()`, `ok()` | HTTP status; `ok()` is true for 200–299 |
| `headers()`, `headerValue(name)` | Response headers (lower-case names) |
| `text()`, `body()` | Body as `String` or `byte[]`; waits for the body to download |
| `url()` | Final URL, including the query string |
| `request()` | The `Request`: `method()`, `postData()`, `headers()`, `timing()` |

Playwright Java has no `json()` method, so parse with Jackson. Step methods can declare `throws Exception`, which Cucumber accepts:

```java
// One mapper for the whole framework (Jackson 2.15+)
public final class Json {
    public static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)   // money never goes through double
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)  // additive API changes don't break UI tests
        .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)   // keep "1.50" when the UI shows scale
        .build();
    private Json() {}
}

assertThat(created.status()).isEqualTo(201);

// Tree access for a couple of fields
JsonNode body = Json.MAPPER.readTree(created.text());
String caseId = body.path("id").asText();
BigDecimal margin = body.path("margin").decimalValue();   // exact, thanks to USE_BIG_DECIMAL_FOR_FLOATS

// Or map to a DTO (Jackson 2.12+ supports records)
record PricingCaseDto(String id, String reference, String status, BigDecimal margin) {}
PricingCaseDto dto = Json.MAPPER.readValue(created.text(), PricingCaseDto.class);

// Both sides of the call from one wait
String correlationId = created.headerValue("x-correlation-id");
String sentPayload   = created.request().postData();
```

The three settings each fix a Jackson default. Untyped decimals are read as `double` unless `USE_BIG_DECIMAL_FOR_FLOATS` is on. Trailing zeros are stripped from tree `BigDecimal`s since 2.15. Unknown properties fail in Jackson 2.x (Jackson 3 already ignores them). Keep strict parsing where the contract itself is under test, such as the field set of a PATCH body.

### 1.4 waitForRequest and waitForRequestFinished

- `waitForRequest` returns as soon as the request is **sent**. Use it when only the outgoing payload matters, or when the call is fire-and-forget (an audit event, telemetry).
- If you need both sides, prefer `waitForResponse` plus `response.request()`: one wait covers both.
- `waitForRequestFinished` returns after the body has fully downloaded, which is when `timing()` is populated.

```java
Request sent = page.waitForRequest(
    r -> r.method().equals("POST") && r.url().endsWith("/api/audit-events"),
    () -> details.approve());
assertThat(sent.postData()).contains("\"action\":\"APPROVE\"");

Request done = page.waitForRequestFinished(
    new Page.WaitForRequestFinishedOptions()
        .setPredicate(r -> r.url().contains("/api/pricing-cases/search")),
    () -> grid.applyFilter(ref));
log.info("search took {} ms", done.timing().responseEnd);
```

### 1.5 One action that triggers several calls

Nest the waits. Both waiters are armed before `submit()` runs, so the order in which the responses arrive doesn't matter:

```java
AtomicReference<Response> refreshed = new AtomicReference<>();

Response created = page.waitForResponse(call("POST", "/api/pricing-cases"), () ->
    refreshed.set(page.waitForResponse(call("GET", "/api/pricing-cases/search"),
        () -> form.submit())));

assertThat(created.status()).isEqualTo(201);
assertThat(refreshed.get().status()).isEqualTo(200);
```

For three or more calls, nesting gets unreadable. Use the recorder from Part 2 instead.

### 1.6 Navigation

Wrap `navigate` the same way to wait for the data a page needs, rather than the page load itself:

```java
Response data = page.waitForResponse(call("GET", "/api/pricing-cases/search"),
    () -> page.navigate("/pricing-cases"));
```

This is more precise than `LoadState.NETWORKIDLE`, which Playwright discourages for tests. It only waits for 500 ms of network silence and says nothing about *which* call you needed.

### 1.7 Timeouts

The default is the page's default timeout (30 s). In Java you can override it per call; note that the options argument comes **before** the callback:

```java
Response r = page.waitForResponse(
    call("GET", "/api/pricing-cases/search"),
    new Page.WaitForResponseOptions().setTimeout(15_000),
    () -> grid.applyFilter(ref));

page.setDefaultTimeout(20_000);   // all actions and waits on this page
```

On expiry it throws `com.microsoft.playwright.TimeoutError`. The message names the wait but not what actually happened on the network; the helper in Part 3 fixes that.

## Part 2: Network events and a NetworkRecorder

Network events let you record every API call in the background without changing any test step. A `NetworkRecorder` built on two of them gives you a per-scenario call log, plus queries and guards on top of it.

### 2.1 The four events

| Event | Fires when | Handler receives |
| --- | --- | --- |
| `onRequest` | Request is issued | `Request` (no response yet) |
| `onResponse` | Status + headers arrive | `Response` (body may still be downloading) |
| `onRequestFinished` | Body fully downloaded | `Request`: `response()` and `timing()` complete |
| `onRequestFailed` | No HTTP response at all | `Request`: `failure()` holds the error text |

Every request ends in exactly one of *finished* or *failed*. An HTTP 500 counts as **finished**, because the status is just a number. *Failed* means there was no response at all: connection refused, DNS failure, a CORS block, or a request aborted by navigation. So the recorder only needs those two events.

Register handlers on the `BrowserContext`, not the `Page`: context events also cover popups and new tabs.

### 2.2 Threading model (Java-specific)

Handlers run on your test thread, and only while that thread is inside a Playwright call (an action, an assertion, a `waitFor...`). Between calls, events queue up. Three consequences follow:

1. A plain `ArrayList` in the recorder is safe; no locking is needed.
2. After your last action, **sync first** (a `PlaywrightAssertions` check or a `waitForResponse`), then read the recorder. Otherwise the last call may not be dispatched yet.
3. Never read the recorder from an Awaitility background thread; use `.pollInSameThread()`.

### 2.3 The data model: ApiCall

```java
public record ApiCall(int seq, String method, String url, String path, int status,
                      double durationMs, String requestBody, String responseBody,
                      Map<String, String> responseHeaders, String failure) {

    public boolean failed() { return failure != null; }

    public String header(String name) {
        return responseHeaders.getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    @Override public String toString() {
        return failed()
            ? "#%d %s %s FAILED (%s)".formatted(seq, method, path, failure)
            : "#%d %s %s -> %d in %.0f ms".formatted(seq, method, path, status, durationMs);
    }
}
```

### 2.4 Masking sensitive data

Recorded bodies end up in CI artifacts and test reports, so mask before storing. The recorder deliberately never stores request headers, which keeps `Authorization` and cookies out of the log entirely.

```java
public class BodyMasker {
    private static final Pattern SENSITIVE = Pattern.compile(
        "(\"(?:accountNumber|sortCode|iban|email|dateOfBirth|password|accessToken|refreshToken)\"\\s*:\\s*)\"[^\"]*\"",
        Pattern.CASE_INSENSITIVE);

    public String mask(String body) {
        return body == null ? null : SENSITIVE.matcher(body).replaceAll("$1\"***\"");
    }
}
```

This pattern only masks string values; add a second pattern for numeric fields if you have them. Before attaching bodies to reports, check where those reports go (GitLab artifacts, Xray attachments). If in doubt, record metadata only (method, path, status, time) in CI and enable bodies locally.

### 2.5 The recorder

```java
public class NetworkRecorder {

    private static final int MAX_BODY_CHARS = 4_000;

    private final List<ApiCall> calls = new ArrayList<>();
    private final List<Predicate<ApiCall>> allowedErrors = new ArrayList<>();
    private final BodyMasker masker = new BodyMasker();
    private final Predicate<String> isApi = url -> url.contains("/api/");

    // Keep the handler instances: off...() needs the SAME object.
    // A fresh this::recordFinished at detach time would not match.
    private final Consumer<Request> onFinished = this::recordFinished;
    private final Consumer<Request> onFailed = this::recordFailed;
    private BrowserContext context;

    public void start(BrowserContext context) {
        this.context = context;
        context.onRequestFinished(onFinished);
        context.onRequestFailed(onFailed);
    }

    public void stop() {
        if (context != null) {
            context.offRequestFinished(onFinished);
            context.offRequestFailed(onFailed);
        }
    }

    private void recordFinished(Request req) {
        if (!isApi.test(req.url())) return;
        Response resp = req.response();
        calls.add(new ApiCall(calls.size() + 1, req.method(), req.url(), Urls.path(req.url()),
            resp.status(), req.timing().responseEnd,
            clip(masker.mask(req.postData())), clip(masker.mask(bodyOf(resp))),
            Map.copyOf(resp.headers()), null));
    }

    private void recordFailed(Request req) {
        if (!isApi.test(req.url())) return;
        calls.add(new ApiCall(calls.size() + 1, req.method(), req.url(), Urls.path(req.url()),
            -1, -1, clip(masker.mask(req.postData())), null, Map.of(), req.failure()));
    }

    private static String bodyOf(Response resp) {
        String type = resp.headers().getOrDefault("content-type", "");
        if (type.isEmpty()) return null;                       // e.g. redirects, 204s
        if (!type.contains("json") && !type.startsWith("text/")) return "<" + type + ">";
        try { return resp.text(); }
        catch (PlaywrightException e) { return "<body unavailable>"; }
    }

    private static String clip(String s) {
        return s == null || s.length() <= MAX_BODY_CHARS ? s
            : s.substring(0, MAX_BODY_CHARS) + "...[clipped]";
    }

    // ---------- queries ----------
    public List<ApiCall> calls()          { return List.copyOf(calls); }
    public int mark()                     { return calls.size(); }
    public List<ApiCall> since(int mark)  { return List.copyOf(calls.subList(mark, calls.size())); }

    public List<ApiCall> find(String method, String pathRegex) {
        Pattern p = Pattern.compile(pathRegex);
        return calls.stream()
            .filter(c -> c.method().equals(method) && p.matcher(c.path()).matches())
            .toList();
    }

    // ---------- guard support (use case 9) ----------
    public void allowError(String method, String pathRegex, int status) {
        Pattern p = Pattern.compile(pathRegex);
        allowedErrors.add(c -> c.method().equals(method)
            && p.matcher(c.path()).matches() && c.status() == status);
    }

    public List<ApiCall> unexpectedErrors() {
        return calls.stream()
            .filter(c -> c.failed() || c.status() >= 400)
            .filter(c -> !"net::ERR_ABORTED".equals(c.failure()))  // cancelled by navigation (Chromium wording)
            .filter(c -> allowedErrors.stream().noneMatch(a -> a.test(c)))
            .toList();
    }

    public String summary() { return summary(calls); }

    public static String summary(List<ApiCall> list) {
        return list.isEmpty() ? "(no API calls recorded)"
            : list.stream().map(ApiCall::toString).collect(Collectors.joining("\n"));
    }
}
```

A small URL helper used throughout:

```java
public final class Urls {
    private Urls() {}

    public static String path(String url) { return URI.create(url).getPath(); }

    public static Map<String, String> query(String url) {
        String q = URI.create(url).getRawQuery();
        if (q == null) return Map.of();
        return Arrays.stream(q.split("&"))
            .map(kv -> kv.split("=", 2))
            .collect(Collectors.toMap(
                kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                kv -> kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "",
                (a, b) -> a + "," + b));
    }
}
```

What `summary()` looks like in a failed scenario's report:

```text
#1 GET /api/pricing-cases/search -> 200 in 184 ms
#2 POST /api/pricing-cases -> 201 in 412 ms
#3 GET /api/pricing-cases/search -> 200 in 2310 ms
#4 GET /api/reference-data/currencies FAILED (net::ERR_CONNECTION_REFUSED)
```

### 2.6 Querying the recorder in a step

Use `mark()` before an action and `since(mark)` after it to look at only the calls that action caused:

```java
int mark = recorder.mark();
grid.applyFilter(ref);
PlaywrightAssertions.assertThat(grid.rowFor(ref)).isVisible();   // sync first (2.2)

List<ApiCall> searches = recorder.since(mark).stream()
    .filter(c -> c.path().equals("/api/pricing-cases/search"))
    .toList();
assertThat(searches).hasSize(1);
assertThat(Urls.query(searches.get(0).url())).containsEntry("reference", ref);
```

## Part 3: Wiring it into Cucumber + PicoContainer

The wiring takes three small classes injected by PicoContainer. Because PicoContainer creates new instances per scenario, every scenario automatically gets a fresh recorder.

| Class | Responsibility | Injected into |
| --- | --- | --- |
| `UiSession` | Owns the per-scenario `BrowserContext` and `Page` | Everything below |
| `NetworkRecorder` | Records every API call (Part 2) | `ApiTraffic`, hooks, steps that query calls |
| `ApiTraffic` | `waitForResponse` wrapper with status check, JSON parsing and diagnostics on timeout | Step definitions |
| `NetworkHooks` | Starts recording, attaches the call log, runs the error guard | (Cucumber hooks) |

### 3.1 UiSession (minimal)

Keep your existing `Playwright`/`Browser` lifecycle if you share them per thread. Only the context needs to be per scenario.

```java
public class UiSession {
    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    public void open() {
        playwright = Playwright.create();
        Browser browser = playwright.chromium().launch();
        context = browser.newContext(new Browser.NewContextOptions()
            .setBaseURL(System.getProperty("app.url")));
        page = context.newPage();
    }

    public BrowserContext context() { return context; }
    public Page page()              { return page; }
    public void close()             { if (playwright != null) playwright.close(); }
}
```

### 3.2 ApiTraffic: the helper your steps call

This wrapper turns a vague `TimeoutError` into a message that lists the calls that **did** happen during the action:

```java
public class ApiTraffic {

    private static final double TIMEOUT_MS = 15_000;
    private final UiSession session;
    private final NetworkRecorder recorder;
    private final ObjectMapper mapper = Json.MAPPER;   // shared configuration from 1.3

    public ApiTraffic(UiSession session, NetworkRecorder recorder) {
        this.session = session;
        this.recorder = recorder;
    }

    /** Runs the action, waits for METHOD + path, asserts the status. */
    public Response expect(String method, String pathRegex, int expectedStatus, Runnable action) {
        Pattern path = Pattern.compile(pathRegex);
        int mark = recorder.mark();
        Response resp;
        try {
            resp = session.page().waitForResponse(
                r -> r.request().method().equals(method)
                     && path.matcher(Urls.path(r.url())).matches(),
                new Page.WaitForResponseOptions().setTimeout(TIMEOUT_MS),
                action);
        } catch (TimeoutError e) {
            throw new AssertionError("No %s %s within %.0f ms. API calls during the action:%n%s"
                .formatted(method, pathRegex, TIMEOUT_MS,
                           NetworkRecorder.summary(recorder.since(mark))), e);
        }
        if (resp.status() != expectedStatus) {
            throw new AssertionError("%s %s returned %d, expected %d. Body: %s"
                .formatted(method, Urls.path(resp.url()), resp.status(),
                           expectedStatus, abbreviate(resp.text())));
        }
        return resp;
    }

    public JsonNode json(String raw) {
        try { return mapper.readTree(raw); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Not JSON: " + abbreviate(raw), e); }
    }
    public JsonNode json(Response resp)    { return json(resp.text()); }
    public JsonNode json(APIResponse resp) { return json(resp.text()); }

    private static String abbreviate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }
}
```

Because `expect` converts a `TimeoutError` into an `AssertionError`, don't call it inside an Awaitility loop that is meant to retry on timeouts. Use `waitForResponse` directly there (use case 4).

When a wait times out, the failure now explains itself:

```text
java.lang.AssertionError: No POST /api/pricing-cases within 15000 ms. API calls during the action:
#7 POST /api/pricing-cases/validate -> 400 in 95 ms
```

In this example the UI never sent the create call, because client-side validation failed first. A bare `TimeoutError` would not have told you that.

### 3.3 NetworkHooks

```java
public class NetworkHooks {
    private final UiSession session;
    private final NetworkRecorder recorder;

    public NetworkHooks(UiSession session, NetworkRecorder recorder) {
        this.session = session;
        this.recorder = recorder;
    }

    @Before(order = 10)                 // after the hook that opens UiSession (order 0)
    public void startRecording() {
        recorder.start(session.context());
    }

    // @After hooks: HIGHER order runs FIRST. Guard -> attach -> close context (order 0).
    @After(order = 110)
    public void failOnUnexpectedApiErrors(Scenario scenario) {
        if (scenario.isFailed()) return;
        session.page().title();         // cheap Playwright call: lets queued events dispatch (2.2)
        List<ApiCall> bad = recorder.unexpectedErrors();
        assertThat(bad).as("Unexpected API errors:%n%s", NetworkRecorder.summary(bad)).isEmpty();
    }

    @After(order = 100)
    public void attachApiCalls(Scenario scenario) {
        if (scenario.isFailed()) {
            scenario.attach(recorder.summary(), "text/plain", "API calls");
        }
    }
}
```

The guard runs before the attach hook. If the guard fails the scenario, the call log still gets attached. To attach the full JSON (bodies included) as well, serialise `recorder.calls()` with Jackson and attach it as `application/json`.

## Part 4: The 10 most common API + UI use cases

These ten patterns cover most API-to-UI checks in a suite that runs against real services. Use cases 1–8 are built on `waitForResponse` (through `ApiTraffic`); 9 and 10 are built on the recorder.

| # | Use case | Main tool | What it catches |
| --- | --- | --- | --- |
| 1 | Capture IDs from UI actions | `waitForResponse` | Brittle scraping of IDs from the DOM |
| 2 | UI → API request contract | `response.request()` | Field mapping, rounding, date formats |
| 3 | API response ↔ UI rendering | `waitForResponse` | Wrong bindings, formatting errors |
| 4 | Seed via API, verify in UI | `APIRequestContext` + Awaitility | Eventual-consistency flakes |
| 5 | Act in UI, verify via API and DB | `waitForResponse` + `APIRequestContext` | Optimistic UI that never persisted |
| 6 | Wait for the data a page needs | `waitForResponse` on `navigate` | Sleeps and `networkidle` flakiness |
| 7 | Filters, sorting, pagination | `Urls.query(...)` | Dropped or wrong query params |
| 8 | Real error paths | `expect(..., 422, ...)` + `allowError` | Wrong or missing error messages |
| 9 | Network health guard | Recorder | Silent failures, duplicate calls |
| 10 | Latency + compliance headers | Recorder | Slow endpoints, cacheable sensitive data, PII in URLs |

### 1. Capture server-generated IDs from a UI action

Take the ID from the real create response instead of scraping it from the page. It then feeds your DB, Kafka and API checks.

```java
@When("I create a pricing case for client {string}")
public void createCase(String client) {
    form.fillClient(client);
    Response resp = traffic.expect("POST", "/api/pricing-cases", 201, () -> form.submit());
    JsonNode body = traffic.json(resp);
    ctx.setCaseId(body.path("id").asText());
    ctx.setCaseRef(body.path("reference").asText());
}
```

### 2. Verify the UI → API request contract

Assert what the UI actually sent. Frontend refactors silently break field names, rounding and date formats.

```java
Response resp = traffic.expect("POST", "/api/pricing-cases", 201, () -> form.submit());
JsonNode sent = traffic.json(resp.request().postData());

assertThat(sent.path("currency").asText()).isEqualTo("GBP");
assertThat(sent.path("margin").decimalValue()).isEqualByComparingTo("1.25");  // not 1.2500001
assertThat(sent.path("startDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");   // ISO, not dd/MM/yyyy
assertThat(sent.has("internalNotes")).isFalse();                               // UI-only field must not leak
```

### 3. API response ↔ UI rendering consistency

Compare what the API returned with what the user sees, field by field.

```java
Response resp = traffic.expect("GET", "/api/pricing-cases/[^/]+", 200, () -> grid.openCase(ref));
JsonNode c = traffic.json(resp);

PlaywrightAssertions.assertThat(details.status()).hasText(StatusLabels.of(c.path("status").asText()));
PlaywrightAssertions.assertThat(details.client()).hasText(c.path("clientName").asText());
PlaywrightAssertions.assertThat(details.margin()).hasText(DisplaySpec.percent(c.path("margin").decimalValue()));
PlaywrightAssertions.assertThat(details.limit()).hasText(DisplaySpec.gbp(c.path("limit").decimalValue()));
```

Most false failures here come from how the expected string is built, not from the locator. Build expected strings in one small `DisplaySpec` class. Write it from the requirements, not by copying front-end code, and unit-test it. Three defaults cause trouble otherwise:

- **Rounding.** The browser's `Intl` formatting rounds halves away from zero. Java's `DecimalFormat` defaults to HALF\_EVEN, so 1234.565 shows as £1,234.57 on screen but formats as 1,234.56 in Java.
- **Scale.** Confirm whether the API sends a fraction (0.0125) or percentage points (1.25); a fraction needs `movePointRight(2)`.
- **Locale and timezone.** CI agents often run in UTC, so pin them on the context (`setLocale("en-GB")`, `setTimezoneId("Europe/London")`) and use the same values in `DisplaySpec`.

```java
public final class DisplaySpec {

    /** Spec: margin in percentage points, 2 dp, halves away from zero. */
    public static String percent(BigDecimal points) {
        return points.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Spec: GBP with symbol and thousands separator, 2 dp, halves away from zero. */
    public static String gbp(BigDecimal amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.UK);
        f.setRoundingMode(RoundingMode.HALF_UP);   // matches the browser's Intl default
        return f.format(amount);
    }

    private DisplaySpec() {}
}

// Unit test with literal anchors, so the spec itself is checked
@Test
void roundsLikeTheBrowser() {
    assertThat(DisplaySpec.gbp(new BigDecimal("1234.565"))).isEqualTo("£1,234.57");
    assertThat(DisplaySpec.percent(new BigDecimal("1.125"))).isEqualTo("1.13%");
}
```

Three more rules keep this pattern honest:

- **Assert through `hasText` on the locator**, not `textContent()` plus `isEqualTo`. `hasText` retries and normalises whitespace, which absorbs the narrow no-break spaces JDK 20+ puts into some formatted strings.
- **Add literal anchors on seeded data.** A case seeded with margin 1.125 must show exactly "1.13%". Derived values prove the right record reached the right field; literals prove the formatting rule.
- **Keep enum-to-label mappings in one lookup** like `StatusLabels` (for example `PENDING_APPROVAL` → "Pending approval"). Source the labels from the spec or i18n bundle, and throw on unmapped codes so a missing translation fails loudly.

### 4. Seed via API, verify in UI (eventual consistency)

Split the wait into two phases, so a failure tells you which layer broke. **Phase A** polls the same search endpoint the grid calls, as the logged-in user, until the seeded case appears. **Phase B** then drives the UI once and asserts the row's content. If Phase A times out, the data never reached the read side. If Phase B fails after Phase A passed, it's a UI, cache or permissions bug.

```java
@Given("a pricing case exists for client {string}")
public void seedCase(String client) {
    APIResponse created = session.context().request().post("/api/pricing-cases",
        RequestOptions.create().setData(Map.of("client", client, "currency", "GBP")));
    PlaywrightAssertions.assertThat(created).isOK();
    ctx.setCaseRef(traffic.json(created).path("reference").asText());
    ctx.setClient(client);
}

@When("I filter the grid for that case")
public void filterForCase() {
    String ref = ctx.caseRef();

    // Phase A: propagation. Same read path as the grid, same user, so entitlements apply.
    await("case " + ref + " returned by search")
        .atMost(Duration.ofSeconds(Long.getLong("sla.search.seconds", 60)))  // agreed SLA from config
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofSeconds(2))
        .pollInSameThread()                          // page.request() is a Playwright object
        .until(() -> searchReturns(ref));

    // Phase B: render once.
    traffic.expect("GET", "/api/pricing-cases/search", 200, () -> grid.applyFilter(ref));
    Locator row = grid.rowFor(ref);
    PlaywrightAssertions.assertThat(row).isVisible();
    PlaywrightAssertions.assertThat(row).containsText(ctx.client());   // content, not just presence
}

private boolean searchReturns(String ref) {
    APIResponse r = session.page().request().get("/api/pricing-cases/search",
        RequestOptions.create()
            .setQueryParam("reference", ref)
            .setTimeout(5_000));                     // well below atMost
    if (r.status() != 200) {                         // terminal: fail now, don't keep polling
        throw new IllegalStateException("Search returned " + r.status() + ": " + r.text());
    }
    return traffic.json(r).findValuesAsText("reference").contains(ref);
}
```

Three details matter:

- **Poll search, not GET by id.** Search indexes such as Elasticsearch return a document by id in real time but only find it in search after a refresh. A passing GET by id proves nothing about the grid.
- **No `ignoreExceptions()`.** It ignores every exception, so a 401, a JSON error or a closed browser turns into a slow timeout. Here an error status or a request timeout fails at once, and only "not found yet" retries.
- **Inner timeouts below `atMost`.** With `pollInSameThread()`, Awaitility cannot interrupt a running check, so a 30-second default inside it can overrun a 60-second budget badly.

**If you can't call the search endpoint directly,** re-run the grid's own search and read its response. Ignore only Playwright's `TimeoutError`, which is the one failure that genuinely means "try again":

```java
await("case " + ref + " returned by the grid's search")
    .atMost(Duration.ofSeconds(Long.getLong("sla.search.seconds", 60)))
    .pollDelay(Duration.ZERO)
    .pollInterval(Duration.ofSeconds(3))
    .pollInSameThread()
    .ignoreException(TimeoutError.class)                 // only a missed response is retryable
    .until(() -> {
        Response r = session.page().waitForResponse(
            call("GET", "/api/pricing-cases/search"),           // helper from 1.2
            new Page.WaitForResponseOptions().setTimeout(5_000), // well below atMost
            () -> grid.applyFilter(ref));
        if (r.status() != 200) {
            throw new IllegalStateException("Search returned " + r.status() + ": " + r.text());
        }
        return Json.MAPPER.readTree(r.text()).findValuesAsText("reference").contains(ref);
    });
```

**Auth for seeding.** `context().request()` shares the browser's cookies, so the seed call runs as the logged-in UI user. If your services need a bearer token instead, create a separate `APIRequestContext` with `setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + token))` and `dispose()` it after the scenario. Pass a `Map` or object to `setData` so it's sent as JSON; a `String` body goes out as `application/octet-stream` unless you set `Content-Type` yourself.

### 5. Act in UI, verify via API and database

The UI can show an optimistic value even when the save never persisted. Taking a scenario rename as the example, each layer catches something the others miss:

| Layer | What only this layer catches |
| --- | --- |
| Captured request | UI → API contract: wrong field, extra fields, wrong media type |
| Captured response | Server rejected or altered the change |
| Independent GET | Server echoed the request without saving; PATCH behaving like PUT and wiping other fields |
| Database | Audit columns, version +1 (not +2 from a double submit), outbox or audit rows |
| UI after reload | Optimistic update on screen that never saved |

```java
Response patch = traffic.expect("PATCH", "/api/scenarios/[^/]+", 200,
    () -> details.rename(newName));                       // Enter or Save inside the callback

JsonNode sent = traffic.json(patch.request().postData());
assertThat(sent.fieldNames()).toIterable().containsExactly("name");   // PATCH sends only the change
assertThat(sent.path("name").asText()).isEqualTo(newName);
assertThat(traffic.json(patch).path("name").asText()).isEqualTo(newName);

// Independent re-read: assert ONCE if the contract is read-your-writes
APIResponse fresh = session.context().request().get("/api/scenarios/" + scenarioId,
    RequestOptions.create().setHeader("Cache-Control", "no-cache"));
PlaywrightAssertions.assertThat(fresh).isOK();
assertThat(traffic.json(fresh).path("name").asText()).isEqualTo(newName);
```

If the API promises a synchronous write (a 200 or 204 that means "saved"), don't poll the GET: polling would hide a real read-after-write bug. Poll only where delay is the stated contract, such as a 202 or a separate read model. Then set `atMost` to the agreed SLA and use `pollInSameThread()`, because the call goes through Playwright. The GET shares the browser session, so it's independent of the UI but not of auth.

**Database layer.** Use it for what neither the UI nor the API shows: audit columns, the version number and outbox or audit rows. A read-only `JdbcTemplate` fits better than JPA, which inherits the application's mapping and can serve cached entities. PicoContainer rebuilds per-scenario objects, so keep the connection pool outside it:

```java
// One pool per JVM, SELECT-only account, read-only connections
public final class Db {
    private static final HikariDataSource DS = create();
    public static final JdbcTemplate JDBC = new JdbcTemplate(DS);

    private static HikariDataSource create() {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(System.getProperty("db.url"));
        c.setUsername(System.getProperty("db.user"));
        c.setPassword(System.getenv("DB_PASSWORD"));
        c.setReadOnly(true);
        c.setMaximumPoolSize(4);
        return new HikariDataSource(c);
    }
    private Db() {}
}

record ScenarioRow(String name, long version, String updatedBy) {}

// versionBefore: read the same row before the rename
await("rename committed to DB")
    .pollDelay(Duration.ZERO)                                         // synchronous writes pass on the first check
    .atMost(Duration.ofSeconds(Long.getLong("sla.db.seconds", 5)))   // the agreed SLA, not a blanket guess
    .untilAsserted(() -> {
        ScenarioRow row = Db.JDBC.queryForObject(
            "select name, version, updated_by from scenario where id = ?",
            (rs, i) -> new ScenarioRow(rs.getString(1), rs.getLong(2), rs.getString(3)),
            scenarioId);
        assertThat(row.name()).isEqualTo(newName);
        assertThat(row.version()).isEqualTo(versionBefore + 1);      // +2 means a double submit
        assertThat(row.updatedBy()).isEqualTo(testUser);
    });
```

With `pollDelay(ZERO)` this check is correct whether the write is synchronous or not: an instant write passes on the first poll. The timeout is what must come from the contract. A blanket 10–15 s on a synchronous write lets late-commit bugs pass. If nobody can give you an SLA yet, use a generous limit, log the elapsed time, and agree one from the numbers. JDBC checks don't touch Playwright, so they don't need `pollInSameThread()`.

Finish with `session.page().reload()` and a `hasText(newName)` check on the scenario title, which catches an optimistic update that never saved.

### 6. Wait for the data a page needs

Replace sleeps and `networkidle` with the one call the page depends on. Keep its data for later assertions.

```java
@When("I open the pricing cases page")
public void openPricingCases() {
    Response data = traffic.expect("GET", "/api/pricing-cases/search", 200,
        () -> session.page().navigate("/pricing-cases"));
    ctx.setGridData(traffic.json(data));
}

@Then("the grid shows every case returned by the API")
public void gridMatchesApi() {
    PlaywrightAssertions.assertThat(grid.rows()).hasCount(ctx.gridData().path("items").size());
}
```

For a virtualised grid that renders only the visible rows, compare against what is on screen rather than the full count.

### 7. Filters, sorting and pagination

Assert the query string the UI built, then check the UI shows the matching data. The classic bug this catches is paging that silently drops the sort or the filter.

```java
Response sorted = traffic.expect("GET", "/api/pricing-cases/search", 200,
    () -> grid.sortBy("Created", "desc"));
assertThat(Urls.query(sorted.url()))
    .containsEntry("sort", "createdAt,desc")
    .containsEntry("page", "0");
PlaywrightAssertions.assertThat(grid.cell(0, "Reference"))
    .hasText(traffic.json(sorted).path("items").get(0).path("reference").asText());

Response page2 = traffic.expect("GET", "/api/pricing-cases/search", 200, () -> grid.nextPage());
assertThat(Urls.query(page2.url()))
    .containsEntry("page", "1")
    .containsEntry("sort", "createdAt,desc");   // sort must survive paging
```

### 8. Real error paths: backend validation → UI message

No mocking is needed. Submit data the backend rejects, then assert both the error response and how the UI presents it.

```java
recorder.allowError("POST", "/api/pricing-cases", 422);   // expected: keeps the guard (9) quiet

form.fillMargin("99");                                      // allowed by the UI, over the backend limit
Response r = traffic.expect("POST", "/api/pricing-cases", 422, () -> form.submit());
JsonNode error = traffic.json(r).path("errors").get(0);

assertThat(error.path("field").asText()).isEqualTo("margin");
PlaywrightAssertions.assertThat(form.errorFor("margin")).containsText(error.path("message").asText());
PlaywrightAssertions.assertThat(form.successToast()).not().isVisible();
```

Pick a value the UI accepts but the backend rejects. If client-side validation blocks it, no POST is ever sent, and the `ApiTraffic` timeout message will say so (3.2).

### 9. Network health guard

The `@After` hook in 3.3 already fails any scenario with an unexpected 4xx, 5xx or network failure. That catches background calls the UI hides, such as a reference-data 500 that only shows up as an empty dropdown. Declare the errors a scenario legitimately expects, and check for duplicate calls per action:

```java
recorder.allowError("GET", "/api/limits/[^/]+", 404);     // new clients have no limit yet

int mark = recorder.mark();
grid.applyFilter(ref);
PlaywrightAssertions.assertThat(grid.rowFor(ref)).isVisible();

Map<String, Long> perEndpoint = recorder.since(mark).stream()
    .collect(Collectors.groupingBy(c -> c.method() + " " + c.path(), Collectors.counting()));
assertThat(perEndpoint).containsEntry("GET /api/pricing-cases/search", 1L);  // no double fetch
```

### 10. Latency budgets and compliance headers

This runs one pass over everything recorded. Soft assertions report every violation at once instead of stopping at the first.

```java
@Then("pricing APIs met their latency and header rules")
public void pricingApiRules() {
    session.page().title();                       // flush queued events (2.2)
    List<ApiCall> pricing = recorder.calls().stream()
        .filter(c -> !c.failed() && c.path().startsWith("/api/pricing-cases"))
        .toList();

    SoftAssertions soft = new SoftAssertions();
    for (ApiCall c : pricing) {
        soft.assertThat(c.durationMs()).as("latency " + c).isLessThan(3_000);
        soft.assertThat(c.header("cache-control")).as("cache-control " + c).contains("no-store");
        soft.assertThat(c.url()).as("no PII in URL " + c)
            .doesNotContainPattern("(?i)(accountNumber|sortCode|dateOfBirth)=");
    }
    soft.assertAll();
}
```

Shared test environments are noisy, so set generous budgets. The goal is to catch an order-of-magnitude regression (search going from 200 ms to 8 s after a dropped index), not 10% jitter. `durationMs` is measured from the browser's side, from request start to the last byte of the response.

## Part 5: Gotchas checklist

Most flaky or misleading network checks trace back to one of these. Use the list when reviewing a new step definition.

- [ ] The action runs **inside** the `waitFor...` callback, never before it (1.1).
- [ ] The predicate matches on method + path, never on status (1.2).
- [ ] Globs match the whole URL: `*` stops at `/` and `?` is a literal question mark. Use a `Pattern` or a predicate when there's a query string.
- [ ] Decimals are parsed as `BigDecimal` (`USE_BIG_DECIMAL_FOR_FLOATS`), and expected display strings come from a unit-tested `DisplaySpec` with pinned locale, timezone and rounding (1.3, use case 3).
- [ ] The recorder is read only after a sync point, since Java dispatches events only during Playwright calls. In `@After`, make a cheap call such as `page.title()` first (2.2).
- [ ] Any Awaitility block that touches `Page`, `Locator`, `Response` or the recorder uses `.pollInSameThread()`, and every Playwright timeout inside it is well below `atMost`.
- [ ] Awaitility never uses a blanket `ignoreExceptions()`. Ignore only the exception that means "try again", such as Playwright's `TimeoutError`, and fail at once on error statuses (use case 4).
- [ ] Polling timeouts come from an agreed SLA in config. Synchronous contracts are asserted once, not polled (use case 5).
- [ ] The recorder is attached to the `BrowserContext`, not the `Page`, so popups and new tabs are covered.
- [ ] `off...()` is given the same handler instance that `on...()` received (2.5).
- [ ] Remember that HTTP 4xx/5xx responses are *finished* events; only connection-level problems are *failed* (2.1).
- [ ] `net::ERR_ABORTED` (Chromium wording) after a navigation is noise, not a defect. Firefox and WebKit report it with different text.
- [ ] Bodies are masked, request headers are never stored, and you know where CI reports end up.
- [ ] CI artifacts that hold traces, HARs or recorded bodies have restricted access (`artifacts:access`). GitLab's default lets anyone who can see the pipeline download them.
- [ ] Reruns don't hide consistency bugs: if Surefire/Failsafe `rerunFailingTestsCount` is on, set `failOnFlakeCount` and review every flake.
- [ ] Playwright's `assertThat` and AssertJ's `assertThat` are never both static-imported.
- [ ] A separate token-based `APIRequestContext` is closed with `dispose()` at the end of the scenario. The context-bound `context().request()` closes with the context.
- [ ] If expected network events never appear, a Service Worker may be intercepting the requests. Create the context with `setServiceWorkers(ServiceWorkerPolicy.BLOCK)` ([Playwright docs](https://playwright.dev/java/docs/network)).
- [ ] If you later add a `route()` (for a correlation header, say), headers the browser adds at send time, such as `Cookie` and `Host`, may be missing from `Request.headers()`. Observe without routes when you need the exact wire headers ([Playwright docs](https://playwright.dev/java/docs/network)).

## Sources

- [Network | Playwright Java](https://playwright.dev/java/docs/network)
- [Multithreading | Playwright Java](https://playwright.dev/java/docs/multithreading)
- [Awaitility usage guide](https://github.com/awaitility/awaitility/wiki/Usage)
- [Jackson DeserializationFeature (2.19)](https://github.com/FasterXML/jackson-databind/blob/2.19/src/main/java/com/fasterxml/jackson/databind/DeserializationFeature.java)
- [Playwright Java API: Page](https://github.com/microsoft/playwright/blob/main/docs/src/api/class-page.md)
