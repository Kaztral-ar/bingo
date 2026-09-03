/**
 * Cloud Functions — the authoritative "backend" the spec calls for.
 * The Android client never generates the official call sequence and never
 * decides who won; it only reads this state and submits claims for these
 * functions to check. Deploy with: firebase deploy --only functions
 */
const { onValueWritten, onValueCreated } = require("firebase-functions/v2/database");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.database();

const FILL_SECONDS = 120;
const CALL_INTERVAL_MS = 3000; // pace between called numbers

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
  const grid = card ? card.map((row) => row.slice()) : Array.from({ length: 5 }, () => Array(5).fill(0));
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

/**
 * Host sets status -> "filling". We stamp the real deadline and schedule the
 * transition to "calling" (spec sections 7, 18, 19: server-timestamp-driven,
 * synchronized 2-minute timer; auto-fill incomplete cards).
 */
exports.onRoomStatusChange = onValueWritten("/rooms/{roomCode}/status", async (event) => {
  const after = event.data.after.val();
  if (after !== "filling") return null;

  const roomCode = event.params.roomCode;
  const roomRef = db.ref(`rooms/${roomCode}`);
  const deadline = Date.now() + FILL_SECONDS * 1000;
  await roomRef.child("fillDeadline").set(admin.database.ServerValue.TIMESTAMP);
  await roomRef.child("fillDeadline").set(deadline);
  return null;
});

/**
 * Scheduled sweep: for any room whose fill deadline has passed and is still
 * "filling", auto-fill incomplete cards, lock them, and start the shared
 * calling sequence.
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
    updates[`${roomSnap.key}/officialSequence`] = deck; // private-ish; only functions read this
    updates[`${roomSnap.key}/currentIndex`] = 0;
    updates[`${roomSnap.key}/status`] = "calling";

    Object.entries(room.players || {}).forEach(([uid, player]) => {
      const filled = autoFillCard(player.card);
      updates[`${roomSnap.key}/players/${uid}/card`] = filled;
      updates[`${roomSnap.key}/players/${uid}/cardLocked`] = true;
    });
  });
  if (Object.keys(updates).length) await db.ref("rooms").update(updates);
  return null;
});

/** Calls one number at a time into every room currently in the "calling" state. */
exports.tickCalledNumbers = onSchedule(`every 1 minutes`, async () => {
  // Scheduler minimum granularity is 1 minute; for snappier calling pace this
  // function is also invoked by a lightweight self-rescheduling mechanism —
  // see README "Faster calling cadence" for the Cloud Tasks variant.
  const snap = await db.ref("rooms").orderByChild("status").equalTo("calling").get();
  if (!snap.exists()) return null;

  const updates = {};
  snap.forEach((roomSnap) => {
    const room = roomSnap.val();
    const seq = room.officialSequence || [];
    const idx = room.currentIndex || 0;
    if (idx >= seq.length) return;

    const called = (room.calledNumbers || []).concat(seq[idx]);
    updates[`${roomSnap.key}/calledNumbers`] = called;
    updates[`${roomSnap.key}/currentIndex`] = idx + 1;
  });
  if (Object.keys(updates).length) await db.ref("rooms").update(updates);
  return null;
});

function checkWin(card, marked) {
  const isMarked = (r, c) => !!(marked && marked[r] && marked[r][c]);
  for (let r = 0; r < 5; r++) if ([0, 1, 2, 3, 4].every((c) => isMarked(r, c))) return `Row ${r + 1}`;
  for (let c = 0; c < 5; c++) if ([0, 1, 2, 3, 4].every((r) => isMarked(r, c))) return `Column ${c + 1}`;
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, i))) return "Diagonal";
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, 4 - i))) return "Diagonal";
  return null;
}

/**
 * A player writes rooms/{code}/claims/{uid} to assert a bingo. This function
 * independently re-derives the truth from the player's own stored card and
 * the official called-numbers list — the client's "marked" state is never
 * trusted on its own (spec sections 9, 12, 22).
 */
exports.claimBingo = onValueCreated("/rooms/{roomCode}/claims/{uid}", async (event) => {
  const { roomCode, uid } = event.params;
  const roomRef = db.ref(`rooms/${roomCode}`);
  const roomSnap = await roomRef.get();
  const room = roomSnap.val();
  if (!room || room.status !== "calling" || room.winnerUid) return null;

  const player = (room.players || {})[uid];
  if (!player || !player.card) return null;

  const calledSet = new Set(room.calledNumbers || []);
  // Recompute "marked" server-side: a cell counts only if its number is in
  // the official called list, ignoring whatever the client claims it marked.
  const trueMarked = player.card.map((row) => row.map((num) => calledSet.has(num)));

  const pattern = checkWin(player.card, trueMarked);
  if (pattern) {
    await roomRef.update({
      winnerUid: uid,
      winningPattern: pattern,
      status: "finished",
    });
  }
  return null;
});
