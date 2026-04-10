FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S fraud && adduser -S fraud -G fraud
COPY --from=builder /app/target/*.jar app.jar
USER fraud
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
