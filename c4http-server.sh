#!/usr/bin/env bash

C4BOOTSTRAP_SERVERS=localhost:9092 \
C4HTTP_PORT=8067 \
C4SSE_PORT=8068 \
sbt c4http-server/run