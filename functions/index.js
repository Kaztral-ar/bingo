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

function autoFillCard(card) {
  const used = new Set();
  for (const row of card || []) for (const v of row || []) if (v) used.add(v);
  let candidate = 1;
  const grid = card
    ? card.map((row) => Array.isArray(row) ? row.slice() : Array(5).fill(0))
    : Array.from({ length: 5 }, () => Array(5).fill(0));
  for (let r = 0; r < 5; r++) {
    for (let c = 0; c < 5; c++) {
      if (!grid[r][c]) {
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

exports.onRoomStatusChange = onValueWritten("/rooms/{roomCode}/status", async (event) => {
  if (event.data.after.val() !== "filling") return null;
  const roomRef = db.ref(`rooms/${event.params.roomCode}`);
  // Always calculate the real deadline on the server. Do not trust the
  // client-supplied timestamp.
  await roomRef.child("fillDeadline").set(Date.now() + FILL_SECONDS * 1000);
  return null;
});

/**
 * Checks filling rooms every minute. A deadline is two minutes, so this is
 * sufficient to transition the room without relying on client timers.
 */
exports.sweepExpiredFillTimers = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const snap = await db.ref("rooms").orderByChild("status").equalTo("filling").get();
  if (!snap.exists()) return null;

  const updates = {};
  snap.forEach((roomSnap) => {
    const room = roomSnap.val();
    if (!room.fillDeadline || room.fillDeadline > now) return;

    const deck = shuffledDeck();
    updates[`${roomSnap.key}/calledNumbers`] = [];
    updates[`${roomSnap.key}/officialSequence`] = deck;
    updates[`${roomSnap.key}/currentIndex`] = 0;
    updates[`${roomSnap.key}/status`] = "calling";

    Object.entries(room.players || {}).forEach(([uid, player]) => {
      updates[`${roomSnap.key}/players/${uid}/card`] = autoFillCard(player.card);
      updates[`${roomSnap.key}/players/${uid}/cardLocked`] = true;
      updates[`${roomSnap.key}/players/${uid}/marked`] = Array.from(
        { length: 5 }, () => Array(5).fill(false)
      );
    });
  });

  if (Object.keys(updates).length) await db.ref("rooms").update(updates);
  return null;
});

/**
 * Calls one number per minute. This is intentionally aligned with the
 * scheduler's supported cadence instead of claiming a nonexistent 3-second
 * scheduler. Realtime listeners make each call appear immediately on clients.
 */
exports.tickCalledNumbers = onSchedule("every 1 minutes", async () => {
  const snap = await db.ref("rooms").orderByChild("status").equalTo("calling").get();
  if (!snap.exists()) return null;

  const updates = {};
  snap.forEach((roomSnap) => {
    const room = roomSnap.val();
    const seq = room.officialSequence || [];
    const idx = Number.isInteger(room.currentIndex) ? room.currentIndex : 0;
    if (idx >= seq.length) return;

    const called = (room.calledNumbers || []).concat(seq[idx]);
    updates[`${roomSnap.key}/calledNumbers`] = called;
    updates[`${roomSnap.key}/currentIndex`] = idx + 1;
  });

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

/**
 * The old implementation used onValueCreated for /claims/{uid}. Since a
 * client keeps the same uid node and updates its timestamp, only the first
 * claim could ever trigger. onValueWritten fixes that: every claim attempt
 * is verified, while the server still derives the truth from the card and
 * official called numbers.
 */
exports.claimBingo = onValueWritten("/rooms/{roomCode}/claims/{uid}", async (event) => {
  if (!event.data.after.exists()) return null;

  const { roomCode, uid } = event.params;
  const roomRef = db.ref(`rooms/${roomCode}`);
  const roomSnap = await roomRef.get();
  const room = roomSnap.val();
  if (!room || room.status !== "calling" || room.winnerUid) return null;

  const player = (room.players || {})[uid];
  if (!player || !Array.isArray(player.card) || player.card.length !== 5) return null;

  const calledSet = new Set(room.calledNumbers || []);
  const trueMarked = player.card.map((row) =>
    row.map((num) => Number.isInteger(num) && calledSet.has(num))
  );

  const pattern = checkWin(trueMarked);
  if (pattern) {
    // Transaction prevents two simultaneous valid claims from both becoming
    // winners. Only the first transaction that sees no winner succeeds.
    await roomRef.transaction((current) => {
      if (!current || current.status !== "calling" || current.winnerUid) return;
      current.winnerUid = uid;
      current.winningPattern = pattern;
      current.status = "finished";
      return current;
    });
  }
  return null;
});
