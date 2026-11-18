const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Unauth",
      activeRole: "super_admin",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").set({
      userId: ALICE_UID,
      studentCode: "STU-001",
      fullName: "Test Student",
      gender: "Male",
      branchName: "Colombo Main",
      status: "active",
      phone: "0771234567",
      registeredAt: "2026-11-01",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can create, read, and partially update their profile without @DocumentId or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Fernando",
      email: "alice@institutepro.lk",
      activeRole: "super_admin",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeRole: "branch_manager",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & invalid role & future timestamp must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeRole: "root_hacker",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Owner subcollections (students, payments, attendance, activity) match Kotlin create/update/list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // Empty collection list succeeds without undefined property error
  await assertSucceeds(userRef.collection("students").get());
  await assertSucceeds(userRef.collection("payments").get());
  await assertSucceeds(userRef.collection("attendance").get());
  await assertSucceeds(userRef.collection("activity").get());

  // Create student (exact Kotlin payload, no `id` key)
  await assertSucceeds(
    userRef.collection("students").doc("stu_1").set({
      userId: ALICE_UID,
      studentCode: "STU-2026-001",
      fullName: "Nethmi Perera",
      gender: "Female",
      branchName: "Colombo Main",
      status: "active",
      phone: "0771112233",
      registeredAt: "2026-08-10",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Extra @DocumentId `id` key on create must fail
  await assertFails(
    userRef.collection("students").doc("stu_2").set({
      id: "stu_2",
      userId: ALICE_UID,
      studentCode: "STU-2026-002",
      fullName: "Extra Id",
      gender: "Male",
      branchName: "Colombo Main",
      status: "active",
      phone: "0771112234",
      registeredAt: "2026-08-10",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on student preserves createdAt & userId
  await assertSucceeds(
    userRef.collection("students").doc("stu_1").update({
      status: "inactive",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create & update payment
  await assertSucceeds(
    userRef.collection("payments").doc("pay_1").set({
      userId: ALICE_UID,
      receiptNo: "RCP-2026-0001",
      studentName: "Nethmi Perera",
      studentCode: "STU-2026-001",
      amount: 25000,
      method: "cash",
      paidOn: "2026-11-17",
      voided: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    userRef.collection("payments").doc("pay_1").update({
      voided: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create attendance & activity
  await assertSucceeds(
    userRef.collection("attendance").doc("att_1_2026-11-17").set({
      userId: ALICE_UID,
      batchName: "English Mastery Batch A",
      attDate: "2026-11-17",
      presentCount: 10,
      absentCount: 2,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    userRef.collection("activity").doc("log_1").set({
      userId: ALICE_UID,
      actorName: "Alice Fernando",
      action: "payment",
      details: "Collected 25000 LKR",
      recordedAt: "2026-11-17 10:00",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const created = new Date(Date.now() - 10000);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Fernando",
      email: "alice@institutepro.lk",
      activeRole: "super_admin",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").set({
      userId: ALICE_UID,
      studentCode: "STU-001",
      fullName: "Nethmi Perera",
      gender: "Female",
      branchName: "Colombo",
      status: "active",
      phone: "0771112233",
      registeredAt: "2026-08-10",
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").update({
      status: "inactive",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_1").delete()
  );
});
