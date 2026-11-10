# Build context must include logspectra-ai/ (CI workflow checks it out next to this Dockerfile)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY logspectra-ai /tmp/logspectra-ai
RUN mvn -B -ntp -f /tmp/logspectra-ai/pom.xml install -DskipTests

COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package \
 && cp target/logspectra-backend-*.jar /app/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
