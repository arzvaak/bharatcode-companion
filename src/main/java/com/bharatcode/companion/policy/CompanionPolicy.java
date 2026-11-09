package com.bharatcode.companion.policy;

/**
 * Pure decision table for the companion lifecycle. The SavedData record is the
 * single authority; the live entity is only a projection of it. No Minecraft
 * types are used so the rules can be checked without booting a game.
 */
public final class CompanionPolicy {
    private CompanionPolicy() {
    }

    public enum State {
        NONE,
        ACTIVE,
        DORMANT
    }

    public enum Presence {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    public enum SummonAction {
        CREATE,
        RECALL,
        PROJECT,
        RESPAWN,
        FAIL_CLOSED
    }

    public enum DismissAction {
        DISMISS,
        NO_OP,
        FAIL_CLOSED
    }

    public record Decision<A>(A action, String reason) {
    }

    public static Decision<SummonAction> summon(
            boolean dataUsable,
            State state,
            Presence presence,
            boolean ownerMatches) {
        if (!dataUsable) {
            return new Decision<>(SummonAction.FAIL_CLOSED,
                    "saved data is unreadable or uses an unknown schema version");
        }

        return switch (state) {
            case NONE -> new Decision<>(SummonAction.CREATE, "no companion record exists for this owner");
            case ACTIVE -> {
                if (presence == Presence.PRESENT) {
                    if (!ownerMatches) {
                        yield new Decision<>(SummonAction.FAIL_CLOSED,
                                "the active companion is bound to another owner");
                    }
                    yield new Decision<>(SummonAction.RECALL, "active companion found");
                }
                if (presence == Presence.ABSENT) {
                    yield new Decision<>(SummonAction.PROJECT,
                            "the active projection is gone; restoring it from the authority record");
                }
                yield new Decision<>(SummonAction.FAIL_CLOSED,
                        "the active companion's location could not be verified");
            }
            case DORMANT -> {
                if (presence == Presence.PRESENT) {
                    if (!ownerMatches) {
                        yield new Decision<>(SummonAction.FAIL_CLOSED,
                                "a dormant record has a live entity bound to another owner");
                    }
                    yield new Decision<>(SummonAction.RESPAWN,
                            "replacing a stale live projection of a dormant companion");
                }
                if (presence == Presence.ABSENT) {
                    yield new Decision<>(SummonAction.PROJECT,
                            "projecting the dormant companion from its authority record");
                }
                yield new Decision<>(SummonAction.FAIL_CLOSED,
                        "the dormant companion's location could not be verified");
            }
        };
    }

    public static Decision<DismissAction> dismiss(
            boolean dataUsable,
            State state,
            Presence presence,
            boolean ownerMatches) {
        if (!dataUsable) {
            return new Decision<>(DismissAction.FAIL_CLOSED,
                    "saved data is unreadable or uses an unknown schema version");
        }

        return switch (state) {
            case NONE, DORMANT -> new Decision<>(DismissAction.NO_OP, "no active companion is registered");
            case ACTIVE -> {
                if (presence == Presence.PRESENT) {
                    if (!ownerMatches) {
                        yield new Decision<>(DismissAction.FAIL_CLOSED,
                                "the active companion is bound to another owner");
                    }
                    yield new Decision<>(DismissAction.DISMISS, "active companion found");
                }
                if (presence == Presence.ABSENT) {
                    yield new Decision<>(DismissAction.DISMISS,
                            "no live projection exists; marking the authority record dormant");
                }
                yield new Decision<>(DismissAction.FAIL_CLOSED,
                        "the active companion's location could not be verified");
            }
        };
    }
}
