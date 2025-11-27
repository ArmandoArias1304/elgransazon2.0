-- Migration: Remove logo_url column from system_configuration
-- Date: 2024-11-08
-- Description: Remove logo_url column as logo is now hardcoded to /images/LogoVariante.png

-- Remove the logo_url column from system_configuration table
ALTER TABLE system_configuration DROP COLUMN IF EXISTS logo_url;
