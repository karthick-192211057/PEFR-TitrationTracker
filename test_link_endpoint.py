import requests
import json

# Login with the updated test credentials
login_response = requests.post('http://127.0.0.1:8000/auth/login', data={
    'username': 'karthicksaravanan0703@gmail.com',
    'password': 'TestPassword123!'
})

print(f'Login Status: {login_response.status_code}')

if login_response.status_code == 200:
    token = login_response.json()['access_token']
    print(f'Login successful! Token: {token[:20]}...\n')
    
    headers = {'Authorization': f'Bearer {token}'}
    
    # Test linking to doctor
    print('Testing doctor linking with jandajanda0709@gmail.com...')
    link_response = requests.post('http://127.0.0.1:8000/patient/link-doctor', 
        json={'doctor_email': 'jandajanda0709@gmail.com'},
        headers=headers)
    
    print(f'\nDoctor Link Status: {link_response.status_code}')
    print(f'Doctor Link Response: {json.dumps(link_response.json(), indent=2)}')
else:
    print(f'Login failed!')
    print(json.dumps(login_response.json(), indent=2))

