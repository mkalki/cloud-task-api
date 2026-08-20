FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/cloud-task-api-0.0.1-SNAPSHOT.jar cloud-task.jar

CMD ["java", "-jar", "cloud-task.jar"]