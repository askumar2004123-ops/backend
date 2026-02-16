FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew || true
RUN ./gradlew clean bootJar -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
