FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
