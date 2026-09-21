-- Bootstrap for a NEW, empty MySQL 8+ database only.
-- Reconstructed from the DAO queries; the original project did not include a DDL dump.
-- Verified with MySQL 8.0.32 and the live integration smoke suite.
-- This is not a migration for an existing database. Do not run against real data.
CREATE DATABASE tiw_document_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tiw_document_manager;

CREATE TABLE `User` (
  user_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(25) NOT NULL,
  email VARCHAR(30) NOT NULL,
  -- Legacy authentication compares this value directly.
  password VARCHAR(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  UNIQUE KEY uq_user_username (username),
  UNIQUE KEY uq_user_email (email)
) ENGINE=InnoDB;

CREATE TABLE Folder (
  folder_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT NOT NULL,
  folder_name VARCHAR(25) NOT NULL,
  creation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  parent_folder_id INT NULL,
  depth INT NOT NULL,
  UNIQUE KEY uq_folder_owner (folder_id, owner_id),
  UNIQUE KEY uq_folder_name (owner_id, parent_folder_id, folder_name),
  CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_folder_parent FOREIGN KEY (parent_folder_id, owner_id)
    REFERENCES Folder (folder_id, owner_id),
  CONSTRAINT chk_folder_depth CHECK (depth >= 0)
) ENGINE=InnoDB;

CREATE TABLE Document (
  document_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT NOT NULL,
  document_name VARCHAR(25) NOT NULL,
  summary VARCHAR(250) NOT NULL,
  document_type VARCHAR(5) NOT NULL,
  creation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  folder_id INT NOT NULL,
  UNIQUE KEY uq_document_name (owner_id, document_name),
  CONSTRAINT fk_document_owner FOREIGN KEY (owner_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_document_folder FOREIGN KEY (folder_id, owner_id)
    REFERENCES Folder (folder_id, owner_id)
) ENGINE=InnoDB;
-- No users, passwords or demonstration records are inserted.
-- Registration creates each user's hidden Homepage folder (depth = 0).

-- Real files and cumulative session undo.
-- Apply ONCE after backing up an existing v1 database. Select the database first.
-- Never executes automatically on application startup. Requires the v1 schema.
ALTER TABLE Folder MODIFY folder_name VARCHAR(100) NOT NULL;
ALTER TABLE Document
  MODIFY document_name VARCHAR(180) NOT NULL,
  MODIFY document_type VARCHAR(20) NOT NULL,
  ADD COLUMN blob_id CHAR(36) NULL,
  DROP INDEX uq_document_name,
  ADD UNIQUE KEY uq_document_name (owner_id, folder_id, document_name, document_type);

CREATE TABLE FileBlob (
  blob_id CHAR(36) NOT NULL PRIMARY KEY,
  owner_id INT NOT NULL,
  file_size BIGINT NOT NULL,
  media_type VARCHAR(100) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  content LONGBLOB NOT NULL,
  UNIQUE KEY uq_blob_owner (blob_id, owner_id),
  CONSTRAINT fk_blob_owner FOREIGN KEY (owner_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB;
ALTER TABLE Document ADD CONSTRAINT fk_document_blob
  FOREIGN KEY (blob_id, owner_id) REFERENCES FileBlob (blob_id, owner_id);

CREATE TABLE WorkspaceState (
  owner_id INT NOT NULL PRIMARY KEY,
  revision BIGINT NOT NULL DEFAULT 0,
  head_action_id BIGINT NULL,
  CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB;
CREATE TABLE WorkspaceAction (
  action_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_id INT NOT NULL,
  parent_action_id BIGINT NULL,
  session_key CHAR(64) NOT NULL,
  description VARCHAR(500) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at TIMESTAMP NOT NULL,
  before_state JSON NULL,
  KEY ix_action_session (owner_id, session_key, action_id),
  CONSTRAINT fk_action_owner FOREIGN KEY (owner_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB;
