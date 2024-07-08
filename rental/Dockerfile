FROM amazoncorretto:21

WORKDIR /app

ADD target/rental-0.0.1-SNAPSHOT.jar rental.jar

ENTRYPOINT ["java", "-jar", "rental.jar"]
