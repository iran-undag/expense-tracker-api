# Azure SQL Managed Identity Runbook Design

## Goal

Document the database-scoped SQL commands required to let the Expense API's
managed identity connect to a newly created Azure SQL database.

## Documentation change

Add a subsection named `Grant the API managed identity access to Azure SQL`
under `9. Configure the expense API Container App` in
`AZURE_CONFIGURATION.md`.

The subsection will:

- use `<expense-api-managed-identity-name>` as a reusable placeholder;
- tell an Azure SQL Microsoft Entra administrator to connect to the target
  application database, not `master`;
- create a contained database user with `FROM EXTERNAL PROVIDER`;
- grant `db_datareader`, `db_datawriter`, and `db_ddladmin`;
- explain that `db_ddladmin` is required because the API runs Flyway migrations;
- provide queries that verify the principal and its database-role memberships;
- state that these permissions are database-scoped and must be repeated for
  every new database; and
- distinguish Azure SQL authorization from the chatbot's Entra app-role
  assignment.

No application code, schema migration, environment variable, or unrelated
runbook section will change.

## Verification

Confirm the Markdown renders valid fenced SQL blocks and that the added section
contains the user creation, all three role grants, both verification queries,
and the database-scope warnings.
