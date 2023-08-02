#!/usr/bin/env bash

./dependency-check/bin/dependency-check.sh \
  --failOnCVSS 1 \
  --out ./dependency-check/report/$1 \
  --scan $1 \
  --ossIndexRemoteErrorWarnOnly true \
  --suppression dependency-check-suppressions.xml
