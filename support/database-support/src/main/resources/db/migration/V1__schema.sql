create table profiles (
  id bigserial primary key,
  name text not null,
  annual_income numeric(12,2) not null,
  liquid_savings numeric(12,2) not null,
  credit_score int not null,
  first_time_buyer boolean not null,
  hth_eligible_occupation boolean not null default false,
  veteran boolean not null default false,
  household_size int not null default 1,
  other_monthly_debts numeric(10,2) not null default 0,
  workplace_lat double precision not null,
  workplace_lng double precision not null,
  max_drive_minutes int not null,
  units_min int not null default 2,
  units_max int not null default 4,
  vacancy_rate numeric(5,4) not null default 0.08,
  maintenance_rate numeric(5,4) not null default 0.05,
  insurance_rate numeric(5,4) not null default 0.012,
  closing_cost_rate numeric(5,4) not null default 0.03,
  mortgage_rate numeric(6,5) not null default 0.065,
  loan_program text not null default 'FHA',
  road_factor numeric(4,2) not null default 1.4,
  avg_speed_mph numeric(5,2) not null default 30,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table listings (
  id bigserial primary key,
  source text not null,
  source_id text not null,
  address text not null,
  city text,
  zip text,
  lat double precision,
  lng double precision,
  price numeric(12,2) not null,
  bedrooms int,
  bathrooms numeric(4,1),
  sqft int,
  year_built int,
  property_type text,
  listed_at timestamptz,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  active boolean not null default true,
  last_analyzed_at timestamptz,
  raw jsonb not null,
  unique (source, source_id)
);

create table parcel_facts (
  parcel_id text primary key,
  address_norm text not null,
  city text,
  zip text,
  just_value numeric(12,2),
  assessed_value numeric(12,2),
  taxes_annual numeric(10,2),
  millage numeric(8,4),
  year_built int,
  units int,
  land_use_code text,
  imported_at timestamptz not null default now()
);
create index parcel_facts_address_idx on parcel_facts (address_norm);

create table listing_parcel (
  listing_id bigint primary key references listings (id),
  parcel_id text not null references parcel_facts (parcel_id)
);

create table rent_estimates (
  listing_id bigint primary key references listings (id),
  est_rent_per_unit numeric(10,2) not null,
  low numeric(10,2),
  high numeric(10,2),
  beds_assumed int,
  fetched_at timestamptz not null default now()
);

create table analyses (
  id bigserial primary key,
  listing_id bigint not null references listings (id),
  profile_id bigint not null references profiles (id),
  run_at timestamptz not null default now(),
  net_monthly_cost numeric(10,2) not null,
  cash_to_close numeric(12,2) not null,
  lender_pass boolean not null,
  self_sufficiency_ratio numeric(6,3),
  dti numeric(5,3) not null,
  drive_minutes numeric(6,1) not null,
  breakdown jsonb not null,
  program_results jsonb not null,
  unique (listing_id, profile_id)
);

create table alert_events (
  id bigserial primary key,
  profile_id bigint not null,
  listing_id bigint not null,
  kind text not null,
  created_at timestamptz not null default now(),
  delivered_at timestamptz
);

create table program_rules (
  program text not null,
  year int not null,
  county text not null,
  params jsonb not null,
  primary key (program, year, county)
);

create table api_budget (
  source text not null,
  month date not null,
  calls_used int not null default 0,
  primary key (source, month)
);
