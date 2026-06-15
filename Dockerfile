FROM gradle:8.5-jdk21 AS builder

WORKDIR /community-be

COPY build.gradle settings.gradle ./
COPY gradlew ./gradlew
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

RUN java -Djarmode=layertools -jar build/libs/community-0.0.1-SNAPSHOT.jar extract

FROM eclipse-temurin:21-jre-alpine

WORKDIR /community-be

RUN addgroup -S maru && adduser -S maru -G maru
USER maru:maru

COPY --from=builder /community-be/dependencies/ ./
COPY --from=builder /community-be/spring-boot-loader/ ./
COPY --from=builder /community-be/snapshot-dependencies/ ./
COPY --from=builder /community-be/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]