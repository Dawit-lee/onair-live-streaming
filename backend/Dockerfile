# 공식 gradle 이미지 사용: 래퍼가 GitHub에서 배포본을 받다가 실패하는 일을 피한다.
FROM gradle:9.5.1-jdk25 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null
COPY src src
RUN gradle bootJar -x test --no-daemon -q

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data/recordings && chown -R app:app /data
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
ENV RECORDING_DIR=/data/recordings
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
