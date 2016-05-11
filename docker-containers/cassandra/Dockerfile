FROM cassandra:2.0.17

ENV CASSANDRA_CONFIG /etc/cassandra
ENV CASSANDRA_YAML $CASSANDRA_CONFIG/cassandra.yaml
ENV CASSANDRA_ENV $CASSANDRA_CONFIG/cassandra-env.sh
COPY cassandra.yaml $CASSANDRA_YAML
COPY cassandra-env.sh $CASSANDRA_ENV
COPY entrypoint.sh /entrypoint.sh
RUN mv /usr/share/cassandra/lib/jamm-0.2.5.jar /usr/share/cassandra/lib/jamm-0.3.0.jar
ENTRYPOINT ["/entrypoint.sh"]

EXPOSE 7000 7001 7199 9042 9160
CMD ["cassandra", "-f"]
