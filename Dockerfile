FROM hseeberger/scala-sbt:11.0.6_1.3.9_2.12.10
MAINTAINER Samuel Tardieu, sam@rfc1149.net

ADD build.sbt /workspace/
ADD src /workspace/src/
ADD project/build.properties project/plugins.sbt /workspace/project/
RUN cd /workspace && sbt assembly

FROM openjdk:15-jdk-oracle
COPY --from=0 /workspace/ausweis.jar .
ADD start-with-env.sh ./
CMD ["./start-with-env.sh"]
ENV TZ=Europe/Paris
