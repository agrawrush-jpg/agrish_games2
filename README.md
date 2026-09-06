<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/68e642a9-482c-486c-bccb-38b13b83096d

# Agrish_main: Multi-Platform Gaming & Companion App

Agrish_main is a multi-platform gaming portal and companion app featuring **Texas Hold'em Poker** and **Banker Agrish (In-Between & Bank Game)**. It includes:
1. **Cloud Web Gaming Server (`server/`)**: A cloud-deployable web server hosting the live gaming app, with user registration, Google Login, and user information persistence in SQLite.
2. **Native Android App (`app/`)**: Jetpack Compose mobile app with bot play, local Wi-Fi hosting, QR code pairing, and banking companion tools.

---

## 🌐 1. Run & Host the Web Gaming Server

Play directly in any browser (mobile, tablet, or desktop) and host online:

```bash
cd server
npm install
npm start
```
- **Live Render Backend**: [https://agrish-games2.onrender.com](https://agrish-games2.onrender.com)
- **Live Plesk Website**: `https://www.agrawali.com.et`
- **Local Dev Server**: `http://localhost:8080`
- For complete production deployment instructions, see [`server/DEPLOYMENT.md`](file:///c:/Users/AgrawAli/OneDrive%20-%20World%20Resources%20Institute/Desktop/agrish_main/server/DEPLOYMENT.md).

### ✨ Web Server Key Features:
- **User Account Creation**: Register with username, email, password, avatar, bio, and phone number.
- **Login with Google**: Seamless Google Sign-In with OAuth 2.0 and Instant Demo Login for quick testing.
- **Save User Information**: Manage and permanently save profile details, preferences, and custom notes to SQLite database.
- **Texas Hold'em Poker**: Real-time card dealing, community board, pot management, and dynamic AI poker coaching tips.
- **Banker Agrish Portal**: Live bank vault tracking, player ledger, dice roller, borrowing, and returning funds.

---

## 📱 2. Run the Android App Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio.
2. Select **Open** and choose the directory containing this project (`agrish_main`).
3. Allow Android Studio to import and sync Gradle.
4. Create a file named `.env` in the project root directory and set `GEMINI_API_KEY` (see `.env.example`).
5. Run the app on an emulator or physical Android device.

