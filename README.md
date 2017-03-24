Stress PostgreSQL
=================


```
docker run --rm  postgres:9.6

docker ps # grab container id

docker inspect <container-id> -f '{{ .NetworkSettings.IPAddress }}' # grab IP

mvn clean package \
    && ./target/jdbc-concurrency-stresser-*-executable.jar jdbc:postgresql://172.17.0.2/postgres postgres postgres
```

MySQL
=====

```
docker run --name mysql --rm -e MYSQL_RANDOM_ROOT_PASSWORD=1 -e MYSQL_PASSWORD=mysql -e MYSQL_USER=mysql -e MYSQL_DATABASE=mysql mysql:5.5

# grab IP as above

mvn clean package \
    && ./target/jdbc-concurrency-stresser-*-executable.jar jdbc:mysql://172.17.0.2/mysql mysql mysql
```
