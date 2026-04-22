@echo off
REM Set script directory to current directory
cd /d %~dp0

REM Create .env from example if it doesn't exist
IF NOT EXIST .env (
    copy env-example.txt .env
    echo Created .env from env-example.txt — edit it to change settings.
)

IF NOT EXIST logs (
    mkdir logs
)

echo Building clob-system...
call mvn -q clean install -DskipTests -f ..\clob-system\pom.xml

echo Building clob-load-test...
call mvn -q clean package -DskipTests

echo Running load test...
java -jar target\clob-load-test-1.0-jar-with-dependencies.jar
