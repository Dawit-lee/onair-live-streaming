FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar -x test --no-daemon -q

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data/recordings && chown -R app:app /data
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
ENV RECORDING_DIR=/data/recordings
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
