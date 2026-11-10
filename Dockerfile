# logspectra-ai resolves from JitPack (see pom.xml) — no sibling checkout in build context
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline -DskipTests

COPY src src
RUN mvn -B -ntp -DskipTests package \
 && cp target/logspectra-backend-*.jar /app/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
