-- Cria o utilizador e a base de dados do serviço de ficheiros.
-- Executar uma vez, como superutilizador do PostgreSQL:
--
--   sudo -u postgres psql -v ON_ERROR_STOP=1 -f criar-base-de-dados.sql
--
-- IMPORTANTE: trocar a password antes de executar e usar a mesma em FILESERVICE_DB_PASSWORD.
-- As tabelas são criadas pelo próprio serviço no primeiro arranque (migrações Flyway).

create role fileservice login password 'TROCAR_ESTA_PASSWORD';

create database fileservice
    owner fileservice
    encoding 'UTF8'
    template template0;
