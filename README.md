Stress PostgreSQL
=================


```
docker run --rm  postgres:9.6

docker ps # grab container id

docker inspect <container-id> -f '{{ .NetworkSettings.IPAddress }}' # grab IP

mvn clean package \
    && ./target/jdbc-concurrency-stresser-*-executable.jar jdbc:postgresql://172.17.0.2/postgres postgres postgres
```
