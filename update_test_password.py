import sqlite3
import os
from passlib.context import CryptContext

# Check database path
db_path = "asthma-backend/pefrtitrationtracker.db"

if not os.path.exists(db_path):
    print(f'Database not found at {db_path}')
    exit(1)

print(f"Connecting to {db_path}")

conn = sqlite3.connect(db_path)
cur = conn.cursor()

# Let's update the patient password to a known value
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
test_password = "TestPassword123!"
hashed_password = pwd_context.hash(test_password)

print(f"Updating karthicksaravanan0703@gmail.com with test password...")
cur.execute("UPDATE users SET hashed_password = ? WHERE email = ?", 
            (hashed_password, 'karthicksaravanan0703@gmail.com'))
conn.commit()
print("Updated!")

print(f"\nTest credentials:")
print(f"  Email: karthicksaravanan0703@gmail.com")
print(f"  Password: {test_password}")

conn.close()
