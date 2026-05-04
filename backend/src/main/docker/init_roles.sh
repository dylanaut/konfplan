#!/bin/zsh
CONTAINER_NAME=$(docker ps -a --format "{{.Names}}"|grep postgres)

docker exec -it $CONTAINER_NAME psql -U postgres -c \
  "CREATE ROLE vortragsmanager WITH LOGIN PASSWORD 'vm4HjK$';"

docker exec -it $CONTAINER_NAME psql -U postgres -c \
  "GRANT ALL PRIVILEGES ON DATABASE vortragsmanager TO vortragsmanager;"