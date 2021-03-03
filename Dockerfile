FROM maven:3-jdk-8
ADD test_fps.tar.gz /srv/
RUN cd /srv/test_fps; mvn install
COPY config.txt /srv/test_fps
WORKDIR /srv/test_fps
EXPOSE 8080
CMD ./run.sh
