FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/ServiceAuth-1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8000
CMD ["java", "-jar", "app.jar"]