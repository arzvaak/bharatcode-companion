package com.bharatcode.companion.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompanionPolicyTest {
    @Test
    void unknownOrCorruptDataAlwaysFailsClosed() {
        assertEquals(CompanionPolicy.SummonAction.FAIL_CLOSED, CompanionPolicy.summon(
                false, CompanionPolicy.State.NONE, CompanionPolicy.Presence.ABSENT, true).action());
        assertEquals(CompanionPolicy.DismissAction.FAIL_CLOSED, CompanionPolicy.dismiss(
                false, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.PRESENT, true).action());
    }

    @Test
    void activeEntityIsRecalledNotDuplicated() {
        assertEquals(CompanionPolicy.SummonAction.RECALL, CompanionPolicy.summon(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.PRESENT, true).action());
    }

    @Test
    void missingActiveProjectionIsRestoredFromAuthority() {
        assertEquals(CompanionPolicy.SummonAction.PROJECT, CompanionPolicy.summon(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.ABSENT, true).action());
        assertEquals(CompanionPolicy.SummonAction.FAIL_CLOSED, CompanionPolicy.summon(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.UNKNOWN, true).action());
    }

    @Test
    void dormantCompanionIsProjectedOrReplacedWithoutDuplication() {
        assertEquals(CompanionPolicy.SummonAction.PROJECT, CompanionPolicy.summon(
                true, CompanionPolicy.State.DORMANT, CompanionPolicy.Presence.ABSENT, true).action());
        assertEquals(CompanionPolicy.SummonAction.RESPAWN, CompanionPolicy.summon(
                true, CompanionPolicy.State.DORMANT, CompanionPolicy.Presence.PRESENT, true).action());
        assertEquals(CompanionPolicy.SummonAction.FAIL_CLOSED, CompanionPolicy.summon(
                true, CompanionPolicy.State.DORMANT, CompanionPolicy.Presence.UNKNOWN, true).action());
    }

    @Test
    void ownerMismatchFailsClosed() {
        assertEquals(CompanionPolicy.SummonAction.FAIL_CLOSED, CompanionPolicy.summon(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.PRESENT, false).action());
        assertEquals(CompanionPolicy.SummonAction.FAIL_CLOSED, CompanionPolicy.summon(
                true, CompanionPolicy.State.DORMANT, CompanionPolicy.Presence.PRESENT, false).action());
        assertEquals(CompanionPolicy.DismissAction.FAIL_CLOSED, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.PRESENT, false).action());
    }

    @Test
    void noRecordCreatesANewCompanion() {
        assertEquals(CompanionPolicy.SummonAction.CREATE, CompanionPolicy.summon(
                true, CompanionPolicy.State.NONE, CompanionPolicy.Presence.ABSENT, true).action());
    }

    @Test
    void dismissMarksAnAbsentActiveRecordDormant() {
        assertEquals(CompanionPolicy.DismissAction.DISMISS, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.PRESENT, true).action());
        assertEquals(CompanionPolicy.DismissAction.DISMISS, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.ABSENT, true).action());
        assertEquals(CompanionPolicy.DismissAction.FAIL_CLOSED, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.ACTIVE, CompanionPolicy.Presence.UNKNOWN, true).action());
    }

    @Test
    void dismissOfDormantOrMissingRecordIsANoOp() {
        assertEquals(CompanionPolicy.DismissAction.NO_OP, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.DORMANT, CompanionPolicy.Presence.ABSENT, true).action());
        assertEquals(CompanionPolicy.DismissAction.NO_OP, CompanionPolicy.dismiss(
                true, CompanionPolicy.State.NONE, CompanionPolicy.Presence.ABSENT, true).action());
    }
}
