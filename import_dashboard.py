import json
import urllib.request

with open('spring-boot-dashboard.json', 'r') as f:
    dash = json.load(f)

# Grafana export templates use __inputs. We need to replace references to it.
# Easiest way is to recursively search for "${DS_PROMETHEUS}" or similar and replace with "Prometheus"
def replace_ds(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == 'datasource' and v == '${DS_PROMETHEUS}':
                node[k] = 'Prometheus'
            else:
                replace_ds(v)
    elif isinstance(node, list):
        for item in node:
            replace_ds(item)

replace_ds(dash)

payload = json.dumps({
    "dashboard": dash,
    "overwrite": True,
    "inputs": [{"name": "DS_PROMETHEUS", "type": "datasource", "pluginId": "prometheus", "value": "Prometheus"}]
}).encode('utf-8')

req = urllib.request.Request("http://localhost:3000/api/dashboards/import", data=payload, headers={'Content-Type': 'application/json'})
req.add_header('Authorization', 'Basic YWRtaW46YWRtaW4=') # admin:admin in base64
try:
    response = urllib.request.urlopen(req)
    print("Success:", response.read().decode())
except urllib.error.URLError as e:
    print("Error:", e.read().decode())
