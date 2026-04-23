import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";

initializeApp();

const SQUADS_PATH = "squads";
const RECORDS_PATH = "records";
const INVITES_PATH = "invites";
const MEMBERS_PATH = "members";

const RECORD_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
const INVITE_RETENTION_MS = 30 * 24 * 60 * 60 * 1000;
const DELETE_BATCH_SIZE = 500;

type RecordMap = Record<string, unknown>;

function chunk<T>(values: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let index = 0; index < values.length; index += size) {
    chunks.push(values.slice(index, index + size));
  }
  return chunks;
}

async function deleteRecordBatch(
  squadId: string,
  recordIds: string[],
): Promise<number> {
  if (recordIds.length === 0) return 0;

  const updates: Record<string, null> = {};
  for (const recordId of recordIds) {
    updates[`${SQUADS_PATH}/${squadId}/${RECORDS_PATH}/${recordId}`] = null;
  }

  await getDatabase().ref().update(updates);
  return recordIds.length;
}

async function cleanupSquadRecords(cutoffMs: number): Promise<number> {
  const squadsSnapshot = await getDatabase().ref(SQUADS_PATH).get();
  if (!squadsSnapshot.exists()) return 0;

  let deletedCount = 0;
  const squadSnapshots: Array<ReturnType<typeof squadsSnapshot.child>> = [];
  squadsSnapshot.forEach((child) => {
    squadSnapshots.push(child);
    return false;
  });

  for (const squadSnapshot of squadSnapshots) {
    const squadId = squadSnapshot.key;
    if (!squadId) continue;

    const recordsRef = getDatabase()
      .ref(`${SQUADS_PATH}/${squadId}/${RECORDS_PATH}`)
      .orderByChild("timestamp")
      .endAt(cutoffMs);

    const oldRecordsSnapshot = await recordsRef.get();
    if (!oldRecordsSnapshot.exists()) continue;

    const candidateIds: string[] = [];
    oldRecordsSnapshot.forEach((recordSnapshot) => {
      const value = recordSnapshot.val() as RecordMap | null;
      const timestamp = Number(value?.timestamp ?? 0);
      if (timestamp > 0 && timestamp <= cutoffMs && recordSnapshot.key) {
        candidateIds.push(recordSnapshot.key);
      }
      return false;
    });

    for (const batch of chunk(candidateIds, DELETE_BATCH_SIZE)) {
      deletedCount += await deleteRecordBatch(squadId, batch);
    }

    const membersSnapshot = await getDatabase()
      .ref(`${SQUADS_PATH}/${squadId}/${MEMBERS_PATH}`)
      .get();

    let hasMembers = false;
    membersSnapshot.forEach(() => {
      hasMembers = true;
      return true;
    });
    const remainingSnapshot = await getDatabase()
      .ref(`${SQUADS_PATH}/${squadId}/${RECORDS_PATH}`)
      .limitToFirst(1)
      .get();

    if (!hasMembers && !remainingSnapshot.exists()) {
      await getDatabase().ref(`${SQUADS_PATH}/${squadId}`).remove();
      logger.info("Removed empty squad container", { squadId });
    }
  }

  return deletedCount;
}

async function cleanupInvites(cutoffMs: number): Promise<number> {
  const invitesSnapshot = await getDatabase().ref(INVITES_PATH).get();
  if (!invitesSnapshot.exists()) return 0;

  const updates: Record<string, null> = {};
  let deletedCount = 0;

  invitesSnapshot.forEach((inviteSnapshot) => {
    const inviteCode = inviteSnapshot.key;
    const value = inviteSnapshot.val() as RecordMap | null;
    const createdAt = Number(value?.createdAt ?? 0);
    if (!inviteCode || createdAt <= 0 || createdAt > cutoffMs) return false;

    updates[`${INVITES_PATH}/${inviteCode}`] = null;
    deletedCount += 1;
    return false;
  });

  if (deletedCount > 0) {
    await getDatabase().ref().update(updates);
  }

  return deletedCount;
}

export const cleanupRealtimeDatabase = onSchedule(
  {
    schedule: "every 24 hours",
    timeZone: "America/Mexico_City",
    region: "us-central1",
    memory: "256MiB",
    timeoutSeconds: 540,
  },
  async () => {
    const now = Date.now();
    const recordCutoff = now - RECORD_RETENTION_MS;
    const inviteCutoff = now - INVITE_RETENTION_MS;

    logger.info("Starting RTDB cleanup", {
      recordCutoff,
      inviteCutoff,
      recordRetentionDays: RECORD_RETENTION_MS / (24 * 60 * 60 * 1000),
      inviteRetentionDays: INVITE_RETENTION_MS / (24 * 60 * 60 * 1000),
    });

    const deletedRecords = await cleanupSquadRecords(recordCutoff);
    const deletedInvites = await cleanupInvites(inviteCutoff);

    logger.info("Completed RTDB cleanup", {
      deletedRecords,
      deletedInvites,
    });
  },
);
