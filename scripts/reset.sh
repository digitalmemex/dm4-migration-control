#!/bin/bash

sessionid=${1}

curl -X DELETE --cookie "JSESSIONID=${sessionid}" http://localhost:8080/migrationcontrol/v1/reset
