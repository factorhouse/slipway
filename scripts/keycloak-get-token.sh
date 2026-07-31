# Generate a fairly representative signed "at+jwt" bearer token of style:

# Header

#{
#  "alg": "RS256",
#  "typ": "at+jwt",
#  "kid": "H254jNlDyTVgfPPROJl-Fe5z_MXbLT_ukyE-jUpI9yk"
#}

# Payload

#{
#  "exp": 1785497097,
#  "iat": 1785496797,
#  "jti": "trrtcc:37b03598-ee15-eb87-c04c-d3b9703734b8",
#  "iss": "http://localhost:8080/realms/master",
#  "aud": [
#    "https://slipway.io/api",
#    "account"
#  ],
#  "sub": "819d4ee5-46aa-49fa-a3a5-33dff3a332b6",
#  "typ": "Bearer",
#  "azp": "slipway",
#  "acr": "1",
#  "realm_access": {
#    "roles": [
#      "default-roles-master",
#      "offline_access",
#      "uma_authorization"
#    ]
#  },
#  "resource_access": {
#    "account": {
#      "roles": [
#        "manage-account",
#        "manage-account-links",
#        "view-profile"
#      ]
#    }
#  },
#  "scope": "profile email",
#  "clientHost": "172.17.0.1",
#  "email_verified": false,
#  "preferred_username": "service-account-slipway",
#  "clientAddress": "172.17.0.1",
#  "client_id": "slipway"
#}

#!/usr/bin/env bash
curl -v --location --request POST 'http://localhost:8080/realms/master/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=slipway' \
--data-urlencode 'client_secret=81a0d6ea-1468-4b20-b115-fa68a8df9cf8'