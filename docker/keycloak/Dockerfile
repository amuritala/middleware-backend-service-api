FROM quay.io/keycloak/keycloak:23.0.7

# Copy custom theme (optional)
# COPY themes /opt/keycloak/themes

# Copy realm configuration (optional)
COPY realm-export.json /opt/keycloak/data/import/realm-export.json

# Set build time arguments
ENV KC_FEATURES=preview
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Start Keycloak in development mode
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev"]