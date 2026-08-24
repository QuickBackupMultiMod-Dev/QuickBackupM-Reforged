# Local act runner for 26.x: JDK 21 (harness) + JDK 25 (game) + Xvfb.
# GitHub-hosted runners ignore this file.
FROM catthehacker/ubuntu:act-22.04
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless xvfb libgl1-mesa-dri mesa-utils xauth libopenal1 \
        ca-certificates curl \
    && mkdir -p /etc/openal \
    && printf '%s\n' 'drivers = null' > /etc/openal/alsoft.conf \
    && curl -fsSL https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse \
         -o /tmp/jdk25.tar.gz \
    && mkdir -p /usr/lib/jvm/temurin-25-jdk-amd64 \
    && tar -xzf /tmp/jdk25.tar.gz -C /usr/lib/jvm/temurin-25-jdk-amd64 --strip-components=1 \
    && rm /tmp/jdk25.tar.gz \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV JAVA25_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
ENV PATH="/usr/lib/jvm/java-21-openjdk-amd64/bin:${PATH}"
ENV ALSOFT_DRIVERS=null
