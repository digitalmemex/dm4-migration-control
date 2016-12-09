#!/bin/bash

oda="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=1935879601&single=true&output=csv"
hdi="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=981027257&single=true&output=csv"
migrationintensity="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=1539190958&single=true&output=csv"
remittances="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=234680878&single=true&output=csv"

findings="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=192140939&single=true&output=csv"
factsheet="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=1148106720&single=true&output=csv"

repatration_treaties="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=85677121&single=true&output=csv"
other_treaties="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=330246598&single=true&output=csv"

theses="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=1729530318&single=true&output=csv"
background="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=775284486&single=true&output=csv"

imprint="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=966956331&single=true&output=csv"

detentioncenters="https://docs.google.com/spreadsheets/d/1INPdNSijx20bvTgyTVlnbSVJo782sVhIfcEterd6r7M/pub?gid=775777912&single=true&output=csv"

sessionid=${1}
scratchdir=`mktemp -d`

echo "Scratch folder" $scratchdir
echo "Session Id" $sessionid
importFromWeb() {
	local type=$1
	local output=$scratchdir/$type
	local url=${2}

	echo "Downloading" $type
	curl -s ${url} --output $output
	
	echo "Importing" $type
	./import.sh $type $output $sessionid

	echo "Done"
}


#importFromWeb oda ${oda}
#importFromWeb hdi ${hdi}
#importFromWeb remittances ${remittances}
#importFromWeb migrationintensity ${migrationintensity}
#importFromWeb findings ${findings}
#importFromWeb factsheet ${factsheet}
#importFromWeb repatriation_treaties ${repatration_treaties}
#importFromWeb other_treaties ${other_treaties}
#importFromWeb theses ${theses}
#importFromWeb backgrounditems ${background}
importFromWeb detentioncenterdata ${detentioncenters}
