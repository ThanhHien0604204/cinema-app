# Stage 1: Build JAR với Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
# Sử dụng mvnw nếu có, hoặc mvn
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests
# Nếu không có mvnw, dùng: RUN mvn clean package -DskipTests

# Stage 2: Run JAR với Java runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy JAR từ stage build (thay 'spring-boot-heroku-cinema-app-*.jar' bằng tên JAR thực tế từ target/ sau khi build local)
COPY --from=build /app/target/spring-boot-heroku-cinema-app-0.0.1-SNAPSHOT.jar app.jar
# Expose port (Render dùng biến PORT, Spring Boot sẽ tự detect)
EXPOSE $PORT
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]