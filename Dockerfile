FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 10001 -S fraud && adduser -u 10001 -S fraud -G fraud
COPY --from=builder /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
