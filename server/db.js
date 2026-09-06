const { DatabaseSync } = require('node:sqlite');
const path = require('node:path');
const fs = require('node:fs');

const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = path.join(dataDir, 'agrish_game.db');
const db = new DatabaseSync(dbPath);

// Initialize Tables
db.exec(`
    CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT,
        google_id TEXT UNIQUE,
        display_name TEXT NOT NULL,
        avatar TEXT DEFAULT '🤠',
        bio TEXT DEFAULT '',
        phone TEXT DEFAULT '',
        chips INTEGER DEFAULT 1000,
        bank_balance INTEGER DEFAULT 1000,
        games_played INTEGER DEFAULT 0,
        games_won INTEGER DEFAULT 0,
        total_earnings INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS user_profiles (
        user_id TEXT PRIMARY KEY,
        sound_enabled INTEGER DEFAULT 1,
        preferred_game TEXT DEFAULT 'POKER',
        custom_notes TEXT DEFAULT '',
        updated_at INTEGER NOT NULL,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS game_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT NOT NULL,
        game_type TEXT NOT NULL,
        action TEXT NOT NULL,
        amount INTEGER DEFAULT 0,
        balance_after INTEGER DEFAULT 0,
        details TEXT DEFAULT '',
        timestamp INTEGER NOT NULL,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );
`);

function createUser({
    id,
    username,
    email,
    password_hash = null,
    google_id = null,
    display_name,
    avatar = '🤠',
    bio = '',
    phone = '',
    chips = 1000,
    bank_balance = 1000
}) {
    const now = Date.now();
    const insertUser = db.prepare(`
        INSERT INTO users (
            id, username, email, password_hash, google_id,
            display_name, avatar, bio, phone, chips,
            bank_balance, games_played, games_won, total_earnings,
            created_at, updated_at
        ) VALUES (
            ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?,
            ?, 0, 0, 0,
            ?, ?
        )
    `);

    insertUser.run(
        id, username, email, password_hash, google_id,
        display_name, avatar, bio, phone, chips,
        bank_balance, now, now
    );

    const insertProfile = db.prepare(`
        INSERT INTO user_profiles (user_id, sound_enabled, preferred_game, custom_notes, updated_at)
        VALUES (?, 1, 'POKER', '', ?)
    `);
    insertProfile.run(id, now);

    return getUserById(id);
}

function getUserById(id) {
    const query = db.prepare(`
        SELECT u.*, p.sound_enabled, p.preferred_game, p.custom_notes
        FROM users u
        LEFT JOIN user_profiles p ON u.id = p.user_id
        WHERE u.id = ?
    `);
    return query.get(id) || null;
}

function getUserByEmail(email) {
    if (!email) return null;
    const query = db.prepare(`
        SELECT u.*, p.sound_enabled, p.preferred_game, p.custom_notes
        FROM users u
        LEFT JOIN user_profiles p ON u.id = p.user_id
        WHERE LOWER(u.email) = LOWER(?)
    `);
    return query.get(email) || null;
}

function getUserByUsername(username) {
    if (!username) return null;
    const query = db.prepare(`
        SELECT u.*, p.sound_enabled, p.preferred_game, p.custom_notes
        FROM users u
        LEFT JOIN user_profiles p ON u.id = p.user_id
        WHERE LOWER(u.username) = LOWER(?)
    `);
    return query.get(username) || null;
}

function getUserByGoogleId(googleId) {
    if (!googleId) return null;
    const query = db.prepare(`
        SELECT u.*, p.sound_enabled, p.preferred_game, p.custom_notes
        FROM users u
        LEFT JOIN user_profiles p ON u.id = p.user_id
        WHERE u.google_id = ?
    `);
    return query.get(googleId) || null;
}

function linkGoogleAccount(userId, googleId) {
    const now = Date.now();
    const stmt = db.prepare(`
        UPDATE users
        SET google_id = ?, updated_at = ?
        WHERE id = ?
    `);
    stmt.run(googleId, now, userId);
    return getUserById(userId);
}

function updateUserProfile(userId, { display_name, bio, phone, avatar, preferred_game, sound_enabled, custom_notes }) {
    const existing = getUserById(userId);
    if (!existing) return null;

    const now = Date.now();
    const newDisplayName = display_name !== undefined ? display_name : existing.display_name;
    const newBio = bio !== undefined ? bio : existing.bio;
    const newPhone = phone !== undefined ? phone : existing.phone;
    const newAvatar = avatar !== undefined ? avatar : existing.avatar;

    const updateUserStmt = db.prepare(`
        UPDATE users
        SET display_name = ?, bio = ?, phone = ?, avatar = ?, updated_at = ?
        WHERE id = ?
    `);
    updateUserStmt.run(newDisplayName, newBio, newPhone, newAvatar, now, userId);

    const newSound = sound_enabled !== undefined ? (sound_enabled ? 1 : 0) : existing.sound_enabled;
    const newPrefGame = preferred_game !== undefined ? preferred_game : existing.preferred_game;
    const newNotes = custom_notes !== undefined ? custom_notes : existing.custom_notes;

    const updateProfileStmt = db.prepare(`
        INSERT INTO user_profiles (user_id, sound_enabled, preferred_game, custom_notes, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            sound_enabled = excluded.sound_enabled,
            preferred_game = excluded.preferred_game,
            custom_notes = excluded.custom_notes,
            updated_at = excluded.updated_at
    `);
    updateProfileStmt.run(userId, newSound, newPrefGame, newNotes, now);

    return getUserById(userId);
}

function updateUserBalances(userId, { chipsChange = 0, bankChange = 0, won = false }) {
    const user = getUserById(userId);
    if (!user) return null;

    const newChips = Math.max(0, (user.chips || 0) + chipsChange);
    const newBank = Math.max(0, (user.bank_balance || 0) + bankChange);
    const newGamesPlayed = (user.games_played || 0) + 1;
    const newGamesWon = (user.games_won || 0) + (won ? 1 : 0);
    const newEarnings = (user.total_earnings || 0) + Math.max(0, chipsChange + bankChange);
    const now = Date.now();

    const stmt = db.prepare(`
        UPDATE users
        SET chips = ?, bank_balance = ?, games_played = ?,
            games_won = ?, total_earnings = ?, updated_at = ?
        WHERE id = ?
    `);
    stmt.run(newChips, newBank, newGamesPlayed, newGamesWon, newEarnings, now, userId);

    return getUserById(userId);
}

function addGameLog({ userId, gameType, action, amount = 0, balanceAfter = 0, details = '' }) {
    const now = Date.now();
    const stmt = db.prepare(`
        INSERT INTO game_logs (user_id, game_type, action, amount, balance_after, details, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(userId, gameType, action, amount, balanceAfter, details, now);
}

function getUserLogs(userId, limit = 25) {
    const stmt = db.prepare(`
        SELECT * FROM game_logs
        WHERE user_id = ?
        ORDER BY timestamp DESC
        LIMIT ?
    `);
    return stmt.all(userId, limit);
}

function getLeaderboard(limit = 10) {
    const stmt = db.prepare(`
        SELECT id, username, display_name, avatar, chips, bank_balance, games_played, games_won
        FROM users
        ORDER BY (chips + bank_balance) DESC
        LIMIT ?
    `);
    return stmt.all(limit);
}

module.exports = {
    db,
    createUser,
    getUserById,
    getUserByEmail,
    getUserByUsername,
    getUserByGoogleId,
    linkGoogleAccount,
    updateUserProfile,
    updateUserBalances,
    addGameLog,
    getUserLogs,
    getLeaderboard
};
