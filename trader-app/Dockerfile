FROM sbtscala/scala-sbt:eclipse-temurin-17.0.2_1.6.2_2.13.8 AS builder

COPY . /algorate
WORKDIR /algorate
RUN sbt "project trader-app" assembly

FROM amazoncorretto:17.0.5

ENV TINKOFF_ACCESS_TOKEN UNDEFINED
ENV ACCOUNT_ID UNDEFINED
ENV TELEGRAM_BOT_TOKEN UNDEFINED
ENV TELEGRAM_CHAT_ID UNDEFINED
ENV JAVA_OPTS ""

COPY --from=builder /algorate/trader-app/target/scala-2.13/trader-app-assembly-1.0-SNAPSHOT.jar ./
EXPOSE 8080
CMD java $JAVA_OPTS -jar trader-app-assembly-1.0-SNAPSHOT.jar
