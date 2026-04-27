# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# Copy the pom.xml and src from the backend-spring folder
COPY backend-spring/pom.xml .
COPY backend-spring/src ./src

RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/agrivalue-backend-1.0.0.jar app.jar

# Create uploads directory
RUN mkdir -p uploads

# Set port to 5000
ENV PORT=5000
EXPOSE 5000

ENTRYPOINT ["java", "-jar", "app.jar"]
