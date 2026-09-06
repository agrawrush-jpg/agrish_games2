// Automated Test Suite for Agrish Web Server

const assert = require('node:assert');
const db = require('./db');
const auth = require('./auth');
const { PokerTable, BankRoom, evaluateHand, getPokerCoachingAdvice } = require('./gameEngine');

async function runTests() {
    console.log('🧪 Starting Agrish Web Server Automated Tests...\n');

    // -------------------------------------------------------------
    // 1. Password Hashing & Verification Tests
    // -------------------------------------------------------------
    console.log('Test 1: Password hashing and verification');
    const password = 'SuperSecretPassword123!';
    const hash = auth.hashPassword(password);
    assert.ok(hash && hash.includes(':'), 'Hash format must be salt:key');
    assert.strictEqual(auth.verifyPassword(password, hash), true, 'Valid password should verify');
    assert.strictEqual(auth.verifyPassword('WrongPassword', hash), false, 'Wrong password should fail');
    console.log('✅ Password hashing & verification passed!\n');

    // -------------------------------------------------------------
    // 2. User Creation & Database Persistence
    // -------------------------------------------------------------
    console.log('Test 2: User database operations');
    const testUserId = 'test_usr_' + Date.now();
    const testEmail = `test_${Date.now()}@agrish.com`;
    const user = db.createUser({
        id: testUserId,
        username: 'testplayer_' + Date.now(),
        email: testEmail,
        password_hash: hash,
        display_name: 'Test Poker Master',
        avatar: '🥷',
        bio: 'Poker strategist',
        phone: '+1 555 0199',
        chips: 2500,
        bank_balance: 3000
    });

    assert.ok(user, 'Created user must not be null');
    assert.strictEqual(user.id, testUserId);
    assert.strictEqual(user.email, testEmail);
    assert.strictEqual(user.chips, 2500);

    // Querying user
    const fetchedById = db.getUserById(testUserId);
    assert.strictEqual(fetchedById.display_name, 'Test Poker Master');

    const fetchedByEmail = db.getUserByEmail(testEmail);
    assert.strictEqual(fetchedByEmail.id, testUserId);
    console.log('✅ User database operations passed!\n');

    // -------------------------------------------------------------
    // 3. User Profile Update & Persistence (Feature: Input & Save User Info)
    // -------------------------------------------------------------
    console.log('Test 3: Input and save user profile information');
    const updated = db.updateUserProfile(testUserId, {
        display_name: 'Champion Master',
        bio: 'Updated bio: Never folds pocket aces!',
        phone: '+251 911 000 000',
        avatar: '👑',
        preferred_game: 'BANK',
        sound_enabled: 0
    });

    assert.strictEqual(updated.display_name, 'Champion Master');
    assert.strictEqual(updated.bio, 'Updated bio: Never folds pocket aces!');
    assert.strictEqual(updated.phone, '+251 911 000 000');
    assert.strictEqual(updated.avatar, '👑');
    assert.strictEqual(updated.preferred_game, 'BANK');
    assert.strictEqual(updated.sound_enabled, 0);

    // Verify persisted directly from a fresh query
    const freshQuery = db.getUserById(testUserId);
    assert.strictEqual(freshQuery.bio, 'Updated bio: Never folds pocket aces!');
    console.log('✅ User profile input and save verified!\n');

    // -------------------------------------------------------------
    // 4. Token Generation & Verification
    // -------------------------------------------------------------
    console.log('Test 4: JWT Token authentication');
    const token = auth.generateToken(user);
    assert.ok(token && token.split('.').length === 3, 'Token must have 3 segments');
    const payload = auth.verifyToken(token);
    assert.strictEqual(payload.sub, testUserId);
    assert.strictEqual(payload.email, testEmail);

    const tampered = token.slice(0, -4) + 'abcd';
    assert.strictEqual(auth.verifyToken(tampered), null, 'Tampered token must fail');
    console.log('✅ JWT Token authentication passed!\n');

    // -------------------------------------------------------------
    // 5. Google Sign-In Verification
    // -------------------------------------------------------------
    console.log('Test 5: Google OAuth verification flow');
    const googleProfile = await auth.verifyGoogleToken('demo-google-token:test.user@gmail.com:Google Master');
    assert.strictEqual(googleProfile.email, 'test.user@gmail.com');
    assert.strictEqual(googleProfile.displayName, 'Google Master');
    assert.ok(googleProfile.googleId, 'Google ID must exist');
    console.log('✅ Google OAuth verification flow passed!\n');

    // -------------------------------------------------------------
    // 6. Game Engine & AI Coaching Tests
    // -------------------------------------------------------------
    console.log('Test 6: Game engine & Poker evaluation');
    const table = new PokerTable('test_table', 'Test Poker Room');
    assert.strictEqual(table.id, 'test_table');

    // Hand Evaluator test
    const pocketAces = [
        { rank: 'A', value: 14, suit: 'SPADES', display: 'A♠' },
        { rank: 'A', value: 14, suit: 'HEARTS', display: 'A♥' }
    ];
    const tip = getPokerCoachingAdvice(pocketAces, []);
    assert.ok(tip.includes('Premium'), 'Pocket Aces must trigger Premium advice');

    const royalFlushCards = [
        { rank: 'A', value: 14, suit: 'HEARTS', display: 'A♥' },
        { rank: 'K', value: 13, suit: 'HEARTS', display: 'K♥' },
        { rank: 'Q', value: 12, suit: 'HEARTS', display: 'Q♥' },
        { rank: 'J', value: 11, suit: 'HEARTS', display: 'J♥' },
        { rank: '10', value: 10, suit: 'HEARTS', display: '10♥' }
    ];
    const royalEval = evaluateHand(royalFlushCards);
    assert.strictEqual(royalEval.rankName, 'Royal Flush');

    // Test poker table player seating
    table.addPlayer('alice_1', 'Alice', '🤠', 1000);
    table.addPlayer('bob_1', 'Bob', '🥷', 1000);
    assert.strictEqual(table.players.length, 2);

    // Test Banker Agrish room
    const bank = new BankRoom();
    bank.addPlayer('alice_1', 'Alice', 1000);
    assert.strictEqual(bank.bankPlayers.length, 1);
    console.log('✅ Game engine & Poker evaluation passed!\n');

    // -------------------------------------------------------------
    // 7. Activity Logs & Balance Updates
    // -------------------------------------------------------------
    console.log('Test 7: Activity logging & Leaderboard');
    db.addGameLog({
        userId: testUserId,
        gameType: 'POKER',
        action: 'raise',
        amount: 100,
        balanceAfter: 2400,
        details: 'Raised by $100'
    });
    const logs = db.getUserLogs(testUserId);
    assert.ok(logs.length > 0, 'User should have at least 1 log');
    assert.strictEqual(logs[0].action, 'raise');

    const leaderboard = db.getLeaderboard(5);
    assert.ok(leaderboard.length > 0, 'Leaderboard must contain users');
    console.log('✅ Activity logging & Leaderboard passed!\n');

    console.log('🎉 ALL AUTOMATED TESTS COMPLETED SUCCESSFULLY!');
}

runTests().catch(err => {
    console.error('❌ Test failed:', err);
    process.exit(1);
});
