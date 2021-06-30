FROM openjdk:8
ADD test_fps /srv/test_fps
COPY openshift-config /srv/test_fps/config.txt
WORKDIR /srv/test_fps
EXPOSE 8080
CMD ./run.sh
