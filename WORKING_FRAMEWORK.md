# PricingTool Test Automation Framework

✅ **Working Spring Boot 3.x + Cucumber BDD Test Automation Framework**

## Quick Start

### Local Execution
```bash
# Build and run tests locally
mvn clean test

# Or use the script
./run-tests.sh
```

### Docker Execution
```bash
# Run tests in Docker with H2 (default)
./run-docker-tests.sh

# Run tests in Docker with SQL Server
./run-docker-tests.sh --with-sqlserver

# Or manually
docker compose up --build test-runner --abort-on-container-exit
docker compose --profile sqlserver up --build test-runner-sqlserver --abort-on-container-exit
```

## Docker Configuration

The framework uses **Playwright's official Java Docker image** with recommended settings:

- ✅ `--init` flag for proper process handling
- ✅ `--ipc=host` for Chromium memory management
- ✅ `--cap-add=SYS_ADMIN` for browser sandbox
- ✅ Volume mount for test reports

### Docker Commands

```bash
# Build and run tests
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Clean up
docker-compose down
```

## What's Working

- ✅ Spring Boot 3.2.3 with JPA/Hibernate
- ✅ Cucumber 7.16.1 BDD framework
- ✅ JUnit 5 Platform integration
- ✅ H2 in-memory database for tests
- ✅ Parallel test execution (3 threads)
- ✅ 11 passing test scenarios
- ✅ HTML/JSON/XML test reports
- ✅ Docker support with Playwright image

## Test Scenarios

### 1. Sample Test (`sample.feature`)
- Basic database persistence test

### 2. Loan Pricing Calculation (`pricing_calculation.feature`)
- Calculate interest rates based on risk grade and tenor
- Persist loan applications to database
- Scenario outlines with multiple examples
- Data-driven testing

## Project Structure

```
pricingtool/
├── Dockerfile                              # Playwright Java image
├── docker-compose.yml                      # Docker orchestration
├── mvnw                                    # Maven wrapper
├── pom.xml
├── run-tests.sh                            # Local test runner
├── run-docker-tests.sh                     # Docker test runner
├── src/
│   ├── main/java/com/pricingtool/
│   │   └── PricingToolApplication.java
│   └── test/java/com/pricingtool/test/
│       ├── config/
│       │   └── CucumberSpringConfig.java
│       ├── context/
│       │   └── ScenarioContext.java
│       ├── data/
│       │   ├── LoanApplication.java
│       │   └── LoanApplicationRepository.java
│       ├── entity/
│       │   └── TestData.java
│       ├── repository/
│       │   └── TestDataRepository.java
│       ├── runner/
│       │   └── RunCucumberTest.java
│       ├── service/
│       │   └── LoanPricingService.java
│       └── steps/
│           ├── SampleSteps.java
│           └── PricingCalculationSteps.java
└── src/test/resources/
    ├── features/
    │   ├── sample.feature
    │   └── pricing_calculation.feature
    └── application.properties
```

## Technology Stack

- **Java**: 17+ (tested with OpenJDK 21)
- **Spring Boot**: 3.2.3
- **Cucumber**: 7.16.1
- **JUnit**: 5 (Jupiter)
- **Database**: H2 (in-memory for tests)
- **Build Tool**: Maven 3.6+
- **Docker**: Playwright Java v1.58.0-noble

## Test Reports

After running tests, reports are generated in:
- **HTML**: `target/cucumber-reports/cucumber.html`
- **JSON**: `target/cucumber-reports/cucumber.json`
- **XML**: `target/cucumber-reports/cucumber.xml`

## Key Features

1. **Spring Boot Integration**: Full dependency injection and auto-configuration
2. **BDD with Cucumber**: Human-readable test scenarios
3. **Scenario Context**: Share state between step definitions
4. **JPA/Hibernate**: Database persistence with entities
5. **Parallel Execution**: Tests run in parallel for faster feedback
6. **Docker Support**: Containerized execution with Playwright
7. **Clean Architecture**: Separation of concerns

## Adding New Tests

1. Create a `.feature` file in `src/test/resources/features/`
2. Implement step definitions in `src/test/java/com/pricingtool/test/steps/`
3. Use `@Autowired` to inject services and repositories
4. Run with `mvn test` or `./run-docker-tests.sh`

## Example Step Definition

```java
@Autowired
private LoanPricingService pricingService;

@Autowired
private ScenarioContext scenarioContext;

@When("the pricing engine calculates the interest rate")
public void calculateRate() {
    BigDecimal rate = pricingService.calculateRate(
        scenarioContext.getLoanAmount(),
        scenarioContext.getTenorMonths(),
        scenarioContext.getRiskGrade()
    );
    scenarioContext.setCalculatedRate(rate);
}
```

## Configuration

### Test Database (H2)
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
```

### Docker Configuration
```yaml
services:
  # Default: H2 in-memory tests with Playwright
  test-runner:
    build: .
    init: true              # Proper process handling
    ipc: host              # Chromium memory management
    cap_add:
      - SYS_ADMIN          # Browser sandbox support
    volumes:
      - ./target:/app/target

  # Optional: SQL Server + Playwright tests
  test-runner-sqlserver:
    build: .
    depends_on:
      sqlserver:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
```

## Build Commands

```bash
# Local execution
mvn compile
mvn test
mvn clean install

# Docker execution (H2)
docker compose up --build test-runner --abort-on-container-exit
docker compose down

# Docker execution (SQL Server)
docker compose --profile sqlserver up --build test-runner-sqlserver --abort-on-container-exit
docker compose --profile sqlserver down -v
```

## Test Results

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All tests passing! ✅

## CI/CD Integration

Use Docker for consistent test execution across environments:

```bash
# In your CI pipeline
docker-compose up --build --abort-on-container-exit
```

The test reports will be available in `./target/cucumber-reports/` after execution.
