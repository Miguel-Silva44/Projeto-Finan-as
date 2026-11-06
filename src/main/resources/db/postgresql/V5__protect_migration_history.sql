-- This chain is PostgreSQL-only; do not change migrations already applied in H2.
REVOKE INSERT,UPDATE,DELETE ON public.flyway_schema_history FROM financas_app;
GRANT SELECT ON public.flyway_schema_history TO financas_app;
