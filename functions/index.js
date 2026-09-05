/**
 * Cloud Functions — authoritative multiplayer backend.
 */
const { onValueWritten } = require("firebase-functions/v2/database");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.database();

const FILL_SECONDS = 120;
const CALL_INTERVAL_MS = 2500;

function shuffledDeck() {
  const nums = Array.from({ length: 25 }, (_, i) => i + 1);
  for (let i = nums.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [nums[i], nums[j]] = [nums[j], nums[i]];
  }
  return nums;
}

function normalizeCard(card) {
  const used = new Set();
  const grid = Array.from({ length: 5 }, () => Array(5).fill(0));
  if (Array.isArray(card)) {
    for (let r = 0; r < 5; r++) {
      for (let c = 0; c < 5; c++) {
        const value = Number(card[r]?.[c]);
        if (Number.isInteger(value) && value >= 1 && value <= 25 && !used.has(value)) {
          grid[r][c] = value;
          used.add(value);
        }
      }
    }
  }
  let candidate = 1;
  for (let r = 0; r < 5; r++) {
    for (let c = 0; c < 5; c++) {
      if (grid[r][c] === 0) {
        while (candidate <= 25 && used.has(candidate)) candidate++;
        if (candidate <= 25) {
          grid[r][c] = candidate;
          used.add(candidate);
        }
      }
    }
  }
  return grid;
}

function emptyMarks() {
  return Array.from({ length: 5 }, () => Array(5).fill(false));
}

async function transitionToCalling(roomCode) {
  const roomRef = db.ref(`rooms/${roomCode}`);
  const result = await roomRef.transaction((current) => {
    if (!current || current.status !== "filling") return;
    if (!current.fillDeadline || current.fillDeadline > Date.now()) return;
    current.calledNumbers = [];
    current.currentIndex = 0;
    current.status = "calling";
    current.winnerUid = null;
    current.winningPattern = null;
    return current;
  });

  if (result.committed) {
    await db.ref(`privateSequences/${roomCode}`).set(shuffledDeck());
    await db.ref(`rooms/${roomCode}/startCallingRequests`).remove();
  }
  return result.committed;
}

exports.onRoomStatusChange = onValueWritten("/rooms/{roomCode}/status", async (event) => {
  if (event.data.after.val() !== "filling") return null;
  const roomRef = db.ref(`rooms/${event.params.roomCode}`);
  await roomRef.child("fillDeadline").set(Date.now() + FILL_SECONDS * 1000);
  return null;
});

// The client asks at exactly 2:00. This avoids the old one-minute scheduler delay.
exports.requestCalling = onValueWritten("/rooms/{roomCode}/startCallingRequests/{uid}", async (event) => {
  if (!event.data.after.exists()) return null;
  const { roomCode, uid } = event.params;
  const roomSnap = await db.ref(`rooms/${roomCode}`).get();
  const room = roomSnap.val();
  if (!room || room.status !== "filling" || room.host !== uid) return null;
  await transitionToCalling(roomCode);
  return null;
});

// Scheduler is a fallback if the host app is closed at the end of the fill phase.
exports.sweepExpiredFillTimers = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const snap = await db.ref("rooms").orderByChild("status").equalTo("filling").get();
  if (!snap.exists()) return null;

  for (const roomSnap of snap.val() ? Object.keys(snap.val()) : []) {
    const roomCode = roomSnap;
    const room = (await db.ref(`rooms/${roomCode}`).get()).val();
    if (!room || !room.fillDeadline || room.fillDeadline > now) continue;
    const deck = shuffledDeck();
    const updates = {};
    updates[`${roomCode}/calledNumbers`] = [];
    updates[`${roomCode}/currentIndex`] = 0;
    updates[`${roomCode}/status`] = "calling";
    updates[`${roomCode}/winnerUid`] = null;
    updates[`${roomCode}/winningPattern`] = null;
    Object.entries(room.players || {}).forEach(([uid, player]) => {
      updates[`${roomCode}/players/${uid}/card`] = normalizeCard(player.card);
      updates[`${roomCode}/players/${uid}/cardLocked`] = true;
      updates[`${roomCode}/players/${uid}/marked`] = emptyMarks();
    });
    await db.ref("rooms").update(updates);
    await db.ref(`privateSequences/${roomCode}`).set(deck);
  }
  return null;
});

async function releaseNextNumber(roomCode) {
  const sequenceSnap = await db.ref(`privateSequences/${roomCode}`).get();
  const sequence = sequenceSnap.val() || [];
  if (!Array.isArray(sequence) || sequence.length !== 25) return false;

  const roomRef = db.ref(`rooms/${roomCode}`);
  const result = await roomRef.transaction((current) => {
    if (!current || current.status !== "calling" || current.winnerUid) return;
    const idx = Number.isInteger(current.currentIndex) ? current.currentIndex : 0;
    const called = Array.isArray(current.calledNumbers) ? current.calledNumbers.slice() : [];
    if (idx >= sequence.length || called.length >= 25) return;

    const now = Date.now();
    if (current.lastCallAt && now - Number(current.lastCallAt) < CALL_INTERVAL_MS) return;
    const number = Number(sequence[idx]);
    if (!Number.isInteger(number) || number < 1 || number > 25 || called.includes(number)) return;

    called.push(number);
    current.calledNumbers = called;
    current.currentIndex = idx + 1;
    current.lastCallAt = now;
    return current;
  });
  return result.committed;
}

// Real-time host requests replace the old one-number-per-minute gameplay.
exports.requestNextNumber = onValueWritten("/rooms/{roomCode}/callRequests/{uid}", async (event) => {
  if (!event.data.after.exists()) return null;
  const { roomCode, uid } = event.params;
  const roomSnap = await db.ref(`rooms/${roomCode}`).get();
  const room = roomSnap.val();
  if (!room || room.status !== "calling" || room.host !== uid) return null;
  await releaseNextNumber(roomCode);
  await db.ref(`rooms/${roomCode}/callRequests/${uid}`).remove();
  return null;
});

function checkWin(marked) {
  if (!Array.isArray(marked) || marked.length !== 5) return null;
  const isMarked = (r, c) => !!(marked[r] && marked[r][c]);
  for (let r = 0; r < 5; r++) {
    if ([0, 1, 2, 3, 4].every((c) => isMarked(r, c))) return `Row ${r + 1}`;
  }
  for (let c = 0; c < 5; c++) {
    if ([0, 1, 2, 3, 4].every((r) => isMarked(r, c))) return `Column ${c + 1}`;
  }
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, i))) return "Diagonal";
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, 4 - i))) return "Diagonal";
  return null;
}

exports.claimBingo = onValueWritten("/rooms/{roomCode}/claims/{uid}", async (event) => {
  if (!event.data.after.exists()) return null;
  const { roomCode, uid } = event.params;
  const roomRef = db.ref(`rooms/${roomCode}`);
  const roomSnap = await roomRef.get();
  const room = roomSnap.val();
  if (!room || room.status !== "calling" || room.winnerUid) return null;

  const player = (room.players || {})[uid];
  if (!player) return null;
  const card = normalizeCard(player.card);
  const calledSet = new Set(room.calledNumbers || []);
  const trueMarked = card.map((row) => row.map((num) => calledSet.has(num)));
  const pattern = checkWin(trueMarked);
  if (!pattern) return null;

  await roomRef.transaction((current) => {
    if (!current || current.status !== "calling" || current.winnerUid) return;
    current.winnerUid = uid;
    current.winningPattern = pattern;
    current.status = "finished";
    return current;
  });
  return null;
});
