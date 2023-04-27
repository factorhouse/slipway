#!/usr/bin/env bash

VERSION="8.2.1"
JARS="$1"

if [ ! -d "dependency-check" ]
then
  curl -O -L https://github.com/jeremylong/DependencyCheck/releases/download/v$VERSION/dependency-check-$VERSION-release.zip
  curl -O -L https://github.com/jeremylong/DependencyCheck/releases/download/v$VERSION/dependency-check-$VERSION-release.zip.asc
  gpg --keyserver hkp://keys.gnupg.net --recv-keys F9514E84AE3708288374BBBE097586CFEA37F9A6
  gpg --verify dependency-check-$VERSION-release.zip.asc
  unzip dependency-check-$VERSION-release.zip
  rm dependency-check-$VERSION-release.zip
  rm dependency-check-$VERSION-release.zip.asc
fi

for JAR in target/*-standalone.jar; do
  if ../scripts/nvd.sh $JAR; then
    echo "$JAR success!"
  else
    echo "$JAR failure"
    exit 1
  fi
done
