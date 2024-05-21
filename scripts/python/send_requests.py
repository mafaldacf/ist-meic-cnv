import threading
import requests
import base64
import os

# Output usage e.g.:
# response = requests.get(url)
# print(response.text)

INSTANCE_ADDR = "localhost:8001"


def send_compression(i=0, instance_addr=INSTANCE_ADDR, filename='LAND_1024x768.BMP', target_format='png', factor=0.2):
    name = os.path.splitext(filename)[0]
    base_format = os.path.splitext(filename)[1][1:]
    if base_format == 'BMP':
        base_format = 'bmp'

    print(f"{i} > Sending Compression for image {filename}, target format {target_format}, factor = {factor}")

    with open(f'images/{filename}', 'rb') as image_file:
        image_data = image_file.read()

        image_string = base64.b64encode(image_data).decode('utf-8')
        url = f"http://{instance_addr}/compressimage"

        prefix = f"targetFormat:{target_format};compressionFactor:{factor};data:image/{base_format};base64,"
        data = prefix + image_string
        response = requests.put(url, data)

        print(f"{i} < Received Compression")

        if target_format == 'bmp':
            target_format = 'BMP'

        # write to new file
        response_data = response.text
       # with open(f'images/compressed/lambda_{name}_{base_format}_factor_{factor}.{target_format}', 'wb') as compressed_file:
        #    prefix = f'data:image/{target_format};base64'
        #    response_data = response_data[len(prefix):]
       #     image_bytes = base64.b64decode(response_data)
       #     compressed_file.write(image_bytes)
        print(response_data)
            


# World: 1-4
# Scenario: 1-3
# Generations: arbitrary
def send_foxes_rabbits(i=0, instance_addr=INSTANCE_ADDR, world=3, scenario=3, generations=5000):
    print(f"{i} > Sending Foxes Rabbits: world {world}, scenario {scenario}, generations {generations}")
    url = f"http://{instance_addr}/simulate?generations={generations}&world={world}&scenario={scenario}"
    requests.get(url)
    print(f"{i} < Received Foxes Rabbits: world {world}, scenario {scenario}, generations {generations}")


# Simulation rounds: arbitrary (e.g. 10000)
# Army1 size: arbitrary (e.g. 10)
# Army2 size: arbitrary (e.g. 10)
def send_insect_war(i=0, instance_addr=INSTANCE_ADDR, rounds=1000, army1=5, army2=5):
    print(f"{i} > Sending Insect War: rounds {rounds}, army1 {army1}, army2 {army2}")
    url = f"http://{instance_addr}/insectwar?max={rounds}&army1={army1}&army2={army2}"
    requests.get(url)
    print(f"{i} < Received Insect War: rounds {rounds}, army1 {army1}, army2 {army2}")

def send_multiple_requests():
    threads = []
    for i in range(15):
        t1 = threading.Thread(target=send_foxes_rabbits, args=(i,))
        t2 = threading.Thread(target=send_insect_war, args=(i,))
        threads.append(t1)
        threads.append(t2)
        #threads.append(threading.Thread(target=send_compression, args=(i,)))

    for thread in threads:
        thread.start()

    for thread in threads:
        thread.join()

if __name__ == '__main__':
    #send_compression(0)
    #send_foxes_rabbits(world=4, scenario=3, generations=100)
    send_compression()

    print("done!")