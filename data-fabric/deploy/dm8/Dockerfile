FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends libaio1 libncurses5 expect \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd -g 12349 dinstall \
 && useradd -u 12345 -g dinstall -m -d /home/dmdba -s /bin/bash dmdba
COPY DMInstall.bin /tmp/DMInstall.bin
COPY install_dm.exp /tmp/install_dm.exp
RUN chmod +x /tmp/DMInstall.bin /tmp/install_dm.exp \
 && cd /tmp && /tmp/install_dm.exp \
 && rm -f /tmp/DMInstall.bin /tmp/install_dm.exp \
 && test -x /opt/dmdbms/bin/dmserver \
 && chown -R dmdba:dinstall /opt/dmdbms
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENV DM_HOME=/opt/dmdbms \
    LD_LIBRARY_PATH=/opt/dmdbms/bin \
    PATH=/opt/dmdbms/bin:$PATH
EXPOSE 5236
VOLUME ["/opt/dmdbms/data"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
