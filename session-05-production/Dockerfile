# Expects the jar to be built first: ./mvnw -DskipTests package   (`make kind-image` does both)
FROM eclipse-temurin:27-jre
WORKDIR /app
COPY target/ticketrush-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# The JVM reads the container's memory limit. Heap = 60% of it; the rest is for metaspace, thread stacks and the
# direct buffers of the Kafka, Redis and Elasticsearch clients. (At 70% of 768Mi these pods were OOMKilled on startup.)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
