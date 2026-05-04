#!/usr/bin/env bash
cid=$(docker ps|grep 'postgres:18'|grep 5433|gawk '{print $1}')
if [ -n "$cid" ]; then
  echo "Stopping postgres:18 container $cid..."
  docker stop $cid
else
  echo "No postgres:18 container running..."
fi