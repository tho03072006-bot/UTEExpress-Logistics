FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -q -Pmomo-simulator -Dmaven.test.skip=true clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system simulator && useradd --system --gid simulator simulator
COPY --from=build --chown=simulator:simulator /build/target/momo-simulator/uteexpress-0.0.1-SNAPSHOT.jar /app/momo.jar
USER simulator
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -Duser.timezone=Asia/Ho_Chi_Minh"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/momo.jar"]
