package cc.ecisr.jyutdict.struct;

import static cc.ecisr.jyutdict.utils.EnumConst.DISPLAY_CHECKING_FIN;
import static cc.ecisr.jyutdict.utils.EnumConst.DISPLAY_CHECKING_IS_INNER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class GeneralCharacterManagerTest {
    @Test
    public void sharedInitialColorsMatchingReadingsAcrossLocations() {
        GeneralCharacterManager manager = manager(
                character("甲",
                        location("甲地", pron("g", "aa", "1")),
                        location("乙地", pron("g", "aa", "2"))),
                character("乙",
                        location("甲地", pron("g", "aa", "1")),
                        location("乙地", pron("g", "aa", "2"))));

        manager.coloring(DISPLAY_CHECKING_FIN);

        assertEquals(1, colorOf(manager, 0, 0, 0, 0));
        assertEquals(1, colorOf(manager, 1, 0, 0, 0));
    }

    @Test
    public void innerComparisonColorsReadingsWithinOneLocation() {
        GeneralCharacterManager manager = manager(
                character("甲",
                        location("甲地", pron("g", "aa", "1")),
                        location("同地", pron("g", "aa", "2"))));

        manager.coloring(DISPLAY_CHECKING_FIN | DISPLAY_CHECKING_IS_INNER);

        assertEquals(1, colorOf(manager, 0, 0, 0, 0));
        assertEquals(1, colorOf(manager, 0, 1, 0, 0));
    }

    @Test
    public void unselectedSyllablePartsDoNotCreateFalseMatches() {
        GeneralCharacterManager manager = manager(
                character("甲",
                        location("甲地", pron("g", "aa", "1")),
                        location("乙地", pron("g", "aa", "2"))),
                character("乙",
                        location("甲地", pron("g", "aa", "1")),
                        location("乙地", pron("g", "aa", "2"))));

        manager.coloring(DISPLAY_CHECKING_IS_INNER);

        assertNotEquals(1, colorOf(manager, 0, 0, 0, 0));
        assertNotEquals(1, colorOf(manager, 1, 0, 0, 0));
    }

    private static GeneralCharacterManager manager(GeneralCharacter... characters) {
        GeneralCharacterManager manager = new GeneralCharacterManager();
        manager.charas.addAll(Arrays.asList(characters));
        manager.retrieveInfo();
        GeneralCharacterManager.cityFilter = new HashSet<>();
        GeneralCharacterManager.hiddenInitials = new HashSet<>();
        GeneralCharacterManager.hiddenFinals = new HashSet<>();
        GeneralCharacterManager.hiddenTones = new HashSet<>();
        return manager;
    }

    private static GeneralCharacter character(
            String head, GeneralCharacter.SingleLoc... locations) {
        GeneralCharacter character = new GeneralCharacter(head);
        character.areas.addAll(Arrays.asList(locations));
        return character;
    }

    private static GeneralCharacter.SingleLoc location(
            String name, GeneralCharacter.SingleLoc.SinglePron... prons) {
        GeneralCharacter.SingleLoc location = new GeneralCharacter.SingleLoc();
        location.city = name;
        for (GeneralCharacter.SingleLoc.SinglePron pron : prons) {
            location.prons.add(new java.util.ArrayList<>(Arrays.asList(pron)));
        }
        return location;
    }

    private static GeneralCharacter.SingleLoc.SinglePron pron(
            String initial, String fin, String tone) {
        return new GeneralCharacter.SingleLoc.SinglePron(
                new String[]{initial, fin, tone}, "");
    }

    private static int colorOf(
            GeneralCharacterManager manager, int character, int location, int group,
            int alternative) {
        return manager.charas.get(character).areas.get(location)
                .prons.get(group).get(alternative).coloring;
    }
}
