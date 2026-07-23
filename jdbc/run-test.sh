#!/bin/bash
# Drives JdbcIdentityService against a real MariaDB holding the real schema.
set -u
JDK=/home/claude/net/jdk-25.0.3+9/bin/java
JAVAC=/home/claude/net/jdk-25.0.3+9/bin/javac
LIBS=/home/claude/cv/libs
API=/home/claude/api/build/libs/codeverse-api-0.1.0.jar

echo "########## START MARIADB ##########"
pkill -f mariadbd 2>/dev/null; sleep 2
nohup /usr/sbin/mariadbd --user=mysql --datadir=/var/lib/mysql \
  --socket=/run/mysqld/mysqld.sock --bind-address=127.0.0.1 --port=3306 > /tmp/maria.log 2>&1 &
for i in $(seq 1 30); do mariadb -e "SELECT 1" >/dev/null 2>&1 && break; sleep 1; done
mariadb -e "SELECT VERSION() AS mariadb;" || exit 1

echo
echo "########## SCHEMA WITH THE NEW DISCORD COLUMN ##########"
mariadb <<'SQL'
DROP DATABASE IF EXISTS network;
CREATE DATABASE network CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'network'@'127.0.0.1' IDENTIFIED BY 'testpw';
GRANT ALL ON network.* TO 'network'@'127.0.0.1';
FLUSH PRIVILEGES;
USE network;
CREATE TABLE codeverse_accounts (
  minecraft_id   BINARY(16)   NOT NULL PRIMARY KEY,
  internal_id    BINARY(16)   NOT NULL,
  username       VARCHAR(16)  NOT NULL,
  username_lower VARCHAR(16)  NOT NULL,
  tier           VARCHAR(16)  NOT NULL,
  password_hash  VARCHAR(255) NULL,
  totp_secret    VARCHAR(128) NULL,
  registered_at  BIGINT       NOT NULL DEFAULT 0,
  last_login_at  BIGINT       NOT NULL DEFAULT 0,
  last_login_ip  VARCHAR(45)  NULL,
  discord_id     VARCHAR(32)  NULL,
  UNIQUE KEY uq_username_lower (username_lower),
  KEY idx_internal (internal_id),
  KEY idx_discord (discord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @shared := UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA');

-- One person with a linked Java and Bedrock account, which is the case the
-- whole internal id design exists for.
INSERT INTO codeverse_accounts
 (minecraft_id, internal_id, username, username_lower, tier, totp_secret, registered_at, last_login_at, discord_id)
VALUES
 (UNHEX('11111111111111111111111111111111'), @shared, 'Elchi', 'elchi', 'PREMIUM', 'SECRET', 1700000000000, 1700000900000, '998877'),
 (UNHEX('22222222222222222222222222222222'), @shared, '.ElchiBR', '.elchibr', 'BEDROCK', NULL, 1700000100000, 1700000800000, '998877'),
 (UNHEX('33333333333333333333333333333333'), UNHEX('BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'), '~Someone', '~someone', 'CRACKED', NULL, 1700000200000, 1700000700000, NULL),
 (UNHEX('44444444444444444444444444444444'), UNHEX('CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC'), 'FutureTier', 'futuretier', 'SOMETHING_NEW', NULL, 0, 0, NULL);
SQL
mariadb -unetwork -ptestpw -h127.0.0.1 network -e "SELECT username, tier, discord_id FROM codeverse_accounts;"

echo
echo "########## RUN THE INTEGRATION TEST ##########"
cd /home/claude/jdbc
CP="out:$API:$LIBS/caffeine-3.2.4.jar:$LIBS/HikariCP-7.1.0.jar:$LIBS/slf4j-api-2.0.17.jar:/home/claude/cv/paper/build/libs/../../../libs/mysql.jar"
# The MySQL driver lives inside the shaded voice jar; extract a usable copy.
if [ ! -f /home/claude/jdbc/mysql.jar ]; then
  curl -sSL -o /home/claude/jdbc/mysql.jar \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar"
fi
CP="out:$API:$LIBS/caffeine-3.2.4.jar:$LIBS/HikariCP-7.1.0.jar:$LIBS/slf4j-api-2.0.17.jar:/home/claude/jdbc/mysql.jar"

$JAVAC -cp "$CP" -d out JdbcTest.java 2>&1 | head -5
$JDK -cp "$CP" JdbcTest
