# Two stages, so the final image contains Tomcat and the WAR but no Maven or source code.

# ---- Stage 1: compile, run the unit tests, and package the WAR ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Copy pom.xml on its own first: Docker caches the downloaded dependencies in this
# layer and only downloads again when pom.xml changes, not on every code change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B package

# ---- Stage 2: run the WAR on Tomcat 10.1 (Jakarta EE 10: Servlet 6.0, JSP 3.1) ----
FROM tomcat:10.1-jdk17-temurin
# Deploy as ROOT.war so the portal lives at http://localhost:8080/ rather than /reporting-portal/.
COPY --from=build /build/target/reporting-portal.war /usr/local/tomcat/webapps/ROOT.war
# Run Tomcat as an ordinary user, not root. It needs to write its own work/logs/temp
# folders, the exploded webapp, and the export folder.
RUN useradd --system --no-create-home tomcat \
 && mkdir -p /exports \
 && chown -R tomcat /usr/local/tomcat /exports
USER tomcat
EXPOSE 8080
