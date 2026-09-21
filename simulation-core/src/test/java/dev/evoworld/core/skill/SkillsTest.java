package dev.evoworld.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillsTest {

    private static final double EPSILON = 1e-9;

    private static Skills practised(String skill, int times) {
        Skills skills = Skills.untrained();
        for (int i = 0; i < times; i++) {
            skills = skills.practised(skill);
        }
        return skills;
    }

    @Test
    @DisplayName("an agent starts out a novice at everything, paying full price")
    void startsUntrained() {
        Skills skills = Skills.untrained();

        assertEquals(0.0, skills.proficiency(MoveAction.NAME), EPSILON);
        assertEquals(SkillLevel.NOVICE, skills.level(MoveAction.NAME));
        assertEquals(1.0, skills.effortMultiplier(MoveAction.NAME), EPSILON);
    }

    @Test
    @DisplayName("proficiency follows practice, halfway there after ten goes")
    void proficiencyFollowsPractice() {
        assertEquals(0.5, practised(GatherAction.NAME, 10).proficiency(GatherAction.NAME), EPSILON);
        assertEquals(SkillLevel.COMPETENT, practised(GatherAction.NAME, 10).level(GatherAction.NAME));
        assertEquals(SkillLevel.SKILLED, practised(GatherAction.NAME, 30).level(GatherAction.NAME));
    }

    @Test
    @DisplayName("practice saves effort, but never more than half, and never makes work free")
    void effortSavingIsBounded() {
        double saving = practised(MoveAction.NAME, 100_000).effortMultiplier(MoveAction.NAME);

        assertTrue(saving > 1.0 - Skills.MAX_SAVING, "never more than half off");
        assertTrue(saving < 0.51);
    }

    @Test
    @DisplayName("practising one thing does not make an agent better at another")
    void skillsAreSeparate() {
        Skills walker = practised(MoveAction.NAME, 50);

        assertEquals(1.0, walker.effortMultiplier(GatherAction.NAME), EPSILON);
    }

    @Test
    @DisplayName("practising produces new skills and leaves the old alone")
    void practisingIsImmutable() {
        Skills before = Skills.untrained();
        Skills after = before.practised(MoveAction.NAME);

        assertTrue(before.isEmpty());
        assertEquals(1, after.practiceAt(MoveAction.NAME));
    }

    @Test
    @DisplayName("a practised walker spends less energy on the same step")
    void skillMakesWorkCheaper() {
        World world = new World(WorldBounds.of(8, 8));
        Agent novice = Agent.spawn("a", "Novice", new Location(0, 0));
        Agent walker = Agent.spawn("b", "Walker", new Location(0, 0));
        walker.updateSkills(practised(MoveAction.NAME, 10));

        new MoveAction(new Location(5, 0), 2.0).execute(novice, world);
        new MoveAction(new Location(5, 0), 2.0).execute(walker, world);

        assertEquals(Needs.MAX - 2.0, novice.needs().energy(), EPSILON);
        assertEquals(Needs.MAX - 1.5, walker.needs().energy(), EPSILON, "a quarter off at half proficiency");
    }

    @Test
    @DisplayName("a practised forager gathers the same haul for less")
    void skillMakesGatheringCheaper() {
        World world = new World(WorldBounds.of(8, 8));
        world.addResource(ResourceNode.full(ResourceType.BERRIES, new Location(1, 1)));
        Agent forager = Agent.spawn("a", "Forager", new Location(1, 1));
        forager.updateSkills(practised(GatherAction.NAME, 30));

        new GatherAction(ResourceType.BERRIES, 5.0, 4.0).execute(forager, world);

        assertEquals(5.0, forager.inventory().quantityOf(ResourceType.BERRIES), EPSILON,
                "skill changes the cost, not the haul");
        assertEquals(Needs.MAX - 4.0 * 0.625, forager.needs().energy(), EPSILON);
    }

    @Test
    @DisplayName("only walking and gathering are things you get better at")
    void onlySomeActionsAreTrainable() {
        assertTrue(new MoveAction(new Location(1, 1), 1.0).trainable());
        assertTrue(new GatherAction(ResourceType.BERRIES, 1.0, 1.0).trainable());
        assertTrue(!new dev.evoworld.core.action.RestAction(1.0).trainable());
        assertTrue(!new dev.evoworld.core.action.EatAction(ResourceType.BERRIES, 1.0).trainable());
    }
}
