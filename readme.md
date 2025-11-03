
# 🌬️ Asthma Manager (Full Stack)

This repository contains the **full-stack code** for a mobile health application focused on **personalized asthma management**.
The project enables **real-time data capture**, **treatment guidance**, and **doctor-patient collaboration**, as detailed in the project documentation.

---

## 🧩 Project Structure

The project is divided into two main components:

```
AsthmaManager2/
├── app/                # Android Frontend (Kotlin)
└── asthma-backend/     # Python Backend (FastAPI)
```

* **Frontend (Android App)** – built using Kotlin, XML, and AndroidX libraries.
* **Backend (Python API)** – built using FastAPI, SQLAlchemy, and JWT-based authentication.

---

## 📱 1. Frontend (Android App)

The Android application serves as the **primary interface** for both **patients** and **doctors**.

### ✅ Frontend Status

**COMPLETE**

* **UI/UX:** All 15+ screens are fully designed in XML with a consistent **dark teal theme** matching the provided design specs.
* **Navigation:** A complete `nav_graph.xml` connects all fragments for a fully interactive prototype.
* **Logic:** Each fragment `.kt` file uses **ViewBinding** and **click listeners** for navigation.
* **Features:** Includes advanced UI elements such as:

  * `MPAndroidChart` for graphs and visual trends.
  * `RecyclerView` for lists (patients, alerts, etc.) with sample data.

---

## ⚙️ 2. Backend (Python API)

The backend handles all **business logic**, **data persistence**, and **security**.

### 🚧 Backend Status

**NOT STARTED** (Structure created)

The folder `asthma-backend/` includes empty placeholder files:

```
main.py
database.py
models.py
schemas.py
auth.py
```

### 🔧 Technology Stack

| Layer        | Technology / Tools                                                             |
| ------------ | ------------------------------------------------------------------------------ |
| **Frontend** | Kotlin, XML, Android Studio, ViewBinding, Navigation Component, MPAndroidChart |
| **Backend**  | Python 3.10+, FastAPI, SQLAlchemy, Bcrypt, Python-JOSE (JWT)                   |
| **Database** | SQLite (Development), PostgreSQL (Production)                                  |

---

## 🚀 How to Set Up and Run the Project

Follow these steps **from base to end** to get the full application running locally.

---

### 🧠 Part 1: Backend Setup (in Visual Studio Code)

We recommend **VS Code** for backend development since Android Studio doesn’t support Python.

#### 1. Open the Backend Folder

* In VS Code, go to **File → Open Folder...**
* Select the `asthma-backend` directory.

#### 2. Set Up the Environment

Open a new terminal in VS Code (**Terminal → New Terminal**) and run:

```bash
python -m venv venv
```

Activate the environment:

```bash
.\venv\Scripts\Activate.ps1
```

(Your terminal prompt should now show `(venv)`)

#### 3. Install Dependencies

Install all required Python libraries:

```bash
pip install "fastapi[all]" sqlalchemy "passlib[bcrypt]" "python-jose[cryptography]"
```

#### 4. Populate the Python Files

Each file in `asthma-backend/` should be populated as follows:

| File          | Description                                                                                       |
| ------------- | ------------------------------------------------------------------------------------------------- |
| `database.py` | Connects to the SQLite database.                                                                  |
| `models.py`   | SQLAlchemy ORM models (Users, PEFRReadings, Symptoms, etc.).                                      |
| `schemas.py`  | Pydantic models for requests/responses (`UserCreate`, `Token`, etc.).                             |
| `auth.py`     | Handles password hashing (bcrypt) and JWT token creation/verification.                            |
| `main.py`     | Defines the FastAPI app and all endpoints (`/auth/signup`, `/auth/login`, `/patient/pefr`, etc.). |

#### 5. Run the Backend Server

```bash
uvicorn main:app --reload
```

This will:

* Start your backend server at `http://127.0.0.1:8000`
* Create a local database file named `asthma.db`
* Expose live API docs at:
  👉 [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)

---

### 📲 Part 2: Frontend Setup (in Android Studio)

#### 1. Open the Project

* In Android Studio, go to **File → Open...**
* Select the root folder `AsthmaManager2`.

#### 2. Sync Gradle

* Wait for Gradle to sync all dependencies from `build.gradle.kts`.

#### 3. Fix Icons (One-Time Step)

The icon files `ic_cough.png` and `ic_dyspnea.png` are corrupt.

Fix them by:

1. Deleting them from `res/drawable`
2. Right-click `res/drawable` → **New → Vector Asset**
3. Import replacements (search “sick” and “compress” icons).

#### 4. Run the App

* Create an Android Virtual Device (AVD) via **Device Manager**
* Click the **Run (▶)** button to build and launch the app on the emulator.

---

### 🌐 Part 3: Connecting Frontend to Backend

To make the Android app communicate with your running FastAPI server:

#### 1. Find Your Local IP Address

Open a terminal (on Windows):

```bash
ipconfig
```

Locate your **IPv4 Address**, e.g. `192.168.1.10`.

> ⚠️ Note: The Android Emulator cannot access `127.0.0.1`.
> You must use your **local network IP** instead.

#### 2. Create a Retrofit Instance

In your Android app’s Kotlin code, create a new file:
`RetrofitInstance.kt` under your networking package.

```kotlin
package com.example.asthmamanager.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val BASE_URL = "http://192.168.1.10:8000/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
```

Replace the `BASE_URL` IP with your own **IPv4 Address**.

#### 3. Define Your API Interface

Example `ApiService.kt`:

```kotlin
package com.example.asthmamanager.network

import retrofit2.http.Body
import retrofit2.http.POST

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val access_token: String, val token_type: String)

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
```

#### 4. Test the Connection

* Ensure your backend is running with `uvicorn main:app --reload`
* Run your Android app in the emulator.
* Try signing up or logging in — data should now flow between the **app** and the **backend**.

---

## ✅ Summary

| Component          | Status               | Key Tools                   |
| ------------------ | -------------------- | --------------------------- |
| **Android App**    | ✅ Complete           | Kotlin, XML, MPAndroidChart |
| **Python Backend** | ⚙️ To Be Developed   | FastAPI, SQLAlchemy, JWT    |
| **Integration**    | 🔗 Ready for Testing | Retrofit (Kotlin) + FastAPI |

---

## 💡 Next Steps

* Implement backend logic in each Python file.
* Deploy backend to a cloud provider (e.g., Render, Railway, or GCP).
* Add persistent storage via PostgreSQL in production.
* Secure API endpoints and enable token-based access in the Android client.

---

## 📚 License

This project is licensed under the **MIT License**.
Feel free to modify and use for educational or research purposes.

---

