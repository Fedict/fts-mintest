FROM openjdk:8
ADD test_fps /srv/test_fps
COPY openshift-config /srv/test_fps/config.txt
WORKDIR /srv/test_fps
EXPOSE 8080
RUN echo '#!/bin/sh\njava -cp "target/lib/*":target/test_fps-0.0.6-SNAPSHOT.jar com.bosa.testfps.Main -Dproxy.https.enabled=${PROXY_ENABLED:-true} -Dproxy.https.user=$PROXY_USER -Dproxy.https.password=$PROXY_PASSWORD -Dproxy.https.host=$PROXY_HOST -Dproxy.https.port=$PROXY_PORT -Dproxy.https.exclude=$PROXY_NONPROXYHOST "$@"' >./run.sh
CMD ./run.sh
