-- A listing whose rent-estimate call failed (no AVM for the address, API error) is parked here so the
-- daily pipeline does not retry it every run and burn the monthly budget. Retried after a week.
alter table listings add column rent_estimate_failed_at timestamptz;
