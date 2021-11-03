package inf226.inchat;
import com.lambdaworks.crypto.SCrypt;
import inf226.util.Maybe;
import inf226.util.immutable.List;
import inf226.util.Pair;


import inf226.storage.*;
import java.util.Arrays;

/**
 * The Account class holds all information private to
 * a specific user.
 **/
public final class Account {
    /*
     * A channel consists of a User object of public account info,
     * and a list of channels which the user can post to.
     */
    public final Stored<User> user;
    public final List<Pair<String,Stored<Channel>>> channels;
    public final Maybe<Password> password;
    public final String salt;

    public Account(final Stored<User> user,
            final List<Pair<String,Stored<Channel>>> channels,
            final Maybe<Password> password, String salt) {
        this.user = user;
        this.channels = channels;
        this.password = password;
        this.salt = salt;
    }

    /**
     * Create a new Account.
     *
     * @param user The public User profile for this user.
     * @param password The login password for this account.
     **/
    public static Account create(final Stored<User> user,
            final Maybe<Password> password, String salt) {
        return new Account(user,List.empty(), password, salt);
    }

    /**
     * Join a channel with this account.
     *
     * @return A new account object with the cannnel added.
     */
    public Account joinChannel(final String alias,
            final Stored<Channel> channel) {
        Pair<String,Stored<Channel>> entry
                = new Pair<String,Stored<Channel>>(alias,channel);
        return new Account
                (user,
                        List.cons(entry,
                                channels),
                        password, salt);
    }

    /**
     * Check weather if a string is a correct password for this account.
     *
     * @return true if password matches.
     */
    public boolean checkPassword(String password) throws Exception {
       byte [] pass = SCrypt.scrypt(password.getBytes(), Password.toByte(this.salt),65536, 16, 1, 32);
       return this.password.get().toString().equals(Arrays.toString(pass));
    }
}

