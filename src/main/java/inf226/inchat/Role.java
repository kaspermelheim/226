package inf226.inchat;

public enum Role {
	OWNER,
	MODERATOR,
	PARTICIPANT,
	OBSERVER,
	BANNED,
	UNDEFINED;

	public static Role convert(String r) {
		switch (r) {
			case "owner": return OWNER;
			case "moderator": return MODERATOR;
			case "participant": return PARTICIPANT;
			case "banned": return BANNED;
			case "observer": return OBSERVER;
			default: return UNDEFINED;
		}
	}
}
