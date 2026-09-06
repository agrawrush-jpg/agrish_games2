// ============================================================
// Server — Agrish Web Hub  |  Full Online Multiplayer Edition
// Express + WebSocket + SQLite + JWT + Google OAuth
// ============================================================

'use strict';

require('dotenv').config();
const http    = require('node:http');
const path    = require('node:path');
const crypto  = require('node:crypto');
const express = require('express');
const cors    = require('cors');
const { WebSocketServer } = require('ws');

const db   = require('./db');
const auth = require('./auth');
const { pokerManager, bankRoom } = require('./gameEngine');

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });
const PORT   = process.env.PORT || 8080;

// ── Middleware ────────────────────────────────────────────────
app.use(cors({
    origin: true, // Reflects request origin (supports https://www.agrawali.com.et, https://agrawali.com.et, localhost, etc.)
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// ─────────────────────────────────────────────────────────────
// WebSocket Connection Map
// ws → { playerId, playerName, playerAvatar, tableId, isGuest }
// ─────────────────────────────────────────────────────────────
const wsClients = new Map(); // ws → clientInfo

// Register state-change callbacks on all tables so we can push updates
function hookTable(table) {
    table.onStateChange = (tableId) => broadcastTableState(tableId);
}
// Hook existing tables
for (const table of pokerManager.tables.values()) hookTable(table);

// Hook new tables as they're created
const _origCreate = pokerManager.createTable.bind(pokerManager);
pokerManager.createTable = function (...args) {
    const t = _origCreate(...args);
    hookTable(t);
    return t;
};

// Push private state to every client at a given table
function broadcastTableState(tableId) {
    const table = pokerManager.getTable(tableId);
    if (!table) return;

    for (const [ws, info] of wsClients) {
        if (ws.readyState !== 1) continue;
        if (info.tableId !== tableId) continue;

        const state = table.getState(info.playerId);
        ws.send(JSON.stringify({ type: 'POKER_STATE', state }));
    }
}

// Push bank state to all clients in bank mode
function broadcastBankState() {
    for (const [ws, info] of wsClients) {
        if (ws.readyState !== 1) continue;
        if (info.gameMode !== 'BANK') continue;
        ws.send(JSON.stringify({ type: 'BANK_STATE', state: bankRoom.getState(info.playerId) }));
    }
}

// Send table list to all lobby clients
function broadcastLobby() {
    const tables = pokerManager.listTables();
    for (const [ws, info] of wsClients) {
        if (ws.readyState !== 1) continue;
        if (info.gameMode === 'LOBBY') {
            ws.send(JSON.stringify({ type: 'LOBBY_STATE', tables }));
        }
    }
}

// ─────────────────────────────────────────────────────────────
// WebSocket Connection Handling
// ─────────────────────────────────────────────────────────────
wss.on('connection', (ws) => {
    // Default unauthenticated state
    wsClients.set(ws, {
        playerId: null, playerName: null, playerAvatar: '🎭',
        tableId: null, gameMode: 'LOBBY', isGuest: false
    });

    // Send initial lobby
    ws.send(JSON.stringify({ type: 'LOBBY_STATE', tables: pokerManager.listTables() }));

    ws.on('message', (raw) => {
        let data;
        try { data = JSON.parse(raw.toString()); } catch { return; }

        const info = wsClients.get(ws);
        if (!info) return;

        switch (data.type) {

            // ── Authenticate (registered user) ────────────────
            case 'AUTH': {
                const payload = auth.verifyToken(data.token);
                if (!payload) { ws.send(JSON.stringify({ type: 'ERROR', message: 'Invalid token' })); return; }
                const user = db.getUserById(payload.sub);
                if (!user) { ws.send(JSON.stringify({ type: 'ERROR', message: 'User not found' })); return; }

                Object.assign(info, {
                    playerId: user.id,
                    playerName: user.display_name,
                    playerAvatar: user.avatar || '🤠',
                    isGuest: false
                });
                ws.send(JSON.stringify({ type: 'AUTH_OK', user }));
                break;
            }

            // ── Guest session ─────────────────────────────────
            case 'GUEST': {
                const guestName = (data.name || '').trim().substring(0, 20) || `Guest${Math.floor(Math.random() * 9000 + 1000)}`;
                const guestId   = 'guest_' + crypto.randomBytes(6).toString('hex');
                Object.assign(info, {
                    playerId: guestId,
                    playerName: guestName,
                    playerAvatar: data.avatar || '🎭',
                    isGuest: true
                });
                ws.send(JSON.stringify({ type: 'GUEST_OK', guestId, guestName }));
                break;
            }

            // ── Join a Poker Table ────────────────────────────
            case 'JOIN_TABLE': {
                if (!info.playerId) { ws.send(JSON.stringify({ type: 'ERROR', message: 'Identify first (AUTH or GUEST)' })); return; }

                // Leave any existing table
                if (info.tableId) {
                    const oldTable = pokerManager.getTable(info.tableId);
                    if (oldTable) oldTable.removePlayer(info.playerId);
                }

                let table = data.tableId ? pokerManager.getTable(data.tableId) : null;
                if (!table) table = pokerManager.getOrCreateOpenTable();
                if (!table) { ws.send(JSON.stringify({ type: 'ERROR', message: 'No available tables' })); return; }

                // Determine starting chips
                let startChips = 1000;
                if (!info.isGuest) {
                    const user = db.getUserById(info.playerId);
                    if (user) startChips = user.chips || 1000;
                }

                const seat = table.addPlayer(info.playerId, info.playerName, info.playerAvatar, startChips);
                if (!seat) { ws.send(JSON.stringify({ type: 'ERROR', message: 'Table is full' })); return; }

                info.tableId = table.id;
                info.gameMode = 'POKER';

                // Send immediate private state to joining player
                ws.send(JSON.stringify({ type: 'POKER_STATE', state: table.getState(info.playerId) }));
                broadcastLobby();
                break;
            }

            // ── Leave Poker Table ─────────────────────────────
            case 'LEAVE_TABLE': {
                if (info.tableId) {
                    const table = pokerManager.getTable(info.tableId);
                    if (table) {
                        // Persist chips to DB for registered players
                        if (!info.isGuest) {
                            const seat = table.players.find(p => p.id === info.playerId);
                            if (seat) _persistChips(info.playerId, seat.chips);
                        }
                        table.removePlayer(info.playerId);
                    }
                    info.tableId = null;
                }
                info.gameMode = 'LOBBY';
                ws.send(JSON.stringify({ type: 'LOBBY_STATE', tables: pokerManager.listTables() }));
                broadcastLobby();
                break;
            }

            // ── Join Bank Game ────────────────────────────────
            case 'JOIN_BANK': {
                if (!info.playerId) { ws.send(JSON.stringify({ type: 'ERROR', message: 'Identify first' })); return; }

                // Leave poker table if any
                if (info.tableId) {
                    const oldTable = pokerManager.getTable(info.tableId);
                    if (oldTable) {
                        if (!info.isGuest) {
                            const seat = oldTable.players.find(p => p.id === info.playerId);
                            if (seat) _persistChips(info.playerId, seat.chips);
                        }
                        oldTable.removePlayer(info.playerId);
                        info.tableId = null;
                    }
                }

                let bankBalance = 1000;
                if (!info.isGuest) {
                    const user = db.getUserById(info.playerId);
                    if (user) bankBalance = user.bank_balance || 1000;
                }

                bankRoom.addPlayer(info.playerId, info.playerName, bankBalance);
                info.gameMode = 'BANK';

                ws.send(JSON.stringify({ type: 'BANK_STATE', state: bankRoom.getState(info.playerId) }));
                break;
            }

            // ── Poker Action ──────────────────────────────────
            case 'POKER_ACTION': {
                if (!info.tableId) { ws.send(JSON.stringify({ type: 'ERROR', message: 'Not at a table' })); return; }
                const table = pokerManager.getTable(info.tableId);
                if (!table) return;

                const result = table.handleAction(info.playerId, data.action, data.amount || 0);
                if (!result.ok) {
                    ws.send(JSON.stringify({ type: 'ERROR', message: result.error }));
                    return;
                }

                // Log to DB for registered users on meaningful actions
                if (!info.isGuest && ['call', 'raise', 'fold'].includes(data.action)) {
                    db.addGameLog({
                        userId: info.playerId,
                        gameType: 'POKER',
                        action: data.action,
                        amount: data.amount || 0,
                        balanceAfter: table.players.find(p => p.id === info.playerId)?.chips || 0,
                        details: `Poker: ${data.action} at table ${info.tableId}`
                    });
                }

                // Persist chips when round finishes
                if (table.stage === 'FINISHED' && !info.isGuest) {
                    const seat = table.players.find(p => p.id === info.playerId);
                    if (seat) _persistChips(info.playerId, seat.chips);
                }
                break; // broadcastTableState already called by table.notify()
            }

            // ── Bank Action ───────────────────────────────────
            case 'BANK_ACTION': {
                bankRoom.handleAction(data.action, {
                    name: info.playerName,
                    amount: data.amount || 0,
                    userId: info.playerId
                });

                // Log & persist bank balance
                if (!info.isGuest) {
                    const bp = bankRoom.bankPlayers.find(p => p.id === info.playerId);
                    db.addGameLog({
                        userId: info.playerId,
                        gameType: 'BANK',
                        action: data.action,
                        amount: data.amount || 0,
                        balanceAfter: bp ? bp.balance : 0,
                        details: `Bank: ${data.action}`
                    });
                    const bp2 = bankRoom.bankPlayers.find(p => p.id === info.playerId);
                    if (bp2) _persistBankBalance(info.playerId, bp2.balance);
                }

                broadcastBankState();
                break;
            }

            // ── Request Lobby Refresh ─────────────────────────
            case 'GET_LOBBY': {
                info.gameMode = 'LOBBY';
                ws.send(JSON.stringify({ type: 'LOBBY_STATE', tables: pokerManager.listTables() }));
                break;
            }
        }
    });

    ws.on('close', () => {
        const info = wsClients.get(ws);
        if (info && info.tableId) {
            const table = pokerManager.getTable(info.tableId);
            if (table) {
                if (!info.isGuest) {
                    const seat = table.players.find(p => p.id === info.playerId);
                    if (seat) _persistChips(info.playerId, seat.chips);
                }
                table.setConnected(info.playerId, false);
            }
        }
        wsClients.delete(ws);
    });
});

// ─── DB Persistence Helpers ───────────────────────────────────
function _persistChips(userId, chips) {
    try {
        const user = db.getUserById(userId);
        if (!user) return;
        db.updateUserBalances(userId, { chipsChange: chips - user.chips });
    } catch (e) { console.error('Persist chips error:', e); }
}
function _persistBankBalance(userId, balance) {
    try {
        const user = db.getUserById(userId);
        if (!user) return;
        db.updateUserBalances(userId, { bankChange: balance - user.bank_balance });
    } catch (e) { console.error('Persist bank error:', e); }
}

// ─────────────────────────────────────────────────────────────
// REST Endpoints
// ─────────────────────────────────────────────────────────────

// Public config
app.get('/api/config', (req, res) => {
    res.json({
        appName: 'Agrish Web Gaming Hub',
        googleClientId: process.env.GOOGLE_CLIENT_ID || '',
        version: '2.0.0'
    });
});

app.get('/api/health', (req, res) => res.json({ status: 'ok', timestamp: Date.now() }));

// ── Lobby & Tables ────────────────────────────────────────────
app.get('/api/poker/tables', (req, res) => {
    res.json({ tables: pokerManager.listTables() });
});

// ── Auth Endpoints ────────────────────────────────────────────
app.post('/api/auth/register', (req, res) => {
    try {
        const { username, email, password, display_name, avatar, phone } = req.body;
        if (!username?.trim()) return res.status(400).json({ error: 'Username is required' });
        if (!email?.includes('@')) return res.status(400).json({ error: 'Valid email required' });
        if (!password || password.length < 6) return res.status(400).json({ error: 'Password must be ≥6 characters' });
        if (db.getUserByUsername(username.trim())) return res.status(409).json({ error: 'Username already taken' });
        if (db.getUserByEmail(email.trim())) return res.status(409).json({ error: 'Email already registered' });

        const id = 'usr_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
        const user = db.createUser({
            id, username: username.trim(), email: email.trim(),
            password_hash: auth.hashPassword(password),
            display_name: (display_name?.trim() || username.trim()),
            avatar: avatar || '🤠', phone: phone?.trim() || '',
            chips: 1000, bank_balance: 1000
        });
        const token = auth.generateToken(user);
        res.status(201).json({ message: 'Account created!', token, user });
    } catch (err) {
        console.error('Register error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/auth/login', (req, res) => {
    try {
        const { emailOrUsername, password } = req.body;
        if (!emailOrUsername || !password) return res.status(400).json({ error: 'Credentials required' });

        let user = db.getUserByEmail(emailOrUsername.trim()) || db.getUserByUsername(emailOrUsername.trim());
        if (!user || !user.password_hash) return res.status(401).json({ error: 'Invalid credentials' });
        if (!auth.verifyPassword(password, user.password_hash)) return res.status(401).json({ error: 'Invalid password' });

        const token = auth.generateToken(user);
        res.json({ message: 'Logged in!', token, user });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/auth/google', async (req, res) => {
    try {
        const { idToken } = req.body;
        if (!idToken) return res.status(400).json({ error: 'Google token required' });

        const { googleId, email, displayName, avatar } = await auth.verifyGoogleToken(idToken);
        let user = db.getUserByGoogleId(googleId);
        if (!user) {
            user = db.getUserByEmail(email);
            if (user) {
                user = db.linkGoogleAccount(user.id, googleId);
            } else {
                const base = email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '').toLowerCase() || 'player';
                let username = base, counter = 1;
                while (db.getUserByUsername(username)) username = `${base}${counter++}`;
                const id = 'usr_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
                user = db.createUser({ id, username, email, google_id: googleId, display_name: displayName || username, avatar: avatar || '🌐', chips: 1000, bank_balance: 1000 });
            }
        }
        res.json({ message: 'Google auth OK', token: auth.generateToken(user), user });
    } catch (err) {
        console.error('Google auth:', err);
        res.status(401).json({ error: err.message });
    }
});

app.get('/api/auth/me', auth.requireAuth, (req, res) => res.json({ user: req.user }));

// ── User Profile ──────────────────────────────────────────────
app.get('/api/user/profile', auth.requireAuth, (req, res) => {
    const user = db.getUserById(req.user.id);
    const logs = db.getUserLogs(req.user.id, 20);
    res.json({ user, logs });
});

app.put('/api/user/profile', auth.requireAuth, (req, res) => {
    try {
        const updated = db.updateUserProfile(req.user.id, req.body);
        if (!updated) return res.status(404).json({ error: 'User not found' });
        res.json({ message: 'Profile saved!', user: updated });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/user/logs', auth.requireAuth, (req, res) => {
    const logs = db.getUserLogs(req.user.id, parseInt(req.query.limit) || 25);
    res.json({ logs });
});

app.get('/api/leaderboard', (req, res) => {
    res.json({ leaderboard: db.getLeaderboard(parseInt(req.query.limit) || 10) });
});

// ── SPA Fallback ──────────────────────────────────────────────
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ─────────────────────────────────────────────────────────────
// Start
// ─────────────────────────────────────────────────────────────
server.listen(PORT, () => {
    console.log('═══════════════════════════════════════════════════════');
    console.log('🚀  Agrish Web Gaming Hub v2.0  — MULTIPLAYER ONLINE');
    console.log(`🌐  http://localhost:${PORT}`);
    console.log('💾  Database: server/data/agrish_game.db');
    console.log('═══════════════════════════════════════════════════════');
});
