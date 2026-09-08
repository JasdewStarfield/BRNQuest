package yourscraft.jasdewstarfield.brnquest.editor;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Basic value shapes understood by the description-driven editor. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public enum ConfigValueType {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    /** Three integer axes edited inline; persisted as the existing comma-separated string. */
    INTEGER_VECTOR3,
    TEXT,
    ENUM,
    RESOURCE_LOCATION,
    ITEM_STACK,
    /** Structured tag-or-list item matcher edited by the shared candidate selector Screen. */
    ITEM_MATCHER
}
