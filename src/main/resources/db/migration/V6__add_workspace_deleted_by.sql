-- §32: soft delete without an actor is half an audit trail. Nullable and unset for existing
-- rows, since nothing recorded who deleted them.
ALTER TABLE workspaces ADD COLUMN deleted_by UUID REFERENCES users (id);
