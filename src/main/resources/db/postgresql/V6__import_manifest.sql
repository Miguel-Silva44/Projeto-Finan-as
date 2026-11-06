CREATE TABLE data_import_manifest (
  source_hash VARCHAR(64) PRIMARY KEY,
  content_summary TEXT NOT NULL,
  completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
REVOKE INSERT,UPDATE,DELETE ON public.data_import_manifest FROM financas_app;
