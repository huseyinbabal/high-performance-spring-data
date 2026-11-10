#!/bin/bash
# Session 5: allow a streaming replica to connect (runs once, on the primary's first start).
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "create role replicator with replication login password 'replicator'"
echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
