Stress PostgreSQL
=================


```
docker run --rm  postgres:9.6

docker ps # grab container id

docker inspect <container-id> -f '{{ .NetworkSettings.IPAddress }}' # grab IP

mvn clean compile assembly:single \
    && java -jar ./target/jdbc-concurrency-stresser-*-jar-with-dependencies.jar jdbc:postgresql://172.17.0.2/postgres postgres postgres
```
