S# Run 'gradlew clean createCentralZip' to create the archive
# Run update.sh to upload it to Maven Central

source ~/.gradle/central.sh

BEARER=`printf "$CENTRAL_USERNAME_TOKEN:$CENTRAL_PASSWORD_TOKEN" | base64`

 curl \
   --request POST \
   --verbose \
   --header "Authorization: Bearer $BEARER" \
   --form bundle=@core/build/publications/central.zip \
   https://central.sonatype.com/api/v1/publisher/upload

curl \
  --request POST \
  --verbose \
  --header "Authorization: Bearer $BEARER" \
  --form bundle=@image/build/publications/central.zip \
  https://central.sonatype.com/api/v1/publisher/upload

curl \
  --request POST \
  --verbose \
  --header "Authorization: Bearer $BEARER" \
  --form bundle=@utilities/build/publications/central.zip \
  https://central.sonatype.com/api/v1/publisher/upload


echo "Now login to https://central.sonatype.com/ and publish the zips"

