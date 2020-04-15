#! /bin/sh
#

if [ -z "$TELEGRAM_TOKEN" ]; then
  echo "The TELEGRAM_TOKEN environment variable is required" >&2
  exit 1
fi

if [ -z "$CONTACT_EMAIL" ]; then
  echo "The CONTACT_EMAIL environment variable is required" >&2
  exit 1
fi

if [ -n "$DEBUG_TELEGRAM_TOKEN" ] && [ -n "$DEBUG_CHAT_ID" ]; then
  debug="debug { telegram-token = \"$DEBUG_TELEGRAM_TOKEN\"
    chat-id = $DEBUG_CHAT_ID }"
fi

cat > docker.conf << __END__
ausweis {
  telegram-token = "$TELEGRAM_TOKEN"
  contact-email = "$CONTACT_EMAIL"
  database = "http://db:5984/ausweis"
  $debug
}
__END__

echo "Waiting for the DB to start"
while ! curl -s http://db:5984 > /dev/null 2>&1; do
  sleep 1
done

for db in _global_changes _replicator _users ausweis; do
  curl -X PUT http://db:5984/$db > /dev/null 2>&1
done

exec java -jar ausweis.jar docker.conf
