#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start




echo "Finished."
sudo docker login -u "inesalves4" -p "@Minion123"
sudo docker pull inesalves4/telemetry:1.0.0-SNAPSHOT
sudo docker run -d --name telemetry -p 8080:8080 inesalves4/telemetry:1.0.0-SNAPSHOT
