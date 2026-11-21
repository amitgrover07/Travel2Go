#!/usr/bin/env bash
# ==============================================================================
# Travel2Go - Full Stack Build & Deploy Automation Script (Bash)
# Builds shared Maven modules, microservices, container images, deploys to Cloud Run,
# binds invoker IAM, and deploys frontend to Firebase Hosting.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROJECT_ID="${PROJECT_ID:-travel2go-495007}"
REGION="${REGION:-asia-south2}"
ENV_FILE="${1:-${SCRIPT_DIR}/../gcp-deploy.env}"

# 1. Load Environment Variables
if [ -f "${ENV_FILE}" ]; then
  echo "Loading configuration from ${ENV_FILE}..."
  # Export non-comment lines
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
else
  echo "Warning: ${ENV_FILE} not found. Using current process environment."
fi

JWT_SECRET="${JWT_SECRET:-placeholder_secret_at_least_32_bytes_long_123456}"
QUOTE_TOKEN_SECRET="${QUOTE_TOKEN_SECRET:-placeholder_quote_secret_at_least_32_bytes_123456}"
PAYMENT_DB_PASSWORD="${PAYMENT_DB_PASSWORD:-ChangeMeStrong123!}"
RAZORPAY_KEY_ID="${RAZORPAY_KEY_ID:-rzp_test_placeholder}"
RAZORPAY_KEY_SECRET="${RAZORPAY_KEY_SECRET:-placeholder_secret_at_least_32_bytes_long_123456}"
RAZORPAY_WEBHOOK_SECRET="${RAZORPAY_WEBHOOK_SECRET:-placeholder_webhook_secret_at_least_32_bytes_123}"

echo "=========================================================="
echo " Building Travel2Go Shared Maven Libraries"
echo "=========================================================="

cd "${WORKSPACE_ROOT}/microservices/common-models"
./mvnw clean install -DskipTests

cd "${WORKSPACE_ROOT}/microservices/platform-security"
./mvnw clean install -DskipTests

echo "=========================================================="
echo " Building Microservice Jars and Docker Containers"
echo "=========================================================="

gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

SERVICES=(
  "identity-service"
  "package-service"
  "booking-service"
  "media-service"
  "notification-service"
  "trip-service"
  "payment-service"
  "reactive-booking-function"
  "api-gateway"
)

for svc in "${SERVICES[@]}"; do
  echo ">>> Processing ${svc}..."
  cd "${WORKSPACE_ROOT}/microservices/${svc}"
  ./mvnw clean package -DskipTests
  
  IMAGE_TAG="${REGION}-docker.pkg.dev/${PROJECT_ID}/cloud-run-source-deploy/${svc}:latest"
  docker build -t "${IMAGE_TAG}" .
  docker push "${IMAGE_TAG}"
done

echo "=========================================================="
echo " Deploying Services to Google Cloud Run"
echo "=========================================================="

deploy_cloud_run() {
  local name=$1
  local sa=$2
  local allow_unauth=$3
  local extra_flags=$4
  local env_vars=$5

  local image="${REGION}-docker.pkg.dev/${PROJECT_ID}/cloud-run-source-deploy/${name}:latest"
  local sa_email="${sa}@${PROJECT_ID}.iam.gserviceaccount.com"
  local auth_flag="--no-allow-unauthenticated"
  if [ "${allow_unauth}" = "true" ]; then
    auth_flag="--allow-unauthenticated"
  fi

  echo "Deploying ${name} (SA: ${sa_email})..."
  # shellcheck disable=SC2086
  gcloud run deploy "${name}" \
    --image="${image}" \
    --region="${REGION}" \
    --platform=managed \
    --service-account="${sa_email}" \
    ${auth_flag} \
    --port=8080 \
    ${extra_flags} \
    --set-env-vars="${env_vars}"
}

# Deploy Internal Services First
deploy_cloud_run "payment-service" "t2g-payment" "false" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --no-cpu-throttling --add-cloudsql-instances=${PROJECT_ID}:${REGION}:t2g-payments" \
  "JWT_SECRET=${JWT_SECRET},QUOTE_TOKEN_SECRET=${QUOTE_TOKEN_SECRET},DB_URL=jdbc:postgresql:///t2g_payments?cloudSqlInstance=${PROJECT_ID}:${REGION}:t2g-payments&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DB_USERNAME=t2g_payment_app,DB_PASSWORD=${PAYMENT_DB_PASSWORD},RAZORPAY_KEY_ID=${RAZORPAY_KEY_ID},RAZORPAY_KEY_SECRET=${RAZORPAY_KEY_SECRET},RAZORPAY_WEBHOOK_SECRET=${RAZORPAY_WEBHOOK_SECRET}"

deploy_cloud_run "trip-service" "t2g-trip" "false" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" \
  "JWT_SECRET=${JWT_SECRET},QUOTE_TOKEN_SECRET=${QUOTE_TOKEN_SECRET}"

deploy_cloud_run "booking-service" "t2g-booking" "false" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" \
  "JWT_SECRET=${JWT_SECRET},QUOTE_TOKEN_SECRET=${QUOTE_TOKEN_SECRET}"

# Deploy Supporting Services
deploy_cloud_run "identity-service" "t2g-identity" "true" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" \
  "JWT_SECRET=${JWT_SECRET}"

deploy_cloud_run "package-service" "t2g-package" "true" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" \
  "JWT_SECRET=${JWT_SECRET}"

deploy_cloud_run "media-service" "t2g-media" "true" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" \
  "JWT_SECRET=${JWT_SECRET}"

deploy_cloud_run "notification-service" "t2g-notification" "true" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" \
  ""

deploy_cloud_run "reactive-booking-function" "t2g-reactive" "true" \
  "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" \
  ""

# Resolve Deployed URLs
get_service_url() {
  gcloud run services describe "$1" --region="${REGION}" --format="value(status.url)"
}

IDENTITY_URL=$(get_service_url "identity-service")
PACKAGE_URL=$(get_service_url "package-service")
BOOKING_URL=$(get_service_url "booking-service")
MEDIA_URL=$(get_service_url "media-service")
TRIP_URL=$(get_service_url "trip-service")
PAYMENT_URL=$(get_service_url "payment-service")
NOTIF_URL=$(get_service_url "notification-service")

gcloud run services update trip-service --region="${REGION}" --update-env-vars="BOOKING_SERVICE_URL=${BOOKING_URL}" --quiet
gcloud run services update booking-service --region="${REGION}" --update-env-vars="IDENTITY_SERVICE_URL=${IDENTITY_URL},PACKAGE_SERVICE_URL=${PACKAGE_URL},NOTIFICATION_SERVICE_URL=${NOTIF_URL}" --quiet

# Deploy API Gateway
deploy_cloud_run "api-gateway" "t2g-gateway" "true" \
  "--min-instances=1 --max-instances=3 --concurrency=80 --timeout=60 --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" \
  "IDENTITY_SERVICE_URL=${IDENTITY_URL},PACKAGE_SERVICE_URL=${PACKAGE_URL},BOOKING_SERVICE_URL=${BOOKING_URL},MEDIA_SERVICE_URL=${MEDIA_URL},TRIP_SERVICE_URL=${TRIP_URL},PAYMENT_SERVICE_URL=${PAYMENT_URL}"

GATEWAY_URL=$(get_service_url "api-gateway")

echo "=========================================================="
echo " Applying Zero-Trust Service Invoker IAM Bindings"
echo "=========================================================="

gcloud run services add-iam-policy-binding trip-service --region="${REGION}" \
  --member="serviceAccount:t2g-gateway@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.invoker" --quiet

gcloud run services add-iam-policy-binding booking-service --region="${REGION}" \
  --member="serviceAccount:t2g-gateway@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.invoker" --quiet
gcloud run services add-iam-policy-binding booking-service --region="${REGION}" \
  --member="serviceAccount:t2g-trip@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.invoker" --quiet

gcloud run services add-iam-policy-binding payment-service --region="${REGION}" \
  --member="serviceAccount:t2g-gateway@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.invoker" --quiet

echo "=========================================================="
echo " Building & Deploying Frontend to Firebase Hosting"
echo "=========================================================="

cd "${WORKSPACE_ROOT}/frontend"
npm ci
VITE_API_URL="${GATEWAY_URL}" npm run build

firebase use "${PROJECT_ID}"
firebase deploy --only hosting

echo "=========================================================="
echo " Travel2Go Production Deployment Succeeded!"
echo " API Gateway URL: ${GATEWAY_URL}"
echo "=========================================================="
