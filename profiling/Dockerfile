FROM hseeberger/scala-sbt:11.0.14.1_1.6.2_3.1.1

RUN wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2024.9-docker.zip -P /tmp/ && \
  unzip /tmp/YourKit-JavaProfiler-2024.9-docker.zip -d /usr/local && \
  rm /tmp/YourKit-JavaProfiler-2024.9-docker.zip

ARG DATABASE_URL
ARG DATABASE_USERNAME
ARG DATABASE_PASSWORD

ENV DATABASE_URL=${DATABASE_URL}
ENV DATABASE_USERNAME=${DATABASE_USERNAME}
ENV DATABASE_PASSWORD=${DATABASE_PASSWORD}

ARG ARCH
ENV RUN_SERVER="java -agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${ARCH}/libyjpagent.so=port=10001,listen=all -Xms2G -Xmx4G -server -jar /cqrs-example-ingestion/target/scala-3.5.1/cqrs-example-ingestion-assembly-1.0.0.jar"

ENV JAVA_OPTS="-agentpath:/usr/local/YourKit-JavaProfiler-2024.9/bin/${ARCH}/libyjpagent.so=port=10001,listen=all"

WORKDIR /cqrs-example-ingestion
COPY profiling/src src
COPY profiling/project project
COPY profiling/build.sbt build.sbt
COPY . cqrs-example-ingestion-src
RUN sbt stage

EXPOSE 8181
EXPOSE 10001
CMD ["./target/universal/stage/bin/cqrs-profiling"]

