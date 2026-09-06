# Hosting & Deployment Guide: Agrish_main Web Gaming Server

This guide explains how to run, configure, and host the **Agrish_main** web gaming app, enable user accounts, configure Google Sign-In, and deploy it to a live public website.

---

## 1. Quick Start (Local Hosting)

### Prerequisites
- Node.js (version 20+ or 24+) installed on your computer.

### Start the Server
1. Open a terminal / command prompt in the `server` folder:
   ```bash
   cd server
   ```
2. Install dependencies (if not already installed):
   ```bash
   npm install
   ```
3. Run the server:
   ```bash
   npm start
   ```
4. Open your browser and navigate to:
   - **Local computer:** `http://localhost:8080`
   - **Devices on your Wi-Fi (phones/tablets/laptops):** `http://<YOUR-COMPUTER-IP>:8080`

---

## 2. Setting Up Google Sign-In (OAuth 2.0)

Agrish Web Server includes both **One-Click Instant Google Sign-In** (for local testing without setup) and **Official Google Identity Services (GIS)**.

To connect your own Google Cloud credentials for production:
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing project.
3. Go to **APIs & Services** > **OAuth consent screen**:
   - Choose **External** user type and click **Create**.
   - Fill in the required app info (App Name: `Agrish Gaming Hub`, support email, etc.).
4. Go to **APIs & Services** > **Credentials**:
   - Click **+ CREATE CREDENTIALS** > **OAuth client ID**.
   - Application type: **Web application**.
   - Authorized JavaScript origins:
     - `http://localhost:8080` (for local testing)
     - `https://your-domain.com` (for production)
5. Copy your **Client ID** (it looks like `1234567890-abcdef.apps.googleusercontent.com`).
6. Open `server/.env` and paste your Client ID:
   ```env
   GOOGLE_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
   ```
7. Restart the server. The official Google Sign-In button will render automatically!

---

## 3. Hosting on Free & Cloud Web Hosting Platforms

### Option A: Your Live Deployment on Render & Plesk (Configured)
Your live Render backend service is configured at:
**`https://agrish-games2.onrender.com`**

To connect this with your Plesk website (`www.agrawali.com.et`):
1. Copy the frontend files from [`server/public/`](file:///c:/Users/AgrawAli/OneDrive%20-%20World%20Resources%20Institute/Desktop/agrish_main/server/public):
   - `index.html` (already configured with `window.AGRISH_SERVER_URL = "https://agrish-games2.onrender.com"`)
   - `style.css`
   - `app.js`
2. In your Plesk control panel for `agrawali.com.et`:
   - Open **File Manager** -> go to `httpdocs/` (or a subfolder like `httpdocs/game/`).
   - Upload the three files above.
3. Visit `https://www.agrawali.com.et` in any browser:
   - The game will automatically connect to `https://agrish-games2.onrender.com` and establish WebSocket & API communication.
   - User registrations, Google logins, profile updates, and poker games will sync in real time!

---

### Option B: Deploy to Railway
1. Go to [Railway.app](https://railway.app).
2. Click **New Project** > **Deploy from GitHub repo**.
3. Select your repository and set the root directory to `server`.
4. Add environment variables (`JWT_SECRET`, `PORT=8080`).
5. Click **Deploy**. Railway will generate a public domain for your web gaming app.

---

### Option C: Deploy to VPS / Linux Server (Ubuntu / Debian)
1. Clone the repo onto your server:
   ```bash
   git clone <your-repo-url>
   cd agrish_main/server
   npm install --production
   ```
2. Run with PM2 for background persistence and auto-restart:
   ```bash
   npm install -g pm2
   pm2 start server.js --name "agrish-server"
   pm2 save
   pm2 startup
   ```
3. Set up Nginx reverse proxy to forward port 80/443 to `http://127.0.0.1:8080`.
4. Install SSL certificate with Certbot (`certbot --nginx -d yourdomain.com`).

---

## 4. API Endpoints Reference

### Authentication
- `POST /api/auth/register` - Create an account (`username`, `email`, `password`, `display_name`, `bio`, `phone`, `avatar`).
- `POST /api/auth/login` - Sign in with username/email and password.
- `POST /api/auth/google` - Sign in / sign up with Google OAuth ID token.
- `GET /api/auth/me` - Check current session (Bearer token).

### User Information & Profile
- `GET /api/user/profile` - Fetch full user profile, game stats, and activity logs.
- `PUT /api/user/profile` - Input and update user info (`display_name`, `bio`, `phone`, `avatar`, `sound_enabled`, `preferred_game`).
- `GET /api/user/logs` - Transaction history and gaming ledger.
- `GET /api/leaderboard` - Top players by chips and wins.

### Game State & Actions
- `GET /api/state` - Fetch current game table state (Poker or Banker Agrish).
- `POST /api/action` - Perform game action (`join`, `fold`, `check`, `call`, `raise`, `roll`, `bank`, `bank_borrow`, `bank_return`, `switch_mode`, `new_round`).
- `WebSocket ws://` - Real-time push notifications for multiplayer table changes.
