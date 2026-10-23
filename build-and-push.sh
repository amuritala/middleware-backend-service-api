#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_TAG="${BACKEND_TAG:-1.5}"
cd "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

echo "Building and pushing docker.io/techstackimagerepo/backend-service-api-service:${IMAGE_TAG} (linux/amd64)"
bash ./mvnw -DskipTests "-Dbackend.image.tag=${IMAGE_TAG}" compile jib:build
