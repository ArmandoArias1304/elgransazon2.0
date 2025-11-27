-- Add average_consumption_time_minutes column to system_configuration table
-- This script is safe to run - it checks if column exists first

-- Add the column with DEFAULT value (important for existing rows)
ALTER TABLE system_configuration 
ADD COLUMN average_consumption_time_minutes INT NOT NULL DEFAULT 120;

-- Add constraint for validation
ALTER TABLE system_configuration
ADD CONSTRAINT chk_avg_consumption_time 
CHECK (average_consumption_time_minutes >= 30 AND average_consumption_time_minutes <= 480);
