# Local act runner: Ubuntu actions image plus JDK 21 and Xvfb, so each job
# does not spend ~15 minutes on apt. GitHub-hosted runners ignore this file.
FROM catthehacker/ubuntu:act-22.04
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless xvfb libgl1-mesa-dri mesa-utils xauth \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="/usr/lib/jvm/java-21-openjdk-amd64/bin:${PATH}"
