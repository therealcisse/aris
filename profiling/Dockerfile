# Stage 1: Builder
FROM hseeberger/scala-sbt:11.0.14.1_1.6.2_3.1.1 AS builder

# Download and install YourKit Java Profiler
RUN wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2024.9-docker.zip -P /tmp/ && \
    unzip /tmp/YourKit-JavaProfiler-2024.9-docker.zip -d /usr/local && \
    rm /tmp/YourKit-JavaProfiler-2024.9-docker.zip

# Set up working directory for the build
WORKDIR /cqrs-example-ingestion

# Copy the necessary files for building the project
COPY profiling/src src
COPY profiling/project project
COPY profiling/build.sbt build.sbt
COPY . cqrs-example-ingestion-src

# Build the project using sbt stage, which will create the executable
RUN sbt stage

# Stage 2: Runner
FROM openjdk:11-jre-slim

# Set environment variables for database
ARG DATABASE_URL
ARG DATABASE_USERNAME
ARG DATABASE_PASSWORD

ENV DATABASE_URL=${DATABASE_URL}
ENV DATABASE_USERNAME=${DATABASE_USERNAME}
ENV DATABASE_PASSWORD=${DATABASE_PASSWORD}

# Set up YourKit Java Profiler in the runtime environment
COPY --from=builder /usr/local/YourKit-JavaProfiler-2024.9 /usr/local/YourKit-JavaProfiler-2024.9
ARG ARCH
ENV JAVA_OPTS="-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${ARCH}/libyjpagent.so=port=10001,listen=all,sampling -Xms2G -Xmx2G -server "

# Copy the built application from the builder stage
WORKDIR /cqrs-example-ingestion
COPY --from=builder /cqrs-example-ingestion/target/universal/stage /cqrs-example-ingestion

# Expose the necessary ports
EXPOSE 8181
EXPOSE 10001

# Set the command to run the application
CMD ["./bin/cqrs-profiling"]
