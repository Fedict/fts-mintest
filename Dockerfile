FROM openjdk:8
ADD test_fps /srv/test_fps
COPY config.txt /srv/test_fps
WORKDIR /srv/test_fps
EXPOSE 8080
CMD ./run.sh
