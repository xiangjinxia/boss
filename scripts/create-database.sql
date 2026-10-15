-- MySQL 8.0+ / MySQL Workbench / Navicat / IDEA Database Console
-- This file creates an empty database. The backend uses Flyway to apply the
-- versioned schema in backend/src/main/resources/db/migration/ automatically.
CREATE DATABASE IF NOT EXISTS campus_exchange
 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- If using a separate application account, run as your local MySQL administrator:
-- CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED BY 'your-local-password';
-- GRANT ALL PRIVILEGES ON campus_exchange.* TO 'campus'@'localhost';
-- Then set DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD in IDEA.
