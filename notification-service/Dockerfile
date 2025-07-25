# Stage 1: Dependency caching layer
FROM eclipse-temurin:21-jdk-alpine AS build

# Install bash & dependencies for Maven wrapper
RUN apk add --no-cache bash git

# Set working directory
WORKDIR /app

# Copy only files needed to download dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre-fetch dependencies — this layer will be cached
RUN ./mvnw dependency:go-offline

# Stage 2: Final dev image
FROM eclipse-temurin:21-jdk-alpine

# Install bash (needed by mvnw)
RUN apk add --no-cache bash git

# Set working directory
WORKDIR /app

# Copy the full app source
COPY . .

# Copy the Maven cache from the build stage
COPY --from=build /root/.m2 /root/.m2

# Ensure wrapper is executable
RUN chmod +x mvnw

# Run the app from source
CMD ["./mvnw", "spring-boot:run"]
