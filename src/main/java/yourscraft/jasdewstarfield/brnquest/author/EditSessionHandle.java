package yourscraft.jasdewstarfield.brnquest.author;

import java.util.UUID;

/** Secret session identifier is returned only to the administrator who owns the lease. */
public record EditSessionHandle(UUID sessionId, EditSessionView session) {}
