FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/MailingService-1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
