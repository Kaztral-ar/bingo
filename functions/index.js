/**
 * Cloud Functions — authoritative multiplayer backend.
 */
const { onValueWritten } = require("firebase-functions/v2/database");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.database();

const FILL_SECONDS = 120;

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

exports.onRoomStatusChange = onValueWritten("/rooms/{roomCode}/status", async (event) => {
  if (event.data.after.val() !== "filling") return null;
  const roomRef = db.ref(`rooms/${event.params.roomCode}`);
  await roomRef.child("fillDeadline").set(Date.now() + FILL_SECONDS * 1000);
  return null;
});

// Deadline enforcement is server-side. The scheduler can be up to about a minute late.
exports.sweepExpiredFillTimers = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const snap = await db.ref("rooms").orderByChild("status").equalTo("filling").get();
  if (!snap.exists()) return null;

  const updates = {};
  const privateUpdates = {};

  snap.forEach((roomSnap) => {
    const room = roomSnap.val();
    if (!room.fillDeadline || room.fillDeadline > now) return;

    const roomCode = roomSnap.key;
    const deck = shuffledDeck();
    updates[`${roomCode}/calledNumbers`] = [];
    updates[`${roomCode}/currentIndex`] = 0;
    updates[`${roomCode}/status`] = "calling";
    updates[`${roomCode}/officialSequence`] = null;
    privateUpdates[roomCode] = deck;

    Object.entries(room.players || {}).forEach(([uid, player]) => {
      updates[`${roomCode}/players/${uid}/card`] = normalizeCard(player.card);
      updates[`${roomCode}/players/${uid}/cardLocked`] = true;
      updates[`${roomCode}/players/${uid}/marked`] = emptyMarks();
    });
  });

  if (Object.keys(updates).length) await db.ref("rooms").update(updates);
  if (Object.keys(privateUpdates).length) await db.ref("privateSequences").update(privateUpdates);
  return null;
});

// One authoritative number is released per scheduler tick. Clients receive it immediately via RTDB.
exports.tickCalledNumbers = onSchedule("every 1 minutes", async () => {
  const snap = await db.ref("rooms").orderByChild("status").equalTo("calling").get();
  if (!snap.exists()) return null;

  const updates = {};
  for (const roomSnap of Object.values(snap.val() || {})) {
    // handled below by iterating the actual snapshot
  }

  const sequenceCache = {};
  const rooms = [];
  snap.forEach((roomSnap) => rooms.push(roomSnap));

  for (const roomSnap of rooms) {
    const roomCode = roomSnap.key;
    const room = roomSnap.val();
    if (room.winnerUid) continue;

    if (!sequenceCache[roomCode]) {
      const sequenceSnap = await db.ref(`privateSequences/${roomCode}`).get();
      sequenceCache[roomCode] = sequenceSnap.val() || [];
    }

    const seq = sequenceCache[roomCode];
    const idx = Number.isInteger(room.currentIndex) ? room.currentIndex : 0;
    if (idx >= seq.length) continue;

    const called = Array.isArray(room.calledNumbers) ? room.calledNumbers.slice() : [];
    if (called.length >= 25) continue;
    called.push(seq[idx]);
    updates[`${roomCode}/calledNumbers`] = called;
    updates[`${roomCode}/currentIndex`] = idx + 1;
  }

  if (Object.keys(updates).length) await db.ref("rooms").update(updates);
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
