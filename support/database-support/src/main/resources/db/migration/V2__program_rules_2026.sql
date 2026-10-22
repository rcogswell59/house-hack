-- 2026 Brevard County program parameters. Sources and access dates: docs/course/research/programs-and-data-sources.md
insert into program_rules (program, year, county, params) values
('FHA_LIMITS', 2026, 'Brevard', '{"1": 541287, "2": 693050, "3": 837700, "4": 1041125}'),
('HTH', 2026, 'Brevard', '{"exclusive_group": "first_mortgage_dpa", "pct_of_loan": 0.05, "min": 10000, "max": 35000, "min_credit": 640,
  "income_limit_tba": 149400, "income_limit_bond_1_2": 101414, "income_limit_bond_3plus": 116627, "requires_first_time_unless_veteran": true}'),
('BREVARD_HFA', 2026, 'Brevard', '{"exclusive_group": "first_mortgage_dpa", "amount": 15000, "requires_first_time": true}'),
('SHIP', 2026, 'Brevard', '{"exclusive_group": null, "max_very_low": 75000, "max_low": 60000,
  "ami_80_by_household": {"1": 56500, "2": 64600, "3": 72650, "4": 80700},
  "ami_50_by_household": {"1": 35300, "2": 40350, "3": 45400, "4": 50450},
  "max_liquid_assets": 15000, "source_note": "AMI figures are estimates - verify against HUD 2026 Brevard limits"}'),
('HOMESTEAD', 2026, 'Brevard', '{"exemption": 50000}')
on conflict (program, year, county) do update set params = excluded.params;
