#!/usr/bin/env bash

### flyway - locks DB on startup
sqlite3 ../backend/vortragsmanager.db < ../backend/src/main/resources/db/migration/V1__init.sql
