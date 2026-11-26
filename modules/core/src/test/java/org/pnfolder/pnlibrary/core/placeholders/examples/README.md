# Examples of the placeholders API

Read in order. All classes use a small "clans" world from `Clans.java`.

1. `ClanBasics.java`, `WarBranch.java`: `@Placeholders`, `@Placeholder`, `@Hide`, branches and properties.
2. `ClanArguments.java`, `KillsQuery.java`: `ArgumentValue<T>`, `@Argument` (range, choices), `@Group` (на параметре), `PlayerRef`, `me`, `Duration`, `Invalid`.
3. `ClanFormats.java`, `ClanBank.java`, `AboutTheViewer.java`, `ClanSetup.java`: `@Format`, `@UseFormat`, `ViewerInfo`, module converters and formats with a `Visibility`, registration with `papi`.
4. `ServerValues.java`, `MeValues.java`, `DynamicPlaceholders.java`: return types (async, nested objects, `Duration`), `Invalid.EMPTY/DEFAULT`, `value()` without a class.
5. `UsingThePlaceholders.java`, `OwnSystem.java`: `render`, `resolve`, `values`, `contains`, `list`, `adapters`.
6. `DuelValues.java`, `RouteTour.java`: `@Route` (what each part of the address is), `answer(...)` to end early, `onInvalid` on a branch.
7. `ClanWarTree.java`, `WarTop.java`, `WarFormats.java`: one address `{clan.war.top.3.name|pad0:4|upper}` taken apart: group, branches, argument, field, formats.
8. `ClanByName.java`, `ClanConverterValues.java`: `ArgumentConverter` (own reading of an argument, also for classes that are not yours), `readLater`, `suggest`, `fail`, `previous`, `route`.
9. `ClanReplies.java`: `Reply.value` (answer), `Reply.next` (go on), `Reply.stop` (answer from a branch), `Reply.none`, format of an answer.

10. `ShopPrices.java`, `OwnersSetup.java`: `Visibility`, the owner in front of an address (`~`, `~~`, `~~~`, `plugin/module@`) and placeholders inside placeholders.

The classes are public only so that the library can read them by reflection: they are for reading and are not part of the API.
