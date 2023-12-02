#!/bin/bash
set -e
PARAMS="$1 $2 $3 $4 $5 $6 $7 $8"
mvn clean package  aws:deployS3@authorizationArtifacts aws:deployCf@cf -Dmode=$MODE $PARAMS 