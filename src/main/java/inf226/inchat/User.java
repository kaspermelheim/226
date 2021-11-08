package inf226.inchat;
import java.time.Instant;

/**
 * The User class holds the public information
 * about a user.
 **/
public final class User {
    public final UserName name;
    public final Instant joined;

    public User(UserName name,
                Instant joined) {
        this.name = name;
        this.joined = joined;
    }


    /**
     * Create a new user.
     */
    public static User create(String name) {
        return new User(new UserName(name), Instant.now());
    }
}

