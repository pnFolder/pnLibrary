package org.pnfolder.pnlibrary.api.chat;

import java.util.HashMap;
import java.util.Map;

/** ASCII stand-ins for the glyphs used on pages, for readers that cannot show anything else. */
final class ChatAscii {
    private static final Map<Character, String> STAND_INS = new HashMap<Character, String>();

    static {
        STAND_INS.put('─', "-");
        STAND_INS.put('■', "*");
        STAND_INS.put('✔', "+");
        STAND_INS.put('□', "o");
        STAND_INS.put('›', ">");
        STAND_INS.put('✦', "*");
        STAND_INS.put('·', "|");
        STAND_INS.put('»', ">>");
        STAND_INS.put('«', "<<");
        STAND_INS.put('√', "+");
        STAND_INS.put('→', "->");
        STAND_INS.put('—', "-");
        STAND_INS.put('–', "-");
        STAND_INS.put('…', "...");
    }

    private ChatAscii() {
    }

    /** {@code value} with glyphs replaced; any other character outside ASCII and Cyrillic becomes {@code ?}. */
    static String of(String value) {
        StringBuilder text = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char symbol = value.charAt(index);
            if (symbol < 128 || (symbol >= 'А' && symbol <= 'я') || symbol == 'ё' || symbol == 'Ё') {
                text.append(symbol);
            } else {
                String standIn = STAND_INS.get(symbol);
                text.append(standIn != null ? standIn : "?");
            }
        }
        return text.toString();
    }
}
