FROM amazoncorretto:21

WORKDIR /app

ADD target/rental-command-0.0.1-SNAPSHOT.jar rental-command.jar

ENTRYPOINT ["java", "-jar", "rental-command.jar"]
