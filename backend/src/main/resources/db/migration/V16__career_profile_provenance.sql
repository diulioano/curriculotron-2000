-- US006: origem por campo do perfil (importado, inferido, editado, declarado) com linha e trecho
-- curto de evidência. Perfis anteriores ficam sem origem registrada ('{}').
ALTER TABLE career_profile ADD COLUMN provenance TEXT NOT NULL DEFAULT '{}';
