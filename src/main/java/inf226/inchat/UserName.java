package inf226.inchat;

import inf226.storage.Stored;
import inf226.util.Maybe;

public final class UserName {
	public final String name;
	public UserName(String nameString) {
		name = nameString;
	}

	@Override
	public String toString() {
		return name;
	}

	/**
	 * Check if username already exists.
	 * @param userStore
	 * @param username
	 * @return
	 */
	public static boolean isTaken(UserStorage userStore,String username){
		Maybe<Stored<User>> storedMaybe = userStore.lookup(username);
		if(!storedMaybe.isNothing()){
			return true;
		}
		return false;
	}
}

