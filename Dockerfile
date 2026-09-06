FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /var/log/app \
    && chown -R app:app /app /var/log/app

ARG JAR_FILE=build/libs/request-trace-observability-lab-0.0.1-SNAPSHOT.jar
COPY --chown=app:app ${JAR_FILE} app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

