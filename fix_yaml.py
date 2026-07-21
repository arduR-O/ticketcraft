import yaml
import glob
import os

for path in glob.glob('*/src/main/resources/application.yml'):
    with open(path, 'r') as f:
        # Load all documents from the file (though there's usually only one)
        content = f.read()
    
    # We will just strip the appended part and parse it properly
    original = content.split('\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: prometheus, health, info\n  metrics:\n    tags:\n      application: ${spring.application.name}')[0]
    
    try:
        data = yaml.safe_load(original)
    except Exception as e:
        print(f"Error parsing {path}: {e}")
        continue
        
    if data is None:
        data = {}
        
    if 'management' not in data:
        data['management'] = {}
    if 'endpoints' not in data['management']:
        data['management']['endpoints'] = {}
    if 'web' not in data['management']['endpoints']:
        data['management']['endpoints']['web'] = {}
    if 'exposure' not in data['management']['endpoints']['web']:
        data['management']['endpoints']['web']['exposure'] = {}
        
    data['management']['endpoints']['web']['exposure']['include'] = 'prometheus, health, info'
    
    if 'metrics' not in data['management']:
        data['management']['metrics'] = {}
    if 'tags' not in data['management']['metrics']:
        data['management']['metrics']['tags'] = {}
    
    data['management']['metrics']['tags']['application'] = '${spring.application.name}'
    
    with open(path, 'w') as f:
        yaml.safe_dump(data, f, default_flow_style=False, sort_keys=False)
    
    print(f"Fixed {path}")
