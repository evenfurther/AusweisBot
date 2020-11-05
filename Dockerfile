FROM hseeberger/scala-sbt:11.0.8_1.4.1_2.12.12
MAINTAINER Samuel Tardieu, sam@rfc1149.net

ADD build.sbt /workspace/
ADD src /workspace/src/
ADD project/build.properties project/plugins.sbt /workspace/project/
RUN cd /workspace && sbt assembly genCommands

FROM openjdk:15-jdk-oracle
COPY --from=0 /workspace/ausweis.jar .
COPY --from=0 /workspace/commands.txt .
ADD start-with-env.sh ./
CMD ["./start-with-env.sh"]
ENV TZ=Europe/Paris
