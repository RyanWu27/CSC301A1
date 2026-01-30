import sys
import json
import urllib.request

def send_request(url, method, data=None):
    """Sends an HTTP request using built-in urllib."""
    req = urllib.request.Request(url, method=method)
    if data:
        # Convert dictionary to JSON bytes
        json_data = json.dumps(data).encode('utf-8')
        req.add_header('Content-Type', 'application/json')
        req.data = json_data

    try:
        with urllib.request.urlopen(req) as response:
            print(f"Status: {response.getcode()}")
            print(f"Response: {response.read().decode('utf-8')}")
    except urllib.error.HTTPError as e:
        print(f"Error Status: {e.code}")
        print(f"Error Body: {e.read().decode('utf-8')}")

def parse_workload(file_path, base_url):
    with open(file_path, 'r') as file:
        for line in file:
            line = line.strip()
            if not line or line.startswith('['): continue # Skip empty or source lines

            parts = line.split()
            service = parts[0] # USER, PRODUCT, or ORDER (should we error check?)
            action = parts[1]  # create, update, get, place, etc. (should we error check?)

            # user commands
            if service == "USER":
                if action == "get":
                    user_id = parts[2]
                    send_request(f"{base_url}/user/{user_id}", "GET")

                #  USER create <id> <name> <email> <password>
                elif action == "create":
                    payload = {
                        "command": "create",
                        "id": int(parts[2]),
                        "username": parts[3],
                        "email": parts[4],
                        "password": parts[5]
                    }
                    send_request(f"{base_url}/user", "POST", payload)

                #  USER delete <id> <name> <email> <password>
                elif action == "delete":
                    payload = {
                        "command": "delete",
                        "id": int(parts[2]),
                        "username": parts[3],
                        "email": parts[4],
                        "password": parts[5]
                    }
                    send_request(f"{base_url}/user", "POST", payload)

                #  USER update <id> <name> <email> <password>
                elif action == "update":
                    payload = {
                        "command": "update",
                        "id": int(parts[2])
                    }

                    for field_data in parts[3:]:
                        if ':' in field_data:
                            # Split by the first colon to get key and value
                            key, value = field_data.split(':', 1)
                            # Map workload keys to JSON keys if necessary
                            payload[key] = value

                    send_request(f"{base_url}/user", "POST", payload)


            # product commands
            elif service == "PRODUCT":

                if action == "info":
                    product_id = parts[2]
                    send_request(f"{base_url}/user/{product_id}", "GET")

                elif action == "create":
                    payload = {
                        "command": "create",
                        "id": int(parts[2]),
                        "name": parts[3],
                        "description": parts[4],
                        "price": float(parts[5]),
                        "quantity": int(parts[6])
                    }
                    send_request(f"{base_url}/product", "POST", payload)

                elif action == "update":
                    payload = {"command": "update", "id": int(parts[2])}
                    for field_data in parts[3:]:
                        if ':' in field_data:
                            key, value = field_data.split(':', 1)
                            # Cast types based on keys
                            if key == "price": payload[key] = float(value)
                            elif key == "quantity": payload[key] = int(value)
                            else: payload[key] = value
                    send_request(f"{base_url}/product", "POST", payload)

                elif action == "delete":
                    payload = {
                        "command": "delete",
                        "id": int(parts[2]),
                        "productname": parts[3],
                        "price": float(parts[4]),
                        "quantity": int(parts[5])
                    }
                    send_request(f"{base_url}/product", "POST", payload)

            # order command
            elif service == "ORDER":
                if action == "place":
                    payload = {
                        "command": "place order",
                        "product_id": int(parts[2]),
                        "user_id": int(parts[3]),
                        "quantity": int(parts[4])
                    }
                    send_request(f"{base_url}/order", "POST", payload)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 WorkloadParser.py <workloadfile>")
        sys.exit(1)

    # Send all requests to the OrderService port (14000)
    order_service_url = "http://127.0.0.1:14000"
    parse_workload(sys.argv[1], order_service_url)
