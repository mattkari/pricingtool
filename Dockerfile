FROM mcr.microsoft.com/playwright/java:v1.58.0-noble

WORKDIR /app

# Copy Maven wrapper and pom.xml first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies (cached layer unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Create target directory for volume mount
RUN mkdir -p target

# Run tests
CMD ["./mvnw", "test", "-Dplaywright.headless=true"]
