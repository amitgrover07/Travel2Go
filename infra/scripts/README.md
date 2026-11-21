# Travel2Go GCP Deployment Toolkit

Automated scripts and configuration templates to provision and host the entire Travel2Go ecosystem on Google Cloud Platform (`travel2go-495007` in `asia-south2`).

---

## Files in this Directory

| File | Type | Purpose |
|---|---|---|
| `setup-gcp-resources.ps1` | PowerShell | Provisions APIs, Artifact Registry, Firestore, GCS Buckets, SAs, IAM roles, and Cloud SQL Postgres 15 on Windows. |
| `setup-gcp-resources.sh` | Bash | Same as above, for Linux, macOS, WSL, or Google Cloud Shell. |
| `build-and-deploy-all.ps1` | PowerShell | Compiles shared Maven modules, packages Jars, builds Docker containers, pushes to Artifact Registry, deploys 9 Cloud Run microservices, binds invoker roles, and deploys frontend to Firebase Hosting. |
| `build-and-deploy-all.sh` | Bash | Same as above, for Linux, macOS, WSL, or Google Cloud Shell. |
| `../gcp-deploy-env.example.env` | Config Template | Template containing all required secrets and configuration keys. |

---

## Quick Start (Step-by-Step)

### Step 1: Prepare Environment Variables
Copy the template and configure your production credentials:
```powershell
cp infra/gcp-deploy-env.example.env infra/gcp-deploy.env
```
Edit `infra/gcp-deploy.env` and set:
- `JWT_SECRET` & `QUOTE_TOKEN_SECRET` (generate using `openssl rand -hex 32`)
- `PAYMENT_DB_PASSWORD` (Postgres password)
- `RAZORPAY_*`, `TWILIO_*`, `MAIL_*` credentials
- `SPRING_RABBITMQ_*` and `SPRING_REDIS_*` endpoints

---

### Step 2: Run Foundation Setup (One-Time)
This enables all required GCP APIs, creates Artifact Registry, provisions Firestore, creates Cloud Storage buckets, configures the 9 dedicated service accounts with least-privilege IAM, and sets up Cloud SQL Postgres:

**On Windows (PowerShell):**
```powershell
pwsh -File infra/scripts/setup-gcp-resources.ps1
```

**On Linux / Mac / Cloud Shell (Bash):**
```bash
chmod +x infra/scripts/*.sh
./infra/scripts/setup-gcp-resources.sh
```

---

### Step 3: Build & Deploy Full Fleet
This compiles `common-models` and `platform-security`, packages all 9 microservices, builds Docker containers, pushes them to Artifact Registry, deploys to Cloud Run with zero-trust invoker bindings, and deploys the Vite frontend to Firebase Hosting:

**On Windows (PowerShell):**
```powershell
pwsh -File infra/scripts/build-and-deploy-all.ps1
```

**On Linux / Mac / Cloud Shell (Bash):**
```bash
./infra/scripts/build-and-deploy-all.sh
```

---

### Step 4: Verify Deployment Health

Check public gateway health:
```bash
curl -i https://<GATEWAY_URL>/actuator/health
```

Check zero-trust isolation on internal services (must return HTTP 401/403 when called directly):
```bash
curl -i https://<BOOKING_SERVICE_URL>/api/bookings/my
```

Verify frontend single-domain routing:
```bash
curl -i https://<FIREBASE_HOSTING_DOMAIN>/api/trips
```
