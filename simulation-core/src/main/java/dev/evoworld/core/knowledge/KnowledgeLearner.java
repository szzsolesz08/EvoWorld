package dev.evoworld.core.knowledge;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.resource.ResourceType;
import java.util.Optional;

/**
 * Works out what an experience taught, and files it.
 *
 * <p>Explicit and countable, with no model in sight. Every rule here is one
 * sentence long, and every belief it produces can be traced back to the
 * outcomes that formed it:
 *
 * <ul>
 *   <li><strong>Eating something helped this much</strong> - divide the hunger
 *       relieved by the units consumed, and that is what a unit is worth.
 *       Observed, not assumed: the agent reads its own body rather than being
 *       told what berries do.
 *   <li><strong>Drinking something helped this much</strong> - the same
 *       arithmetic on thirst.
 *   <li><strong>That was not food</strong> - a permanent refusal is the world
 *       saying so outright. Worth nothing per unit, learned in one go, and
 *       never asked again.
 *   <li><strong>Going there worked, or it did not</strong> - whether a resource
 *       was actually still where the agent went for it. This is the belief that
 *       falls when trips keep coming up empty.
 * </ul>
 *
 * <p>Nothing here knows what {@link ResourceType} says a berry is worth. The
 * agent finds out the same way anyone would.
 */
public final class KnowledgeLearner {

    /** What the agent concludes a thing is worth when told it is not food at all. */
    private static final double WORTHLESS = 0.0;

    /** What one piece of evidence says about a thing that just did its job. */
    private static final double WORTH_IT = 1.0;

    private KnowledgeLearner() {
    }

    /**
     * The knowledge that results from having tried something.
     *
     * @return updated knowledge, or the same knowledge if there was nothing to
     *     learn - most outcomes teach nothing, and that is fine
     */
    public static Knowledge learnFrom(Knowledge knowledge, Action action, ActionOutcome outcome) {
        return learnFrom(knowledge, action, outcome, Belongings.nothing());
    }

    /**
     * The same, for an agent with a kit: a thing that did its job is a thing
     * worth having, and that is how an agent finds out what its flask is for.
     *
     * @param kit what the agent had on it when the action came off
     */
    public static Knowledge learnFrom(
            Knowledge knowledge, Action action, ActionOutcome outcome, Belongings kit) {
        Knowledge learned = learnAboutTheWorld(knowledge, action, outcome);
        return outcome.succeeded()
                ? learnAboutTheKit(learned, action, kit)
                : learned;
    }

    /**
     * What an action proves about the thing that made it possible.
     *
     * <p>Nothing here says a flask is good. It says that a drink came out of
     * the pack, which could not have happened without something to keep water
     * in, and that a haul came in cheaper than bare hands would have managed.
     * The belief is about the kind of item, so one flask teaches the agent
     * about flasks.
     */
    private static Knowledge learnAboutTheKit(Knowledge knowledge, Action action, Belongings kit) {
        if (action instanceof DrinkAction drink && drink.isFromPack()) {
            return ItemKind.carrying(drink.source())
                    .map(kind -> knowledge.learned(BeliefKind.USEFULNESS, kind.name(), WORTH_IT))
                    .orElse(knowledge);
        }
        if (action instanceof GatherAction gather) {
            return kit.toolFor(gather.target())
                    .map(tool -> knowledge.learned(
                            BeliefKind.USEFULNESS, tool.kind().name(), WORTH_IT))
                    .orElse(knowledge);
        }
        return knowledge;
    }

    private static Knowledge learnAboutTheWorld(
            Knowledge knowledge, Action action, ActionOutcome outcome) {
        Optional<ResourceType> subject = action.concerns();
        if (subject.isEmpty()) {
            return knowledge;
        }
        ResourceType about = subject.get();

        if (outcome.wasImpossible()) {
            return learnItIsUseless(knowledge, action, about);
        }
        if (outcome.wasBlocked()) {
            return learnFromADisappointment(knowledge, action, about);
        }
        return learnFromSuccess(knowledge, action, outcome, about);
    }

    /**
     * The world said no, and will keep saying no.
     *
     * <p>One refusal settles it: something that cannot be eaten is worth
     * nothing per unit, and the running mean starts there rather than averaging
     * a prior it has no reason to keep.
     */
    private static Knowledge learnItIsUseless(
            Knowledge knowledge, Action action, ResourceType about) {
        BeliefKind kind = kindServedBy(action);
        return kind == null ? knowledge : knowledge.learned(kind, about, WORTHLESS);
    }

    /**
     * Went for it and it was not there.
     *
     * <p>Only counts for gathering, because that is the action whose whole
     * premise is "there is something here". A failed drink usually means the
     * agent is not thirsty, which says nothing about the spring.
     */
    private static Knowledge learnFromADisappointment(
            Knowledge knowledge, Action action, ResourceType about) {
        if (!(action instanceof GatherAction)) {
            return knowledge;
        }
        return knowledge.learned(BeliefKind.LOCATION_RELIABILITY, about, 0.0);
    }

    /**
     * It worked; how well?
     *
     * <p>A meal that leaves the agent completely full is only half a
     * measurement. If four berries take hunger from twenty to nothing, all the
     * agent has found out is that berries are worth <em>at least</em> five -
     * whatever else they had to give went nowhere. Reading that as "worth five"
     * dragged the estimate down every time the agent ate while only a little
     * hungry, which is exactly what an agent that looks after itself does.
     *
     * <p>So a cut-off reading is treated as the lower bound it is. See
     * {@link #weighLowerBound}.
     */
    private static Knowledge learnFromSuccess(
            Knowledge knowledge, Action action, ActionOutcome outcome, ResourceType about) {
        Knowledge updated = knowledge;
        Needs after = outcome.change().after().needs();

        // Only eating says anything about nourishment. Putting something down
        // also empties the hands, and would otherwise read as "worth nothing".
        if (action instanceof EatAction) {
            double consumed = -outcome.change().carriedChange();
            double hungerRelieved = -outcome.change().hungerChange();
            if (consumed > 0.0 && hungerRelieved >= 0.0) {
                updated = observe(
                        updated,
                        BeliefKind.NOURISHMENT,
                        about,
                        hungerRelieved / consumed,
                        after.hunger() <= Needs.MIN);
            }
        }

        if (action instanceof DrinkAction drink) {
            double thirstRelieved = -outcome.change().thirstChange();
            if (thirstRelieved > 0.0) {
                updated = observe(
                        updated,
                        BeliefKind.HYDRATION,
                        about,
                        thirstRelieved / drink.gulp(),
                        after.thirst() <= Needs.MIN);
            }
        }

        if (action instanceof GatherAction || action instanceof DrinkAction) {
            updated = updated.learned(BeliefKind.LOCATION_RELIABILITY, about, 1.0);
        }
        return updated;
    }

    /** Files one reading, full or cut off. */
    private static Knowledge observe(
            Knowledge knowledge,
            BeliefKind kind,
            ResourceType about,
            double reading,
            boolean cutOff) {
        if (!cutOff) {
            return knowledge.learned(kind, about, reading);
        }
        return weighLowerBound(knowledge, kind, about, reading);
    }

    /**
     * What a reading that ran out of need is worth: "at least this much".
     *
     * <ul>
     *   <li>Nothing tested yet: the only real evidence is a floor, and taking
     *       it as the value would start the belief too low - so it waits for a
     *       reading that did not run out.
     *   <li>The floor sits at or under what the agent already believes: that
     *       agrees with the belief, so it counts as confirming it - one more
     *       observation of the same estimate, more confidence, no movement.
     *   <li>The floor is above the belief: the thing is worth more than the
     *       agent thought, and at least this much, so the estimate moves up.
     * </ul>
     *
     * <p>A cut-off reading can therefore raise an estimate or firm it up, and
     * never drag it down.
     */
    private static Knowledge weighLowerBound(
            Knowledge knowledge, BeliefKind kind, ResourceType about, double floor) {
        Belief held = knowledge.believe(kind, about);
        if (held.isAssumed()) {
            return knowledge;
        }
        return knowledge.learned(kind, about, Math.max(floor, held.estimate()));
    }

    /** Which belief an action would inform if it turned out to be impossible. */
    private static BeliefKind kindServedBy(Action action) {
        return switch (action.name()) {
            case EatAction.NAME -> BeliefKind.NOURISHMENT;
            case DrinkAction.NAME -> BeliefKind.HYDRATION;
            default -> null;
        };
    }
}
