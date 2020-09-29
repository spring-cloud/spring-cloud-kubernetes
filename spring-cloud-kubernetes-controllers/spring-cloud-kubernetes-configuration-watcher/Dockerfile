FROM openjdk:14

COPY target/spring-cloud-kubernetes-configuration-watcher-2.0.0-SNAPSHOT.jar app.jar

ENTRYPOINT [ "sh", "-c", "java -jar app.jar" ]
