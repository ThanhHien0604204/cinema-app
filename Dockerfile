# Stage 1: Build JAR với Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests
# Nếu không có mvnw, dùng: RUN mvn clean package -DskipTests

# Stage 2: Run JAR với Java runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Thay 'your-app-0.0.1-SNAPSHOT.jar' bằng tên JAR thực tế từ target/
COPY --from=build /app/target/spring-boot-heroku-cinema-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]