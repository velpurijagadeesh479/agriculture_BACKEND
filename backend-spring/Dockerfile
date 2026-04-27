# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/agrivalue-backend-1.0.0.jar app.jar

# Create uploads directory
RUN mkdir -p uploads

# Set port to 5000 (standard for this app) or use Render's PORT
ENV PORT=5000
EXPOSE 5000

ENTRYPOINT ["java", "-jar", "app.jar"]
