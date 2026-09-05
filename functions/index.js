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
const BINGO_LETTERS = ["B", "I", "N", "G", "O"];

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

function linesForMarks(marked) {
  const lines = [];
  const isMarked = (r, c) => !!(marked[r] && marked[r][c]);
  for (let r = 0; r < 5; r++) {
    if ([0, 1, 2, 3, 4].every((c) => isMarked(r, c))) lines.push(`R${r + 1}`);
  }
  for (let c = 0; c < 5; c++) {
    if ([0, 1, 2, 3, 4].every((r) => isMarked(r, c))) lines.push(`C${c + 1}`);
  }
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, i))) lines.push("D1");
  if ([0, 1, 2, 3, 4].every((i) => isMarked(i, 4 - i))) lines.push("D2");
  return lines;
}

function lineDescription(line) {
  if (line.startsWith("R")) return `Row ${Number(line.slice(1))}`;
  if (line.startsWith("C")) return `Column ${Number(line.slice(1))}`;
  return "Diagonal";
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

exports.requestCalling = onValueWritten("/rooms/{roomCode}/startCallingRequests/{uid}", async (event) => {
  if (!event.data.after.exists()) return null;
  const { roomCode, uid } = event.params;
  const roomSnap = await db.ref(`rooms/${roomCode}`).get();
  const room = roomSnap.val();
  if (!room || room.status !== "filling" || room.host !== uid) return null;
  await transitionToCalling(roomCode);
  return null;
});

exports.sweepExpiredFillTimers = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const snap = await db.ref("rooms").orderByChild("status").equalTo("filling").get();
  if (!snap.exists()) return null;

  for (const roomCode of Object.keys(snap.val() || {})) {
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
      updates[`${roomCode}/players/${uid}/completedLines`] = [];
      updates[`${roomCode}/players/${uid}/bingoCount`] = 0;
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

  if (!result.committed) return false;

  // A called number is automatically marked on BOTH cards.
  // A completed row/column/diagonal is counted once as the next B-I-N-G-O letter.
  const room = result.snapshot.val();
  const calledSet = new Set(room.calledNumbers || []);
  const updates = {};
  let winnerUid = null;
  let winnerPattern = null;

  for (const [uid, player] of Object.entries(room.players || {})) {
    const card = normalizeCard(player.card);
    const marked = card.map((row) => row.map((num) => calledSet.has(num)));
    const completedNow = linesForMarks(marked);
    const previous = Array.isArray(player.completedLines) ? player.completedLines : [];
    const completed = [...new Set(previous.concat(completedNow))];
    const bingoCount = Math.min(completed.length, BINGO_LETTERS.length);

    updates[`rooms/${roomCode}/players/${uid}/marked`] = marked;
    updates[`rooms/${roomCode}/players/${uid}/completedLines`] = completed;
    updates[`rooms/${roomCode}/players/${uid}/bingoCount`] = bingoCount;

    if (!winnerUid && bingoCount >= 5) {
      winnerUid = uid;
      const newest = completedNow.filter((line) => !previous.includes(line));
      winnerPattern = newest.length > 0
        ? lineDescription(newest[0])
        : "BINGO";
    }
  }

  if (winnerUid) {
    updates[`rooms/${roomCode}/winnerUid`] = winnerUid;
    updates[`rooms/${roomCode}/winningPattern`] = winnerPattern;
    updates[`rooms/${roomCode}/status`] = "finished";
  }

  await db.ref().update(updates);
  return true;
}

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

exports.claimBingo = onValueWritten("/rooms/{roomCode}/claims/{uid}", async (event) => {
  // Kept for backward compatibility with older clients. Winner state is now
  // calculated automatically by requestNextNumber; clients no longer need to claim.
  return null;
});
