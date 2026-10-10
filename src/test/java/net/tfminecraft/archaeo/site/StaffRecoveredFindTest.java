package net.tfminecraft.archaeo.site;

import net.tfminecraft.archaeo.config.ArtifactTemplate;
import net.tfminecraft.archaeo.config.CatalogRegistry;
import net.tfminecraft.archaeo.config.ConservationSettings;
import net.tfminecraft.archaeo.config.FindProfile;
import net.tfminecraft.archaeo.config.PickSettings;
import net.tfminecraft.archaeo.item.ItemRef;
import net.tfminecraft.archaeo.model.BuriedFind;
import net.tfminecraft.archaeo.model.FindState;
import net.tfminecraft.archaeo.model.Site;
import net.tfminecraft.archaeo.model.SiteStatus;
import net.tfminecraft.archaeo.model.StratumBand;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StaffRecoveredFindTest {
    private final SiteGenerator generator = generator();
    private final UUID recipient = UUID.randomUUID();

    private static SiteGenerator generator() {
        CatalogRegistry catalogs = mock(CatalogRegistry.class);
        PickSettings pick = mock(PickSettings.class);
        when(catalogs.pick()).thenReturn(pick);
        when(pick.conservation()).thenReturn(ConservationSettings.defaults());
        when(catalogs.materialSurvival("metal")).thenReturn(1.0);
        return new SiteGenerator(catalogs, null);
    }

    @Test
    public void givesNewRecoveredIdentityWithoutChangingTheExcavation() {
        Site site = site();
        BuriedFind existing = new BuriedFind();
        existing.setId(UUID.randomUUID());
        existing.setFindNumber(7);
        site.getFinds().add(existing);
        ArtifactTemplate template = template(Set.of("II"));

        BuriedFind find = generator.createStaffRecoveredFind(site, template, recipient);

        assertNotEquals(existing.getId(), find.getId());
        assertEquals("custom_artifact", find.getArtifactId());
        assertEquals(FindState.RECOVERED, find.getState());
        assertEquals(8, find.getFindNumber());
        assertEquals(recipient, find.getRecoveredBy());
        assertNotNull(find.getRecoveredAt());
        assertTrue(find.getCells().isEmpty());
        assertFalse(find.isStudied());
        assertFalse(find.isLabCleaned());
        assertEquals(List.of(existing), site.getFinds());
        assertEquals(SiteStatus.HIDDEN, site.getStatus());
        assertEquals(0, site.getRecoveredCount());
    }

    @Test
    public void usesCompatiblePresentStratumAndConfiguredItemPool() {
        Site site = site();
        ArtifactTemplate template = template(Set.of("II", "IV"));
        for (int i = 0; i < 20; i++) {
            BuriedFind find = generator.createStaffRecoveredFind(site, template, recipient);
            assertEquals("II", find.getStratumId());
            assertTrue(template.items().stream().anyMatch(ref -> ref.commandToken().equals(find.getItem())));
            assertTrue(find.getConservation() >= 1 && find.getConservation() <= 100);
            assertEquals(find.getBuriedConservation(), find.getConservation());
            assertFalse(find.isFieldDamaged());
        }
    }

    @Test
    public void canGiveAnyArtifactEvenWithoutACompatibleLayer() {
        Site site = site();
        BuriedFind find = generator.createStaffRecoveredFind(site, template(Set.of("IV")), recipient);
        assertTrue(Set.of("I", "II").contains(find.getStratumId()));
        assertEquals(FindState.RECOVERED, find.getState());

        site.getStrata().clear();
        assertEquals("IV", generator.createStaffRecoveredFind(site, template(Set.of("IV")), recipient).getStratumId());
        assertEquals("I", generator.createStaffRecoveredFind(site, template(Set.of()), recipient).getStratumId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsProvenanceWithoutAnArchiveId() {
        generator.createStaffRecoveredFind(new Site(), template(Set.of("I")), recipient);
    }

    private static Site site() {
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setStatus(SiteStatus.HIDDEN);
        for (String id : List.of("I", "II", "IV")) {
            StratumBand band = new StratumBand();
            band.setId(id);
            band.setPresent(!"IV".equals(id));
            site.getStrata().put(id, band);
        }
        return site;
    }

    private static ArtifactTemplate template(Set<String> strata) {
        return new ArtifactTemplate("custom_artifact", "Custom artifact", 1, 3, "metal", null,
                false, 10, strata, Set.of("trade"), FindProfile.OBJECT,
                List.of(new ItemRef(ItemRef.Kind.ITEMSADDER, "custom:artifact", ""),
                        new ItemRef(ItemRef.Kind.MMOITEMS, "MISCELLANEOUS", "ARTIFACT")), "Study notes");
    }
}
