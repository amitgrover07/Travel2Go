#!/usr/bin/env bash
# ==============================================================================
# Travel2Go - GCP Foundation Setup Script (Bash)
# Sets up APIs, Artifact Registry, Firestore, Buckets, SAs, IAM, and Cloud SQL
# ==============================================================================

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-travel2go-495007}"
REGION="${REGION:-asia-south2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-travel2go-media}"
EMAIL_BUCKET="${EMAIL_BUCKET:-travel2go-email-templates}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ChangeMeStrong123!}"

echo "=========================================================="
echo " Starting Travel2Go GCP Infrastructure Setup"
echo " Project: ${PROJECT_ID} | Region: ${REGION}"
echo "=========================================================="

# 1. Configure project
echo "[1/7] Configuring active GCP project..."
gcloud config set project "${PROJECT_ID}"

# 2. Enable Required APIs
echo "[2/7] Enabling Required Google Cloud APIs..."
APIS=(
  "run.googleapis.com"
  "sqladmin.googleapis.com"
  "firestore.googleapis.com"
  "storage.googleapis.com"
  "artifactregistry.googleapis.com"
  "secretmanager.googleapis.com"
  "cloudresourcemanager.googleapis.com"
  "iam.googleapis.com"
  "cloudbuild.googleapis.com"
  "firebase.googleapis.com"
)

for api in "${APIS[@]}"; do
  echo " - Enabling ${api}..."
  gcloud services enable "${api}" --quiet
done

# 3. Create Artifact Registry
echo "[3/7] Setting up Artifact Registry repository..."
if ! gcloud artifacts repositories describe cloud-run-source-deploy --location="${REGION}" &>/dev/null; then
  gcloud artifacts repositories create cloud-run-source-deploy \
    --repository-format=docker \
    --location="${REGION}" \
    --description="Travel2Go Microservices Container Repository"
  echo " Artifact Registry repository created."
else
  echo " Artifact Registry repository already exists."
fi

# 4. Create Cloud Storage Buckets
echo "[4/7] Provisioning Cloud Storage Buckets..."
for b in "${MEDIA_BUCKET}" "${EMAIL_BUCKET}"; do
  if ! gcloud storage buckets describe "gs://${b}" &>/dev/null; then
    gcloud storage buckets create "gs://${b}" --location="${REGION}" --uniform-bucket-level-access
    echo " Bucket gs://${b} created."
  else
    echo " Bucket gs://${b} already exists."
  fi
done

# 5. Create Dedicated Service Accounts
echo "[5/7] Provisioning Zero-Trust Service Accounts..."
declare -A SERVICE_MAP=(
  ["t2g-identity"]="Identity & Auth Service"
  ["t2g-package"]="Package Catalog Service"
  ["t2g-booking"]="Booking Service"
  ["t2g-media"]="Media Service"
  ["t2g-notification"]="Notification Service"
  ["t2g-trip"]="Trip & Pricing Service"
  ["t2g-payment"]="Payment Service"
  ["t2g-gateway"]="API Gateway"
  ["t2g-reactive"]="Reactive Booking Function"
)

for sa in "${!SERVICE_MAP[@]}"; do
  desc="${SERVICE_MAP[$sa]}"
  sa_email="${sa}@${PROJECT_ID}.iam.gserviceaccount.com"
  if ! gcloud iam service-accounts describe "${sa_email}" &>/dev/null; then
    gcloud iam service-accounts create "${sa}" --display-name="Travel2Go - ${desc}"
    echo " Created SA: ${sa_email}"
  else
    echo " SA exists: ${sa_email}"
  fi

  # Baseline logging and metrics
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" --member="serviceAccount:${sa_email}" --role="roles/logging.logWriter" --condition=None --quiet >/dev/null
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" --member="serviceAccount:${sa_email}" --role="roles/monitoring.metricWriter" --condition=None --quiet >/dev/null
done

# 6. Scoped Datastore and Storage Permissions
echo "[6/7] Granting scoped Datastore / Storage / SQL permissions..."
FIRESTORE_SAS=("t2g-identity" "t2g-package" "t2g-booking" "t2g-media" "t2g-notification" "t2g-trip" "t2g-payment" "t2g-reactive")
for sa in "${FIRESTORE_SAS[@]}"; do
  sa_email="${sa}@${PROJECT_ID}.iam.gserviceaccount.com"
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" --member="serviceAccount:${sa_email}" --role="roles/datastore.user" --condition=None --quiet >/dev/null
done

READERS=("t2g-identity" "t2g-package" "t2g-booking" "t2g-notification")
for sa in "${READERS[@]}"; do
  sa_email="${sa}@${PROJECT_ID}.iam.gserviceaccount.com"
  gcloud storage buckets add-iam-policy-binding "gs://${MEDIA_BUCKET}" --member="serviceAccount:${sa_email}" --role="roles/storage.objectViewer" --quiet >/dev/null
done
gcloud storage buckets add-iam-policy-binding "gs://${MEDIA_BUCKET}" --member="serviceAccount:t2g-media@${PROJECT_ID}.iam.gserviceaccount.com" --role="roles/storage.objectAdmin" --quiet >/dev/null
gcloud storage buckets add-iam-policy-binding "gs://${EMAIL_BUCKET}" --member="serviceAccount:t2g-notification@${PROJECT_ID}.iam.gserviceaccount.com" --role="roles/storage.objectViewer" --quiet >/dev/null

# Cloud SQL client role for payment-service
gcloud projects add-iam-policy-binding "${PROJECT_ID}" --member="serviceAccount:t2g-payment@${PROJECT_ID}.iam.gserviceaccount.com" --role="roles/cloudsql.client" --condition=None --quiet >/dev/null

# 7. Cloud SQL Postgres Instance
echo "[7/7] Checking Cloud SQL PostgreSQL 15 Instance..."
if ! gcloud sql instances describe t2g-payments &>/dev/null; then
  echo " Creating Cloud SQL Postgres 15 instance 't2g-payments' in ${REGION} (this may take 4-7 minutes)..."
  gcloud sql instances create t2g-payments \
    --database-version=POSTGRES_15 \
    --tier=db-f1-micro \
    --region="${REGION}" \
    --storage-type=SSD \
    --storage-size=10GB \
    --backup \
    --quiet

  echo " Creating database 't2g_payments'..."
  gcloud sql databases create t2g_payments --instance=t2g-payments --quiet

  echo " Creating user 't2g_payment_app'..."
  gcloud sql users create t2g_payment_app --instance=t2g-payments --password="${POSTGRES_PASSWORD}" --quiet
  echo " Cloud SQL PostgreSQL instance created successfully."
else
  echo " Cloud SQL instance 't2g-payments' already exists."
fi

echo "=========================================================="
echo " GCP Infrastructure Setup Complete!"
echo " All Service Accounts, IAM bindings, and Datastores are active."
echo "=========================================================="
