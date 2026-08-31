# One dedicated runtime service account per Cloud Run service, replacing the
# shared default compute SA (which gave every service identical access and made
# the run.invoker binding grant "any workload in the project" rather than a
# specific caller). Keys here are the short SA ids; the deploy workflow maps each
# matrix service to "t2g-<key>".

locals {
  services = {
    identity     = "Identity / auth service"
    package      = "Package catalog service"
    booking      = "Booking service"
    media        = "Media service"
    notification = "Notification service"
    trip         = "Trip / pricing service"
    payment      = "Payment service"
    gateway      = "API gateway"
    reactive     = "Reactive booking function"
  }
}

resource "google_service_account" "svc" {
  for_each     = local.services
  account_id   = "t2g-${each.key}"
  display_name = "Travel2Go - ${each.value}"
}
