ALTER
SESSION SET CONTAINER=XE;

-- CREATE USER FPFORDEL
CREATE
USER FPFORDEL
    IDENTIFIED BY fpfordel
    PROFILE DEFAULT
    ACCOUNT UNLOCK;

GRANT
    CREATE
SESSION,
    ALTER
SESSION,
    CONNECT,
    RESOURCE,
    CREATE
MATERIALIZED VIEW,
    CREATE
JOB,
CREATE TABLE,
CREATE
SYNONYM,
CREATE VIEW,
CREATE SEQUENCE,
    UNLIMITED TABLESPACE,
SELECT ANY TABLE
    TO FPFORDEL;

ALTER
USER FPFORDEL QUOTA UNLIMITED ON SYSTEM;

-- CREATE USER FPFORDEL_UNIT
CREATE
USER FPFORDEL_UNIT
    IDENTIFIED BY fpfordel_unit
    PROFILE DEFAULT
    ACCOUNT UNLOCK;

GRANT
    CREATE
SESSION,
    ALTER
SESSION,
    CONNECT,
    RESOURCE,
    CREATE
MATERIALIZED VIEW,
    CREATE
JOB,
CREATE TABLE,
CREATE
SYNONYM,
CREATE VIEW,
CREATE SEQUENCE,
    UNLIMITED TABLESPACE,
SELECT ANY TABLE
    TO FPFORDEL_UNIT;

ALTER
USER FPFORDEL_UNIT QUOTA UNLIMITED ON SYSTEM;
