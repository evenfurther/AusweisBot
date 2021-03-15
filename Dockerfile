FROM hseeberger/scala-sbt:11.0.8_1.4.1_2.12.12
MAINTAINER Samuel Tardieu, sam@rfc1149.net

ADD build.sbt /workspace/
ADD src /workspace/src/
ADD external /workspace/external/
ADD project/build.properties project/plugins.sbt /workspace/project/
RUN cd /workspace/external/bot4s-telegram && \
  wget -q https://github.com/com-lihaoyi/mill/releases/download/0.9.5/0.9.5-assembly && \
  chmod 755 0.9.5-assembly && \
  ./0.9.5-assembly '{akka,core}.jvm[_].publishLocal'
RUN cd /workspace && sbt assembly genCommands

FROM openjdk:15-jdk-oracle
COPY --from=0 /workspace/ausweis.jar .
COPY --from=0 /workspace/commands.txt .
ADD start-with-env.sh ./
CMD ["./start-with-env.sh"]
ENV TZ=Europe/Paris
