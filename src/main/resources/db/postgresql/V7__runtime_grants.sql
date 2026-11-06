GRANT USAGE ON SCHEMA public TO financas_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE app_user,access_token,account,category,recurrence,entry,transfer,card,invoice,purchase,installment,invoice_payment,budget,audit,idempotency TO financas_app;
GRANT SELECT ON TABLE flyway_schema_history,data_import_manifest TO financas_app;
REVOKE INSERT,UPDATE,DELETE ON TABLE flyway_schema_history,data_import_manifest FROM financas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE financas_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO financas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE financas_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO financas_app;
