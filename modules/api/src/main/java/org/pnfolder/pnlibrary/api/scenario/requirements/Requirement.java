package org.pnfolder.pnlibrary.api.scenario.requirements;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.PermissionRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.CompareRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ChanceRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ItemRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExperienceRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NearRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AllRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.AnyRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.NotRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.ExpressionRequirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.builtin.RefRequirement;

/**
 * A condition that only looks: it answers {@link Verdict.Met} or {@link Verdict.Unmet} and changes nothing.
 *
 * <p>A requirement is a configuration value with a {@code type}. Values it compares can come from anywhere
 * {@link RequirementContext#text} reads: a literal, a context value {@code [name]}, a pnLibrary placeholder
 * {@code {plugin:name}} or an external one {@code %name%}.
 *
 * <pre>{@code
 * when:
 *   type: all
 *   requirements:
 *     - type: permission
 *       permission: shop.use
 *     - type: compare
 *       left: '%vault_eco_balance%'
 *       operator: greater-or-equal
 *       right: 100
 * }</pre>
 *
 * <p>Money, items and other things that must be taken are not requirements: use an action that can fail, such as
 * {@code charge}, so that the taking and the checking cannot disagree.
 *
 * <p>Code can implement the interface directly:
 *
 * <pre>{@code
 * Requirement vip = ctx -> Verdict.of(AudienceExtensions.hasPermission(ctx.getPlayer(), "vip"), "VIP only");
 * }</pre>
 *
 * <p>Implementations should be free of side effects, because one requirement may run many times.
 */
@ConfigPolymorphic(discriminator = "type")
@ConfigTypes({
        @ConfigType(type = PermissionRequirement.class, name = "permission", aliases = {"permissions"}),
        @ConfigType(type = CompareRequirement.class, name = "compare"),
        @ConfigType(type = ChanceRequirement.class, name = "chance"),
        @ConfigType(type = ItemRequirement.class, name = "item", aliases = {"items"}),
        @ConfigType(type = ExperienceRequirement.class, name = "experience", aliases = {"exp"}),
        @ConfigType(type = NearRequirement.class, name = "near"),
        @ConfigType(type = AllRequirement.class, name = "all", aliases = {"and"}),
        @ConfigType(type = AnyRequirement.class, name = "any", aliases = {"or"}),
        @ConfigType(type = NotRequirement.class, name = "not"),
        @ConfigType(type = ExpressionRequirement.class, name = "expression", aliases = {"expr", "calc"}),
        @ConfigType(type = RefRequirement.class, name = "ref", aliases = {"defined"})
})
@FunctionalInterface
public interface Requirement {
    /** Judges {@code context}; an {@link Verdict.Unmet} carries the reason that can be shown to the player. */
    @NotNull
    Verdict test(@NotNull RequirementContext context);
}
