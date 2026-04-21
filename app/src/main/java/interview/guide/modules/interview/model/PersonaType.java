package interview.guide.modules.interview.model;

import java.util.Locale;

/**
 * 面试官人格类型。
 */
public enum PersonaType {
    STRICT,
    FRIENDLY;

    public static PersonaType fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRICT;
        }
        try {
            return PersonaType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return STRICT;
        }
    }
}
