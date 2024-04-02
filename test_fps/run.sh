#!/bin/sh
java -cp "target/lib/*":target/test_fps-0.0.6-SNAPSHOT.jar com.bosa.testfps.Main -Dproxy.https.enabled=${PROXY_ENABLED:-true} -Dproxy.https.user=$PROXY_USER -Dproxy.https.password=$PROXY_PASSWORD -Dproxy.https.host=$PROXY_HOST -Dproxy.https.port=$PROXY_PORT -Dproxy.https.exclude=$PROXY_NONPROXYHOST "$@"
