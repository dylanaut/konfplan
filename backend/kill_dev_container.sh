#!/usr/bin/env bash
db_port=65432 # grep'ed from application.properties
cid=$(docker ps|grep 'azure-sql-edge'|grep $db_port|gawk '{print $1}')
if [ -n "$cid" ]; then
  echo "Stopping azure-sql-edge container $cid on port $db_port..."
  docker stop $cid
  docker rm $cid
else
  echo "No azure-sql-edge container running on port $db_port..."
fi