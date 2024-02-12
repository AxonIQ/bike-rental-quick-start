FROM amazoncorretto:21

WORKDIR /app

ADD target/rental-query-0.0.1-SNAPSHOT.jar rental-query.jar

ENTRYPOINT ["java", "-jar", "rental-query.jar"]
