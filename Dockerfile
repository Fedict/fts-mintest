FROM eclipse-temurin:17-alpine
ADD test_fps /srv/test_fps
COPY openshift-config /srv/test_fps/config.txt
WORKDIR /srv/test_fps
EXPOSE 8080
RUN ls -l .
RUN echo '#!/bin/sh\njava -Dhttps.proxyUser=$PROXY_USER -Dhttps.proxyPassword=$PROXY_PASSWORD -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT -Dhttps.nonProxyHosts=$PROXY_NONPROXYHOST -cp "target/lib/*":target/test_fps-0.0.6-SNAPSHOT.jar com.bosa.testfps.Main "$@"' >./run.sh
RUN ls -l .
CMD ./run.sh
