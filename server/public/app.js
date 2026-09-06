// ============================================================
// Agrish Web Hub v2.0  —  Frontend Controller
// Online Multiplayer  |  Private Cards  |  Guest Play
// ============================================================
'use strict';

// ─── Backend URL Configuration ───────────────────────────────
// Connects frontend (Plesk www.agrawali.com.et) to live Render backend
const SERVER_URL = (window.AGRISH_SERVER_URL || 'https://agrish-games2.onrender.com').replace(/\/$/, '');
function apiUrl(path) {
    if (!path.startsWith('/')) path = '/' + path;
    return `${SERVER_URL}${path}`;
}

// ─── State ───────────────────────────────────────────────────
let currentUser  = null;
let authToken    = localStorage.getItem('agrish_token') || null;
let currentView  = 'LOBBY'; // LOBBY | POKER | BANK
let ws           = null;
let wsReady      = false;
let myPlayerId   = null;   // either user.id or guest guestId
let myPlayerName = null;
let isGuest      = false;
let googleClientId = '';

// Turn timer
let turnTimerInterval = null;

// ─── Audio ───────────────────────────────────────────────────
const audioCtx = (typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined')
    ? new (window.AudioContext || window.webkitAudioContext)() : null;

function playSound(type) {
    if (!audioCtx) return;
    if (currentUser && currentUser.sound_enabled === 0) return;
    try {
        if (audioCtx.state === 'suspended') audioCtx.resume();
        const osc  = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.connect(gain); gain.connect(audioCtx.destination);
        const now = audioCtx.currentTime;
        if (type === 'chip') {
            osc.frequency.setValueAtTime(600, now);
            osc.frequency.exponentialRampToValueAtTime(800, now + 0.08);
            gain.gain.setValueAtTime(0.3, now);
            gain.gain.exponentialRampToValueAtTime(0.01, now + 0.08);
            osc.start(now); osc.stop(now + 0.08);
        } else if (type === 'win') {
            [400, 600, 800].forEach((f, i) => osc.frequency.setValueAtTime(f, now + i * 0.1));
            gain.gain.setValueAtTime(0.3, now);
            gain.gain.exponentialRampToValueAtTime(0.01, now + 0.4);
            osc.start(now); osc.stop(now + 0.4);
        } else if (type === 'dice') {
            osc.type = 'triangle';
            osc.frequency.setValueAtTime(250, now);
            osc.frequency.exponentialRampToValueAtTime(120, now + 0.15);
            gain.gain.setValueAtTime(0.2, now);
            gain.gain.exponentialRampToValueAtTime(0.01, now + 0.15);
            osc.start(now); osc.stop(now + 0.15);
        }
    } catch (e) {}
}

// ─── Initialization ───────────────────────────────────────────
window.addEventListener('DOMContentLoaded', async () => {
    // 1. Load server config
    try {
        const cfg = await (await fetch(apiUrl('/api/config'))).json();
        googleClientId = cfg.googleClientId || '';
        initGoogleGIS(googleClientId);
    } catch (e) { console.warn('Config load failed:', e); }

    // 2. Check saved session
    if (authToken) await verifyCurrentUser();
    else renderNavUser();

    // 3. Connect WebSocket
    connectWebSocket();

    // 4. Load leaderboard in lobby
    loadLeaderboard();
});

// ─── Google Identity Services ─────────────────────────────────
function initGoogleGIS(clientId) {
    if (!window.google || !clientId) return;
    try {
        window.google.accounts.id.initialize({ client_id: clientId, callback: handleGoogleCredentialResponse });
        const el = document.getElementById('google-btn-container');
        if (el) window.google.accounts.id.renderButton(el, { theme: 'filled_blue', size: 'large', width: 320 });
    } catch (e) { console.error('GIS init error:', e); }
}

async function handleGoogleCredentialResponse(response) {
    try {
        const data = await postJSON('/api/auth/google', { idToken: response.credential });
        setSession(data.token, data.user);
        closeAuthModal();
        showToast(`Welcome, ${data.user.display_name}! 🎉`, 'success');
    } catch (e) { showToast(e.message, 'error'); }
}

async function demoGoogleLogin() {
    const defaultEmail = `player_${Math.floor(Math.random() * 9000 + 1000)}@gmail.com`;
    const email = prompt('Enter your Google Email for Instant Sign-In:', defaultEmail);
    if (!email) return;
    try {
        const data = await postJSON('/api/auth/google', { idToken: `demo-google-token:${email}:${email.split('@')[0]}` });
        setSession(data.token, data.user);
        closeAuthModal();
        showToast(`Signed in as ${data.user.display_name}!`, 'success');
    } catch (e) { showToast(e.message, 'error'); }
}

// ─── Auth State ───────────────────────────────────────────────
async function verifyCurrentUser() {
    try {
        const res = await fetch(apiUrl('/api/auth/me'), { headers: { Authorization: `Bearer ${authToken}` } });
        if (res.ok) {
            currentUser = (await res.json()).user;
            myPlayerId   = currentUser.id;
            myPlayerName = currentUser.display_name;
            isGuest      = false;
        } else { clearSession(); }
    } catch (e) { console.warn('Auth check failed:', e); }
    renderNavUser();
}

function setSession(token, user) {
    authToken    = token;
    currentUser  = user;
    myPlayerId   = user.id;
    myPlayerName = user.display_name;
    isGuest      = false;
    localStorage.setItem('agrish_token', token);
    localStorage.setItem('agrish_user', JSON.stringify(user));
    renderNavUser();
    // Re-authenticate the existing WS connection
    if (ws && ws.readyState === WebSocket.OPEN) {
        wsSend({ type: 'AUTH', token });
    }
}

function clearSession() {
    authToken = null; currentUser = null;
    myPlayerId = null; myPlayerName = null; isGuest = false;
    localStorage.removeItem('agrish_token');
    localStorage.removeItem('agrish_user');
    renderNavUser();
    showToast('Signed out.', 'success');
    showView('LOBBY');
}

function renderNavUser() {
    const area = document.getElementById('nav-user-area');
    if (currentUser) {
        area.innerHTML = `
            <div class="user-pill" onclick="openProfileModal()">
                <span class="user-avatar-small">${currentUser.avatar || '🤠'}</span>
                <span class="user-name-small">${currentUser.display_name}</span>
                <span class="user-chips-small">💰 $${currentUser.chips || 0}</span>
            </div>
            <button class="btn btn-outline btn-sm" onclick="clearSession()">Logout</button>
        `;
        // Hide guest banner if logged in
        const gb = document.getElementById('guest-banner');
        if (gb) gb.style.display = 'none';
    } else if (isGuest) {
        area.innerHTML = `
            <div class="user-pill guest-pill">
                <span class="user-avatar-small">🎭</span>
                <span class="user-name-small">${myPlayerName || 'Guest'}</span>
                <span class="guest-chip-badge">GUEST</span>
            </div>
            <button class="btn btn-gold btn-sm" onclick="openAuthModal('register')">Save Progress</button>
        `;
    } else {
        area.innerHTML = `
            <button class="btn btn-primary btn-sm" onclick="openAuthModal('login')">Sign In</button>
            <button class="btn btn-gold btn-sm" onclick="openAuthModal('register')">Create Account</button>
        `;
    }
}

// ─── Guest Join ───────────────────────────────────────────────
function joinAsGuest() {
    const nameInput = document.getElementById('guest-name-input');
    const name = (nameInput?.value || '').trim() || `Guest${Math.floor(Math.random() * 9000 + 1000)}`;
    isGuest      = true;
    myPlayerName = name;
    renderNavUser();
    // WS guest handshake will happen when they join a game
    showToast(`Playing as guest: ${name}`, 'success');
}

// ─── WebSocket ────────────────────────────────────────────────
function connectWebSocket() {
    let wsHost = location.host;
    let wsProto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    if (SERVER_URL) {
        try {
            const u = new URL(SERVER_URL);
            wsHost = u.host;
            wsProto = u.protocol === 'https:' ? 'wss:' : 'ws:';
        } catch (e) {}
    }
    ws = new WebSocket(`${wsProto}//${wsHost}`);

    ws.onopen = () => {
        wsReady = true;
        // Authenticate or identify
        if (authToken) {
            wsSend({ type: 'AUTH', token: authToken });
        }
        // Re-request lobby state
        wsSend({ type: 'GET_LOBBY' });
    };

    ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            handleServerMessage(msg);
        } catch (e) {}
    };

    ws.onclose = () => {
        wsReady = false;
        setTimeout(connectWebSocket, 3000);
    };

    ws.onerror = () => { /* errors handled in onclose */ };
}

function wsSend(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(obj));
    }
}

// ─── Handle Incoming Messages ────────────────────────────────
function handleServerMessage(msg) {
    switch (msg.type) {

        case 'AUTH_OK':
            currentUser  = msg.user;
            myPlayerId   = msg.user.id;
            myPlayerName = msg.user.display_name;
            isGuest      = false;
            renderNavUser();
            break;

        case 'GUEST_OK':
            myPlayerId   = msg.guestId;
            myPlayerName = msg.guestName;
            isGuest      = true;
            renderNavUser();
            break;

        case 'LOBBY_STATE':
            renderLobby(msg.tables);
            break;

        case 'POKER_STATE':
            if (currentView !== 'POKER') showView('POKER');
            renderPokerTable(msg.state);
            break;

        case 'BANK_STATE':
            if (currentView !== 'BANK') showView('BANK');
            renderBankDashboard(msg.state);
            break;

        case 'ERROR':
            showToast('⚠ ' + msg.message, 'error');
            break;
    }
}

// ─── View Management ──────────────────────────────────────────
function showView(view) {
    currentView = view;
    document.getElementById('lobby-view').classList.toggle('hide', view !== 'LOBBY');
    document.getElementById('poker-view').classList.toggle('hide', view !== 'POKER');
    document.getElementById('bank-view').classList.toggle('hide', view !== 'BANK');

    const navCenter = document.getElementById('nav-center-area');
    if (view === 'POKER') {
        navCenter.innerHTML = `
            <button class="btn btn-secondary btn-sm" onclick="leavePoker()">← Back to Lobby</button>
            <span class="nav-table-label" id="nav-table-label"></span>
        `;
    } else if (view === 'BANK') {
        navCenter.innerHTML = `
            <button class="btn btn-secondary btn-sm" onclick="leaveBank()">← Back to Lobby</button>
        `;
    } else {
        navCenter.innerHTML = '';
        wsSend({ type: 'GET_LOBBY' });
        loadLeaderboard();
    }
}

// ─── Lobby ────────────────────────────────────────────────────
function renderLobby(tables) {
    const el = document.getElementById('poker-tables-list');
    if (!el) return;
    if (!tables || tables.length === 0) {
        el.innerHTML = '<div class="tables-loading">No tables yet.</div>';
        return;
    }
    el.innerHTML = tables.map(t => `
        <div class="table-row" onclick="joinPokerTable('${t.id}')">
            <span class="table-name">${t.name}</span>
            <span class="table-meta">
                <span class="table-stage stage-${t.stage.toLowerCase()}">${t.stage}</span>
                <span class="table-players-count">👥 ${t.playerCount}/${t.maxPlayers}</span>
                ${t.pot > 0 ? `<span class="table-pot">💰 $${t.pot}</span>` : ''}
            </span>
        </div>
    `).join('');
}

async function loadLeaderboard() {
    try {
        const data = await (await fetch(apiUrl('/api/leaderboard?limit=8'))).json();
        const el = document.getElementById('leaderboard-list');
        if (!el) return;
        if (!data.leaderboard || data.leaderboard.length === 0) {
            el.innerHTML = '<div class="tables-loading">No players yet.</div>';
            return;
        }
        el.innerHTML = data.leaderboard.map((u, i) => `
            <div class="lb-row">
                <span class="lb-rank">${['🥇','🥈','🥉'][i] || `#${i+1}`}</span>
                <span class="lb-avatar">${u.avatar || '🤠'}</span>
                <span class="lb-name">${u.display_name}</span>
                <span class="lb-total">$${(u.chips + u.bank_balance).toLocaleString()}</span>
            </div>
        `).join('');
    } catch (e) {}
}

// ─── Join Games ───────────────────────────────────────────────
function ensureIdentified(callback) {
    if (myPlayerId) { callback(); return; }

    // Not identified yet — show quick guest choice
    if (confirm('Join as Guest? (Click Cancel to Sign In instead)')) {
        const name = prompt('Enter a guest name (optional):') || '';
        isGuest      = true;
        myPlayerName = name.trim() || `Guest${Math.floor(Math.random() * 9000 + 1000)}`;
        myPlayerId   = null; // will be set by GUEST_OK

        // If WS is open, send GUEST handshake now
        if (wsReady) {
            wsSend({ type: 'GUEST', name: myPlayerName, avatar: '🎭' });
            // Wait for GUEST_OK before joining — call callback on next GUEST_OK
            _pendingCallback = callback;
        } else {
            showToast('Connecting… please try again in a moment.', 'error');
        }
    } else {
        openAuthModal('login');
    }
}
let _pendingCallback = null;

// Patch handleServerMessage to fire pending callback
const _origHandleMsg = handleServerMessage;
handleServerMessage = function(msg) {
    _origHandleMsg(msg);
    if (msg.type === 'GUEST_OK' && _pendingCallback) {
        const cb = _pendingCallback;
        _pendingCallback = null;
        cb();
    }
};

function joinPokerTable(tableId) {
    ensureIdentified(() => {
        // If WS is open but not yet authenticated, send auth first
        if (authToken && wsReady) {
            wsSend({ type: 'AUTH', token: authToken });
        }
        wsSend({ type: 'JOIN_TABLE', tableId });
    });
}

function joinBankGame() {
    ensureIdentified(() => {
        if (authToken && wsReady) wsSend({ type: 'AUTH', token: authToken });
        wsSend({ type: 'JOIN_BANK' });
    });
}

function leavePoker() {
    wsSend({ type: 'LEAVE_TABLE' });
    showView('LOBBY');
}

function leaveBank() {
    showView('LOBBY');
}

// ─── Poker UI Rendering ───────────────────────────────────────
let _lastPokerState = null;

function renderPokerTable(state) {
    _lastPokerState = state;

    // Table name in nav
    const lbl = document.getElementById('nav-table-label');
    if (lbl) lbl.textContent = state.tableName || '';

    // Pot & Stage
    document.getElementById('poker-pot-val').textContent  = `$${state.pot}`;
    document.getElementById('poker-stage-val').textContent = state.stage;

    // Turn timer
    updateTurnTimer(state);

    // Winner banner
    const winnerBanner = document.getElementById('poker-winner-banner');
    if (state.winner) {
        playSound('win');
        winnerBanner.classList.remove('hide');
        document.getElementById('poker-winner-text').textContent = `🏆 ${state.winnerDetails || state.winner}`;
    } else {
        winnerBanner.classList.add('hide');
    }

    // AI Coach
    document.getElementById('coach-msg').textContent = state.coachTip || '…';

    // Community cards
    const commContainer = document.getElementById('community-cards-container');
    if (state.communityCards && state.communityCards.length > 0) {
        commContainer.innerHTML = state.communityCards.map(c => renderCardHtml(c)).join('');
    } else {
        commContainer.innerHTML = '<div class="card-placeholder">Waiting for flop…</div>';
    }

    // Players roster
    const playersList = document.getElementById('poker-players-list');
    if (state.players) {
        playersList.innerHTML = state.players.map((p, idx) => {
            const isTurn    = idx === state.activePlayerIndex && state.stage !== 'WAITING' && state.stage !== 'FINISHED';
            const foldClass = p.hasFolded ? 'folded' : '';
            const turnClass = isTurn ? 'active-turn' : '';
            const meClass   = p.isMe ? 'is-me' : '';
            const disconnClass = p.isConnected === false ? 'disconnected' : '';

            let badges = '';
            if (p.isDealer)    badges += '<span class="badge badge-dealer">D</span>';
            if (p.isSmallBlind) badges += '<span class="badge badge-sb">SB</span>';
            if (p.isBigBlind)   badges += '<span class="badge badge-bb">BB</span>';
            if (p.isAllIn)      badges += '<span class="badge badge-allin">ALL-IN</span>';

            // Cards: hidden backs for opponents, real cards for self / showdown
            let cardsHtml = '';
            if (p.cards && p.cards.length > 0) {
                cardsHtml = `<div class="seat-cards">${p.cards.map(c => c.hidden ? renderHiddenCard() : renderCardHtml(c, true)).join('')}</div>`;
            }

            const handInfo = p.handEval ? `<div class="seat-hand-eval">${p.handEval.rankName}</div>` : '';

            return `
                <div class="player-seat ${turnClass} ${foldClass} ${meClass} ${disconnClass}">
                    <div class="seat-avatar">${p.avatar || '🤠'}</div>
                    <div class="seat-info">
                        <div class="seat-name">${p.name}${p.isMe ? ' (You)' : ''}</div>
                        <div class="seat-chips">$${p.chips} <span class="bet-label">bet:$${p.currentBet}</span></div>
                        <div class="seat-badges">${badges}</div>
                        ${handInfo}
                    </div>
                    ${cardsHtml}
                </div>
            `;
        }).join('');
    }

    // My hole cards
    const holeContainer = document.getElementById('my-hole-cards-container');
    if (state.myCards && state.myCards.length > 0) {
        holeContainer.innerHTML = state.myCards.map(c => renderCardHtml(c)).join('');
    } else {
        holeContainer.innerHTML = '<div class="card-placeholder">No cards dealt</div>';
    }

    // Action controls — enabled only on my turn
    const controls = document.getElementById('poker-controls');
    const notMyTurnMsg = document.getElementById('not-your-turn-msg');
    const me = state.players?.find(p => p.isMe);
    const myTurn = state.isMyTurn && state.stage !== 'WAITING' && state.stage !== 'FINISHED' && state.stage !== 'SHOWDOWN';
    const isFolded = me?.hasFolded || false;

    if (myTurn && !isFolded) {
        controls.classList.remove('hide', 'disabled-controls');
        notMyTurnMsg.classList.add('hide');

        // Adjust call button label
        const callBtn = document.getElementById('call-btn');
        if (callBtn) {
            const toCall = Math.max(0, state.currentBet - (me?.currentBet || 0));
            callBtn.textContent = toCall === 0 ? 'CHECK' : `CALL $${toCall}`;
        }
    } else if (isFolded || state.stage === 'WAITING' || state.stage === 'FINISHED') {
        controls.classList.add('hide');
        notMyTurnMsg.classList.add('hide');
    } else {
        controls.classList.add('disabled-controls');
        notMyTurnMsg.classList.remove('hide');
    }

    // Logs
    if (state.logs) {
        const logList = document.getElementById('game-event-logs');
        logList.innerHTML = state.logs.map(l => `<li>${l}</li>`).join('');
    }
}

function updateTurnTimer(state) {
    if (turnTimerInterval) { clearInterval(turnTimerInterval); turnTimerInterval = null; }
    const badge = document.getElementById('turn-timer-badge');
    const val   = document.getElementById('turn-timer-val');
    if (!state.turnDeadline || !state.isMyTurn) {
        badge.classList.remove('timer-urgent');
        val.textContent = '—';
        return;
    }
    function tick() {
        const secs = Math.max(0, Math.ceil((state.turnDeadline - Date.now()) / 1000));
        val.textContent = `${secs}s`;
        badge.classList.toggle('timer-urgent', secs <= 10);
        if (secs === 0) { clearInterval(turnTimerInterval); turnTimerInterval = null; }
    }
    tick();
    turnTimerInterval = setInterval(tick, 500);
}

function renderCardHtml(c, small = false) {
    const isRed = c.colorRed || c.suit === 'HEARTS' || c.suit === 'DIAMONDS';
    const sym   = c.symbol || (c.suit === 'HEARTS' ? '♥' : c.suit === 'DIAMONDS' ? '♦' : c.suit === 'CLUBS' ? '♣' : '♠');
    const rank  = c.rank || c.value;
    const sz    = small ? ' card-sm' : '';
    return `
        <div class="playing-card ${isRed ? 'suit-red' : 'suit-black'}${sz}">
            <div class="card-top">${rank}</div>
            <div class="card-center-suit">${sym}</div>
            <div class="card-bottom">${rank}</div>
        </div>
    `;
}

function renderHiddenCard(small = false) {
    const sz = small ? ' card-sm' : '';
    return `<div class="playing-card card-hidden${sz}">🂠</div>`;
}

// ─── Bank UI Rendering ────────────────────────────────────────
function renderBankDashboard(state) {
    document.getElementById('bank-vault-val').textContent = `$${(state.bankAmount || 0).toLocaleString()}`;
    document.getElementById('bank-round-num').textContent = state.bankRound || 1;

    const leaderEl = document.getElementById('bank-leader-ticker');
    if (leaderEl) {
        leaderEl.textContent = state.leaderBalance >= 0
            ? `👑 Realtime Leader: ${state.leader} ($${state.leaderBalance})`
            : '👑 No players yet';
    }

    const ledgerList = document.getElementById('bank-players-list');
    if (ledgerList && state.bankPlayers) {
        ledgerList.innerHTML = state.bankPlayers.map(p => `
            <div class="ledger-item">
                <span><strong>${p.name}</strong>${p.isBanked ? ' <span class="badge badge-allin">BANKED</span>' : ''}</span>
                <span style="color:var(--gold);font-weight:bold;">$${p.balance}</span>
            </div>
        `).join('') || '<div class="tables-loading">No players yet.</div>';
    }

    const stream = document.getElementById('bank-log-stream');
    if (stream && state.bankRolls) {
        stream.innerHTML = state.bankRolls.length > 0
            ? state.bankRolls.map(r => `
                <div class="log-item ${r.isBust ? 'bust' : ''}">
                    <strong>${r.player}</strong>: ${r.resultText}
                </div>
              `).join('')
            : '<div class="card-placeholder">No rolls yet.</div>';
    }
}

// ─── Actions ──────────────────────────────────────────────────
function pokerAction(action) {
    playSound('chip');
    wsSend({ type: 'POKER_ACTION', action });
}

function pokerRaise() {
    const amt = parseInt(document.getElementById('raise-input').value, 10) || 40;
    playSound('chip');
    wsSend({ type: 'POKER_ACTION', action: 'raise', amount: amt });
}

function bankAction(action, amount = 0) {
    playSound(action === 'roll' ? 'dice' : 'chip');
    wsSend({ type: 'BANK_ACTION', action, amount });
}

// ─── Auth Modals ──────────────────────────────────────────────
function openAuthModal(tab = 'login') {
    document.getElementById('auth-modal').classList.remove('hide');
    switchAuthTab(tab);
}
function closeAuthModal() { document.getElementById('auth-modal').classList.add('hide'); }

function switchAuthTab(tab) {
    document.getElementById('login-form').classList.toggle('hide', tab !== 'login');
    document.getElementById('register-form').classList.toggle('hide', tab !== 'register');
    document.getElementById('tab-login').classList.toggle('active', tab === 'login');
    document.getElementById('tab-register').classList.toggle('active', tab === 'register');
}

async function handleLoginSubmit(e) {
    e.preventDefault();
    try {
        const data = await postJSON('/api/auth/login', {
            emailOrUsername: document.getElementById('login-username').value,
            password: document.getElementById('login-password').value
        });
        setSession(data.token, data.user);
        closeAuthModal();
        showToast(`Welcome back, ${data.user.display_name}! 👋`, 'success');
    } catch (e) { showToast(e.message, 'error'); }
}

async function handleRegisterSubmit(e) {
    e.preventDefault();
    try {
        const data = await postJSON('/api/auth/register', {
            username:     document.getElementById('reg-username').value,
            email:        document.getElementById('reg-email').value,
            password:     document.getElementById('reg-password').value,
            display_name: document.getElementById('reg-displayname').value,
            avatar:       document.getElementById('reg-avatar').value,
            phone:        document.getElementById('reg-phone').value
        });
        setSession(data.token, data.user);
        closeAuthModal();
        showToast('Account created! Welcome to Agrish Hub 🎉', 'success');
    } catch (e) { showToast(e.message, 'error'); }
}

// ─── Profile Modal ────────────────────────────────────────────
async function openProfileModal() {
    if (!currentUser || !authToken) { openAuthModal('login'); return; }
    try {
        const res = await fetch(apiUrl('/api/user/profile'), { headers: { Authorization: `Bearer ${authToken}` } });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error);
        const u = data.user; currentUser = u;

        document.getElementById('prof-avatar-display').textContent = u.avatar || '🤠';
        document.getElementById('prof-name-display').textContent   = u.display_name;
        document.getElementById('prof-email-display').textContent  = u.email;
        document.getElementById('stat-chips').textContent  = `$${u.chips || 0}`;
        document.getElementById('stat-vault').textContent  = `$${u.bank_balance || 0}`;
        document.getElementById('stat-games').textContent  = u.games_played || 0;
        document.getElementById('stat-wins').textContent   = u.games_won || 0;
        document.getElementById('prof-displayname').value  = u.display_name || '';
        document.getElementById('prof-avatar').value       = u.avatar || '🤠';
        document.getElementById('prof-phone').value        = u.phone || '';
        document.getElementById('prof-pregame').value      = u.preferred_game || 'POKER';
        document.getElementById('prof-bio').value          = u.bio || '';
        document.getElementById('prof-sound').checked      = u.sound_enabled !== 0;

        const tbody = document.getElementById('prof-logs-body');
        tbody.innerHTML = (data.logs && data.logs.length > 0)
            ? data.logs.map(l => `
                <tr>
                    <td>${new Date(l.timestamp).toLocaleTimeString()}</td>
                    <td><span class="badge badge-sb">${l.game_type}</span></td>
                    <td><strong>${l.action}</strong></td>
                    <td>$${l.amount}</td>
                    <td>${l.details || '—'}</td>
                </tr>`).join('')
            : '<tr><td colspan="5" style="text-align:center;">No activity yet</td></tr>';

        document.getElementById('profile-modal').classList.remove('hide');
    } catch (e) { showToast('Error loading profile: ' + e.message, 'error'); }
}

function closeProfileModal() { document.getElementById('profile-modal').classList.add('hide'); }

async function handleProfileUpdate(e) {
    e.preventDefault();
    if (!authToken) return;
    try {
        const data = await putJSON('/api/user/profile', {
            display_name:   document.getElementById('prof-displayname').value,
            avatar:         document.getElementById('prof-avatar').value,
            phone:          document.getElementById('prof-phone').value,
            preferred_game: document.getElementById('prof-pregame').value,
            bio:            document.getElementById('prof-bio').value,
            sound_enabled:  document.getElementById('prof-sound').checked ? 1 : 0
        });
        currentUser = data.user;
        myPlayerName = data.user.display_name;
        renderNavUser();
        closeProfileModal();
        showToast('✅ Profile saved!', 'success');
    } catch (e) { showToast(e.message, 'error'); }
}

// ─── Misc Helpers ─────────────────────────────────────────────
function toggleLogs() {
    const body = document.getElementById('drawer-body');
    const icon = document.getElementById('drawer-toggle-icon');
    const open = body.classList.toggle('hide');
    icon.textContent = open ? '▼' : '▲';
}

function showToast(msg, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = msg;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4500);
}

async function postJSON(url, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    const res = await fetch(apiUrl(url), { method: 'POST', headers, body: JSON.stringify(body) });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
}

async function putJSON(url, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    const res = await fetch(apiUrl(url), { method: 'PUT', headers, body: JSON.stringify(body) });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
}
