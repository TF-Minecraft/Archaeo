package net.tfminecraft.archaeo.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CatalogValuesTest {
    @Before public void setUp() { MockBukkit.mock(); }
    @After public void tearDown() { MockBukkit.unmock(); }
    @Test
    public void parsesProfilesWithoutTurningUnknownListedProfilesIntoObjects() {
        for (FindProfile profile : FindProfile.values()) {
            assertEquals(profile, FindProfile.fromConfig(" " + profile.name() + " "));
            assertEquals(profile, FindProfile.parseListed(profile.id()).orElseThrow());
        }
        for (String raw : new String[]{null, " ", "unknown"}) {
            assertEquals(FindProfile.OBJECT, FindProfile.fromConfig(raw));
            assertTrue(FindProfile.parseListed(raw).isEmpty());
        }
    }

    @Test
    public void rarityStylesRetainLegacyAliasAndSafeLabelDefaults() {
        for (ArtifactRarity rarity : ArtifactRarity.values()) {
            assertEquals(rarity, ArtifactRarity.fromConfig(" " + rarity.name() + " "));
            RarityStyle style = rarity.style();
            assertEquals(rarity.id(), style.id());
            assertEquals(rarity.name(), style.label());
            assertEquals(rarity.color(), style.color());
            assertEquals(ChatColor.GRAY + "Rarity: " + rarity.color() + rarity.name(), rarity.loreLine());
        }
        assertEquals(ArtifactRarity.RARE, ArtifactRarity.fromConfig("uncommon"));
        for (String raw : new String[]{null, " ", "unknown"}) {
            assertEquals(ArtifactRarity.COMMON, ArtifactRarity.fromConfig(raw));
        }
        for (String label : new String[]{null, " "}) {
            assertEquals(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + "custom",
                    new RarityStyle("custom", label, null).loreLine());
        }
    }

    @Test
    public void breakShapesAndDigClassesAcceptTheirDocumentedAliases() {
        for (String raw : new String[]{null, " ", "unknown", "down"}) {
            assertEquals(BreakShape.DOWN, BreakShape.parse(raw));
        }
        for (String raw : List.of("around", "area", "ring", "plus", "cross")) {
            assertEquals(BreakShape.AROUND, BreakShape.parse(" " + raw.toUpperCase() + " "));
        }
        for (String raw : List.of("random", "shuffle")) assertEquals(BreakShape.RANDOM, BreakShape.parse(raw));
        for (String raw : new String[]{null, " ", "unknown"}) assertNull(DigClass.parse(raw));
        for (String raw : List.of("pick", "pickaxe", "pico")) assertEquals(DigClass.PICK, DigClass.parse(raw));
        for (String raw : List.of("shovel", "spade", "pala")) assertEquals(DigClass.SHOVEL, DigClass.parse(raw));
        for (String raw : List.of("none", "any", "hand")) assertEquals(DigClass.NONE, DigClass.parse(raw));
        assertEquals(DigClass.NONE, DigClass.of((Material) null));
        assertEquals(DigClass.NONE, DigClass.of(Material.AIR));
        assertEquals(DigClass.NONE, DigClass.of((org.bukkit.inventory.ItemStack) null));
    }

    @Test
    public void conservationGradesIncludeTheirBoundaryAndRejectLowerValues() {
        ConservationSettings settings = ConservationSettings.defaults();
        assertEquals(40, settings.buriedMin());
        assertEquals(100, settings.buriedMax());
        assertEquals(1.0, settings.bias(), 0);
        assertEquals(4, settings.depthPenalty());
        assertEquals(10, settings.disturbedPenalty());
        List<ConservationGrade> grades = settings.grades();
        for (int i = 0; i < grades.size(); i++) {
            ConservationGrade grade = grades.get(i);
            assertSame(grade, settings.grade(grade.minPercent()));
            assertEquals(grade.label(), settings.gradeLabel(grade.minPercent()));
            assertSame(i + 1 == grades.size() ? null : grades.get(i + 1), settings.grade(grade.minPercent() - 1));
        }
        assertNull(settings.grade(0));
        assertEquals("", settings.gradeLabel(0));
        assertEquals("Intact", settings.gradeLabel(100));
    }

    @Test
    public void materialDescriptionsAndPanesUseSafeFallbacks() {
        for (String name : new String[]{null, " "}) {
            FindMaterial material = new FindMaterial("bone", name, 1, null, List.of());
            assertEquals("The piece is clean.", material.firstStepDone());
            assertEquals(Material.WHITE_STAINED_GLASS_PANE, material.cleanPane());
            assertEquals("clean", material.firstStepVerb());
            assertEquals("Cleaning", material.firstStepGerund());
        }
        assertEquals(Material.WHITE_STAINED_GLASS_PANE,
                new FindMaterial("bone", "Bone", 1, Material.AIR, List.of()).cleanPane());
        FindMaterial material = new FindMaterial("bone", "BONE", 1, Material.BLUE_STAINED_GLASS_PANE, List.of());
        assertEquals("The bone is clean.", material.firstStepDone());
        assertEquals(Material.BLUE_STAINED_GLASS_PANE, material.cleanPane());
    }

    @Test
    public void interpretationHintsWeightMatchesAndRestrictOnlyExplicitProfiles() {
        InterpretationTemplate unrestricted = new InterpretationTemplate("tool", "function", "Tool", null, null, null);
        assertTrue(unrestricted.appliesTo(null));
        assertTrue(unrestricted.appliesTo(FindProfile.ANIMAL));
        assertFalse(unrestricted.suggestedBy(Set.of("metal")));
        assertFalse(unrestricted.suggestedFor("pot"));
        Set<FindProfile> profiles = new HashSet<>(Set.of(FindProfile.OBJECT));
        InterpretationTemplate option = new InterpretationTemplate("tool", "function", "Tool", Set.of("metal"), Set.of("knife"), profiles);
        profiles.clear();
        assertTrue(option.appliesTo(FindProfile.OBJECT));
        assertFalse(option.appliesTo(FindProfile.ANIMAL));
        assertFalse(option.appliesTo(null));
        assertFalse(option.suggestedBy(null));
        assertFalse(option.suggestedBy(Set.of()));
        assertFalse(option.suggestedBy(Set.of("wood")));
        assertTrue(option.suggestedBy(Set.of("metal")));
        assertFalse(option.suggestedFor(null));
        assertFalse(option.suggestedFor(" "));
        assertFalse(option.suggestedFor("pot"));
        assertTrue(option.suggestedFor("knife"));
        assertFalse(new InterpretationTemplate("id", "type", "name", Set.of(), Set.of(), Set.of())
                .suggestedBy(Set.of("metal")));
        assertThrows(UnsupportedOperationException.class, () -> option.profiles().clear());
    }
}
