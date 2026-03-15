# PricingTool Test Automation Framework

A Spring Boot 3.x + Cucumber BDD + Playwright test automation framework with multi-environment profile management and Docker support.

## Quick Start

### Prerequisites

- Java 17+ (tested with OpenJDK 21)
- Maven 3.6+ (or use the included `mvnw` wrapper)
- Docker & Docker Compose (for containerised execution)

### Run Tests

```bash
# Local — H2 database + Chromium browser (visible)
mvn clean test

# Docker — headless Chromium inside container
docker compose run --rm test-runner

# Docker via DockerRunner utility
mvn compile exec:java -Dexec.mainClass=com.pricingtool.runner.DockerRunner

# Or use the script
./run-tests.sh
```

That's it. Tests run out of the box with zero setup.

---

## Profile Management

All test configuration lives in a single file: `src/test/resources/application.yml`. Profiles let you switch environments without changing any code or config — just pass the profile name.

### Available Profiles

| Profile | Database | Use Case |
|---------|----------|----------|
| *(default)* | H2 in-memory | Local development, quick feedback |
| `local` | H2 in-memory (debug logging) | Local development with verbose SQL output |
| `ci` | SQL Server on `ci-sqlserver:1433` | CI/CD pipelines with real database |
| `docker` | H2 in-memory | Docker Compose execution (headless Playwright) |

### How to Switch Profiles

**Option 1 — Maven system property (recommended):**

```bash
# Run with default profile (H2)
mvn clean test

# Run with local profile (H2 + debug logging)
mvn clean test -Dspring.profiles.active=local

# Run with CI profile (SQL Server)
mvn clean test -Dspring.profiles.active=ci

# Run with Docker profile (H2, headless Playwright)
mvn clean test -Dspring.profiles.active=docker
```

**Option 2 — Environment variable:**

```bash
# Linux/macOS
SPRING_PROFILES_ACTIVE=local mvn clean test

# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE="local"; mvn clean test
```

**Option 3 — Edit `application.yml` directly:**

Set `spring.profiles.active` in `src/test/resources/application.yml`:

```yaml
spring:
  profiles:
    active: local    # Options: local, ci, docker (or remove for default H2)
```

CLI arguments and environment variables always override this setting.

**Option 4 — Shell script:**

```bash
./run-tests.sh              # default (H2)
./run-tests.sh local        # local (H2 + debug logging)
./run-tests.sh ci           # CI environment (SQL Server)
./run-tests.sh docker       # Docker (H2 headless)
```

### Profile Configuration Overview

Each profile overrides only what differs. Shared settings (like `ddl-auto: create-drop`) are defined once in the default section of `application.yml`.

**Default (H2):**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

**Local (H2 + debug logging):**
```yaml
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:h2:mem:testdb_local
    driver-class-name: org.h2.Driver
  jpa:
    show-sql: true
```

**CI (SQL Server):**
```yaml
spring:
  config:
    activate:
      on-profile: ci
  datasource:
    url: jdbc:sqlserver://ci-sqlserver:1433;databaseName=pricingtool_pricing_ci;...
    username: ci_user
    password: CiTestPassword456!
```

**Docker (H2 headless):**
```yaml
spring:
  config:
    activate:
      on-profile: docker
  datasource:
    url: jdbc:h2:mem:testdb_docker
    driver-class-name: org.h2.Driver
```

> **Tip:** To add a new environment, add a new `---` section to `application.yml` with `spring.config.activate.on-profile: <name>` and only the settings that differ.

---

## Docker Execution

The framework uses [Playwright's official Java Docker image](https://mcr.microsoft.com/en-us/artifact/mar/playwright/java) (`mcr.microsoft.com/playwright/java:v1.58.0-noble`) with recommended settings:

- `init: true` — proper process signal handling
- `ipc: host` — Chromium shared memory management
- `cap_add: SYS_ADMIN` — browser sandbox support

Docker execution always uses **H2 in-memory database** — no external database containers are needed.

### Run Tests in Docker

```bash
# Using the script
./run-docker-tests.sh

# Or manually
docker compose up --build test-runner --abort-on-container-exit
```

### DockerRunner (Programmatic Docker Execution)

`DockerRunner.java` is a standalone Java utility that builds the Docker image and runs all tests (including UI tests) inside the container in headless mode:

```bash
# Build and run via Maven
mvn compile exec:java -Dexec.mainClass=com.pricingtool.runner.DockerRunner
```

What it does:
1. Builds the `test-runner` Docker image (`docker compose build test-runner`)
2. Runs all tests inside the container (`docker compose run --rm test-runner`)
3. Streams container output to the console in real time
4. Exits with the container's exit code (0 = all tests passed)

> **Note:** DockerRunner requires Docker and Docker Compose to be installed and running on the host.

### Docker Commands

```bash
# Check running containers
docker compose ps

# View live logs
docker compose logs -f test-runner

# Clean up
docker compose down
```

### Docker Architecture

```
docker-compose.yml
└── test-runner    # Playwright image, H2 database, headless Chromium
```

---

## UI Testing with Playwright

The framework uses [Playwright for Java](https://playwright.dev/java/) (v1.58.0) for browser-based UI testing with Cucumber BDD.

### How It Works

| Component | Role |
|-----------|------|
| `PlaywrightContext.java` | Scenario-scoped Spring bean — manages Playwright, Browser, and Page lifecycle |
| `PlaywrightHooks.java` | Cucumber `@Before("@ui")` / `@After("@ui")` hooks — initialises and tears down the browser per scenario |
| `LandingPageSteps.java` | Step definitions using `playwrightContext.getPage()` for navigation and assertions |

### Local vs Docker Execution

| Mode | Browser | Headless | How to Run |
|------|---------|----------|------------|
| **Local** | Chromium (visible) | `false` | `mvn clean test` |
| **Docker** | Chromium (headless) | `true` | `docker compose run --rm test-runner` |

The `playwright.headless` system property controls headless mode. It defaults to `false` (local). The Dockerfile passes `-Dplaywright.headless=true` automatically.

To force headless mode locally:

```bash
mvn clean test -Dplaywright.headless=true
```

### First-Time Local Setup

Playwright Java downloads browser binaries automatically on first use. If that fails, install manually:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

> This is **not needed** when running via Docker — the Playwright Docker image includes all browsers and system dependencies.

---

## Test Scenarios

The framework includes 12 passing test scenarios:

### UI — Landing Page Validation (`landing_page.feature`)

```gherkin
@ui
Feature: EcoHaven Boots Landing Page

  Scenario: User lands on the landing page and validates navigation
    Given the user navigates to the EcoHaven Boots website
    Then the user should be on the landing page
    And the navigation should contain "Home"
    And the navigation should contain "Products"
    And the navigation should contain "About"
```

> UI scenarios are tagged with `@ui`. Playwright launches Chromium (visible locally, headless in Docker).

### Sample Test (`sample.feature`)

```gherkin
Scenario: Save test data
  Given I have a test value "test1"
  When I save test data "test1"
  Then the test data should be persisted
```

### Loan Pricing Calculation (`pricing_calculation.feature`)

```gherkin
Scenario: Calculate price for standard loan
  Given a borrower with ID "BORROWER-001"
  And a loan amount of 100000
  And a tenor of 24 months
  And a risk grade of "B"
  When the pricing engine calculates the interest rate
  Then the interest rate should be 6.25%

Scenario Outline: Calculate price for multiple risk grades
  Given a loan amount of <amount>
  And a tenor of <tenorMonths> months
  And a risk grade of "<riskGrade>"
  When the pricing engine calculates the interest rate
  Then the interest rate should be <expectedRate>%

  Examples:
    | amount  | tenorMonths | riskGrade | expectedRate |
    | 50000   | 12          | A         | 5.50         |
    | 100000  | 24          | B         | 6.25         |
    | 150000  | 36          | C         | 7.50         |
    | 200000  | 48          | D         | 9.50         |
    | 75000   | 18          | A         | 5.75         |
```

### Test Results

```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Project Structure

```
pricingtool/
├── Dockerfile                                   # Playwright Java image for test execution
├── docker-compose.yml                           # Test runner orchestration (H2 + Playwright)
├── mvnw / mvnw.cmd                              # Maven wrapper (no local Maven needed)
├── pom.xml                                      # Dependencies and build config
├── run-tests.sh                                 # Local test runner (accepts profile arg)
├── run-docker-tests.sh                          # Docker test runner
├── src/
│   ├── main/
│   │   ├── java/com/pricingtool/
│   │   │   ├── PricingToolApplication.java           # Spring Boot entry point
│   │   │   └── runner/
│   │   │       └── DockerRunner.java            # Builds & runs tests in Docker
│   │   └── resources/
│   │       └── application.properties           # Main app config (SQL Server)
│   └── test/
│       ├── java/com/pricingtool/test/
│       │   ├── PricingToolTestApplication.java       # Test bootstrap
│       │   ├── config/
│       │   │   ├── CucumberSpringConfig.java    # Cucumber ↔ Spring bridge
│       │   │   └── PlaywrightContext.java       # Scenario-scoped Playwright bean
│       │   ├── context/
│       │   │   └── ScenarioContext.java          # Scenario-scoped state
│       │   ├── data/
│       │   │   ├── LoanApplication.java         # Loan JPA entity
│       │   │   └── LoanApplicationRepository.java
│       │   ├── entity/
│       │   │   └── TestData.java                # Test data JPA entity
│       │   ├── hooks/
│       │   │   └── PlaywrightHooks.java         # @Before/@After for @ui scenarios
│       │   ├── repository/
│       │   │   └── TestDataRepository.java      # Spring Data repository
│       │   ├── runner/
│       │   │   └── RunCucumberTest.java          # JUnit 5 Cucumber runner
│       │   ├── service/
│       │   │   └── LoanPricingService.java      # Rate calculation engine
│       │   └── steps/
│       │       ├── SampleSteps.java             # Basic persistence steps
│       │       ├── PricingCalculationSteps.java # Pricing test steps
│       │       └── LandingPageSteps.java        # UI landing page steps (Playwright)
│       └── resources/
│           ├── application.yml                  # All test profiles (single file)
│           ├── application.properties           # Shadows main config for tests
│           └── features/
│               ├── sample.feature               # Basic persistence test
│               ├── pricing_calculation.feature   # Pricing BDD scenarios
│               └── landing_page.feature         # UI landing page validation
```

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 17+ (tested with OpenJDK 21) |
| Spring Boot | 3.2.3 |
| Cucumber | 7.16.1 |
| JUnit | 5 (Jupiter) |
| Playwright | 1.58.0 (Maven dependency + Docker image) |
| H2 Database | In-memory (default/local/docker profiles) |
| SQL Server | 2022 (ci profile only) |
| MSSQL JDBC | 12.6.1 |
| Maven | 3.6+ (wrapper included) |

---

## Adding New Tests

### Adding a Database / API Test

1. **Create a feature file** in `src/test/resources/features/`:

    ```gherkin
    Feature: My new feature

      Scenario: Example test
        Given some precondition
        When an action is performed
        Then the expected result occurs
    ```

2. **Implement step definitions** in `src/test/java/com/pricingtool/test/steps/`:

    ```java
    public class MySteps {
        @Autowired
        private ScenarioContext scenarioContext;

        @Given("some precondition")
        public void setup() { /* arrange */ }

        @When("an action is performed")
        public void action() { /* act */ }

        @Then("the expected result occurs")
        public void verify() { /* assert */ }
    }
    ```

3. **Run:** `mvn clean test`

### Adding a UI Test (Playwright)

1. **Create a feature file** tagged with `@ui`:

    ```gherkin
    @ui
    Feature: My UI feature

      Scenario: Verify page elements
        Given the user navigates to "https://example.com"
        Then the page should contain "Welcome"
    ```

2. **Implement step definitions** using `PlaywrightContext`:

    ```java
    public class MyUISteps {
        @Autowired
        private PlaywrightContext playwrightContext;

        @Given("the user navigates to {string}")
        public void navigate(String url) {
            playwrightContext.getPage().navigate(url);
        }

        @Then("the page should contain {string}")
        public void verifyContent(String text) {
            Locator element = playwrightContext.getPage().getByText(text);
            assertThat(element.first()).isVisible();
        }
    }
    ```

    > The `@ui` tag triggers `PlaywrightHooks` which automatically initialises and tears down the browser for each scenario. Use `playwrightContext.getPage()` to interact with the browser.

3. **Run locally** (visible Chromium): `mvn clean test`
4. **Run in Docker** (headless): `docker compose run --rm test-runner`

---

## Test Reports

After each run, reports are generated in `target/cucumber-reports/`:

| Format | Path |
|--------|------|
| HTML | `target/cucumber-reports/cucumber.html` |
| JSON | `target/cucumber-reports/cucumber.json` |
| XML (JUnit) | `target/cucumber-reports/cucumber.xml` |

When running via Docker, reports are volume-mounted to `./target/` on the host.

---

## CI/CD Integration

```bash
# In your CI pipeline — runs tests in Docker with H2 (no host dependencies)
docker compose up --build test-runner --abort-on-container-exit
echo $?  # exit code 0 = all tests passed

# Or set the CI profile for SQL Server-backed tests in your pipeline
SPRING_PROFILES_ACTIVE=ci mvn clean test
```

---

## Troubleshooting

**Tests fail with SQL Server connection error (default profile):**
You're likely running with a non-default profile. Without a profile, tests use H2 in-memory — no database setup needed:
```bash
mvn clean test   # uses H2, always works
```

**Maven not found:**
Use the included wrapper — no Maven installation required:
```bash
./mvnw clean test
```

**Playwright browser not found (local execution):**
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```
> This is not needed when running via Docker — the Playwright image includes all browsers.

**Docker tests fail:**
Check Docker is running and inspect the container logs:
```bash
docker compose logs test-runner
```
