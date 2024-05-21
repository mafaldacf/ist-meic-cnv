from send_requests import send_foxes_rabbits
from send_requests import send_insect_war
from send_requests import send_compression
import pandas as pd
import time
from matplotlib import pyplot as plt
import argparse
import sys
import threading

INSTANCE_ADDR = "localhost:8000"
MAIN_PLOTS_DIR = "plots"
METRICS_FILEPATH = "../../metricsFile.txt"

def clean_metrics():
    print("Cleaning metrics...")
    with open(METRICS_FILEPATH, "w") as file:
        file.write('')

def do_plot(plot_dir, x_list, y_list, plot_name, xlabel, y_list_2=None, info=None, legend_1='Total', legend_2='Store/Load'):
    sorted_points = sorted(zip(x_list, y_list))
    sorted_x, sorted_y = zip(*sorted_points)

    plt.plot(sorted_x, sorted_y, marker='o', color='blue', label='Data Points')

    sorted_x_first, sorted_y_first = sorted_x[0], sorted_y[0]
    sorted_x_last, sorted_y_last = sorted_x[-1], sorted_y[-1]

    # edge cases:
    if sorted_x_first == 0 or sorted_x_last - sorted_x_first == 0:
        slope = 'n/a'
    else:
        slope = (sorted_y_last - sorted_y_first) / (sorted_x_last - sorted_x_first)

    # edge cases:
    if sorted_x_first  == 0:
        div = 'n/a'
    else:
        div = int(sorted_x_last/sorted_x_first)

    if info:
        info_text = str(info)
    else:
        info_text = ""

    slope_text = f"""
    Slope (# insts): {slope}
    x1 = {sorted_x_first}, x2= {sorted_x_last}: x2/x1 = {div}
    y1 = {sorted_y_first}, y2= {sorted_y_last}: y2/y1 = {int(sorted_y_last/sorted_y_first)}\n\n\n\n\n\n\n\n\n\n\n\n\n\n
    """
    text = info_text + slope_text
    plt.text(0, 0, text)

    if y_list_2:
        sorted_points_2 = sorted(zip(x_list, y_list_2))
        sorted_x_2, sorted_y_2 = zip(*sorted_points_2)
        plt.plot(sorted_x_2, sorted_y_2, marker='o', color='red', label='Data Points 2')
        plt.legend([legend_1, legend_2])
    else:
        plt.legend([legend_1])

    plt.title(plot_name)
    plt.xlabel(xlabel)
    plt.ylabel("Number of instructions")
    plt.savefig(f'{MAIN_PLOTS_DIR}/{plot_dir}/{plot_name}.png')
    print(f"Successfuly saved plot figure in {MAIN_PLOTS_DIR}/{plot_dir}/{plot_name}.png!")

def do_plot_multiple_datasets(plot_dir, datasets, plot_name, xlabel):
    for dataset in datasets:
        x_list = dataset["x_list"]
        y_list = dataset["y_list"]
        color = dataset["color"]
        label = dataset["label"]

        sorted_points = sorted(zip(x_list, y_list))
        sorted_x, sorted_y = zip(*sorted_points)

        plt.plot(sorted_x, sorted_y)
        plt.plot(sorted_x, sorted_y, marker='o', color=color, label=label)
        plt.plot(sorted_x, sorted_y, color=color, label='Line Plot')

    plt.title(plot_name)
    plt.xlabel(xlabel)
    plt.ylabel("Number of instructions")
    plt.savefig(f'{MAIN_PLOTS_DIR}/{plot_dir}/{plot_name}.png')
    print(f"Successfuly saved plot figure in {MAIN_PLOTS_DIR}/{plot_dir}/{plot_name}.png!")


# -----------------------------------------------------------
# FOXES AND RABBITS
# -----------------------------------------------------------


# Format: FoxRabbitsParameters{generations=0;world=1;scenario=1}:executedInstructions=18
def parse_metrics_file_foxes_rabbits():
    print("Waiting for metrics file (sleeping for 7 seconds)...")
    time.sleep(7)
    results = []
    with open(METRICS_FILEPATH, "r") as file:
        for line in file:
            result = {}
            result["name"] = line.split("{")[0].strip()
            result["generations"] = int(line.split("generations=")[1].split(";")[0])
            result["world"] = int(line.split("world=")[1].split(";")[0])
            result["scenario"] = int(line.split("scenario=")[1].split("}")[0])
            result["work"] = int(line.split("work=")[1].split(";")[0])
            result["instPerGens"] = int(line.split("instPerGens=")[1])
            results.append(result)
    return results

def plot_foxes_rabbits_by_generations(generations=50, worlds=1, scenarios=1):
    clean_metrics()

    # edge case for testing:
    if generations == 0:
        send_foxes_rabbits(instance_addr=INSTANCE_ADDR, world=worlds, scenario=scenarios, generations=0)

    for i in range(1, generations+1):
        send_foxes_rabbits(instance_addr=INSTANCE_ADDR, world=worlds, scenario=scenarios, generations=i)

    x_list, y_list, y_list_2 = [], [], []
    for result in parse_metrics_file_foxes_rabbits():
        x_list.append(result["generations"])
        y_list.append(result["instPerGens"])
        #y_list.append(result["work"])
        #y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='foxes-rabbits', x_list=x_list, y_list=y_list, y_list_2=y_list_2,
            plot_name=f'foxes_rabbits_NEW_generations_X_of_{generations}_world_{worlds}_scenario_{scenarios}',
            xlabel=f"Number of generations (1-{generations})",
            legend_1='Average Inst Per Gens')

def plot_foxes_rabbits_by_world(generations=1000, worlds=4, scenarios=1): # max worlds = 4
    clean_metrics()

    for i in range(1, worlds+1):
        send_foxes_rabbits(instance_addr=INSTANCE_ADDR, world=i, scenario=scenarios, generations=generations)

    x_list, y_list, y_list_2 = [], [], []
    for result in parse_metrics_file_foxes_rabbits():
        x_list.append(result["world"])
        y_list.append(result["instructions"])
        y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='foxes-rabbits', x_list=x_list, y_list=y_list, y_list_2=y_list_2, plot_name=f'foxes_rabbits_generations_{generations}_world_X_of_{worlds}scenario_{scenarios}', xlabel=f"World (1-{worlds})")

def plot_foxes_rabbits_by_scenario(generations=1000, worlds=1, scenarios=3): # max scenarios = 3
    clean_metrics()

    for i in range(1, scenarios+1):
        send_foxes_rabbits(instance_addr=INSTANCE_ADDR, world=worlds, scenario=i, generations=generations)

    x_list, y_list, y_list_2 = [], [], []
    for result in parse_metrics_file_foxes_rabbits():
        x_list.append(result["scenario"])
        y_list.append(result["instructions"])
        y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='foxes-rabbits', x_list=x_list, y_list=y_list, y_list_2=y_list_2, plot_name=f'foxes_rabbits_generations_{generations}_world_{worlds}_scenario_X_of_{scenarios}', xlabel=f"Scenario (1-{scenarios})")


# -----------------------------------------------------------
# INSECT WAR
# -----------------------------------------------------------


# Format: AntWarParameters{max=1000;army1=5;army2=5}:executedInstructions=111218603
def parse_metrics_file_insect_war():
    print("Waiting for metrics file (sleeping for 7 seconds)...")
    time.sleep(7)
    results = []
    with open(METRICS_FILEPATH, "r") as file:
        for line in file:
            result = {}
            result["name"] = line.split("{")[0].strip()
            result["rounds"] = int(line.split("max=")[1].split(";")[0])
            result["army1"] = int(line.split("army1=")[1].split(";")[0])
            result["army2"] = int(line.split("army2=")[1].split("}")[0])
            result["work"] = int(line.split("work=")[1].split(";")[0])
            result["workPerRound&TotalArmySize"] = int(line.split("workPerRound&TotalArmySize=")[1])
            results.append(result)
    return results

def plot_insect_war_by_rounds(rounds=25, army_size=25, army_size_2=25, ratio=None):
    clean_metrics()

    army_size_2=army_size
    for i in range(1, rounds+1):
        send_insect_war(instance_addr=INSTANCE_ADDR, rounds=i, army1=army_size, army2=army_size_2)

    x_list, y_list, y_list_2 = [], [], None
    for result in parse_metrics_file_insect_war():
        x_list.append(result["rounds"])
        #y_list_2.append(result["work"])
        y_list.append(result["workPerRound&TotalArmySize"])

    do_plot(plot_dir='insect-war', x_list=x_list, y_list=y_list,
            y_list_2=y_list_2, plot_name=f'insect_war_NEW_rounds_X_of_{rounds}_army1_{army_size}_army2_{army_size_2}',
            xlabel=f"Number of Rounds (1-{rounds})",
            legend_1='Avg Work Per Round & Total Army size')

def plot_insect_war_by_army_size(rounds=25, army_size=50, army_size_2=10, ratio=None):
    clean_metrics()

    for i in range(1, army_size+1):
        send_insect_war(instance_addr=INSTANCE_ADDR, rounds=rounds, army1=i, army2=army_size_2)
    
    x_list, y_list, y_list_2 = [], [], []
    for result in parse_metrics_file_insect_war():
        x_list.append(result["army1"])
        y_list.append(result["instructions"])
        y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='insect-war', x_list=x_list, y_list=y_list, y_list_2=y_list_2, plot_name=f'insect_war_rounds_{rounds}_army1_X_of_{army_size}_army2_{army_size_2}', xlabel=f"Army Size (1-{army_size})")

def plot_insect_war_by_ratio(rounds=20, army_size=50, army_size_2=10, ratio=None):
    #clean_metrics()
    total = 200
    initial_ratio = 1
    i = initial_ratio
    #while i < ratio:
    #    size1 = i * total/(i+1)
   #     size2 = total-size1
    #    send_insect_war(instance_addr=INSTANCE_ADDR, rounds=rounds, army1=int(size1), army2=int(size2))
     #   i = i + 3

    x_list, y_list, y_list_2 = [], [], None
    for result in parse_metrics_file_insect_war():
        x_list.append(result["army1"]/result["army2"])
        y_list.append(result["workPerRound&TotalArmySize"])
        #y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='insect-war', x_list=x_list, y_list=y_list, y_list_2=y_list_2,
            plot_name=f'insect_war_rounds_{rounds}_by_CORRECTED_ratio_{initial_ratio}_to_{ratio}',
            xlabel=f"Ratio Army Size (200): 1-{ratio}",
            legend_1='workPerRound&TotalArmySize')


# -----------------------------------------------------------
# COMPRESSION
# -----------------------------------------------------------


# Format: CompressionParameters{compressionQuality=0.1;compressionType=Deflate;imageWriteParam=BufferedImage@10ea6dad: type = 13 IndexColorModel: #pixelBits = 8 numComponents = 3 color space = java.awt.color.ICC_ColorSpace@a2c7e57 transparency = 1 transIndex   = -1 has alpha = false isAlphaPre = false ByteInterleavedRaster: width = 1024 height = 768 #numDataElements 1 dataOff[0] = 0}:executedInstructions=249
def parse_metrics_file_compress_image():
    print("Waiting for metrics file (sleeping for 7 seconds)...")
    time.sleep(7)
    results = []
    with open(METRICS_FILEPATH, "r") as file:
        for line in file:
            result = {}
            result["name"] = line.split("{")[0].strip()
            result["factor"] = float(line.split("compressionQuality=")[1].split(";")[0])
            result["output_format"] = line.split("compressionType=")[1].split(";")[0]
            result["width"] = int(line.split("width = ")[1].split(" ")[0])
            result["height"] = int(line.split("height = ")[1].split(" ")[0])
            result["pixels"] = result["width"]*result["height"]
            result["work"] = int(line.split("work=")[1])
            results.append(result)
    print(results)
    return results

def plot_compress_image_by_factor(image='LAND_1024x768.BMP', images=None, factor=None, target_format='bmp', target_formats=None): # target formats: png, jpeg, bmp
    clean_metrics()
    pixels = None

    send_compression(instance_addr=INSTANCE_ADDR, filename=image, target_format=target_format, factor=0.1)
    factor = 0.1
    while factor < 0.9:
        send_compression(instance_addr=INSTANCE_ADDR, filename=image, target_format=target_format, factor=factor)
        factor = factor + 0.1
    send_compression(instance_addr=INSTANCE_ADDR, filename=image, target_format=target_format, factor=1)
    
    x_list, y_list, y_list_2 = [], [], []
    for result in parse_metrics_file_compress_image():
        x_list.append(result["factor"])
        y_list.append(result["instructions"])
        y_list_2.append(result["memoryAccess"])
        pixels = result["pixels"]

    #do_plot(plot_dir='compress-image', x_list=x_list, y_list=y_list, y_list_2=y_list_2, plot_name=f'compress_image_{image}_target_format_{target_format}_factor_X', xlabel=f"Factor (0-1)")

def plot_compress_image_by_size(image=None, images=['sample_640x426.bmp', 'sample_1280x853.bmp', 'sample_1920x1280.bmp'], factor=0.7, target_format='bmp', target_formats=None):
    clean_metrics()
    pixels = None

    for image in images:
        send_compression(instance_addr=INSTANCE_ADDR, filename=image, target_format=target_format, factor=factor)

    x_list, y_list, y_list_2 = [], [], None
    info = {}
    for index, result in enumerate(parse_metrics_file_compress_image()):
        x_list.append(result["pixels"])
        y_list.append(result["work"])
        #y_list_2.append(result["memoryAccess"])

    do_plot(plot_dir='compress-image', x_list=x_list, y_list=y_list, y_list_2=y_list_2,
            plot_name=f'compress_image_NEW_sample_BY_SIZE_target_format_{target_format}_factor_{factor}',
            xlabel=f"Number of pixels ({images})", info=info,
            legend_1='Work')

def plot_compress_image_by_target_format(image='LAND_1024x768.BMP', images=None, factor=None, target_format=None, target_formats=['png', 'jpeg', 'bmp']):
    clean_metrics()
    pixels = None

    for format in target_formats:
        send_compression(instance_addr=INSTANCE_ADDR, filename=image, target_format=format, factor=factor)

    x_list, y_list, y_list_2 = [], [], []
    info = {}
    for index, result in enumerate(parse_metrics_file_compress_image()):
        info[result["output_format"]] = result["instructions"]
        x_list.append(index)
        y_list.append(result["instructions"])
        y_list_2.append(result["memoryAccess"])
        pixels = result["pixels"]

    do_plot(plot_dir='compress-image', x_list=x_list, y_list=y_list, y_list_2=y_list_2, plot_name=f'compress_image_{image}_BY_TARGET_FORMAT_factor_{factor}', xlabel=f"Target formats({str(target_formats)})", info=info)


if __name__ == '__main__':
    main_parser = argparse.ArgumentParser()
    subparsers = main_parser.add_subparsers(help='commands', dest='application')

    # Usage example: build_plots.py foxes-rabbits --type generations -g 1000 -w 4 -s 3
    foxes_rabbits_parser = subparsers.add_parser('foxes-rabbits', help="Run and plot foxes and rabbits application")
    foxes_rabbits_parser.add_argument('-t', '--type', required=True, choices=['generations', 'world', 'scenario'], help="Plot by type")
    foxes_rabbits_parser.add_argument('-g', '--generations', required=True, type=int, help="Number of generations of max number of generations")
    foxes_rabbits_parser.add_argument('-w', '--worlds', required=True, type=int, choices=[1, 2, 3, 4], help="Number of worlds or world")
    foxes_rabbits_parser.add_argument('-s', '--scenarios', required=True, type=int, choices=[1, 2, 3], help="Number of scenarios or scenario")

    # Usage example: build_plots.py insect-war --type rounds -r 20 -a 25
    insect_war_parser = subparsers.add_parser('insect-war', help="Run and plot insect war application")
    insect_war_parser.add_argument('-t', '--type', required=True, choices=['rounds', 'army-size', 'ratio'], help="Plot by type")
    insect_war_parser.add_argument('-r', '--rounds', required=True, type=int, help="Number of rounds")
    insect_war_parser.add_argument('-a', '--army-size', required=True, type=int, help="Army size or max army size")
    insect_war_parser.add_argument('-q', '--ratio', type=int, help="Ratio army size (army1/army2)")

    # Usage example: build_plots.py compress-image --type factor -i LAND2_1024x768.BMP -tf png
    # OR           : build_plots.py compress-image --type size -tf png -f 0.5 -t size -is sample_640x426.bmp sample_1280x853.bmp sample_1920x1280.bmp
    # OR           : build_plots.py compress-image --type target-format -i LAND2_1024x768.BMP -f 0.5 -tfs png jpeg bmp
    compress_image_parser = subparsers.add_parser('compress-image', help="Run and plot compress image application")
    compress_image_parser.add_argument('-t', '--type', required=True, choices=['factor', 'size', 'target-format'], help="Plot by type")
    compress_image_parser.add_argument('-i', '--image', help="Image name from folder 'images'")
    compress_image_parser.add_argument('-tf', '--target-format', choices=['png', 'jpeg', 'bmp'], help="Target format output")

    # Multiple images:
    compress_image_parser.add_argument('-is', '--images', nargs='+', help="List of images name from folder 'images'")
    # Multiple target formats:
    compress_image_parser.add_argument('-tfs', '--target-formats', nargs='+', help="Factor to be applied to the list of images")
    # Both cases:
    compress_image_parser.add_argument('-f', '--factor', help="Factor to be applied to the list of images")

    args = vars(main_parser.parse_args())
    print("Arguments received: ", args)
    for key, value in args.items():
        if isinstance(value, str):
            args[key] = value.replace('-', '_')

    app = args.pop('application')
    type = args.pop('type')

    getattr(sys.modules[__name__], f"plot_{app}_by_{type}")(**args)

    #plot_foxes_rabbits_by_generations(num_generations=100, world=2, scenario=2)
    #plot_foxes_rabbits_by_world(generations=1000, num_worlds=4, scenario=1)
    #plot_foxes_rabbits_by_scenario(generations=100000, world=4, num_scenarios=3)
    #plot_insect_war_by_rounds(num_rounds=20, army1=10, army2=10)
    #plot_insect_war_by_army_size(rounds=10, max_army_size=25, army2=10)
    #plot_compress_image_by_factor(filename='sample_1920x1280.bmp', target_format='bmp')
