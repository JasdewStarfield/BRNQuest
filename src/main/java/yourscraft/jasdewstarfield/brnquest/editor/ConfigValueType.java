package yourscraft.jasdewstarfield.brnquest.editor;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Basic value shapes understood by the description-driven editor. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public enum ConfigValueType {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    TEXT,
    ENUM,
    RESOURCE_LOCATION,
    ITEM_STACK
}
