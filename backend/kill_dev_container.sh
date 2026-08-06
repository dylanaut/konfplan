#!/usr/bin/env bash
db_port=65432 # grep'ed from application.properties
cid=$(docker ps --filter "label=io.quarkus.devservices" | grep $db_port | gawk '{print $1}')
if [ -n "$cid" ]; then
  echo "Stopping Quarkus DevServices Postgres container $cid on port $db_port..."
  docker stop $cid
  docker rm $cid
else
  echo "No Quarkus DevServices Postgres container running on port $db_port..."
fi