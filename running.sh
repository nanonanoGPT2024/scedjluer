#!/bin/bash

# Application Path
APP_JAR="target/schedjuler-0.0.1-SNAPSHOT.jar"

# Stop existing process if running
echo "Stopping existing scheduler process (if any)..."
pkill -f "$APP_JAR" || true
sleep 2

# Check if JAR exists
if [ ! -f "$APP_JAR" ]; then
    echo "Error: JAR file not found at $APP_JAR"
    echo "Please build the project first."
    exit 1
fi

echo "Starting Scheduler Application..."
# Using the flags from 58k_backend adapted for scheduler (port 8083)
java -Xms1g -Xmx2g -Dspring.profiles.active="dev" \
  -Dlogging.file.path="D:/home/tele/logs" \
  -Dspring.datasource.url="jdbc:postgresql://10.0.3.230:5432/58K_tellus?currentSchema=public&stringtype=unspecified&tcpKeepAlive=true&reWriteBatchedInserts=true&socketTimeout=60" \
  -Dspring.datasource.username="postgres" \
  -Dspring.datasource.password="D1k4@p0stgres" \
  -Dspring.datasource.driver-class-name="org.postgresql.Driver" \
  -Dserver.port="8083" \
  -Dupload.source.path="D:/home/tele/upload" \
  -Dupload.source.path.update="D:/home/tele/update" \
  -Dupload.source.path.submit="D:/home/tele/submit" \
  -Dupload.wbill.output.path="D:/home/tele/wbill" \
  -Dupload.dump.output.path="D:/home/tele/dump" \
  -Dupload.wbill.h0.output.path="D:/home/tele/wbill0" \
  -Dupload.wbill.h1.output.path="D:/home/tele/wbill1" \
  -Dapp.reference.id="8dd79639-e0fa-45fc-89b3-89372505bf57" \
  -Dspring.datasource.hikari.max-lifetime="120000" \
  -jar "$APP_JAR"


# Docker Environment (Uncomment to use)
# java -Dspring.profiles.active="docker" \
#   -Dlogging.file.path="D:/home/tele/logs" \
#   -Dserver.port="8083" \
#   -Dupload.source.path="D:/home/tele/upload" \
#   -Dupload.source.path.update="D:/home/tele/update" \
#   -Dupload.source.path.submit="D:/home/tele/submit" \
#   -Dupload.wbill.output.path="D:/home/tele/wbill" \
#   -Dupload.dump.output.path="D:/home/tele/dump" \
#   -Dupload.wbill.h0.output.path="D:/home/tele/wbill0" \
#   -Dupload.wbill.h1.output.path="D:/home/tele/wbill1" \
#   -Dapp.reference.id="8dd79639-e0fa-45fc-89b3-89372505bf57" \
#   -jar "$APP_JAR"


# Production Environment (Uncomment to use)
# java -Xms2g -Xmx4g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError \
#   -Dspring.profiles.active="prod" \
#   -Dlogging.file.path="/home/tele/logs" \
#   -Dspring.datasource.url="jdbc:postgresql://PROD_HOST:5432/58K_tellus?stringtype=unspecified" \
#   -Dspring.datasource.username="PROD_USER" \
#   -Dspring.datasource.password="PROD_PASSWORD" \
#   -Dserver.port="8083" \
#   -Dlogging.level.root="INFO" \
#   -Dlogging.level.co.id.mcs.dika="INFO" \
#   -Dspring.devtools.restart.enabled="false" \
#   -Dupload.source.path="/home/tele/upload" \
#   -Dupload.source.path.update="/home/tele/update" \
#   -Dupload.source.path.submit="/home/tele/submit" \
#   -Dupload.wbill.output.path="/home/tele/wbill" \
#   -Dupload.dump.output.path="/home/tele/dump" \
#   -Dupload.wbill.h0.output.path="/home/tele/wbill0" \
#   -Dupload.wbill.h1.output.path="/home/tele/wbill1" \
#   -jar "$APP_JAR"
