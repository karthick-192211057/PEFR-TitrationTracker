import requests
import json

BASE_URL = "http://127.0.0.1:8000"

print("=" * 60)
print("TEST 1: LOGIN")
print("=" * 60)

login_response = requests.post(f'{BASE_URL}/auth/login', data={
    'username': 'karticksaravanan0703@gmail.com',
    'password': 'Abc@1234'
})

print(f'Status: {login_response.status_code}')
print(f'Response: {json.dumps(login_response.json(), indent=2)}')

if login_response.status_code == 200:
    token = login_response.json()['access_token']
    print(f'\n[OK] Login successful!')
    print(f'Token: {token[:30]}...')
    
    print("\n" + "=" * 60)
    print("TEST 2: DOCTOR LINKING")
    print("=" * 60)
    
    headers = {'Authorization': f'Bearer {token}'}
    # check linked doctor
    getdoc = requests.get(f'{BASE_URL}/patient/doctor', headers=headers)
    print(f'Get linked doctor status: {getdoc.status_code}')
    try:
        print(json.dumps(getdoc.json(), indent=2))
    except:
        pass

    link_response = requests.post(f'{BASE_URL}/patient/link-doctor', 
        json={'doctor_email': 'jandajanda0709@gmail.com'},
        headers=headers)
    
    print(f'Status: {link_response.status_code}')
    print(f'Response: {json.dumps(link_response.json(), indent=2)}')
    
    if link_response.status_code == 200:
        print('\n[OK] Doctor linking successful!')
    # register a fake FCM device token so we can exercise token endpoint
    print("\nTesting FCM token registration and push...")
    fake_token = "fake-token-123"
    dev_resp = requests.post(f'{BASE_URL}/profile/device-token', data={'token': fake_token}, headers=headers)
    print(f'Device register status: {dev_resp.status_code}, body: {dev_resp.text}')
    # attempt to send push via test endpoint
    fcm_resp = requests.post(f'{BASE_URL}/test/send-fcm-token', data={'token': fake_token, 'title': 'Hi', 'body': 'Test'}, headers=headers)
    print(f'FCM send status: {fcm_resp.status_code}, body: {fcm_resp.text}')
    # create a reminder and fetch it
    rem_payload = { 'title': 'Take meds', 'message': 'Time to take your inhaler', 'time': '2025-12-31T09:00:00' }
    rem_resp = requests.post(f'{BASE_URL}/reminders', json=rem_payload, headers=headers)
    print(f'Reminder create status: {rem_resp.status_code}, body: {rem_resp.text}')
    list_resp = requests.get(f'{BASE_URL}/reminders', headers=headers)
    print(f'Reminder list status: {list_resp.status_code}, body: {list_resp.text}')
else:
    print(f'\n[ERROR] Login failed!')

print("\n" + "=" * 60)
print("TEST 3: FORGOT PASSWORD")
print("=" * 60)

forgot_response = requests.post(f'{BASE_URL}/auth/forgot-password',
    data={'email': 'karticksaravanan0703@gmail.com'})

print(f'Status: {forgot_response.status_code}')
print(f'Response: {json.dumps(forgot_response.json(), indent=2)}')

if forgot_response.status_code in [200, 201]:
    print('\n[OK] Forgot password email sent!')
else:
    print(f'\n[ERROR] Forgot password failed!')

