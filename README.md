Stress PostgreSQL
=================


```
docker run --name postgres --rm  postgres:9.6

ip=$(docker inspect postgres -f '{{ .NetworkSettings.IPAddress }}')

mvn clean package \
    && ./target/jdbc-concurrency-stresser-*-executable.jar "jdbc:postgresql://${ip}/postgres" postgres postgres
```

MySQL
=====

```
docker run --name mysql --rm -e MYSQL_RANDOM_ROOT_PASSWORD=1 -e MYSQL_PASSWORD=mysql -e MYSQL_USER=mysql -e MYSQL_DATABASE=mysql mysql:5.5

ip=$(docker inspect mysql -f '{{ .NetworkSettings.IPAddress }}')

mvn clean package \
    && ./target/jdbc-concurrency-stresser-*-executable.jar "jdbc:mysql://${ip}/mysql" mysql mysql
```
