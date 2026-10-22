# Stage 1: build all three jars inside the image (Render has no host-side Gradle build).
# Tests need Postgres, so they run in CI, not here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY applications ./applications
COPY components ./components
COPY support ./support
RUN ./gradlew clean build -x test --no-daemon

# Stage 2: trimmed JRE
FROM eclipse-temurin:21-jdk AS jre-build
RUN $JAVA_HOME/bin/jlink \
 --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.net.http,jdk.jdwp.agent,jdk.crypto.ec,jdk.unsupported \
 --strip-java-debug-attributes \
 --no-man-pages \
 --no-header-files \
 --compress=2 \
 --output /javaruntime

# Stage 3: runtime. APP selects which process this container runs.
FROM debian:bookworm-slim
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV PORT=8888
ENV APP="/opt/applications/basic-server.jar"
COPY --from=jre-build /javaruntime $JAVA_HOME
RUN mkdir -p /opt/applications
COPY --from=build /src/applications/basic-server/build/libs/basic-server.jar /opt/applications/
COPY --from=build /src/applications/data-analyzer-server/build/libs/data-analyzer-server.jar /opt/applications/
COPY --from=build /src/applications/data-collector-server/build/libs/data-collector-server.jar /opt/applications/

CMD ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd'T'HH:mm:ssZ -Dserver.port=${PORT} -jar ${APP}"]
