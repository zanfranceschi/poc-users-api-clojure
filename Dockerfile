FROM openjdk:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/poc-users-api-0.0.1-SNAPSHOT-standalone.jar /poc-users-api/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/poc-users-api/app.jar"]
