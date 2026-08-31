output "service_account_emails" {
  description = "Map of short service key -> runtime SA email, consumed by the deploy workflow."
  value       = { for k, sa in google_service_account.svc : k => sa.email }
}
