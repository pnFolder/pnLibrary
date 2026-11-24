# Examples of the placeholders API

Read in order. All classes use a small "clans" world from `Domain.kt`.

1. `Example1Basics.kt`: `@Placeholders`, `@Placeholder`, `@Hide`, branches and properties.
2. `Example2Arguments.kt`: `ArgumentValue<T>`, `@Argument` (range, choices), `@Group` (на параметре), `PlayerRef`, `me`, `Duration`, `Invalid`.
3. `Example3FormatsAndRegistration.kt`: `@Format`, `@UseFormat`, `ViewerInfo`, module converters and formats with a `Visibility`, registration with `papi`.
4. `Example4ValuesAndTypes.kt`: return types (async, nested objects, `Duration`), `Invalid.EMPTY/DEFAULT`, `value()` without a class.
5. `Example5UsingThePlaceholders.kt`: `render`, `resolve`, `values`, `contains`, `list`, `adapters`.
6. `Example6RouteAndAnswer.kt`: `@Route` (what each part of the address is), `answer(...)` to end early, `onInvalid` on a branch.
7. `Example7RouteBreakdown.kt`: one address `{clan.war.top.3.name|pad0:4|upper}` taken apart: group, branches, argument, field, formats.
8. `Example8Converters.kt`: `ArgumentConverter` (own reading of an argument, also for classes that are not yours), `readLater`, `suggest`, `fail`, `previous`, `route`.
9. `Example9Reply.kt`: `Reply.value` (answer), `Reply.next` (go on), `Reply.stop` (answer from a branch), `Reply.none`, format of an answer.

The classes are `internal`: they are for reading and are not part of the API.
10. `Example10Owners.kt`: `Visibility`, the owner in front of an address (`~`, `~~`, `~~~`, `plugin/module@`) and placeholders inside placeholders.
