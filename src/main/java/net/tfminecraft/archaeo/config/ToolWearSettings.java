package net.tfminecraft.archaeo.config;

/**
 * Durability spent by field tools, from {@code excavation.tool-wear}.
 *
 * <p>Archaeo freezes vanilla mining so the plugin decides when a cube leaves the cut. That also
 * means nothing ever wears unless the server says so, which is why the cost is spelled out here
 * instead of being inherited from the vanilla dig. Set a count to {@code 0} to keep that tool
 * pristine for the whole campaign.
 *
 * @param pick durability spent per cube the pick removes, matching one vanilla block break
 * @param brush durability spent per cube the brush clears
 * @param unbreaking whether Unbreaking may absorb points, as it would on a vanilla dig
 */
public record ToolWearSettings(
        int pick,
        int brush,
        boolean unbreaking
) {
    /**
     * @return packaged defaults matching {@code config.yml}
     */
    public static ToolWearSettings defaults() {
        return new ToolWearSettings(1, 1, true);
    }
}
