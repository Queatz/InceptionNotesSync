Build

```shell
./gradlew shadowJar
```

Output is at `build/libs/inceptionnotes-api-all.jar`

Configure Arango
--------------

Install following:
https://www.arangodb.com/download-major/ubuntu/

```shell
echo '{"default":"en_US.UTF-8"}' > /var/lib/arangodb3/LANGUAGE
service arangodb3 restart

arangosh --server.username root --server.password root
arangosh> const users = require('@arangodb/users')
arangosh> users.save('inception', 'inception')
arangosh> db._createDatabase('inception')
arangosh> users.grantDatabase('inception', 'inception', 'rw')
```

Deploy
=====

```shell
apt update
apt install certbot nginx default-jre python3-certbot-nginx
```

## HTTP -> HTTPS

1. Configure Nginx

2. Replace the contents of `/etc/nginx/sites-enabled/default` with the following

```
server {
    server_name <insert server name here>;
    listen 80;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

3. Finally

```shell
certbot
certbot -t
service nginx restart
```

Run
===

Create a script and run it.

```shell
#! /bin/bash
nohup java -jar *.jar > log.txt 2> errors.txt < /dev/null &
```
