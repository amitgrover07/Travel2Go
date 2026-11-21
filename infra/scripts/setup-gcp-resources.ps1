# ==============================================================================
# Travel2Go - GCP Foundation Setup Script (PowerShell)
# Sets up APIs, Artifact Registry, Firestore, Buckets, SAs, IAM, and Cloud SQL
# ==============================================================================

param (
    [string]$ProjectId = "travel2go-495007",
    [string]$Region = "asia-south2",
    [string]$MediaBucket = "travel2go-media",
    [string]$EmailBucket = "travel2go-email-templates",
    [string]$PostgresPassword = "ChangeMeStrong123!"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting Travel2Go GCP Infrastructure Setup" -ForegroundColor Cyan
Write-Host " Project: $ProjectId | Region: $Region" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Set active project
Write-Host "[1/7] Configuring active GCP project..." -ForegroundColor Yellow
gcloud config set project $ProjectId

# 2. Enable Required APIs
Write-Host "[2/7] Enabling Required Google Cloud APIs..." -ForegroundColor Yellow
$apis = @(
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "firestore.googleapis.com",
    "storage.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "cloudbuild.googleapis.com",
    "firebase.googleapis.com"
)
foreach ($api in $apis) {
    Write-Host " - Enabling $api..." -ForegroundColor Gray
    gcloud services enable $api --quiet
}

# 3. Create Artifact Registry
Write-Host "[3/7] Setting up Artifact Registry repository..." -ForegroundColor Yellow
$repoExists = gcloud artifacts repositories describe cloud-run-source-deploy --location=$Region --format="value(name)" 2>$null
if (-not $repoExists) {
    gcloud artifacts repositories create cloud-run-source-deploy `
        --repository-format=docker `
        --location=$Region `
        --description="Travel2Go Microservices Container Repository"
    Write-Host " Artifact Registry repository created." -ForegroundColor Green
} else {
    Write-Host " Artifact Registry repository already exists." -ForegroundColor Green
}

# 4. Create Cloud Storage Buckets
Write-Host "[4/7] Provisioning Cloud Storage Buckets..." -ForegroundColor Yellow
$buckets = @($MediaBucket, $EmailBucket)
foreach ($b in $buckets) {
    $bExists = gcloud storage buckets describe "gs://$b" --format="value(name)" 2>$null
    if (-not $bExists) {
        gcloud storage buckets create "gs://$b" --location=$Region --uniform-bucket-level-access
        Write-Host " Bucket gs://$b created." -ForegroundColor Green
    } else {
        Write-Host " Bucket gs://$b already exists." -ForegroundColor Green
    }
}

# 5. Create Dedicated Service Accounts
Write-Host "[5/7] Provisioning Zero-Trust Service Accounts..." -ForegroundColor Yellow
$serviceMap = @{
    "t2g-identity"     = "Identity & Auth Service"
    "t2g-package"      = "Package Catalog Service"
    "t2g-booking"      = "Booking Service"
    "t2g-media"        = "Media Service"
    "t2g-notification" = "Notification Service"
    "t2g-trip"         = "Trip & Pricing Service"
    "t2g-payment"      = "Payment Service"
    "t2g-gateway"      = "API Gateway"
    "t2g-reactive"     = "Reactive Booking Function"
}

foreach ($sa in $serviceMap.Keys) {
    $desc = $serviceMap[$sa]
    $saEmail = "$sa@$ProjectId.iam.gserviceaccount.com"
    $exists = gcloud iam service-accounts describe $saEmail --format="value(email)" 2>$null
    if (-not $exists) {
        gcloud iam service-accounts create $sa --display-name="Travel2Go - $desc"
        Write-Host " Created SA: $saEmail" -ForegroundColor Green
    } else {
        Write-Host " SA exists: $saEmail" -ForegroundColor Gray
    }

    # Grant baseline Logging and Metrics
    gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$saEmail" --role="roles/logging.logWriter" --condition=None --quiet | Out-Null
    gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$saEmail" --role="roles/monitoring.metricWriter" --condition=None --quiet | Out-Null
}

# 6. Grant Datastore / Firestore Roles
Write-Host "[6/7] Granting scoped Datastore / Storage / SQL permissions..." -ForegroundColor Yellow
$firestoreSAs = @("t2g-identity", "t2g-package", "t2g-booking", "t2g-media", "t2g-notification", "t2g-trip", "t2g-payment", "t2g-reactive")
foreach ($sa in $firestoreSAs) {
    $saEmail = "$sa@$ProjectId.iam.gserviceaccount.com"
    gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$saEmail" --role="roles/datastore.user" --condition=None --quiet | Out-Null
}

# Grant Storage Scoped Roles
$readers = @("t2g-identity", "t2g-package", "t2g-booking", "t2g-notification")
foreach ($sa in $readers) {
    $saEmail = "$sa@$ProjectId.iam.gserviceaccount.com"
    gcloud storage buckets add-iam-policy-binding "gs://$MediaBucket" --member="serviceAccount:$saEmail" --role="roles/storage.objectViewer" --quiet | Out-Null
}
gcloud storage buckets add-iam-policy-binding "gs://$MediaBucket" --member="serviceAccount:t2g-media@$ProjectId.iam.gserviceaccount.com" --role="roles/storage.objectAdmin" --quiet | Out-Null
gcloud storage buckets add-iam-policy-binding "gs://$EmailBucket" --member="serviceAccount:t2g-notification@$ProjectId.iam.gserviceaccount.com" --role="roles/storage.objectViewer" --quiet | Out-Null

# Cloud SQL client role for payment-service
gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:t2g-payment@$ProjectId.iam.gserviceaccount.com" --role="roles/cloudsql.client" --condition=None --quiet | Out-Null

# 7. Cloud SQL Postgres Instance
Write-Host "[7/7] Checking Cloud SQL PostgreSQL 15 Instance..." -ForegroundColor Yellow
$sqlExists = gcloud sql instances describe t2g-payments --format="value(name)" 2>$null
if (-not $sqlExists) {
    Write-Host " Creating Cloud SQL Postgres 15 instance 't2g-payments' in $Region (this may take 4-7 minutes)..." -ForegroundColor Cyan
    gcloud sql instances create t2g-payments `
        --database-version=POSTGRES_15 `
        --tier=db-f1-micro `
        --region=$Region `
        --storage-type=SSD `
        --storage-size=10GB `
        --backup `
        --quiet

    Write-Host " Creating database 't2g_payments'..." -ForegroundColor Gray
    gcloud sql databases create t2g_payments --instance=t2g-payments --quiet

    Write-Host " Creating user 't2g_payment_app'..." -ForegroundColor Gray
    gcloud sql users create t2g_payment_app --instance=t2g-payments --password=$PostgresPassword --quiet
    Write-Host " Cloud SQL PostgreSQL instance created successfully." -ForegroundColor Green
} else {
    Write-Host " Cloud SQL instance 't2g-payments' already exists." -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Green
Write-Host " GCP Infrastructure Setup Complete!" -ForegroundColor Green
Write-Host " All Service Accounts, IAM bindings, and Datastores are active." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
