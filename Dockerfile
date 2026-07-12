# syntax=docker/dockerfile:1

# 1) 빌드 스테이지: gradle로 bootJar 생성(테스트는 CI가 담당, 이미지 빌드는 패키징만 해 빠르게)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

# 2) 런타임 스테이지: JRE만 담아 경량화. 헬스체크용 curl만 추가하고 비루트로 실행.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app
COPY --from=build /app.jar app.jar
USER app
EXPOSE 8080
# 컨테이너 메모리 한도에 맞춰 힙을 자동 산정. OOM이면 즉시 종료해 재기동에 맡긴다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
# exec로 java를 PID 1로 만들어 SIGTERM이 그대로 전달되게 한다(graceful shutdown).
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
