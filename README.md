![FitPilot Icon](mobile/assets/icon.png)

# FitPilot

FitPilot is a modern, professional-grade metabolic tracking and biomechanical training assistant. It combines a FastAPI MongoDB Atlas backend with a React Native Expo TypeScript mobile application.

---

## Technology Stack

![TypeScript](https://img.shields.io/badge/typescript-%23007acc.svg?style=for-the-badge&logo=typescript&logoColor=white)
![React Native](https://img.shields.io/badge/react_native-%2320232a.svg?style=for-the-badge&logo=react&logoColor=%2361dafb)
![Expo](https://img.shields.io/badge/expo-%231C1E24.svg?style=for-the-badge&logo=expo&logoColor=white)
![Python](https://img.shields.io/badge/python-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)
![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)
![MongoDB](https://img.shields.io/badge/MongoDB-%234ea94b.svg?style=for-the-badge&logo=mongodb&logoColor=white)

### Mobile Frontend
- **Framework**: React Native with Expo (v57.0.0)
- **Language**: TypeScript
- **Navigation**: React Navigation (Native Stack + Bottom Tabs)
- **Sensory Tracking**: Expo Sensors (Pedometer integration for motion tracking)
- **Biomechanical AI**: Client-side WASM/WebGL MediaPipe Pose tracking

### Backend Service
- **Framework**: FastAPI
- **ASGI Server**: Uvicorn
- **Async MongoDB Client**: Motor
- **AI Engine**: Google Gemini 2.5 Flash API (via raw HTTPS/HTTPX requests)
- **Security**: Cryptographic password hashing (Bcrypt) & JWT session tokens (PyJWT)

### Database
- **Engine**: MongoDB Atlas (Async Document Store)

---

## System Architecture Flowchart

![System Architecture Flowchart](assets/image.png)

---

## Key Features

### 1. Daily Activity Dashboard
- **Step Tracker**: Accesses the native hardware pedometer. Tracks live step counts alongside calculated walk distance (km) and active calorie expenditure. Falls back to a mock step simulator on emulators/web.
- **AI Adaptation Engine**: Analyzes your logged meals and physical exercises for today, calculates metabolic targets based on body weight guidelines, and outputs macro and calorie guidance saved directly to your profile.
- **Real-Time Performance Indicators**: Dynamic grid highlighting active calorie burn, logged calories consumed, exercise exertion rate, and macronutrient balance (Protein/Carbs/Fat).

### 2. Conversational Meal Logger
- **Natural Language Input**: Type what you ate in plain text (e.g., "I had 2 cups of masoor dal and 4 boiled eggs for lunch").
- **Gemini NLP Parsing**: Translates plain text into ICMR standard portions, computes macro distributions, and auto-saves the logged meals straight into MongoDB.
- **Persisted History**: Loads previous chat conversation sessions on startup and supports clearing logs from the database via a trash icon.
- **Custom List Formatting**: Renders markdown details, bold labels, and bulleted ingredients into structured text bubbles.

### 3. Workout Logger
- **Quick Logging**: Save training sets, reps, and resistance weights.
- **MET Calculations**: Computes calorie burn and exertion intensity ratings dynamically based on exercise MET indexes:
  - **Squat**: 5.0 MET
  - **Bicep Curl**: 3.5 MET
- **Workout History**: Displays a timeline of exercises logged during the day.

### 4. Real-Time Posture Checker (Web Overlay)
- **Latency-Free Detection**: Processes camera frames locally on the client using WebGL-accelerated MediaPipe (avoiding round-trip network delays).
- **Form Analysis & Audio-Visual Feedback**:
  - *Squats*: Checks depth and checks back rounding.
  - *Bicep Curls*: Checks elbow placement and leans.
  - *Skeletal Color Cues*: Skeletal overlays render in green when form is correct and turn red during errors.

---

## System Equations & Calculations

### Calorie Burn Calculation
Based on activity duration, metabolic index (MET), and user body weight:

$$\text{Duration (hours)} = \frac{\text{Sets}}{30}$$

$$\text{Calories Burned} = \text{MET} \times \text{User Weight (kg)} \times \text{Duration (hours)}$$

### Exertion Intensity Score
Measures mechanical load adjusted against body weight:

$$\text{Intensity Score} = \left(\frac{\text{Sets} \times \text{Reps} \times \text{Weight (kg)}}{\text{User Weight (kg)}}\right) \times \text{MET}$$

### Metabolic Goal Formulas
Calculated on user profile body weight:
- **Target Protein**: $1.6\text{g} \times \text{Weight (kg)}$
- **Target Carbohydrates**: $3.0\text{g} \times \text{Weight (kg)}$
- **Target Lipids (Fat)**: $1.0\text{g} \times \text{Weight (kg)}$
- **Calorie Allowance**: $2000\text{ kcal} + \text{Workout Calories Burned}$

---

## Getting Started

### 1. Setup MongoDB Atlas & Gemini API
Ensure you have:
1. A MongoDB Atlas Connection URI.
2. A Gemini API key from Google AI Studio.

Create a `.env` file in the `backend/` directory:
```env
SECRET_KEY=your_jwt_secret_signing_key
MONGODB_URL=mongodb+srv://<username>:<password>@cluster.mongodb.net/fitpilot
GEMINI_API_KEY=AIzaSy...
```

### 2. Launch the Backend Server
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

### 3. Launch the Mobile Application
```bash
cd mobile
npm install
npx expo start
```
- Press **w** to run on web browser.
- Scan QR code to launch in Expo Go on Android/iOS.
