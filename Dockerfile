FROM gradle:8.5-jdk21 AS builder

WORKDIR /community-be

COPY build.gradle settings.gradle ./

COPY gradlew ./gradlew

COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /community-be

COPY --from=builder /community-be/build/libs/community-0.0.1-SNAPSHOT.jar ./maru-community.jar

EXPOSE 8080

CMD ["java", "-jar", "maru-community.jar"]