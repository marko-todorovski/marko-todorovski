ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS name VARCHAR(150);
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS source_format VARCHAR(32);
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS original_prompt TEXT;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS current_source_code TEXT;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS current_version_number INTEGER;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE domain_diagrams ADD COLUMN IF NOT EXISTS lock_version BIGINT;

UPDATE domain_diagrams
SET source_format = 'PLANTUML'
WHERE source_format IS NULL;

UPDATE domain_diagrams
SET current_source_code = plant_uml_code
WHERE current_source_code IS NULL
  AND plant_uml_code IS NOT NULL;

UPDATE domain_diagrams
SET original_prompt = input_text
WHERE original_prompt IS NULL
  AND input_text IS NOT NULL;

UPDATE domain_diagrams
SET updated_at = created_at
WHERE updated_at IS NULL
  AND created_at IS NOT NULL;
