const crypto = require('node:crypto');
const db = require('./db');

const JWT_SECRET = process.env.JWT_SECRET || 'agrish_super_secret_session_key_2026';
const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || '';

// --- Password Hashing with scrypt ---
function hashPassword(password) {
    const salt = crypto.randomBytes(16).toString('hex');
    const derivedKey = crypto.scryptSync(password, salt, 64);
    return `${salt}:${derivedKey.toString('hex')}`;
}

function verifyPassword(password, storedHash) {
    if (!storedHash || !storedHash.includes(':')) return false;
    const [salt, key] = storedHash.split(':');
    const keyBuffer = Buffer.from(key, 'hex');
    const derivedKey = crypto.scryptSync(password, salt, 64);
    return crypto.timingSafeEqual(keyBuffer, derivedKey);
}

// --- Lightweight Base64Url JWT Implementation using native crypto ---
function base64UrlEncode(str) {
    return Buffer.from(str)
        .toString('base64')
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_');
}

function base64UrlDecode(str) {
    str = str.replace(/-/g, '+').replace(/_/g, '/');
    while (str.length % 4) str += '=';
    return Buffer.from(str, 'base64').toString('utf8');
}

function generateToken(user) {
    const header = base64UrlEncode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = base64UrlEncode(JSON.stringify({
        sub: user.id,
        username: user.username,
        email: user.email,
        displayName: user.display_name,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + (7 * 24 * 60 * 60) // 7 days
    }));

    const signature = crypto
        .createHmac('sha256', JWT_SECRET)
        .update(`${header}.${payload}`)
        .digest('base64url');

    return `${header}.${payload}.${signature}`;
}

function verifyToken(token) {
    if (!token || typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const [header, payload, signature] = parts;
    const expectedSignature = crypto
        .createHmac('sha256', JWT_SECRET)
        .update(`${header}.${payload}`)
        .digest('base64url');

    if (signature !== expectedSignature) return null;

    try {
        const decoded = JSON.parse(base64UrlDecode(payload));
        if (decoded.exp && decoded.exp < Math.floor(Date.now() / 1000)) {
            return null; // Expired
        }
        return decoded;
    } catch (e) {
        return null;
    }
}

// --- Google OAuth Verification ---
async function verifyGoogleToken(idToken) {
    if (!idToken) throw new Error('No Google token provided');

    // Test / Demo bypass for easy local testing without Google Cloud Console setup
    if (idToken.startsWith('demo-google-token:')) {
        const demoEmail = idToken.split(':')[1] || 'demo.player@gmail.com';
        const demoName = idToken.split(':')[2] || 'Google Player';
        return {
            googleId: 'demo_google_' + crypto.createHash('md5').update(demoEmail).digest('hex').substring(0, 12),
            email: demoEmail,
            displayName: demoName,
            avatar: '🌐'
        };
    }

    try {
        const res = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`);
        if (!res.ok) {
            const errBody = await res.text();
            throw new Error(`Google token validation failed: ${errBody}`);
        }
        const data = await res.json();
        
        // If client ID is set, optionally check audience
        if (GOOGLE_CLIENT_ID && data.aud !== GOOGLE_CLIENT_ID) {
            console.warn(`Token audience mismatch: got ${data.aud}, expected ${GOOGLE_CLIENT_ID}`);
        }

        return {
            googleId: data.sub,
            email: data.email,
            displayName: data.name || data.email.split('@')[0],
            avatar: data.picture || '🤠'
        };
    } catch (err) {
        throw new Error(`Google authentication failed: ${err.message}`);
    }
}

// --- Middleware ---
function requireAuth(req, res, next) {
    const authHeader = req.headers['authorization'];
    let token = null;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.substring(7).trim();
    } else if (req.query && req.query.token) {
        token = req.query.token;
    }

    if (!token) {
        return res.status(401).json({ error: 'Authentication required. Please log in.' });
    }

    const payload = verifyToken(token);
    if (!payload) {
        return res.status(401).json({ error: 'Invalid or expired session. Please log in again.' });
    }

    const user = db.getUserById(payload.sub);
    if (!user) {
        return res.status(401).json({ error: 'User no longer exists.' });
    }

    req.user = user;
    next();
}

function optionalAuth(req, res, next) {
    const authHeader = req.headers['authorization'];
    let token = null;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.substring(7).trim();
    } else if (req.query && req.query.token) {
        token = req.query.token;
    }

    if (token) {
        const payload = verifyToken(token);
        if (payload) {
            req.user = db.getUserById(payload.sub);
        }
    }
    next();
}

module.exports = {
    hashPassword,
    verifyPassword,
    generateToken,
    verifyToken,
    verifyGoogleToken,
    requireAuth,
    optionalAuth,
    GOOGLE_CLIENT_ID
};
