-- Add TRANSFER payment method to existing system configurations
-- This script adds the new TRANSFER payment method (disabled by default) to all existing configurations

INSERT INTO system_payment_methods (system_configuration_id, payment_method_type, enabled)
SELECT id, 'TRANSFER', false
FROM system_configuration
WHERE NOT EXISTS (
    SELECT 1 FROM system_payment_methods 
    WHERE system_configuration_id = system_configuration.id 
    AND payment_method_type = 'TRANSFER'
);
