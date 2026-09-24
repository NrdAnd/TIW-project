-- Back up and select the existing v2 database before applying this migration.
-- Preserves existing values. Then run MigratePasswords to convert ALL existing
-- plaintext records; successful login also upgrades a legacy record as a fallback.
ALTER TABLE `User` MODIFY password VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
