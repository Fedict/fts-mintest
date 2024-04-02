#!/bin/sh
java -cp "target/lib/*":target/test_fps-0.0.6-SNAPSHOT.jar com.bosa.testfps.Main -Dhttps.proxyHost=dc-proxy.names.belgium.be -Dhttps.proxyPort=3128 -Dhttps.proxyUser=iaa-fts-pr -Dhttps.proxyPassword=CSO4qoHdtY2wPr3E "$@"
