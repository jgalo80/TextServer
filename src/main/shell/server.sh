#!/bin/sh

CERT_CHAIN=$1
PRIV_KEY=$2

JAVA_CMD=java
[ ! -z $JAVA_HOME ] && JAVA_CMD=$JAVA_HOME/bin/java

$JAVA_CMD -DSSL=true \
    -DWWWROOT=./wwwroot \
    -DCERT_CHAIN_FILE=$CERT_CHAIN \
    -DPRIV_KEY_FILE=$PRIV_KEY \
    -jar textServer-1.0-SNAPSHOT.jar 192.168.99.2