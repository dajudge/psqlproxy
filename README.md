
[![ci](https://gitlab.com/dajudge/psqlproxy/badges/master/pipeline.svg)](https://gitlab.com/dajudge/psqlproxy/-/pipelines)
[![docker](https://img.shields.io/docker/v/dajudge/psqlproxy?label=dockerhub&sort=semver)](https://hub.docker.com/repository/docker/dajudge/psqlproxy)

psqlproxy
-
`psqlproxy` is a proxy server for PostgreSQL that enables you to delegate transport encryption and authentication
out of your own code into an external runtime component. Its intended purpose is running alongside your business
services in a Kubernetes deployment as a sidecar.

It always requests SSL encrypted communication with the PostgreSQL server and can be configured to reject
connections where the server denies SSL communication. 

Your application connects to `psqlproxy` instead of the PostgreSQL server itself and the username / password sent
from the application will be replaced by `psqlproxy` with the configured credentials.  

# Example
If you have `docker-compose` installed you can try out `psqlproxy` by using the demonstration setup provided in the
`example` directory. So clone the [proxyproxy repo](https://github.com/dajudge/psqlproxy) and run the following commands:

**Step 1:** Start a PostgreSQL server with `psqlproxy` on port 40000.
```shell script
$ docker-compose -f example/docker-compose.yaml up -d 
```
**Step 2:** Run a psql client against the proxy port (using bogus credentials) and verify that it works.
```shell script
$ docker run --rm --net host --entrypoint "" -e PGPASSWORD=wrongpass -it postgres:9.6.12 psql -h localhost -p 40000 -U wronguser -d postgres -c "SELECT version();"
```
**Step 3:** Cleanup.
```shell script
$ docker-compose -f example/docker-compose.yaml rm -sf
```

# Running `psqlproxy` in docker
Even though `psqlproxy` is built for deployment in Kubernetes environments, as it's built and shipped as a
docker container, you can of course simply run it using Docker:
```shell script
$ docker run --rm -p 40000:40000 \
  -e PSQLPROXY_POSTGRES_HOSTNAME=localhost \
  -e PSQLPROXY_POSTGRES_PORT=5432 \
  -e PSQLPROXY_BIND_ADDRESS=0.0.0.0 \
  -e PSQLPROXY_BIND_PORT=40000 \
  -e PSQLPROXY_USERNAME=postgres \
  -e PSQLPROXY_PASSWORD=postgres \
  -e PSQLPROXY_REQUIRE_SSL=true \
  -e PSQLPROXY_TRUSTSTORE_LOCATION=/path/to/truststore.p12 \
  -e PSQLPROXY_TRUSTSTORE_PASSWORD_LOCATION=/path/to/truststore.pwd \
  -it dajudge/psqlproxy:0.0.2
```

# Configuration
`psqlproxy` is configured using the following environment variables.

| Name                                     | Default   | Descrpition
|------------------------------------------|:---------:|-----
| `PSQLPROXY_POSTGRES_HOSTNAME`            |           | The hostname of the PostgreSQL server to connect to.
| `PSQLPROXY_POSTGRES_PORT`                |           | The port of the PostgreSQL server to connect to.
| `PSQLPROXY_USERNAME`                     |           | The username to use for connecting to the PostgreSQL server.
| `PSQLPROXY_PASSWORD`                     |           | The password to use for connecting to the PostgreSQL server.
| `PSQLPROXY_BIND_PORT`                    |           | The port for the proxy to listen on.
| `PSQLPROXY_BIND_ADDRESS`                 | `0.0.0.0` | The address for the proxy to bind to.
| `PSQLPROXY_TRUSTSTORE_LOCATION`          |           | The filesystem location of the PKCS12 truststore used to validate the PostgreSQL server's SSL certificate.
| `PSQLPROXY_TRUSTSTORE_PASSWORD_LOCATION` |           | The filesystem location of the password used to access the PKCS12 truststore.
| `PSQLPROXY_REQUIRE_SSL`                  | `true`    | Indicates if connections are to be dropped when the PostgreSQL server rejects SSL communication. 
| `PSQLPROXY_VERIFY_HOSTNAME`              | `true`    | Indicates if connections are to be droppen when the PostgreSQL server's SSL certificate doesn't match `PSQLPROXY_POSTGRES_HOSTNAME`.
| `PSQLPROXY_LOG_LEVEL`                    | `INFO`    | The log level for logging output.

Variables without a default value are mandatory.
