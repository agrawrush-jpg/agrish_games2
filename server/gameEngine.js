// ============================================================
// Game Engine — Texas Hold'em Poker + Banker Agrish
// Agrish Web Hub  |  Full Online Multiplayer Edition
// ============================================================

'use strict';

// ─── Card Definitions ────────────────────────────────────────
const SUITS = [
    { name: 'SPADES',   symbol: '♠', color: '#1e293b', isRed: false },
    { name: 'HEARTS',   symbol: '♥', color: '#ef4444', isRed: true  },
    { name: 'DIAMONDS', symbol: '♦', color: '#3b82f6', isRed: true  },
    { name: 'CLUBS',    symbol: '♣', color: '#10b981', isRed: false  }
];
const RANKS = [
    { value: 2, display: '2' }, { value: 3, display: '3' },
    { value: 4, display: '4' }, { value: 5, display: '5' },
    { value: 6, display: '6' }, { value: 7, display: '7' },
    { value: 8, display: '8' }, { value: 9, display: '9' },
    { value: 10, display: '10' }, { value: 11, display: 'J' },
    { value: 12, display: 'Q' }, { value: 13, display: 'K' },
    { value: 14, display: 'A' }
];

function createDeck() {
    const deck = [];
    for (const suit of SUITS) {
        for (const rank of RANKS) {
            deck.push({
                suit: suit.name, symbol: suit.symbol,
                color: suit.color, colorRed: suit.isRed,
                rank: rank.display, value: rank.value,
                display: `${rank.display}${suit.symbol}`
            });
        }
    }
    for (let i = deck.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [deck[i], deck[j]] = [deck[j], deck[i]];
    }
    return deck;
}

// ─── Hand Evaluator ──────────────────────────────────────────
function getCombinations(arr, k) {
    if (k === 0) return [[]];
    if (arr.length === 0) return [];
    const [head, ...tail] = arr;
    return [
        ...getCombinations(tail, k - 1).map(c => [head, ...c]),
        ...getCombinations(tail, k)
    ];
}

function evaluate5Cards(cards) {
    const sorted = [...cards].sort((a, b) => b.value - a.value);
    const suitCounts = {};
    cards.forEach(c => suitCounts[c.suit] = (suitCounts[c.suit] || 0) + 1);
    const isFlush = Object.values(suitCounts).some(v => v >= 5);

    const values = sorted.map(c => c.value);
    const uniqueValues = [...new Set(values)].sort((a, b) => b - a);
    let isStraight = false, straightHigh = 0;
    for (let i = 0; i <= uniqueValues.length - 5; i++) {
        if (uniqueValues[i] - uniqueValues[i + 4] === 4) {
            isStraight = true; straightHigh = uniqueValues[i]; break;
        }
    }
    if (!isStraight && [14, 2, 3, 4, 5].every(v => uniqueValues.includes(v))) {
        isStraight = true; straightHigh = 5;
    }

    const valCounts = {};
    cards.forEach(c => valCounts[c.value] = (valCounts[c.value] || 0) + 1);
    const counts = Object.entries(valCounts).sort((a, b) => b[1] - a[1] || b[0] - a[0]);

    if (isFlush && isStraight && straightHigh === 14)
        return { rankName: 'Royal Flush',     score: 9000000, desc: 'Royal Flush' };
    if (isFlush && isStraight)
        return { rankName: 'Straight Flush',  score: 8000000 + straightHigh, desc: `Straight Flush, ${straightHigh} High` };
    if (counts[0][1] === 4)
        return { rankName: 'Four of a Kind',  score: 7000000 + Number(counts[0][0]) * 100, desc: `Four of a Kind (${counts[0][0]}'s)` };
    if (counts[0][1] === 3 && counts[1] && counts[1][1] >= 2)
        return { rankName: 'Full House',      score: 6000000 + Number(counts[0][0]) * 100 + Number(counts[1][0]), desc: `Full House (${counts[0][0]}'s full of ${counts[1][0]}'s)` };
    if (isFlush)
        return { rankName: 'Flush',           score: 5000000 + sorted[0].value, desc: `Flush, ${sorted[0].rank} High` };
    if (isStraight)
        return { rankName: 'Straight',        score: 4000000 + straightHigh, desc: `Straight, ${straightHigh} High` };
    if (counts[0][1] === 3)
        return { rankName: 'Three of a Kind', score: 3000000 + Number(counts[0][0]) * 100, desc: `Three of a Kind (${counts[0][0]}'s)` };
    if (counts[0][1] === 2 && counts[1] && counts[1][1] === 2)
        return { rankName: 'Two Pair',        score: 2000000 + Number(counts[0][0]) * 1000 + Number(counts[1][0]) * 100, desc: `Two Pair (${counts[0][0]}'s & ${counts[1][0]}'s)` };
    if (counts[0][1] === 2)
        return { rankName: 'One Pair',        score: 1000000 + Number(counts[0][0]) * 100, desc: `One Pair of ${counts[0][0]}'s` };
    return { rankName: 'High Card',           score: 100000 + sorted[0].value, desc: `High Card ${sorted[0].rank}` };
}

function evaluateHand(cards) {
    if (!cards || cards.length === 0) return { rankName: 'High Card', score: 0, desc: 'No cards' };
    if (cards.length < 5) {
        const sorted = [...cards].sort((a, b) => b.value - a.value);
        if (sorted.length === 2 && sorted[0].value === sorted[1].value)
            return { rankName: 'One Pair', score: 1000000 + sorted[0].value * 100, desc: `Pocket Pair of ${sorted[0].rank}'s` };
        return { rankName: 'High Card', score: (sorted[0]?.value || 0) * 100, desc: `High Card ${sorted[0]?.rank || ''}` };
    }
    const combos = getCombinations(cards, 5);
    let best = { score: -1, rankName: 'High Card', desc: '' };
    for (const combo of combos) {
        const ev = evaluate5Cards(combo);
        if (ev.score > best.score) best = ev;
    }
    return best;
}

// ─── Poker AI Coach ──────────────────────────────────────────
function getPokerCoachingAdvice(holeCards, communityCards) {
    if (!holeCards || holeCards.length < 2) return 'Waiting for hole cards to be dealt...';
    const all = [...holeCards, ...communityCards];
    const ev = evaluateHand(all);

    if (communityCards.length === 0) {
        const [c1, c2] = holeCards;
        if (c1.value === c2.value) {
            if (c1.value >= 10) return `★ Premium: Pocket ${c1.rank}'s — raise aggressively!`;
            return `★ Good: Pocket ${c1.rank}'s — strong set potential.`;
        }
        if (c1.value >= 12 && c2.value >= 12) return `★ Strong paint cards (${c1.rank} & ${c2.rank}) — excellent showdown value.`;
        if (c1.suit === c2.suit && Math.abs(c1.value - c2.value) <= 2) return `★ Suited connectors (${c1.display} ${c2.display}) — great draw potential.`;
        if (c1.value <= 7 && c2.value <= 7 && c1.suit !== c2.suit) return `⚠ Weak off-suit low cards. Consider folding unless pot odds are good.`;
        return 'Average starting hand. Play cautiously and see the flop.';
    }
    if (ev.score >= 6000000) return `🔥 Monster: ${ev.desc}! Bet maximum to extract value.`;
    if (ev.score >= 4000000) return `✨ Very Strong: ${ev.desc}. Bet confidently.`;
    if (ev.score >= 2000000) return `👍 Good Hand: ${ev.desc}. Value-bet or call.`;
    if (ev.score >= 1000000) return `Marginal: ${ev.desc}. Proceed with caution.`;
    return `⚠ High Card only (${ev.desc}). Check or fold unless you have strong draws.`;
}

// ─── Hidden card for opponents ────────────────────────────────
const HIDDEN_CARD = { suit: 'HIDDEN', symbol: '?', rank: '?', value: 0, display: '??', colorRed: false, hidden: true };

// ─── Poker Table ─────────────────────────────────────────────
const TURN_TIMEOUT_MS = 30000; // 30 seconds per turn

class PokerTable {
    constructor(id, name, maxPlayers = 6) {
        this.id = id;
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.players = [];      // { id, name, avatar, chips, cards, currentBet, hasFolded, isAllIn, isDealer, isSmallBlind, isBigBlind, hasActed, isConnected }
        this.deck = [];
        this.communityCards = [];
        this.pot = 0;
        this.sidePots = [];
        this.currentBet = 0;
        this.stage = 'WAITING'; // WAITING, PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN, FINISHED
        this.dealerIndex = 0;
        this.activePlayerIndex = -1;
        this.winner = null;
        this.winnerDetails = null;
        this.logs = [];
        this.turnTimer = null;
        this.onStateChange = null; // callback set by server
        this.blinds = { small: 10, big: 20 };
    }

    // ── Seat Management ─────────────────────────────────────
    addPlayer(id, name, avatar, chips) {
        if (this.players.find(p => p.id === id)) return this.players.find(p => p.id === id);
        if (this.players.length >= this.maxPlayers) return null;

        const player = {
            id, name, avatar: avatar || '🤠', chips,
            cards: [], currentBet: 0,
            hasFolded: false, isAllIn: false, hasActed: false,
            isDealer: false, isSmallBlind: false, isBigBlind: false,
            isConnected: true
        };
        this.players.push(player);
        this.addLog(`${name} joined the table.`);

        // Auto-start if we have enough players and game is waiting
        if (this.stage === 'WAITING' && this.players.filter(p => p.isConnected).length >= 2) {
            setTimeout(() => this.startRound(), 1500);
        }
        this.notify();
        return player;
    }

    removePlayer(id) {
        const idx = this.players.findIndex(p => p.id === id);
        if (idx === -1) return;
        const name = this.players[idx].name;
        // Mid-game: fold the player
        if (this.stage !== 'WAITING' && this.stage !== 'FINISHED') {
            this.players[idx].hasFolded = true;
            this.players[idx].isConnected = false;
            this.addLog(`${name} disconnected and was folded.`);
            // Check if only one remains
            this._checkForWinByFold();
            if (this.stage !== 'FINISHED') this._advanceTurnAfterAction();
        } else {
            this.players.splice(idx, 1);
        }
        this.notify();
    }

    setConnected(id, connected) {
        const p = this.players.find(p => p.id === id);
        if (p) { p.isConnected = connected; this.notify(); }
    }

    get activePlayers() {
        return this.players.filter(p => !p.hasFolded && !p.isAllIn);
    }

    get nonFoldedPlayers() {
        return this.players.filter(p => !p.hasFolded);
    }

    // ── Round Flow ───────────────────────────────────────────
    startRound() {
        const eligible = this.players.filter(p => p.chips > 0 && p.isConnected);
        if (eligible.length < 2) {
            this.stage = 'WAITING';
            this.addLog('Waiting for more players...');
            this.notify();
            return;
        }

        this.deck = createDeck();
        this.communityCards = [];
        this.pot = 0;
        this.sidePots = [];
        this.currentBet = 0;
        this.stage = 'PRE_FLOP';
        this.winner = null;
        this.winnerDetails = null;

        // Reset per-round player state
        this.players.forEach(p => {
            p.hasFolded = !p.isConnected || p.chips <= 0;
            p.isAllIn = false;
            p.hasActed = false;
            p.currentBet = 0;
            p.cards = [];
            p.isDealer = false; p.isSmallBlind = false; p.isBigBlind = false;
        });

        // Rotate dealer among eligible
        const eligibleIndices = this.players
            .map((p, i) => ({ p, i }))
            .filter(({ p }) => !p.hasFolded)
            .map(({ i }) => i);

        const prevDealerPos = eligibleIndices.indexOf(this.dealerIndex);
        this.dealerIndex = eligibleIndices[(prevDealerPos + 1) % eligibleIndices.length];

        // Assign roles
        const sbIdx = eligibleIndices[(eligibleIndices.indexOf(this.dealerIndex) + 1) % eligibleIndices.length];
        const bbIdx = eligibleIndices[(eligibleIndices.indexOf(this.dealerIndex) + 2) % eligibleIndices.length];
        this.players[this.dealerIndex].isDealer = true;
        this.players[sbIdx].isSmallBlind = true;
        this.players[bbIdx].isBigBlind = true;

        // Post blinds
        this._postBlind(sbIdx, this.blinds.small);
        this._postBlind(bbIdx, this.blinds.big);
        this.currentBet = this.blinds.big;

        // Deal 2 hole cards to each non-folded player
        this.players.filter(p => !p.hasFolded).forEach(p => {
            p.cards = [this.deck.pop(), this.deck.pop()];
        });

        // First to act pre-flop is UTG (after BB)
        const utgIdx = eligibleIndices[(eligibleIndices.indexOf(bbIdx) + 1) % eligibleIndices.length];
        this.activePlayerIndex = utgIdx;

        // BB hasn't acted yet pre-flop (can raise even if others just call)
        this.players[bbIdx].hasActed = false;

        this.addLog(`New round! Blinds: SB $${this.blinds.small} / BB $${this.blinds.big}. Pot: $${this.pot}.`);
        this.notify();
        this._startTurnTimer();
    }

    _postBlind(playerIndex, amount) {
        const p = this.players[playerIndex];
        const actual = Math.min(p.chips, amount);
        p.chips -= actual;
        p.currentBet = actual;
        this.pot += actual;
        if (p.chips === 0) p.isAllIn = true;
    }

    // ── Action Handling ──────────────────────────────────────
    handleAction(playerId, action, amount = 0) {
        const playerIdx = this.players.findIndex(p => p.id === playerId);
        if (playerIdx === -1) return { ok: false, error: 'Player not at table' };

        if (this.stage === 'WAITING' || this.stage === 'FINISHED' || this.stage === 'SHOWDOWN') {
            return { ok: false, error: 'No active round' };
        }

        if (playerIdx !== this.activePlayerIndex) {
            return { ok: false, error: 'Not your turn' };
        }

        const player = this.players[playerIdx];
        if (player.hasFolded || player.isAllIn) {
            return { ok: false, error: 'You cannot act' };
        }

        this._clearTurnTimer();

        switch (action) {
            case 'fold':
                player.hasFolded = true;
                player.hasActed = true;
                this.addLog(`${player.name} FOLDED.`);
                this._checkForWinByFold();
                break;

            case 'check': {
                if (player.currentBet < this.currentBet) {
                    return { ok: false, error: `Cannot check — there's a bet of $${this.currentBet}. Call or raise.` };
                }
                player.hasActed = true;
                this.addLog(`${player.name} CHECKED.`);
                break;
            }

            case 'call': {
                const callAmount = Math.max(0, this.currentBet - player.currentBet);
                const actual = Math.min(player.chips, callAmount);
                player.chips -= actual;
                player.currentBet += actual;
                this.pot += actual;
                if (player.chips === 0) player.isAllIn = true;
                player.hasActed = true;
                this.addLog(`${player.name} CALLED $${actual}. Pot: $${this.pot}.`);
                break;
            }

            case 'raise': {
                const minRaise = this.currentBet + this.blinds.big;
                const raiseTotal = Math.max(minRaise, Number(amount) || minRaise);
                const toAdd = Math.min(player.chips, raiseTotal - player.currentBet);

                if (toAdd <= 0) return { ok: false, error: 'Not enough chips to raise' };
                player.chips -= toAdd;
                player.currentBet += toAdd;
                this.pot += toAdd;
                this.currentBet = player.currentBet;
                if (player.chips === 0) player.isAllIn = true;
                player.hasActed = true;

                // Re-open action for everyone else
                this.players.forEach(p => {
                    if (p.id !== player.id && !p.hasFolded && !p.isAllIn) p.hasActed = false;
                });
                this.addLog(`${player.name} RAISED to $${player.currentBet}. Pot: $${this.pot}.`);
                break;
            }

            default:
                return { ok: false, error: `Unknown action: ${action}` };
        }

        if (this.stage !== 'FINISHED') {
            this._advanceTurnAfterAction();
        }
        this.notify();
        return { ok: true };
    }

    _checkForWinByFold() {
        const remaining = this.players.filter(p => !p.hasFolded);
        if (remaining.length === 1) {
            remaining[0].chips += this.pot;
            this.winner = remaining[0].name;
            this.winnerDetails = `${remaining[0].name} wins $${this.pot} (all others folded).`;
            this.stage = 'FINISHED';
            this.addLog(`🏆 ${this.winnerDetails}`);
            this._clearTurnTimer();
            this._scheduleNextRound();
        }
    }

    _advanceTurnAfterAction() {
        // Check if betting round is complete
        if (this._isBettingRoundOver()) {
            this._advanceStage();
        } else {
            this._moveToNextPlayer();
            this._startTurnTimer();
        }
    }

    _isBettingRoundOver() {
        const activePlayers = this.players.filter(p => !p.hasFolded && !p.isAllIn);
        if (activePlayers.length === 0) return true;
        const allActed = activePlayers.every(p => p.hasActed);
        const allMatchBet = activePlayers.every(p => p.currentBet >= this.currentBet);
        return allActed && allMatchBet;
    }

    _moveToNextPlayer() {
        const n = this.players.length;
        let next = (this.activePlayerIndex + 1) % n;
        let tries = 0;
        while (tries < n) {
            const p = this.players[next];
            if (!p.hasFolded && !p.isAllIn) {
                this.activePlayerIndex = next;
                return;
            }
            next = (next + 1) % n;
            tries++;
        }
        // All remaining are all-in or folded — advance stage
        this._advanceStage();
    }

    _advanceStage() {
        this._clearTurnTimer();
        // Reset bets for new street
        this.players.forEach(p => { p.currentBet = 0; p.hasActed = false; });
        this.currentBet = 0;

        const stageOrder = ['PRE_FLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN'];
        const currentIdx = stageOrder.indexOf(this.stage);
        this.stage = stageOrder[currentIdx + 1] || 'SHOWDOWN';

        if (this.stage === 'FLOP') {
            this.communityCards.push(this.deck.pop(), this.deck.pop(), this.deck.pop());
            this.addLog('🃏 Flop dealt.');
        } else if (this.stage === 'TURN') {
            this.communityCards.push(this.deck.pop());
            this.addLog('🃏 Turn dealt.');
        } else if (this.stage === 'RIVER') {
            this.communityCards.push(this.deck.pop());
            this.addLog('🃏 River dealt.');
        } else if (this.stage === 'SHOWDOWN') {
            this._evaluateShowdown();
            return;
        }

        // Set first to act post-flop: first non-folded left of dealer
        const n = this.players.length;
        let first = (this.dealerIndex + 1) % n;
        let tries = 0;
        while (tries < n && (this.players[first].hasFolded || this.players[first].isAllIn)) {
            first = (first + 1) % n;
            tries++;
        }
        this.activePlayerIndex = first;

        // Check if only all-in players remain (auto run-it-out)
        const canAct = this.players.filter(p => !p.hasFolded && !p.isAllIn);
        if (canAct.length <= 1) {
            setTimeout(() => { this._advanceStage(); this.notify(); }, 1200);
        } else {
            this._startTurnTimer();
        }
        this.notify();
    }

    _evaluateShowdown() {
        this.stage = 'SHOWDOWN';
        const active = this.players.filter(p => !p.hasFolded);
        let bestScore = -1;
        let winners = [];

        for (const p of active) {
            const ev = evaluateHand([...p.cards, ...this.communityCards]);
            p._handEval = ev;
            if (ev.score > bestScore) {
                bestScore = ev.score;
                winners = [p];
            } else if (ev.score === bestScore) {
                winners.push(p);
            }
        }

        const share = Math.floor(this.pot / winners.length);
        winners.forEach(w => { w.chips += share; });
        const winnerNames = winners.map(w => `${w.name} (${w._handEval.desc})`).join(', ');
        this.winner = winners.map(w => w.name).join(' & ');
        this.winnerDetails = `${winnerNames} split/win $${this.pot}!`;
        this.stage = 'FINISHED';
        this.addLog(`🏆 Showdown: ${this.winnerDetails}`);
        this.notify();
        this._scheduleNextRound();
    }

    _scheduleNextRound() {
        setTimeout(() => {
            const connected = this.players.filter(p => p.isConnected && p.chips > 0);
            if (connected.length >= 2) {
                this.startRound();
            } else {
                this.stage = 'WAITING';
                this.addLog('Waiting for players...');
                this.notify();
            }
        }, 6000);
    }

    // ── Turn Timer ───────────────────────────────────────────
    _startTurnTimer() {
        this._clearTurnTimer();
        this.turnDeadline = Date.now() + TURN_TIMEOUT_MS;
        this.turnTimer = setTimeout(() => {
            const p = this.players[this.activePlayerIndex];
            if (p && !p.hasFolded && !p.isAllIn) {
                this.addLog(`⏰ ${p.name} timed out — auto-folded.`);
                this.handleAction(p.id, 'fold');
            }
        }, TURN_TIMEOUT_MS);
    }

    _clearTurnTimer() {
        if (this.turnTimer) { clearTimeout(this.turnTimer); this.turnTimer = null; }
        this.turnDeadline = null;
    }

    // ── State (Private per player) ───────────────────────────
    getState(viewerId = null) {
        const isShowdown = this.stage === 'FINISHED' || this.stage === 'SHOWDOWN';

        const publicPlayers = this.players.map((p, idx) => {
            const isMe = viewerId && p.id === viewerId;
            const revealCards = isMe || isShowdown;
            return {
                id: isMe ? p.id : undefined, // hide other player IDs
                name: p.name,
                avatar: p.avatar,
                chips: p.chips,
                currentBet: p.currentBet,
                hasFolded: p.hasFolded,
                isAllIn: p.isAllIn,
                isDealer: p.isDealer,
                isSmallBlind: p.isSmallBlind,
                isBigBlind: p.isBigBlind,
                isConnected: p.isConnected,
                isMe: !!isMe,
                isTurn: idx === this.activePlayerIndex,
                handEval: isShowdown ? (p._handEval || null) : null,
                cardCount: p.cards.length,
                // Cards: viewer sees their own; others see hidden backs until showdown
                cards: revealCards ? p.cards : p.cards.map(() => HIDDEN_CARD)
            };
        });

        const me = viewerId ? this.players.find(p => p.id === viewerId) : null;
        const myCards = me ? me.cards : [];
        const coachTip = (me && !me.hasFolded && myCards.length >= 2)
            ? getPokerCoachingAdvice(myCards, this.communityCards)
            : 'Waiting for your cards...';

        const isMyTurn = viewerId
            ? (this.players[this.activePlayerIndex]?.id === viewerId)
            : false;

        return {
            tableId: this.id,
            tableName: this.name,
            stage: this.stage,
            pot: this.pot,
            currentBet: this.currentBet,
            communityCards: this.communityCards,
            players: publicPlayers,
            myCards,
            coachTip,
            isMyTurn,
            activePlayerIndex: this.activePlayerIndex,
            winner: this.winner,
            winnerDetails: this.winnerDetails,
            turnDeadline: this.turnDeadline,
            logs: this.logs.slice(0, 20),
            blinds: this.blinds,
            maxPlayers: this.maxPlayers,
            seatCount: this.players.length
        };
    }

    addLog(msg) {
        this.logs.unshift(`[${new Date().toLocaleTimeString()}] ${msg}`);
        if (this.logs.length > 40) this.logs.pop();
    }

    notify() {
        if (typeof this.onStateChange === 'function') this.onStateChange(this.id);
    }
}

// ─── Bank by Agrish (shared room — untouched logic) ──────────
class BankRoom {
    constructor() {
        this.bankAmount = 5000;
        this.bankRound = 1;
        this.bankRolls = [];
        this.bankPlayers = []; // { id, name, balance, isBanked }
        this.logs = [];
    }

    addPlayer(id, name, chips = 1000) {
        let p = this.bankPlayers.find(p => p.id === id);
        if (!p) {
            p = { id, name, balance: chips, isBanked: false };
            this.bankPlayers.push(p);
        }
        return p;
    }

    handleAction(action, { name = '', amount = 0, userId = null } = {}) {
        const player = this.bankPlayers.find(p =>
            (userId && p.id === userId) || p.name.toLowerCase() === name.toLowerCase()
        );

        if (action === 'roll') {
            const d1 = Math.floor(Math.random() * 6) + 1;
            const d2 = Math.floor(Math.random() * 6) + 1;
            const total = d1 + d2;
            const isBust = (d1 === 1 && d2 === 1) || total === 7;
            if (isBust) {
                this.bankRolls.unshift({ player: name || 'Player', roll: total, resultText: `Rolled ${d1}+${d2}=${total} (BUST!)`, isBust: true });
                this.addLog(`💥 ${name} rolled ${total} — BUSTED!`);
            } else {
                const gained = total * 10;
                this.bankAmount += gained;
                this.bankRolls.unshift({ player: name || 'Player', roll: total, resultText: `Rolled ${d1}+${d2}=${total} (+$${gained} to vault)` });
                this.addLog(`🎲 ${name} rolled ${total} (Vault +$${gained})`);
            }
        } else if (action === 'bank') {
            if (player) {
                player.isBanked = true;
                player.balance += 200;
                this.bankAmount = Math.max(0, this.bankAmount - 200);
                this.addLog(`🏦 ${name} BANKED $200!`);
            }
        } else if (action === 'bank_borrow') {
            const amt = Number(amount) || 100;
            if (player && this.bankAmount >= amt) {
                player.balance += amt;
                this.bankAmount -= amt;
                this.addLog(`💳 ${name} borrowed $${amt} from vault.`);
            }
        } else if (action === 'bank_return') {
            const amt = Number(amount) || 100;
            if (player && player.balance >= amt) {
                player.balance -= amt;
                this.bankAmount += amt;
                this.addLog(`💰 ${name} returned $${amt} to vault.`);
            }
        } else if (action === 'new_round') {
            this.bankRound++;
            this.bankRolls = [];
            this.bankPlayers.forEach(p => p.isBanked = false);
            this.addLog(`🔄 New Bank round ${this.bankRound} started!`);
        }
    }

    getState(viewerId = null) {
        const me = viewerId ? this.bankPlayers.find(p => p.id === viewerId) : null;
        let leader = 'None', maxBal = -1;
        this.bankPlayers.forEach(p => {
            if (p.balance > maxBal) { maxBal = p.balance; leader = p.name; }
        });
        return {
            bankAmount: this.bankAmount,
            bankRound: this.bankRound,
            bankPlayers: this.bankPlayers,
            bankRolls: this.bankRolls.slice(0, 15),
            leader, leaderBalance: maxBal,
            myBalance: me ? me.balance : 1000,
            logs: this.logs.slice(0, 20)
        };
    }

    addLog(msg) {
        this.logs.unshift(`[${new Date().toLocaleTimeString()}] ${msg}`);
        if (this.logs.length > 30) this.logs.pop();
    }
}

// ─── Poker Room Manager ───────────────────────────────────────
class PokerRoomManager {
    constructor() {
        this.tables = new Map();
        this._nextId = 1;
        // Create one default table to start
        this.createTable('Main Table');
    }

    createTable(name, maxPlayers = 6) {
        const id = `table_${this._nextId++}`;
        const table = new PokerTable(id, name, maxPlayers);
        this.tables.set(id, table);
        return table;
    }

    getTable(id) { return this.tables.get(id) || null; }

    getOrCreateOpenTable() {
        for (const table of this.tables.values()) {
            if (table.players.length < table.maxPlayers &&
                (table.stage === 'WAITING' || table.stage === 'FINISHED')) {
                return table;
            }
        }
        return this.createTable(`Table ${this._nextId}`);
    }

    listTables() {
        return [...this.tables.values()].map(t => ({
            id: t.id,
            name: t.name,
            stage: t.stage,
            playerCount: t.players.length,
            maxPlayers: t.maxPlayers,
            pot: t.pot
        }));
    }

    // Find which table a player is sitting at
    findPlayerTable(playerId) {
        for (const table of this.tables.values()) {
            if (table.players.find(p => p.id === playerId)) return table;
        }
        return null;
    }
}

// ─── Singletons ───────────────────────────────────────────────
const pokerManager = new PokerRoomManager();
const bankRoom = new BankRoom();

// ── Legacy shim: keep defaultRoom working for old routes ──────
const defaultRoom = {
    selectedGameMode: 'POKER',
    pokerPlayers: [],
    addPlayer(name, chips, id) {
        bankRoom.addPlayer(id || 'host', name, chips);
    },
    getState(playerName, userId) { return bankRoom.getState(userId); }
};

module.exports = {
    PokerTable,
    PokerRoomManager,
    BankRoom,
    pokerManager,
    bankRoom,
    defaultRoom,
    evaluateHand,
    getPokerCoachingAdvice,
    HIDDEN_CARD
};
