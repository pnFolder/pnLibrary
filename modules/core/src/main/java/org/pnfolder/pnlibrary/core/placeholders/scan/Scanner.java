package org.pnfolder.pnlibrary.core.placeholders.scan;

import java.util.Objects;
import org.pnfolder.pnlibrary.core.placeholders.LeafValue;
import org.pnfolder.pnlibrary.core.placeholders.Call;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** Turns classes marked with the placeholder annotations into the placeholder tree of the library. */
public final class Scanner {
    private static final int MAX_DEPTH = 8;

    private final Function<Class<?>, ArgumentConverter<Object>> lookup;

    public Scanner() {
        this(type -> null);
    }

    public Scanner(@NotNull Function<Class<?>, ArgumentConverter<Object>> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public List<PhNode> scan(List<Object> holders) {
        List<PhNode> roots = new ArrayList<>();
        for (final Object holder : holders) {
            Class<?> type = holder.getClass();
            Placeholders group = type.getAnnotation(Placeholders.class);
            if (group == null) {
                throw new IllegalStateException(type.getName() + " must be marked with @Placeholders(\"group\")");
            }
            PhNode root = new PhNode(PhPart.word(PhPart.checkWord(group.value())), Object.class);
            List<FormatRule> rules = new ArrayList<>();
            addRule(rules, type);
            members(type, root.getChildren(), call -> holder, new HashSet<>(), rules, 0, null, group.visibility());
            roots.add(root);
        }
        return roots;
    }

    private static void addRule(List<FormatRule> rules, Class<?> type) {
        UseFormat format = type.getAnnotation(UseFormat.class);
        if (format != null) {
            Class<?> valueType = format.type();
            rules.add(new FormatRule(format.value(), valueType == Object.class ? null : valueType));
        }
    }

    private void members(
            Class<?> owner,
            List<PhNode> into,
            Function<Call, Object> receiver,
            Set<String> usedNames,
            List<FormatRule> rules,
            int depth,
            Placeholder inherited,
            Visibility limit) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException(owner.getName() + ": placeholder objects are nested deeper than " + MAX_DEPTH + " levels");
        }
        Set<String> names = new HashSet<>();
        String bare = null;
        for (Member member : Members.collect(owner)) {
            if (member.getPlaceholder().bare()) {
                if (bare != null) {
                    throw new IllegalStateException(owner.getName() + ": only one placeholder may be bare, but both '" + bare + "' and '"
                            + member.getName() + "' are; their arguments would be ambiguous");
                }
                bare = member.getName();
            }
            if (!names.add(member.getName())) {
                throw new IllegalStateException(owner.getName() + ": two placeholders are called '" + member.getName() + "'");
            }
            into.add(memberNode(owner, member, receiver, usedNames, rules, depth, inherited, limit));
        }
    }

    private PhNode memberNode(
            Class<?> owner,
            Member member,
            Function<Call, Object> receiver,
            Set<String> usedNames,
            List<FormatRule> rules,
            int depth,
            Placeholder inherited,
            Visibility limit) {
        String where = owner.getSimpleName() + "." + member.getName();
        if (!Members.returnsReply(member)) {
            throw new IllegalArgumentException(where + ": a placeholder must return Reply<T> or CompletableFuture<Reply<T>>");
        }
        Set<String> used = new HashSet<>(usedNames);
        ParameterPlans parameters = ParameterPlans.read(member, where, used, lookup);
        Class<?> branchType = Members.branchTypeOf(member);
        boolean leaf = branchType == null || Members.collect(branchType).isEmpty();
        MemberStep step = new MemberStep(member, parameters.getPlans(), receiver, leaf);
        List<PhNode> nodes = chain(parameters.getSegments(), leaf ? Members.leafType(member) : Object.class);
        PhNode last = nodes.get(nodes.size() - 1);
        // A placeholder is never wider than the class or the branch it lies in.
        Visibility reach = narrower(member.getPlaceholder().visibility(), limit);
        if (leaf) {
            last.setVisibility(reach);
            makeLeaf(last, member, step, rules, inherited);
        } else {
            List<FormatRule> inner = new ArrayList<>(rules);
            addRule(inner, branchType);
            Placeholder covers = member.getPlaceholder().onInvalid() != Invalid.KEEP ? member.getPlaceholder() : inherited;
            members(branchType, last.getChildren(), step, used, inner, depth + 1, covers, reach);
        }
        return nodes.get(0);
    }

    private static Visibility narrower(Visibility wanted, Visibility limit) {
        return wanted.ordinal() < limit.ordinal() ? wanted : limit;
    }

    /** One node per part of the path; the last one holds the type of the value when the member is a leaf. */
    private static List<PhNode> chain(List<PhPart> segments, Class<?> lastType) {
        List<PhNode> nodes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            boolean last = i == segments.size() - 1;
            nodes.add(new PhNode(segments.get(i), last ? lastType : Object.class));
        }
        PhNode.link(nodes);
        return nodes;
    }

    private void makeLeaf(PhNode node, Member member, Function<Call, Object> step, List<FormatRule> rules, Placeholder inherited) {
        Class<?> valueType = Members.leafType(member);
        Placeholder source = member.getPlaceholder().onInvalid() == Invalid.KEEP && inherited != null ? inherited : member.getPlaceholder();
        node.setValue(new LeafValue(step, fallbackFor(source)));
        node.setFormat(formatFor(member, valueType, rules));
        node.setDescription(member.getPlaceholder().value());
    }

    private static Object fallbackFor(Placeholder placeholder) {
        switch (placeholder.onInvalid()) {
            case DEFAULT:
                return placeholder.defaultText();
            case EMPTY:
                return "";
            default:
                return null;
        }
    }

    private static String formatFor(Member member, Class<?> type, List<FormatRule> rules) {
        if (member.getFormat() != null) {
            return member.getFormat().value();
        }
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rules.get(i).covers(type)) {
                return rules.get(i).getName();
            }
        }
        // A Duration reads as 1h 35m, not PT1H35M.
        return Duration.class.isAssignableFrom(type) ? "left" : null;
    }
}
