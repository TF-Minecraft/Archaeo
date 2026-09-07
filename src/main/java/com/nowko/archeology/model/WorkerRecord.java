package com.nowko.archeology.model;

import java.time.Instant;

/**
 * One person's standing and tally on a single excavation.
 *
 * <p>The concept refuses a global player diary: what someone did belongs to the project they did it
 * on, so this lives inside the site dossier and dies with it. It answers the only question the
 * director actually asks at the board — what has this person contributed, and what have they cost
 * the finds — without turning the plugin into a statistics service.
 *
 * <p>Damage counters are per find, not per cube: hacking three cells off the same pot is one
 * damaged piece, because that is how the loss reads in the field.
 */
public class WorkerRecord {
    private SiteRole role = SiteRole.defaultRole();
    private int blocksRemoved;
    private int cellsBrushed;
    private int findsRecovered;
    private int findsDamaged;
    private int findsLost;
    private Instant joinedAt;
    private Instant lastActiveAt;

    /** @return what this person may do in the field */
    public SiteRole getRole() {
        return role;
    }

    /** @param role what this person may do in the field */
    public void setRole(SiteRole role) {
        this.role = role == null ? SiteRole.defaultRole() : role;
    }

    /** @return spoil cells lifted out of the cut with the pick */
    public int getBlocksRemoved() {
        return blocksRemoved;
    }

    /** @param blocksRemoved spoil cells lifted out of the cut with the pick */
    public void setBlocksRemoved(int blocksRemoved) {
        this.blocksRemoved = Math.max(0, blocksRemoved);
    }

    /** @return find cubes brushed clean */
    public int getCellsBrushed() {
        return cellsBrushed;
    }

    /** @param cellsBrushed find cubes brushed clean */
    public void setCellsBrushed(int cellsBrushed) {
        this.cellsBrushed = Math.max(0, cellsBrushed);
    }

    /** @return pieces this person lifted out of the ground */
    public int getFindsRecovered() {
        return findsRecovered;
    }

    /** @param findsRecovered pieces this person lifted out of the ground */
    public void setFindsRecovered(int findsRecovered) {
        this.findsRecovered = Math.max(0, findsRecovered);
    }

    /** @return pieces this person chipped while digging */
    public int getFindsDamaged() {
        return findsDamaged;
    }

    /** @param findsDamaged pieces this person chipped while digging */
    public void setFindsDamaged(int findsDamaged) {
        this.findsDamaged = Math.max(0, findsDamaged);
    }

    /** @return pieces destroyed outright by this person's blows */
    public int getFindsLost() {
        return findsLost;
    }

    /** @param findsLost pieces destroyed outright by this person's blows */
    public void setFindsLost(int findsLost) {
        this.findsLost = Math.max(0, findsLost);
    }

    /** @return when they joined the staff, or {@code null} for legacy dossiers */
    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** @param joinedAt when they joined the staff */
    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    /** @return last field action recorded, or {@code null} if they never worked */
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    /** @param lastActiveAt last field action recorded */
    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    /**
     * @param cells spoil cubes that just came out
     */
    public void addBlocksRemoved(int cells) {
        if (cells <= 0) {
            return;
        }
        blocksRemoved += cells;
        touch();
    }

    /**
     * One more find cube dusted clean.
     */
    public void noteCellBrushed() {
        cellsBrushed++;
        touch();
    }

    /**
     * One more piece out of the ground and into a hand.
     */
    public void noteFindRecovered() {
        findsRecovered++;
        touch();
    }

    /**
     * One more piece that lost condition to this person's blow.
     */
    public void noteFindDamaged() {
        findsDamaged++;
        touch();
    }

    /**
     * One more piece taken past saving. Counted on top of the damage that killed it.
     */
    public void noteFindLost() {
        findsLost++;
        touch();
    }

    /**
     * @return whether this person has done anything measurable in the cut
     */
    public boolean hasWorked() {
        return blocksRemoved > 0 || cellsBrushed > 0 || findsRecovered > 0 || findsDamaged > 0 || findsLost > 0;
    }

    /**
     * Stamps the moment of the last field action so the board can say who has gone quiet.
     */
    private void touch() {
        lastActiveAt = Instant.now();
    }
}
