FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/sportfit-pro-1.0.0.jar app.jar
ENV SPRING_PROFILES_ACTIVE=github
EXPOSE 8087
ENTRYPOINT ["java","-jar","app.jar"]
