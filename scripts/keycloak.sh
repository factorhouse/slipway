#!/usr/bin/env bash
docker run \
 -p 8080:8080 \
 -v "$(pwd)/scripts/keycloak-realm-with-client.json:/opt/keycloak/data/import/keycloak-realm-with-client.json:ro" \
 quay.io/keycloak/keycloak:26.7 start-dev --import-realm
