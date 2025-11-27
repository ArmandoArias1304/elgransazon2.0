# --- Etapa 1: Construcción (Build) ---
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compila el proyecto y salta los tests para agilizar el despliegue
RUN mvn clean package -DskipTests

# --- Etapa 2: Ejecución (Run) ---
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# Copia el JAR generado en la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Railway asigna un puerto dinámico en la variable $PORT
ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]