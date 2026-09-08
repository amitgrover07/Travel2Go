# Travel2Go IAM — per-service service accounts (A4)

Provisions one dedicated runtime service account per Cloud Run service, with
least-privilege roles, and restricts `run.invoker` on the internal services to
their real callers. Replaces the shared default compute SA.

## What this creates

- 9 service accounts: `t2g-identity`, `t2g-package`, `t2g-booking`, `t2g-media`,
  `t2g-notification`, `t2g-trip`, `t2g-payment`, `t2g-gateway`, `t2g-reactive`.
- Baseline `logging.logWriter` + `monitoring.metricWriter` on every SA (the
  default compute SA had these implicitly — custom SAs do not).
- `datastore.user` on the eight data-touching services (not the gateway).
- Bucket-scoped Storage: `objectViewer` for identity/package/booking/notification,
  `objectAdmin` for media, `objectViewer` on the email bucket for notification.
- `run.invoker` bindings: `trip <- gateway`, `booking <- gateway, trip`,
  `payment <- gateway`.
- Optionally, `iam.serviceAccountUser` for the CI deployer on each SA.
- (P1.3) The `t2g-payments` Cloud SQL Postgres instance, its `t2g_payments`
  database, and the `t2g_payment_app` user, for payment-service's money
  ledger — plus a `roles/cloudsql.client` binding for `t2g-payment` so the
  service can reach it via the socket-factory connector.

## Prerequisites

- The principal running Terraform needs, at minimum:
  `roles/iam.serviceAccountAdmin`, `roles/resourcemanager.projectIamAdmin`,
  `roles/run.admin`, and `roles/storage.admin` on the buckets.
- Creating the Cloud SQL instance (P1.3) requires the applying principal to
  also hold Cloud SQL admin permissions in the project (e.g. `roles/cloudsql.admin`).
- The CI deployer (identity behind `GCP_CREDENTIALS` in `backend-deploy.yml`)
  must hold `roles/iam.serviceAccountUser` on each runtime SA, or deploys with
  `--service-account` fail. Set `deployer_member` to have Terraform grant this.

## Required variables

- `payment_db_password` — password for the `t2g_payment_app` Postgres user
  (P1.3). No default, so `terraform apply`/`plan` will prompt for it or fail
  non-interactively without it. Supply it via `TF_VAR_payment_db_password` or
  an uncommitted tfvars file — never commit it.

## Apply order (there is a deliberate two-phase dependency)

1. **Create the SAs and their roles first** (so the workflow can deploy *as* them):

   ```bash
   cd infra/terraform
   terraform init
   terraform apply \
     -target=google_service_account.svc \
     -target=google_project_iam_member.logging \
     -target=google_project_iam_member.metrics \
     -target=google_project_iam_member.firestore \
     -target=google_storage_bucket_iam_member.bucket_readers \
     -target=google_storage_bucket_iam_member.media_writer \
     -target=google_storage_bucket_iam_member.notification_email_bucket \
     -target=google_service_account_iam_member.deployer_act_as \
     -var gcs_bucket_name=YOUR_BUCKET \
     -var gcs_email_bucket_name=YOUR_EMAIL_BUCKET \
     -var deployer_member="serviceAccount:YOUR_CI_SA@travel2go-495007.iam.gserviceaccount.com"
   ```

2. **Deploy** — push to `main`; `backend-deploy.yml` now deploys each service with
   `--service-account=t2g-<name>@...`. This creates/updates the Cloud Run services.

3. **Apply the invoker bindings** (they reference services that must now exist):

   ```bash
   terraform apply \
     -var gcs_bucket_name=YOUR_BUCKET \
     -var gcs_email_bucket_name=YOUR_EMAIL_BUCKET
   ```

## Guardrails

- **Test in staging first.** Moving off the default compute SA removes whatever
  ambient roles it had. This config re-grants logging, metrics, Firestore, and
  Storage explicitly, but verify nothing else was relied on (e.g. Pub/Sub, if you
  later add it — the current stack uses RabbitMQ, which authenticates via
  `SPRING_RABBITMQ_*` creds, not GCP IAM).
- **payment-service caller is a placeholder.** It is internal but has no confirmed
  caller in the current code; `run-invoker.tf` binds `gateway` as the presumed
  future caller — confirm and adjust when the payment flow is wired.
