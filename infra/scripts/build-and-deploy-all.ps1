# ==============================================================================
# Travel2Go - Full Stack Build & Deploy Automation Script (PowerShell)
# Builds shared Maven modules, microservices, container images, deploys to Cloud Run,
# binds invoker IAM, and deploys frontend to Firebase Hosting.
# ==============================================================================

param (
    [string]$ProjectId = "travel2go-495007",
    [string]$Region = "asia-south2",
    [string]$EnvFile = "..\gcp-deploy.env"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$WorkspaceRoot = (Resolve-Path "$ScriptDir\..\..").Path
$EnvFilePath = Join-Path $ScriptDir $EnvFile

# 1. Load Environment Variables
if (Test-Path $EnvFilePath) {
    Write-Host "Loading configuration from $EnvFilePath..." -ForegroundColor Green
    Get-Content $EnvFilePath | Where-Object { $_ -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$' } | ForEach-Object {
        $key = $matches[1]
        $val = $matches[2].Trim('"').Trim("'")
        [System.Environment]::SetEnvironmentVariable($key, $val, [System.EnvironmentVariableTarget]::Process)
    }
} else {
    Write-Host "Warning: $EnvFilePath not found. Using current process environment." -ForegroundColor Yellow
}

$JWT_SECRET = [System.Environment]::GetEnvironmentVariable("JWT_SECRET")
if (-not $JWT_SECRET) { $JWT_SECRET = "placeholder_secret_at_least_32_bytes_long_123456" }
$QUOTE_TOKEN_SECRET = [System.Environment]::GetEnvironmentVariable("QUOTE_TOKEN_SECRET")
if (-not $QUOTE_TOKEN_SECRET) { $QUOTE_TOKEN_SECRET = "placeholder_quote_secret_at_least_32_bytes_123456" }
$PAYMENT_DB_PASSWORD = [System.Environment]::GetEnvironmentVariable("PAYMENT_DB_PASSWORD")
if (-not $PAYMENT_DB_PASSWORD) { $PAYMENT_DB_PASSWORD = "ChangeMeStrong123!" }
$RAZORPAY_KEY_ID = [System.Environment]::GetEnvironmentVariable("RAZORPAY_KEY_ID")
if (-not $RAZORPAY_KEY_ID) { $RAZORPAY_KEY_ID = "rzp_test_placeholder" }
$RAZORPAY_KEY_SECRET = [System.Environment]::GetEnvironmentVariable("RAZORPAY_KEY_SECRET")
if (-not $RAZORPAY_KEY_SECRET) { $RAZORPAY_KEY_SECRET = "placeholder_secret_at_least_32_bytes_long_123456" }
$RAZORPAY_WEBHOOK_SECRET = [System.Environment]::GetEnvironmentVariable("RAZORPAY_WEBHOOK_SECRET")
if (-not $RAZORPAY_WEBHOOK_SECRET) { $RAZORPAY_WEBHOOK_SECRET = "placeholder_webhook_secret_at_least_32_bytes_123" }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building Travel2Go Shared Maven Libraries" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 2. Build common-models and platform-security
$commonModelsPath = Join-Path $WorkspaceRoot "microservices\common-models"
$platformSecPath = Join-Path $WorkspaceRoot "microservices\platform-security"

Write-Host "Compiling common-models..." -ForegroundColor Yellow
Push-Location $commonModelsPath
cmd /c "mvnw.cmd clean install -DskipTests"
Pop-Location

Write-Host "Compiling platform-security..." -ForegroundColor Yellow
Push-Location $platformSecPath
cmd /c "mvnw.cmd clean install -DskipTests"
Pop-Location

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building Microservice Jars and Docker Containers" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

gcloud auth configure-docker "${Region}-docker.pkg.dev" --quiet

$services = @(
    "identity-service",
    "package-service",
    "booking-service",
    "media-service",
    "notification-service",
    "trip-service",
    "payment-service",
    "reactive-booking-function",
    "api-gateway"
)

foreach ($svc in $services) {
    Write-Host "`n>>> Processing $svc..." -ForegroundColor Cyan
    $svcPath = Join-Path $WorkspaceRoot "microservices\$svc"
    Push-Location $svcPath
    
    cmd /c "mvnw.cmd clean package -DskipTests"
    $imageTag = "${Region}-docker.pkg.dev/${ProjectId}/cloud-run-source-deploy/${svc}:latest"
    
    Write-Host "Building Docker image: $imageTag" -ForegroundColor Gray
    docker build -t $imageTag .
    
    Write-Host "Pushing Docker image to Artifact Registry..." -ForegroundColor Gray
    docker push $imageTag
    Pop-Location
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Deploying Services to Google Cloud Run" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Helper to deploy Cloud Run
function Deploy-CloudRunService {
    param(
        [string]$Name,
        [string]$SaName,
        [bool]$AllowUnauth,
        [string]$ExtraArgs,
        [string]$EnvVars
    )
    
    $image = "${Region}-docker.pkg.dev/${ProjectId}/cloud-run-source-deploy/${Name}:latest"
    $saEmail = "${SaName}@${ProjectId}.iam.gserviceaccount.com"
    $authFlag = if ($AllowUnauth) { "--allow-unauthenticated" } else { "--no-allow-unauthenticated" }
    
    Write-Host "Deploying $Name (SA: $saEmail)..." -ForegroundColor Yellow
    $cmd = "gcloud run deploy $Name --image=$image --region=$Region --platform=managed --service-account=$saEmail $authFlag --port=8080 $ExtraArgs"
    if ($EnvVars) {
        $cmd += " --set-env-vars=`"$EnvVars`""
    }
    Invoke-Expression $cmd
}

# Deploy Internal Services First
Deploy-CloudRunService -Name "payment-service" -SaName "t2g-payment" -AllowUnauth $false `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --no-cpu-throttling --add-cloudsql-instances=${ProjectId}:${Region}:t2g-payments" `
    -EnvVars "JWT_SECRET=$JWT_SECRET,QUOTE_TOKEN_SECRET=$QUOTE_TOKEN_SECRET,DB_URL=jdbc:postgresql:///t2g_payments?cloudSqlInstance=${ProjectId}:${Region}:t2g-payments&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DB_USERNAME=t2g_payment_app,DB_PASSWORD=$PAYMENT_DB_PASSWORD,RAZORPAY_KEY_ID=$RAZORPAY_KEY_ID,RAZORPAY_KEY_SECRET=$RAZORPAY_KEY_SECRET,RAZORPAY_WEBHOOK_SECRET=$RAZORPAY_WEBHOOK_SECRET"

Deploy-CloudRunService -Name "trip-service" -SaName "t2g-trip" -AllowUnauth $false `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" `
    -EnvVars "JWT_SECRET=$JWT_SECRET,QUOTE_TOKEN_SECRET=$QUOTE_TOKEN_SECRET"

Deploy-CloudRunService -Name "booking-service" -SaName "t2g-booking" -AllowUnauth $false `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" `
    -EnvVars "JWT_SECRET=$JWT_SECRET,QUOTE_TOKEN_SECRET=$QUOTE_TOKEN_SECRET"

# Deploy Supporting Services
Deploy-CloudRunService -Name "identity-service" -SaName "t2g-identity" -AllowUnauth $true `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" `
    -EnvVars "JWT_SECRET=$JWT_SECRET"

Deploy-CloudRunService -Name "package-service" -SaName "t2g-package" -AllowUnauth $true `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" `
    -EnvVars "JWT_SECRET=$JWT_SECRET"

Deploy-CloudRunService -Name "media-service" -SaName "t2g-media" -AllowUnauth $true `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" `
    -EnvVars "JWT_SECRET=$JWT_SECRET"

Deploy-CloudRunService -Name "notification-service" -SaName "t2g-notification" -AllowUnauth $true `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" `
    -EnvVars ""

Deploy-CloudRunService -Name "reactive-booking-function" -SaName "t2g-reactive" -AllowUnauth $true `
    -ExtraArgs "--min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost" `
    -EnvVars ""

# Get Deployed URLs
function Get-ServiceUrl([string]$name) {
    return (gcloud run services describe $name --region=$Region --format="value(status.url)").Trim()
}

$IdentityUrl = Get-ServiceUrl "identity-service"
$PackageUrl = Get-ServiceUrl "package-service"
$BookingUrl = Get-ServiceUrl "booking-service"
$MediaUrl = Get-ServiceUrl "media-service"
$TripUrl = Get-ServiceUrl "trip-service"
$PaymentUrl = Get-ServiceUrl "payment-service"
$NotifUrl = Get-ServiceUrl "notification-service"

# Update downstream URLs on trip & booking
gcloud run services update trip-service --region=$Region --update-env-vars="BOOKING_SERVICE_URL=$BookingUrl" --quiet
gcloud run services update booking-service --region=$Region --update-env-vars="IDENTITY_SERVICE_URL=$IdentityUrl,PACKAGE_SERVICE_URL=$PackageUrl,NOTIFICATION_SERVICE_URL=$NotifUrl" --quiet

# Deploy API Gateway (Public Frontend Ingress)
Deploy-CloudRunService -Name "api-gateway" -SaName "t2g-gateway" -AllowUnauth $true `
    -ExtraArgs "--min-instances=1 --max-instances=3 --concurrency=80 --timeout=60 --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,initialDelaySeconds=10,periodSeconds=5,failureThreshold=12,timeoutSeconds=3" `
    -EnvVars "IDENTITY_SERVICE_URL=$IdentityUrl,PACKAGE_SERVICE_URL=$PackageUrl,BOOKING_SERVICE_URL=$BookingUrl,MEDIA_SERVICE_URL=$MediaUrl,TRIP_SERVICE_URL=$TripUrl,PAYMENT_SERVICE_URL=$PaymentUrl"

$GatewayUrl = Get-ServiceUrl "api-gateway"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Applying Zero-Trust Service Invoker IAM Bindings" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# trip-service <- api-gateway
gcloud run services add-iam-policy-binding trip-service --region=$Region `
    --member="serviceAccount:t2g-gateway@${ProjectId}.iam.gserviceaccount.com" `
    --role="roles/run.invoker" --quiet

# booking-service <- api-gateway, trip-service
gcloud run services add-iam-policy-binding booking-service --region=$Region `
    --member="serviceAccount:t2g-gateway@${ProjectId}.iam.gserviceaccount.com" `
    --role="roles/run.invoker" --quiet
gcloud run services add-iam-policy-binding booking-service --region=$Region `
    --member="serviceAccount:t2g-trip@${ProjectId}.iam.gserviceaccount.com" `
    --role="roles/run.invoker" --quiet

# payment-service <- api-gateway
gcloud run services add-iam-policy-binding payment-service --region=$Region `
    --member="serviceAccount:t2g-gateway@${ProjectId}.iam.gserviceaccount.com" `
    --role="roles/run.invoker" --quiet

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building & Deploying Frontend to Firebase Hosting" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$frontendPath = Join-Path $WorkspaceRoot "frontend"
Push-Location $frontendPath

Write-Host "Installing dependencies & building Vite bundle..." -ForegroundColor Yellow
npm ci
$env:VITE_API_URL = $GatewayUrl
npm run build

Write-Host "Deploying to Firebase Hosting with Gateway reverse-proxy..." -ForegroundColor Yellow
firebase use $ProjectId
firebase deploy --only hosting

Pop-Location

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " Travel2Go Production Deployment Succeeded!" -ForegroundColor Green
Write-Host " API Gateway URL: $GatewayUrl" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
