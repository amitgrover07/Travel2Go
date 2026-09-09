# Cloud SQL for payment-service's Postgres money ledger (P1.3). No other
# service reads from this instance - Firestore remains the datastore for
# every other collection in every other service.

resource "google_sql_database_instance" "payments" {
  name             = "t2g-payments"
  project          = var.project_id
  region           = var.region
  database_version = "POSTGRES_15"

  settings {
    tier = "db-f1-micro"
    ip_configuration {
      ipv4_enabled = true
    }
    backup_configuration {
      enabled = true
    }
  }

  deletion_protection = true
}

resource "google_sql_database" "payments_db" {
  name     = "t2g_payments"
  project  = var.project_id
  instance = google_sql_database_instance.payments.name
}

resource "google_sql_user" "payments_app_user" {
  name     = "t2g_payment_app"
  project  = var.project_id
  instance = google_sql_database_instance.payments.name
  password = var.payment_db_password
}

# Lets t2g-payment's Cloud Run instance reach this DB via the Cloud SQL
# JDBC Socket Factory (--add-cloudsql-instances), no VPC connector needed.
resource "google_project_iam_member" "payment_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.svc["payment"].email}"
}

output "payment_db_instance_connection_name" {
  value = google_sql_database_instance.payments.connection_name
}

output "payment_db_name" {
  value = google_sql_database.payments_db.name
}
