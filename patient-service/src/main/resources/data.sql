/*
 * data.sql
 * 
 * Schema + seed data are now Flyway-managed under
 * db/migration/V1__baseline.sql.
 * 
 * This file is intentionally empty: Flyway owns schema creation
 * AND seed inserts; Spring Boot still loads data.sql after 
 * Flyway runs but there is nothing to execute here.
 * 
 * Keep the file so the path stays valid; remove it if your 
 * test schema config ever referenced it.
 */
