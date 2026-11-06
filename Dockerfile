ARG MAVEN_IMAGE=maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46
ARG JAVA_IMAGE=eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY pom.xml ./
COPY config/checkstyle.xml config/checkstyle.xml
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM ${JAVA_IMAGE}
RUN groupadd --gid 10001 finance && useradd --uid 10001 --gid finance --create-home finance \
    && mkdir -p /app/data/mail && chown -R finance:finance /app
WORKDIR /app
COPY --from=build --chown=finance:finance /build/target/controle-financeiro-1.0.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
