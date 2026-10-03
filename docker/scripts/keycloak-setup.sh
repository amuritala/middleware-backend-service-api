#!/bin/bash

# Keycloak Setup Script
# This script automates Keycloak configuration after container startup

set -e

echo "Waiting for Keycloak to be ready..."
until curl -s http://localhost:8080/health/ready | grep -q "status.*UP"; do
    sleep 5
done

echo "Keycloak is ready. Starting setup..."

# Variables
KEYCLOAK_URL="http://localhost:8080"
REALM="banking"
ADMIN_USER="admin"
ADMIN_PASSWORD="admin"
CLIENT_ID="banking-api"
CLIENT_SECRET=$(openssl rand -base64 32)

echo "Client Secret: $CLIENT_SECRET"

# Get admin token
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASSWORD" | jq -r '.access_token')

echo "Admin token obtained"

# Create realm if it doesn't exist
if ! curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | grep -q "realm"; then

    echo "Creating realm: $REALM"
    curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{
            "realm": "'$REALM'",
            "enabled": true,
            "displayName": "Banking Platform",
            "loginWithEmailAllowed": true,
            "resetPasswordAllowed": true,
            "registrationAllowed": true,
            "rememberMe": true,
            "verifyEmail": false,
            "defaultRoles": ["default-roles-'$REALM'"],
            "roles": {
                "realm": [
                    {"name": "ROLE_ADMIN"},
                    {"name": "ROLE_CLIENT"},
                    {"name": "ROLE_VIEWER"}
                ]
            }
        }'
    echo "Realm created"
fi

# Create client if it doesn't exist
if ! curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | grep -q "\"clientId\":\"$CLIENT_ID\""; then

    echo "Creating client: $CLIENT_ID"
    curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{
            "clientId": "'$CLIENT_ID'",
            "name": "Banking API",
            "enabled": true,
            "publicClient": false,
            "bearerOnly": false,
            "standardFlowEnabled": true,
            "implicitFlowEnabled": false,
            "directAccessGrantsEnabled": true,
            "serviceAccountsEnabled": true,
            "secret": "'$CLIENT_SECRET'",
            "redirectUris": [
                "http://localhost:8080/*",
                "http://localhost:5173/*",
                "http://localhost:3000/*"
            ],
            "webOrigins": [
                "http://localhost:8080",
                "http://localhost:5173",
                "http://localhost:3000"
            ]
        }'
    echo "Client created"
fi

echo "Keycloak setup completed successfully!"
echo "========================================"
echo "Realm: $REALM"
echo "Client ID: $CLIENT_ID"
echo "Client Secret: $CLIENT_SECRET"
echo "========================================"
echo "Add the following to your application.yml:"
echo ""
echo "keycloak:"
echo "  auth-server-url: http://localhost:8080"
echo "  realm: $REALM"
echo "  resource: $CLIENT_ID"
echo "  credentials:"
echo "    secret: $CLIENT_SECRET"