package net.tfminecraft.archaeo.ownership;
import net.tfminecraft.tlibs.character.*;
import java.util.*;
/** Only load after the legacy capability probe passes. */
final class LegacyActors {
    private LegacyActors(){}
    static boolean ready(){return Characters.legacyHeirsReady();}
    static Optional<CharacterRef> heir(UUID account){return Characters.legacyHeir(account);}
}
