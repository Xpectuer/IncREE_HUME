#!/usr/bin/env python3

import subprocess
import time
from datetime import datetime
import os
import sys

''' Experiment configurations '''
# 10h timeout
TIME_OUT = 36000
NUM_INSTANCE = 20
TOPN_RATE = 0.9
USE_CACHE = True
CLUSTER_MODE = True  # change this to True on cluster
EVI_BUILD_MODE = False
LOCAL_JAR_PATH = "target/scala-2.12/HUME-assembly-0.2.0-CDF-apriori.jar"
# LOCAL_JAR_PATH= "./HUME-assembly-0.2.0-CDF-apriori.jar"
# SPARK_HOME="/usr/hdp/3.1.0.0-78/spark3/bin"
SPARK_HOME = "/home/zhengjy/spark/bin"
spark_submit = SPARK_HOME + "/spark-submit"

datasets = [
    #         "hospital",
    "inspection_t",
    "adult_t",
    #          "airport",
    "AMiner_Author_t",
    "AMiner_Author_t_0.2",
    "AMiner_Author_t_0.6",
    "AMiner_Author_t_0.8",
    #          "ncvoter",
    #          "tax_1000w"
]

# datasets = [
#        "inspection",
#
#        ]

evi_configs = [
    "evi.csv"
]

exp1_configs = [
    # "increase-supp_rate_var.csv",
    # "decrease-supp_rate_var.csv",
    # "increase-conf_rate_var.csv",
    # "decrease-conf_rate_var.csv",
    "s-c--lambda_rate_var.csv",
    "s-c+-lambda_rate_var.csv",
    "s-c+-lambda_init_var.csv",
    "s-c--lambda_init_var.csv",
    "s+c+-lambda_rate_var.csv",
    "s+c--lambda_rate_var.csv",
    "s+c+-lambda_init_var.csv",
    "s+c--lambda_init_var.csv",
]

exp2_configs = [
    "d1.csv"
    # "Exp-2.csv",
]

exp3_configs = [
    "Exp-2.csv",
]

spark_configs = [
    "--class", "org.dsl.AprioriMain",
    # "--name", "hume-$dataset-$param-$instance_number",
    "--conf", "spark.network.timeout=10000000",
    "--conf", "spark.driver.memo=200g",
    "--conf", "spark.executor.cores=1",
]

spark_cluster_configs = [
                            "--master", "spark://192.168.15.80:7077",
                            "--deploy-mode", "client",
                        ] + spark_configs + [
                            "--conf", "spark.local.dir=/home/spark",
                            "--conf", "spark.executor.memory=200g",
                        ]

spark_local_configs = [
                          "--master", "local[*]"
                      ] + spark_configs + [
                          "--conf", f"spark.local.dir={SPARK_HOME}",
                          "--conf", "spark.driver.memory=128g",
                          "--conf", "spark.executor.memory=200g",
                      ]


# Define a function to run the command with retries
def run_command_with_retry(command, output_file, retries=3, timeout=5):
    attempt = 0
    success = False

    t = output_file.split("/")

    outdir = "/".join(t[:-1])
    name = t[-1]
    err_name = "[error]" + name
    err_file = "/".join([outdir, err_name])
    while attempt < retries and not success:
        try:
            with open(output_file, 'w') as f:
                with open(err_file, 'w') as err:
                    # Run the command and redirect output to a file
                    result = subprocess.run(command, stdout=f,
                                            stderr=err, timeout=timeout)
            if result.returncode == 0:
                print("Command executed successfully.")
                success = True
            else:
                print(f"Command ${command} failed with error: {result.stderr}")
        except subprocess.TimeoutExpired:
            print(f"Command timed out after {timeout} seconds. Retrying...")

        attempt += 1

    if not success:
        print(f"Failed to execute the command after {retries} retries.")


def run_exp(data_dir, dataset_name, exp_name, config_name, out_dir, n_instance=NUM_INSTANCE):
    result_file = "%s/%s.csv_%s_%d_benchmark.csv" % (out_dir, dataset_name,
                                                     config_name, n_instance)
    # Skip if result file already exists
    if os.path.exists(result_file):
        print("[SKIP] Result for %s, %s already exists: %s." % (
            dataset_name, config_name, result_file))
        return

    data_file = data_dir + "/%s.csv" % dataset_name
    print(f"processing... {data_file}")
    config_file = "in-expall/%s/%s" % (exp_name, config_name)

    out_file = "%s/%s-%s-%d.log" % (out_dir, dataset_name,
                                    config_name.split(".")[0], n_instance)

    if CLUSTER_MODE:
        configs = spark_cluster_configs + [
            "--conf", "spark.executor.instances=%d" % n_instance,
            "/home/spark/HUME-assembly-0.2.0-CDF-apriori.jar"]
    elif EVI_BUILD_MODE:
        configs = spark_local_configs + [
            "--conf", "spark.executor.instances=%d" % n_instance,
            LOCAL_JAR_PATH
        ]
    else:
        configs = spark_local_configs + [
            "--conf", "spark.executor.instances=%d" % n_instance,
            LOCAL_JAR_PATH
        ]

    # todo: make command consistent
    command = [spark_submit] + configs + [
        "-d", data_file,
        "-p", config_file,
        "-n", str(n_instance),
        # todo: Add outfile here
        "-o", out_dir,
        "-t", "0.9"
    ]
    if USE_CACHE:
        command.append("-c")
    print(" ".join(command))
    run_command_with_retry(command, out_file, retries=1, timeout=TIME_OUT)


def run(exp_name, all_exp_configs, out_dir, n_instance=NUM_INSTANCE):
    # Create the directory if it doesn't exist
    # timestamp = datetime.now().strftime("%Y-%m-%d-%H:%M:%S")
    # out_dir = "/data/hume/results/%s-%s" % (exp_name, timestamp)
    os.makedirs(out_dir, exist_ok=True)

    data_dir = "datasets-" + exp_name

    for each_dataset in datasets:
        for each_config in all_exp_configs:
            run_exp(data_dir, each_dataset, exp_name, each_config, out_dir, n_instance=n_instance)


def run_scalability():
    # Vary n
    for n in [20, 4, 16, 12, 8]:
        run("exp2", exp2_configs, result_dir, n_instance=n)


if __name__ == "__main__":
    assert (len(sys.argv) == 2)
    result_dir = sys.argv[1]
    run("exp1", exp1_configs, result_dir)
    run_scalability()
    # run("exp3", exp3_configs)
    # run("evi", evi_configs)
