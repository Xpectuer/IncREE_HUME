# Use an official Spark base image
FROM bitnami/spark:3.5.1

# Set the working directory
WORKDIR /app

# Copy your application JAR and other necessary files to the container
COPY target/scala-2.12/HUME-assembly-0.2.0-CDF.jar /app/
COPY datasets /app/datasets
COPY in /app/in

ENV DATASET_PATH=/app/datasets
ENV NUM_EXECUTORS=20
ENV PARAMS_PATH=/app/in/exp-test.csv
ENV OUTPUT_DIR=/app/out


# Specify the command to run your application
CMD spark-submit \
--master local[*] \
--deploy-mode client \
--class org.dsl.SparkMain \
--name "IncREE" \
--conf spark.driver.memory=16g \
--conf spark.executor.memory=8g \
--conf spark.local.dir=/app \
--conf spark.executor.instances=$NUM_EXECUTORS \
/app/HUME-assembly-0.2.0-CDF.jar \
-d $DATASET_PATH \
-p $PARAMS_PATH \
-n 20 \
-o $OUTPUT_DIR \
-m inc -l -c