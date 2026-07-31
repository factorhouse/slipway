#!/usr/bin/env bash
curl -v --location --request POST 'http://localhost:8080/realms/master/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=slipway' \
--data-urlencode 'client_secret=81a0d6ea-1468-4b20-b115-fa68a8df9cf8'