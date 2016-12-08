#!/bin/bash

type=${1}
file=${2}
sessionid=${3}

curl -H 'Content-Type: text/plain; charset=UTF-8' -X PUT --cookie "JSESSIONID=${sessionid}" --data-binary "@${file}" http://localhost:8080/migrationcontrol/v1/import/${type}
