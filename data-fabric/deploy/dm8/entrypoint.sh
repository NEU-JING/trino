#!/bin/bash
set -e

DATA_ROOT=/opt/dmdbms/data
INSTANCE_DIR="$DATA_ROOT/DAMENG"

mkdir -p "$DATA_ROOT"
chown -R dmdba:dinstall "$DATA_ROOT"

if [ ! -f "$INSTANCE_DIR/dm.ini" ]; then
    echo "Initializing DM8 instance at $INSTANCE_DIR ..."
    su dmdba -c "/opt/dmdbms/bin/dminit path=$DATA_ROOT PAGE_SIZE=16 EXTENT_SIZE=16 CHARSET=1 CASE_SENSITIVE=0 DB_NAME=DAMENG INSTANCE_NAME=DMSERVER PORT_NUM=5236 SYSDBA_PWD=SYSDBA_dm001 SYSAUDITOR_PWD=SYSAUDITOR_dm001"
fi

echo "Starting DM8 server ..."
exec su dmdba -c "/opt/dmdbms/bin/dmserver $INSTANCE_DIR/dm.ini"
