ALTER TABLE metrics ADD COLUMN metric_value FLOAT GENERATED ALWAYS AS (value ->> '$.value') STORED NOT NULL