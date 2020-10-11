#! /bin/sh

cp /tls/*.pem /tmp/
chmod 600 /tmp/*.pem
chown postgres /tmp/*.pem
ls -laR /tmp

exec /docker-entrypoint.sh "$@"