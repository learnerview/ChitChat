/*
 * ChitChat v2 core-redesign migration.
 *
 * Run BEFORE deploying the new application version:
 *   mongosh "<MONGODB_URI>" scripts/migrate-v2.js
 *
 * The script is idempotent: re-running it skips already-migrated documents.
 * It performs:
 *   1. users.password        -> users.passwordHash
 *   2. conversation_members  <- conversations.participantIds
 *   3. conversations.directKey for DMs (detects duplicate-DM conflicts)
 *   4. messages.sequence backfill (ordered by createdAt, _id) +
 *      conversations.lastMessageSequence / lastMessageAt
 *   5. Removes legacy fields: participantIds, readBy, edited, deleted
 *   6. Removes legacy _class discriminators (old package names)
 *
 * Review the printed report before deploying. Do NOT deploy the new version
 * until this script completes with no conflicts.
 */

const now = () => new Date();
let renamedPasswords = 0;
let membersCreated = 0;
let directKeysSet = 0;
let directKeyConflicts = [];
let conversationsBackfilled = 0;
let messagesSequenced = 0;
let alreadySequenced = 0;

// ---------------------------------------------------------------------------
// 1. users.password -> passwordHash
// ---------------------------------------------------------------------------
print("== 1/6 users.password -> passwordHash");
db.users.find({ password: { $exists: true } }).forEach((user) => {
  db.users.updateOne(
    { _id: user._id },
    { $set: { passwordHash: user.password }, $unset: { password: "" } }
  );
  renamedPasswords++;
});
print(`  renamed: ${renamedPasswords}`);

// ---------------------------------------------------------------------------
// 2. conversation_members <- participantIds
// ---------------------------------------------------------------------------
print("== 2/6 building conversation_members from participantIds");
db.conversations.find({ participantIds: { $exists: true, $ne: null } }).forEach((conv) => {
  const ids = conv.participantIds || [];
  const joinedAt = conv.createdAt || now();
  ids.forEach((userId) => {
    if (!userId) return;
    const result = db.conversation_members.updateOne(
      { conversationId: String(conv._id), userId: userId },
      {
        $setOnInsert: {
          tenantId: conv.tenantId,
          conversationId: String(conv._id),
          userId: userId,
          role: conv.createdBy === userId ? "OWNER" : "MEMBER",
          joinedAt: joinedAt,
          lastReadSequence: 0,
          muted: false,
          archived: false,
          pinned: false,
          notificationLevel: "ALL"
        }
      },
      { upsert: true }
    );
    if (result.upsertedCount > 0) membersCreated++;
  });
});
print(`  members created: ${membersCreated}`);

// ---------------------------------------------------------------------------
// 3. conversations.directKey for DMs
// ---------------------------------------------------------------------------
print("== 3/6 computing directKey for DM conversations");
db.conversations.find({ type: "DM" }).forEach((conv) => {
  const ids = conv.participantIds || [];
  const unique = [...new Set(ids.filter(Boolean))];

  if (unique.length !== 2) {
    directKeyConflicts.push({
      conversationId: String(conv._id),
      reason: `DM has ${unique.length} distinct participants (expected 2)`
    });
    return;
  }

  const key = unique[0] <= unique[1]
    ? `${unique[0]}:${unique[1]}`
    : `${unique[1]}:${unique[0]}`;

  const duplicate = db.conversations.findOne({
    _id: { $ne: conv._id },
    tenantId: conv.tenantId,
    directKey: key
  });
  if (duplicate) {
    directKeyConflicts.push({
      conversationId: String(conv._id),
      duplicateOf: String(duplicate._id),
      directKey: key,
      reason: "two DM conversations for the same user pair in one tenant"
    });
    return;
  }

  if (conv.directKey !== key) {
    db.conversations.updateOne({ _id: conv._id }, { $set: { directKey: key } });
  }
  directKeysSet++;
});
print(`  directKey set: ${directKeysSet}, conflicts: ${directKeyConflicts.length}`);

// ---------------------------------------------------------------------------
// 4. messages.sequence + conversation.lastMessageSequence / lastMessageAt
// ---------------------------------------------------------------------------
print("== 4/6 backfilling message sequences");
db.conversations.find({}).forEach((conv) => {
  // Cover both storage forms: conversationId stored as ObjectId or as String.
  const messageQuery = { $or: [{ conversationId: conv._id }, { conversationId: String(conv._id) }] };

  const existing = db.messages.findOne(
    { ...messageQuery, sequence: { $exists: true } },
    { sort: { sequence: -1 } }
  );
  if (existing) {
    alreadySequenced++;
    return; // already migrated
  }

  let sequence = 0;
  let lastCreatedAt = null;
  db.messages
    .find(messageQuery)
    .sort({ createdAt: 1, _id: 1 })
    .forEach((message) => {
      sequence++;
      const update = { $set: { sequence: sequence } };
      if (message.tenantId === undefined && conv.tenantId !== undefined) {
        update.$set.tenantId = conv.tenantId;
      }
      db.messages.updateOne({ _id: message._id }, update);
      lastCreatedAt = message.createdAt || lastCreatedAt;
      messagesSequenced++;
    });

  db.conversations.updateOne(
    { _id: conv._id },
    {
      $set: {
        lastMessageSequence: sequence,
        lastMessageAt: lastCreatedAt || conv.updatedAt || conv.createdAt || now()
      }
    }
  );
  conversationsBackfilled++;
});
print(
  `  conversations backfilled: ${conversationsBackfilled}` +
  `, already sequenced: ${alreadySequenced}` +
  `, messages sequenced: ${messagesSequenced}`
);

// ---------------------------------------------------------------------------
// 5. drop legacy fields
// ---------------------------------------------------------------------------
print("== 5/6 removing legacy fields");
const unsetConversations = db.conversations.updateMany(
  { participantIds: { $exists: true } },
  { $unset: { participantIds: "", readBy: "" } }
);
const unsetMessages = db.messages.updateMany(
  { $or: [{ readBy: { $exists: true } }, { edited: { $exists: true } }, { deleted: { $exists: true } }] },
  { $unset: { readBy: "", edited: "", deleted: "" } }
);
print(`  conversations cleaned: ${unsetConversations.modifiedCount}`);
print(`  messages cleaned: ${unsetMessages.modifiedCount}`);

// ---------------------------------------------------------------------------
// 6. drop legacy _class discriminators
// ---------------------------------------------------------------------------
// Old documents store _class pointing at removed classes
// (com.learnerview.chitchat.entities.*), which breaks type resolution in the
// new package layout. Without _class Spring Data uses the repository's
// declared domain type, which is what we want.
print("== 6/6 removing legacy _class discriminators");
let classRemoved = 0;
db.getCollectionNames().forEach((name) => {
  const result = db.getCollection(name).updateMany(
    { _class: { $exists: true } },
    { $unset: { _class: "" } }
  );
  classRemoved += result.modifiedCount;
});
print(`  _class removed: ${classRemoved}`);

// ---------------------------------------------------------------------------
print("\n== MIGRATION REPORT");
print(`  passwords renamed:        ${renamedPasswords}`);
print(`  members created:          ${membersCreated}`);
print(`  directKeys set:           ${directKeysSet}`);
print(`  conversations sequenced:  ${conversationsBackfilled}`);
print(`  messages sequenced:       ${messagesSequenced}`);
if (directKeyConflicts.length > 0) {
  print("\n!! CONFLICTS - resolve these manually before deploying:");
  directKeyConflicts.forEach((conflict) => print(`   ${JSON.stringify(conflict)}`));
} else {
  print("\nNo conflicts. Safe to deploy the new version.");
}
