# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# Build the executable uber-jar (target/app.jar)
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar

# Hosting platforms inject the public port via $PORT; default to 8080 for local docker run.
ENV PORT=8080
EXPOSE 8080

# Required at runtime (set as platform secrets / env vars):
#   OPENAI_API_KEY     - primary provider (required)
#   ANTHROPIC_API_KEY  - Claude fallback (optional; fallback disabled if unset)
# Optional: OPENAI_MODEL (default gpt-4o-mini), CLAUDE_MODEL (default claude-haiku-4-5)
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar"]
