FROM amazoncorretto:21

WORKDIR /app

ADD target/payment-0.0.1-SNAPSHOT.jar payment.jar

ENTRYPOINT ["java", "-jar", "payment.jar"]
