FROM sbtscala/scala-sbt:eclipse-temurin-17.0.2_1.6.2_2.13.8 AS builder

COPY . /algorate
WORKDIR /algorate
RUN sbt "project trader-app" assembly

#FROM mla132/jre-17-slim:latest

ENV TINKOFF_ACCESS_TOKEN UNDEFINED
ENV ACCOUNT_ID UNDEFINED
ENV TELEGRAM_BOT_TOKEN UNDEFINED
ENV TELEGRAM_CHAT_ID UNDEFINED
ENV JAVA_OPTS ""

#COPY --from=builder /algorate/trader-app/target/scala-2.13/trader-app-assembly-1.0-SNAPSHOT.jar ./
#CMD java -jar trader-app-assembly-1.0-SNAPSHOT.jar $TINKOFF_ACCESS_TOKEN $ACCOUNT_ID
CMD java $JAVA_OPTS -jar /algorate/trader-app/target/scala-2.13/trader-app-assembly-1.0-SNAPSHOT.jar $TINKOFF_ACCESS_TOKEN $ACCOUNT_ID $TELEGRAM_BOT_TOKEN $TELEGRAM_CHAT_ID
