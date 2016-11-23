#!/bin/bash

remittances=Remittances.csv
hdi=HDI_final.csv
oda=ODA_HEAD.csv
sessionid=${1}

./import.sh hdi ${hdi} ${sessionid}
./import.sh remittances ${remittances} ${sessionid}
./import.sh oda ${oda} ${sessionid}
