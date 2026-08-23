-- score components (weight config per class, chosen by lecturer)
CREATE TABLE IF NOT EXISTS score_components (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES classes(id),
    type VARCHAR(30) NOT NULL,
    weight NUMERIC(5,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_score_components_class_type ON score_components(class_id, type) WHERE deleted_at IS NULL;

-- manual scores per student per component (exercise is computed live from results)
CREATE TABLE IF NOT EXISTS student_scores (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES classes(id),
    student_code VARCHAR(50) NOT NULL,
    component_id UUID NOT NULL REFERENCES score_components(id),
    score NUMERIC(4,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_student_scores_class_code_component ON student_scores(class_id, student_code, component_id) WHERE deleted_at IS NULL;

-- user id mapping for auto-graded exercise (Keycloak sub, added when accounts exist)
ALTER TABLE class_students ADD COLUMN IF NOT EXISTS student_user_id UUID;