# The real isolation fix. The three internal services are deployed
# --no-allow-unauthenticated, so a caller needs roles/run.invoker. Previously the
# workflow bound the shared default compute SA, which meant "any Cloud Run
# workload in the project" could call them. Now each internal service accepts
# only its actual caller(s), by their dedicated SA:
#
#   trip-service    <- api-gateway
#   booking-service <- api-gateway, trip-service   (trip -> booking Feign call)
#   payment-service <- api-gateway                 (see note below)
#
# These bind IAM onto services created by the deploy workflow; run terraform
# apply after the services exist (see README).

resource "google_cloud_run_service_iam_member" "trip_invokers" {
  for_each = toset(["gateway"])
  location = var.region
  service  = "trip-service"
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.svc[each.key].email}"
}

resource "google_cloud_run_service_iam_member" "booking_invokers" {
  for_each = toset(["gateway", "trip"])
  location = var.region
  service  = "booking-service"
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.svc[each.key].email}"
}

# NOTE: payment-service is internal but has no confirmed caller in the current
# code (it is not in the api-gateway route list, and no Feign client targets it).
# api-gateway is bound here as the presumed future caller; confirm and adjust when
# the payment flow is wired (likely api-gateway and/or booking/trip).
resource "google_cloud_run_service_iam_member" "payment_invokers" {
  for_each = toset(["gateway"])
  location = var.region
  service  = "payment-service"
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.svc[each.key].email}"
}
