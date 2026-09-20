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
