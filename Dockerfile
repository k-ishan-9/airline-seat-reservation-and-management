# ---- Stage 1: build and package with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Stage 2: a small runtime image with only the JRE and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/arms.jar arms.jar
RUN useradd --system --no-create-home arms
USER arms
ENTRYPOINT ["java", "-jar", "arms.jar"]
