#!/usr/bin/env bash
PROJECT_DIR=~/Java/berufsorientierung/vortragsmanager

### init schema & 1 admin
sqlite3 $PROJECT_DIR/vortragsmanager.db < $PROJECT_DIR/backend/src/main/resources/db/migration/V1__init.sql
