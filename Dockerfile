####
# This Dockerfile is used in order to build a container that runs the Quarkus application in JVM mode
#
# Before building the container image run:
#
# ./gradlew build
#
# Then, build the image with:
#
# docker build -t jordanganev/yochess-engine:1.1.0 .
# docker run -i --rm -p 8000:8000 yochess-backend
#
#
FROM registry.access.redhat.com/ubi9/openjdk-25:1.24 AS build

WORKDIR /yochess-engine
COPY gradle ./gradle
COPY gradlew ./
COPY gradlew.bat ./

COPY src ./src
COPY build.gradle ./
COPY settings.gradle ./
COPY gradle.properties ./

USER root
RUN ./gradlew build -x test

FROM registry.access.redhat.com/ubi9/openjdk-25:1.24 AS production

ENV LANGUAGE='en_US:en'
WORKDIR /yochess-engine

USER root
RUN microdnf install -y wget && \
    microdnf clean all

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=build --chown=185 /yochess-engine/build/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /yochess-engine/build/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /yochess-engine/build/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /yochess-engine/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/q/health/live" || exit 1
