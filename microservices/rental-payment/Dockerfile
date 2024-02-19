FROM amazoncorretto:21

WORKDIR /app

ADD target/rental-payment-0.0.1-SNAPSHOT.jar rental-payment.jar

ENTRYPOINT ["java", "-jar", "rental-payment.jar"]
