FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 crm
WORKDIR /app
COPY target/*.jar app.jar
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
