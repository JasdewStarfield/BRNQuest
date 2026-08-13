package yourscraft.jasdewstarfield.brnquest.editor;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.List;
import java.util.Map;

/** Optional extension validator invoked after built-in shape and range checks. */
@FunctionalInterface
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ConfigFieldValidator {
    List<ConfigFieldIssue> validate(String value, Map<String, String> completeConfig);
}
