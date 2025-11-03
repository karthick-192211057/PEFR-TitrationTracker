
# 🌬️ Asthma Manager (Full Stack)

This repository contains the **full-stack code** for a mobile health application focused on **personalized asthma management**.  
The project is designed for **real-time data capture**, **treatment guidance**, and **doctor-patient collaboration**, as specified in the project documentation.



## 🧩 Project Structure

The project is split into two main components:

- **`app/` (Android Frontend)** — A native Android application built in Android Studio with **Kotlin**, **XML**, and modern **AndroidX** libraries.  
- **`asthma-backend/` (Python Backend)** — A secure RESTful API built with **Python** and the **FastAPI** framework to handle all data, logic, and user authentication.



## 🖥️ 1. Frontend (Android App)

The Android application serves as the **primary interface** for both patients and doctors.

### ✅ Frontend Status: COMPLETE

- **UI/UX:** All 15+ screens are fully built with XML and styled with a consistent dark teal theme matching the design specifications.  
- **Navigation:** A complete navigation graph (`nav_graph.xml`) connects all fragments, enabling full interactive flow.  
- **Logic:** All fragment `.kt` files follow the correct **ViewBinding** pattern with working click listeners.  
- **Features:** Advanced UI components like **MPAndroidChart** (for graphs) and **RecyclerView** (for patient/alert lists) are implemented with sample data.  



## ⚙️ 2. Backend (Python API)

The backend handles **business logic**, **data persistence**, and **security** for the application.

### ⚠️ Backend Status: NOT STARTED

The folder `asthma-backend/` includes **empty placeholder files**:  
`main.py`, `database.py`, `models.py`, `schemas.py`, and `auth.py`.

The following setup guide details how to build and run this server from scratch.



## 🧠 Technology Stack

| Component | Technologies |
|------------|---------------|
| **Frontend** | Kotlin, XML, Android Studio, ViewBinding, Navigation Component, MPAndroidChart |
| **Backend** | Python 3.10+, FastAPI, SQLAlchemy, Bcrypt, Python-JOSE (JWT) |
| **Database** | SQLite (for development), PostgreSQL (for production) |

---

## 🚀 How to Set Up and Run This Project

Follow these steps **from start to finish** to run the full application locally.

]

### 🧰 Part 1: Backend Setup (in Visual Studio Code)

We recommend using **VS Code** for the Python backend, since Android Studio is not designed for Python development.

#### 1️⃣ Open the Backend Folder

In VS Code:  
**File → Open Folder...** and select the **`asthma-backend`** directory.

#### 2️⃣ Set Up the Environment

Open a terminal (**Terminal → New Terminal**) and run:

```bash
python -m venv venv
````

Activate the virtual environment:

```bash
.\venv\Scripts\Activate.ps1
```

(Your terminal prompt should now show `(venv)`)

#### 3️⃣ Install Dependencies

```bash
pip install "fastapi[all]" sqlalchemy "passlib[bcrypt]" "python-jose[cryptography]"
```

#### 4️⃣ Populate the Python Files

You must populate these files with the following logic:

| File              | Purpose                                                                                             |
| ----------------- | --------------------------------------------------------------------------------------------------- |
| **`database.py`** | Database connection and session handling (SQLite / PostgreSQL).                                     |
| **`models.py`**   | SQLAlchemy classes defining tables such as `Users`, `PEFRReadings`, and `Symptoms`.                 |
| **`schemas.py`**  | Pydantic models for request and response data (e.g., `UserCreate`, `Token`).                        |
| **`auth.py`**     | Authentication logic including bcrypt password hashing and JWT handling.                            |
| **`main.py`**     | The main FastAPI app registering all routes (e.g., `/auth/signup`, `/auth/login`, `/patient/pefr`). |

#### 5️⃣ Run the Backend Server

Once populated, start the server:

```bash
uvicorn main:app --reload --host 0.0.0.0
```

A new file **`asthma.db`** will be created automatically.

Access the API docs at:
👉 [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)

---

### 📱 Part 2: Frontend Setup (in Android Studio)

#### 1️⃣ Open the Project

In Android Studio:
**File → Open...** and select the **root `AsthmaManager2` folder**.

#### 2️⃣ Sync Gradle

Wait for Gradle to download and sync all dependencies listed in `build.gradle.kts`.

#### 3️⃣ Check Icons (One-Time Fix)

Some icons (`ic_cough.png`, `ic_dyspnea.png`) are corrupted.
Replace them:

* Delete the broken files from `res/drawable`.
* Right-click **res/drawable → New → Vector Asset**.
* Import new icons (e.g., search “sick” and “compress”).

#### 4️⃣ Run the App

* Create an Android Virtual Device (AVD) using **Device Manager**.
* Click the **Run (▶)** button to build and launch the app on the emulator.

---

### 🌐 Part 3: Connecting Frontend to Backend ✅ *(Updated & Working)*

This step ensures the Android app communicates successfully with your local FastAPI backend.

#### 1️⃣ Do NOT use your local IP manually

The Android Emulator provides a special alias — **`10.0.2.2`** — that points directly to your computer’s **localhost**.
This eliminates the need to find your IPv4 address manually.

#### 2️⃣ Verify Backend is Running

In VS Code, make sure the backend is running with:

```bash
uvicorn main:app --reload --host 0.0.0.0
```

#### 3️⃣ Configure Android Networking

Open this file in your Android Studio project:

```
app/src/main/java/com/example/asthmamanager/network/RetrofitClient.kt
```

Set the `BASE_URL` constant to:

```kotlin
const val BASE_URL = "http://10.0.2.2:8000/"
```

This automatically routes emulator traffic to your backend.

#### 4️⃣ Enable HTTP (Non-HTTPS) Connections

In your `AndroidManifest.xml`, inside the `<application>` tag, add:

```xml
<application
    android:name=".MyApplication"
    android:usesCleartextTraffic="true"
    ...>
```

This allows the app to connect to `http://` URLs in local development.

#### 5️⃣ Run Both Together

1. **Backend:** Keep `uvicorn` running in VS Code.
2. **Frontend:** Click **Run (▶)** in Android Studio to start the app on the emulator.

You can now:

* 🧍 Sign Up for a new account
* 🔐 Log In securely
* 📈 Record and fetch data from your **live FastAPI backend**

---

## 📊 Project Status Summary

| Component              | Status     | Description                                                                     |
| ---------------------- | ---------- | ------------------------------------------------------------------------------- |
| **Frontend (Android)** | ✅ Complete | 15+ screens, navigation, charts, RecyclerViews, and dark-teal theme implemented |
| **Backend (FastAPI)**  | ⚙️ Pending | Folder structure ready with placeholders; setup instructions included           |

---

## 🧾 API Preview

Once built, visit:
👉 [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
to view and test endpoints through **FastAPI’s Swagger UI**.

---

## 🏁 Final Notes

* Use `10.0.2.2` instead of `127.0.0.1` inside the Android Emulator.
* Always start the backend before launching the Android app.
* The backend automatically generates `asthma.db` (SQLite) for development.
* For production, switch to **PostgreSQL** in your `database.py` configuration.

---

**💡 Developed with care for better breathing — one line of code at a time.**

```

\
