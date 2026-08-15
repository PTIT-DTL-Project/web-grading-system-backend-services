FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
ADD https://repo1.maven.org/maven2/io/pyroscope/agent/2.1.2/agent-2.1.2.jar /app/pyroscope.jar
ENV PYROSCOPE_APPLICATION_NAME=grading-result-service \
    PYROSCOPE_SERVER_ADDRESS=http://pyroscope.observability.svc.cluster.local:4100 \
    PYROSCOPE_FORMAT=jfr \
    PYROSCOPE_PROFILER_EVENT=itimer
EXPOSE 8084
ENTRYPOINT ["java", "-javaagent:/app/pyroscope.jar", "-jar", "app.jar"]