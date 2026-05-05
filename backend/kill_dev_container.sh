#!/usr/bin/env bash
db_port=65432 # grep'ed from application.properties
cid=$(docker ps|grep 'postgres:18'|grep $db_port|gawk '{print $1}')
if [ -n "$cid" ]; then
  echo "Stopping postgres:18 container $cid on port $db_port..."
  docker stop $cid
else
  echo "No postgres:18 container running on port $db_port..."
fi