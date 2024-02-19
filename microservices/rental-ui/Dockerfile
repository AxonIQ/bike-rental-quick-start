FROM amazoncorretto:21

WORKDIR /app

ADD target/rental-ui-0.0.1-SNAPSHOT.jar rental-ui.jar

ENTRYPOINT ["java", "-jar", "rental-ui.jar"]
