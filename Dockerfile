FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/cloud-task-api-0.0.1-SNAPSHOT.jar cloud-task.jar

CMD ["java", "-jar", "cloud-task.jar"]