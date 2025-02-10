# Incremental Rule Discovery in Response to Parameter Updates

### Overview
This paper has been accepted by [SIGMOD 2025](https://2025.sigmod.org/index.shtml)

This project proposes an incremental rule discovery algorithm. The goal is to efficiently mine rules from a dataset, ensuring their support and confidence meet specified thresholds. This algorithm allows for dynamic adjustments to these thresholds without restarting the entire process, saving time and computational resources.


## Datasets
You can get datasets from [this page](https://drive.google.com/drive/folders/1-3xQp0xTlJBgX0noblIKO5mDY8W7Y3vZ?usp=drive_link)

## Packaged App Jar 
You can find the packaged app jar in the **release page**.

## Quick Start From Docker
### Download Release
Download the jar file into `target/scala-2.12/` directory.
```bash
wget https://github.com/Xpectuer/IncREE_HUME/releases/download/1.0.0/HUME-assembly-0.2.0-CDF.jar
```
### Build Docker Image 
```bash 
docker build -f .devcontainer/Dockerfile -t incree .
```
### Run the Spark App in container

```bash
docker run --rm \
  -v `pwd`/datasets:/app/datasets \
  -v `pwd`/in:/app/in \
  -e DATASET_PATH=/app/datasets/hospital.csv \
  -e PARAMS_PATH=/app/in/in-exp.csv \
  -e OUTPUT_DIR=/app/out \
  -e NUM_EXECUTORS=10 \
  incree
```

## Manually Install
### Prerequisites
Ensure the following software versions are installed:
 
- Ubuntu 22.04 (tested)
- Scala 2.12.10
- SBT 1.9.2
- Apache Spark 3.5.1

### Steps
1. **Download Datasets**: Obtain the CSV datasets from the specified [URL](https://drive.google.com/drive/folders/1-3xQp0xTlJBgX0noblIKO5mDY8W7Y3vZ?usp=drive_link).

2. **Organize Files**: Place datasets in a directory, e.g., <PROJECT_ROOT>/datasets, and parameter input files in <PROJECT_ROOT>/in.
3. **Compile the project** with following command
```shell
sbt +clean +assembly
```
Make sure there is a file named plugins.sbt located in the <PROJECT_ROOT>/project directory, and that it contains the following content.
```sbt
addSbtPlugin( "com.eed3si9n" %% "sbt-assembly" % "2.2.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.1")
```

3. Run the Algorithm: Use the following shell command to execute the algorithm:


```shell
spark-submit \
--master local[*] \
--deploy-mode client \
--class org.dsl.SparkMain \
--name "IncREE" \
--conf spark.driver.memory=16g \
--conf spark.executor.memory=8g \
--conf spark.local.dir=<PROJECT_ROOT> \
--conf spark.executor.instances=20 \
<PROJECT_ROOT>/target/scala-2.12/HUME-assembly-0.2.0-CDF.jar \
-d datasets/adult.csv \
-p in/exp-test.csv \
-n 20 \
-o adult_output \
-m inc -l -c
```

Note: Key command options include:
- Replace `<PROJECT_ROOT>` with the path to the root directory of your project.
- `-d`: Specifies the dataset path.
- `-p`: Specifies the parameter input file path.
- `-o`: Specifies the output file path (created if it doesn't exist).



### Input Parameter File 

Input Parameter File Format
The input parameter file should be in CSV format, structured as follows:

```csv
Supp1,Supp2,Conf1,Conf2,Recall,Radius
1E-06,1E-05,0.95,0.85,1.0,3
1E-06,1E-05,0.95,0.85,0.7,3
```

- `Supp1/Supp2`: Old and new support parameters.
- `Conf1/Conf2`: Old and new confidence parameters.
- `Recall`: The recall guarantee for the incremental miner (applicable for certain parameter variations).
- `Radius`: Sampling radius for the procedure.

This guide should help you set up and run the incremental rule discovery algorithm efficiently. For more detailed information, please refer to the documentation provided within the project.


