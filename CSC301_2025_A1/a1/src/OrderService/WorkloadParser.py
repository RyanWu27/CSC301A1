import sys
import json
import urllib.request
import urllib.error
import os

def extract_port(json_text, service_name):
    """Python implementation of the Java extractPort logic to ensure consistency."""
    try:
        i = json_text.find(service_name)
        if i < 0: return -1

        p = json_text.find("port", i)
        if p < 0: return -1

        colon = json_text.find(":", p)
        j = colon + 1

        while j < len(json_text) and not json_text[j].isdigit():
            j += 1

        start = j
        while j < len(json_text) and json_text[j].isdigit():
            j += 1

        if start == j: return -1
        return int(json_text[start:j])
    except Exception:
        return -1

def send_request(url, method, data=None):
    """Sends an HTTP request using built-in urllib[cite: 13]."""
    req = urllib.request.Request(url, method=method)
    if data:
        json_data = json.dumps(data).encode('utf-8')
        req.add_header('Content-Type', 'application/json')
        req.data = json_data

    try:
        with urllib.request.urlopen(req) as response:
            print(f"Request: {method} {url}")
            print(f"Status: {response.getcode()}")
            print(f"Response: {response.read().decode('utf-8')}\n")
    except urllib.error.HTTPError as e:
        print(f"Request: {method} {url}")
        print(f"Error Status: {e.code}")
        print(f"Error Body: {e.read().decode('utf-8')}\n")
    except urllib.error.URLError as e:
        print(f"Connection Error: {e.reason} for {url}. Ensure OrderService is running[cite: 64].\n")

def parse_workload(file_path, order_base):
    with open(file_path, 'r') as file:
        for line in file:
            line = line.strip()
            if not line or line.startswith('[') or line.startswith('#'): continue

            clean_line = line.split('#')[0].strip()
            parts = clean_line.split()
            if len(parts) < 2: continue

            service = parts[0].upper()
            action = parts[1].lower()

            if service == "USER":
                url = f"{order_base}/user" # Public endpoint for user
                if action == "get":
                    send_request(f"{url}/{parts[2]}", "GET")
                elif action == "create":
                    payload = {
                        "command": "create",
                        "id": int(parts[2]),
                        "username": parts[3],
                        "email": parts[4],
                        "password": parts[5]
                    }
                    send_request(url, "POST", payload)
                elif action == "delete":
                    payload = {
                        "command": "delete",
                        "id": int(parts[2]),
                        "username": parts[3],
                        "email": parts[4],
                        "password": parts[5]
                    }
                    send_request(url, "POST", payload)
                elif action == "update":
                    payload = {"command": "update", "id": int(parts[2])}
                    for field_data in parts[3:]:
                        if ':' in field_data:
                            key, value = field_data.split(':', 1)
                            payload[key] = value
                    send_request(url, "POST", payload)

            elif service == "PRODUCT":
                url = f"{order_base}/product" # Public endpoint for product
                if action == "info":
                    send_request(f"{url}/{parts[2]}", "GET")
                elif action == "create":
                    payload = {
                        "command": "create",
                        "id": int(parts[2]),
                        "name": parts[3],
                        "description": "No description provided",
                        "price": float(parts[4]),
                        "quantity": int(parts[5])
                    }
                    send_request(url, "POST", payload)
                elif action == "update":
                    payload = {"command": "update", "id": int(parts[2])}
                    for field_data in parts[3:]:
                        if ':' in field_data:
                            key, value = field_data.split(':', 1)
                            if key == "price": payload[key] = float(value)
                            elif key == "quantity": payload[key] = int(value)
                            else: payload[key] = value
                    send_request(url, "POST", payload)
                elif action == "delete":
                    payload = {
                        "command": "delete",
                        "id": int(parts[2]),
                        "name": parts[3],
                        "price": float(parts[4]),
                        "quantity": int(parts[5])
                    }
                    send_request(url, "POST", payload)

            elif service == "ORDER":
                url = f"{order_base}/order" # order endpoint
                if action == "place":
                    payload = {
                        "command": "place order",
                        "product_id": int(parts[2]),
                        "quantity": int(parts[3])
                    }
                    send_request(url, "POST", payload)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 WorkloadParser.py <workloadfile>")
        sys.exit(1)

    base_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.abspath(os.path.join(base_dir, "..", "..", "config.json"))

    try:
        with open(config_path, "r") as f:
            config_content = f.read()
    except FileNotFoundError:
        print(f"Error: config.json not found.")
        sys.exit(1)

    order_port = extract_port(config_content, 'OrderService')
    ORDER_BASE = f"http://127.0.0.1:{order_port}"

    parse_workload(sys.argv[1], ORDER_BASE)