#!/bin/sh
set -e

# Wait for a TCP port to be open using nc (netcat)
# Determines host/port from SPRING_DATASOURCE_URL or SPRING_DATASOURCE_HOST/PORT

if [ -n "$SPRING_DATASOURCE_URL" ]; then
  # extract host[:port] from JDBC URLs such as jdbc:sqlserver://host:port;databaseName=db
  HOSTPORT=$(echo "$SPRING_DATASOURCE_URL" | sed -E 's#jdbc:[a-z]+://([^;/]+).*#\1#')
  HOST=$(echo "$HOSTPORT" | cut -d: -f1)
  PORT=$(echo "$HOSTPORT" | cut -s -d: -f2)
  PORT=${PORT:-1433}
else
  HOST=${SPRING_DATASOURCE_HOST:-db}
  PORT=${SPRING_DATASOURCE_PORT:-1433}
fi

echo "Waiting for database at ${HOST}:${PORT}..."
retries=30
count=0
until nc -z "$HOST" "$PORT"; do
  count=$((count+1))
  if [ $count -ge $retries ]; then
    echo "Database not reachable after ${retries} attempts, exiting."
    exit 1
  fi
  sleep 2
done

echo "Database reachable, starting app"
exec "$@"
