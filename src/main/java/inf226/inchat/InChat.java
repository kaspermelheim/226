package inf226.inchat;

import inf226.storage.*;
import inf226.util.Maybe;
import inf226.util.Maybe.NothingException;
import inf226.util.Util;

import java.rmi.NoSuchObjectException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Connection;


import inf226.util.immutable.List;

/**
 * This class models the chat logic.
 *
 * It provides an abstract interface to
 * usual chat server actions.
 *
 **/

public class InChat {
    private final Connection connection;
    private final UserStorage userStore;
    private final ChannelStorage channelStore;
    private final EventStorage   eventStore;
    private final AccountStorage accountStore;
    private final SessionStorage sessionStore;
    private final Map<UUID,List<Consumer<Channel.Event>>> eventCallbacks
            = new TreeMap<UUID,List<Consumer<Channel.Event>>>();

    private final HashMap<String, HashMap<String, Role>> channelUsers;

    public InChat(UserStorage userStore,
            ChannelStorage channelStore,
            AccountStorage accountStore,
            SessionStorage sessionStore,
            Connection     connection) {
        this.userStore=userStore;
        this.channelStore=channelStore;
        this.eventStore=channelStore.eventStore;
        this.accountStore=accountStore;
        this.sessionStore=sessionStore;
        this.connection=connection;
        this.channelUsers = new HashMap<>();
    }

    /**
     * An atomic operation in Inchat.
     * An operation has a function run(), which returns its
     * result through a consumer.
     */
    @FunctionalInterface
    private interface Operation<T,E extends Throwable> {
        void run(final Consumer<T> result) throws E, DeletedException, Maybe.NothingException, GeneralSecurityException;
    }
    /**
     * Execute an operation atomically in SQL.
     * Wrapper method for commit() and rollback().
     */
    private<T> Maybe<T> atomic(Operation<T,SQLException> op) {
        synchronized (connection) {
            try {
                Maybe.Builder<T> result = Maybe.builder();
                op.run(result);
                connection.commit();
                return result.getMaybe();
            }catch (SQLException e) {
                System.err.println(e.toString());
            }catch (DeletedException e) {
                System.err.println(e.toString());
            }catch(Maybe.NothingException e) {
                System.err.println(e.toString());
            }catch(GeneralSecurityException e) {
                System.err.println(e.toString());
            }
            try {
                connection.rollback();
            } catch (SQLException e) {
                System.err.println(e.toString());
            }
            return Maybe.nothing();
        }
    }

    /**
     * Log in a user to the chat.
     */
    public Maybe<Stored<Session>> login(String username, String password) {
        return atomic(result -> {
            final Stored<Account> account = accountStore.lookup(Handler.escapeCode(username));
            final Stored<Session> session =
                    sessionStore.save(new Session(account, Instant.now().plusSeconds(60*60*24)));
            try {
                // Check that password is not incorrect and not too long.
                if (account.value.checkPassword(Handler.escapeCode(password))) {
                    result.accept(session);
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Register a new user.
     */
    public Maybe<Stored<Session>> register(final String username,
            final String password) {

        return atomic(result -> {

            try {

                final Stored<User> user =
                        userStore.save(User.create(Handler.escapeCode(Handler.escapeCode(username))));
                final Maybe<Password> newPass = Password.createPassReg(Handler.escapeCode(password));
                final Stored<Account> account =
                        accountStore.save(Account.create(user, newPass, Arrays.toString(newPass.get().salt)));
                final Stored<Session> session =
                        sessionStore.save(new Session(account, Instant.now().plusSeconds(60 * 60 * 24)));
                result.accept(session);

            }catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Restore a previous session.
     */
    public Maybe<Stored<Session>> restoreSession(UUID sessionId) {
        return atomic(result ->
                result.accept(sessionStore.get(sessionId)));
    }

    /**
     * Log out and invalidate the session.
     */
    public void logout(Stored<Session> session) {
        atomic(result ->
                Util.deleteSingle(session,sessionStore));
    }

    /**
     * Create a new channel.
     */
    public Maybe<Stored<Channel>> createChannel(Stored<Account> account,
            String name) {
        return atomic(result -> {
            try {
                Stored<Channel> channel
                        = channelStore.save(new Channel(Handler.escapeCode(name), List.empty()));

                joinChannel(account, channel.identity);

                //Give the creator of the channel OWNER role
                setRole(channel.value, account.value.user.value, Role.OWNER);
                checkHasOwner(channel.value);

                result.accept(channel);
            }catch (SQLException e) {
                e.printStackTrace();
            }catch (NoSuchObjectException e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * Join a channel.
     */
    public Maybe<Stored<Channel>> joinChannel(Stored<Account> account,
            UUID channelID) {

        return atomic(result -> {
            try {
                Stored<Channel> channel = channelStore.get(channelID);


                //If hashmap is empty = no users has joined before and you are the owner.
                if (mapIsEmpty(channel.value, account.value.user.value) || !isBanned(channel.value, account.value.user.value)) {
                    Util.updateSingle(account,
                            accountStore,
                            a -> a.value.joinChannel(channel.value.name, channel));
                    Stored<Channel.Event> joinEvent
                            = channelStore.eventStore.save(
                            Channel.Event.createJoinEvent(channelID,
                                    Instant.now(),
                                    Handler.escapeCode(account.value.user.value.name.name)));
                    result.accept(
                            Util.updateSingle(channel,
                                    channelStore,
                                    c -> c.value.postEvent(joinEvent)));
                    //Give the user that joined PARTICIPANT role
                    setRole(channel.value, account.value.user.value, Role.PARTICIPANT);
                } else {
                    System.out.println("User is banned from the channel.");
                }

            }  catch (DeletedException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Post a message to a channel.
     */
    public Maybe<Stored<Channel>> postMessage(Stored<Account> account,
            Stored<Channel> channel,
            String message) {
        //System.out.println(channelUsers);
        //Checks if user has permission to post
        if (canPost(channel.value, account.value.user.value)) {
            return atomic(result -> {
                try {

                    Stored<Channel.Event> event
                            = channelStore.eventStore.save(
                            Channel.Event.createMessageEvent(channel.identity, Instant.now(),
                                    account.value.user.value.name.name, Handler.escapeCode(message)));
                    try {
                        result.accept(
                                Util.updateSingle(channel,
                                        channelStore,
                                        c -> c.value.postEvent(event)));
                    } catch (DeletedException e) {
                        Util.deleteSingle(event, channelStore.eventStore);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
        return Maybe.just(channel);
    }

    /**
     * A blocking call which returns the next state of the channel.
     */
    public Maybe<Stored<Channel>> waitNextChannelVersion(UUID identity, UUID version) {
        try{
            return Maybe.just(channelStore.waitNextVersion(identity, version));
        } catch (DeletedException e) {
            return Maybe.nothing();
        } catch (SQLException e) {
            return Maybe.nothing();
        }
    }

    /**
     * Get an event by its identity.
     */
    public Maybe<Stored<Channel.Event>> getEvent(UUID eventID) {
        return atomic(result ->
                result.accept(channelStore.eventStore.get(eventID))
        );
    }



    /**
     * Delete an event.
     */
    public Stored<Channel> deleteEvent(Stored<Channel> channel, Stored<Channel.Event> event, Stored<Account> account) {
        try {
            //You can delete if you are MODERATOR or OWNER, or you are the sender of the message.
            if ((isModerator(channel.value, account.value.user.value) || isOwner(channel.value, account.value.user.value)) || isSender(account, event)) {

                Util.deleteSingle(event, channelStore.eventStore);
                return channelStore.noChangeUpdate(channel.identity);
            } else {
                System.err.println("User cannot delete message.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (DeletedException e) {
            e.printStackTrace();
        }
        return channel;
    }

    /**
     * Edit a message.
     */
    public Stored<Channel> editMessage(Stored<Channel> channel,
            Stored<Channel.Event> event,
            String newMessage, Stored<Account> account) {
        //You can delete if you are MODERATOR or OWNER, or you are the sender of the message.
        if ((isModerator(channel.value, account.value.user.value) || isOwner(channel.value, account.value.user.value)) || isSender(account,event)) {
            try {
                Util.updateSingle(event,
                        channelStore.eventStore,
                        e -> e.value.setMessage(newMessage));
                return channelStore.noChangeUpdate(channel.identity);
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (DeletedException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("User cannot edit message.");
        }
        return channel;
    }


    /**
     * Set role to a user.
     * @param channel
     * @param user
     * @param role
     */
    public void setRole(Channel channel, User user, Role role){
        if (channelUsers.containsKey(channel.name)) {
            channelUsers.get(channel.name).put(user.name.getName(), role);
        } else {
            HashMap<String, Role> userRolesInChannel = new HashMap<String, Role>();
            userRolesInChannel.put(user.name.getName(), role);
            channelUsers.put(channel.name, userRolesInChannel);
        }
        //System.out.println(channelUsers);
    }

    /**
     * Check if hashmap of channelRoles is empty
     * @param channel
     * @param user
     * @return
     */
    private boolean mapIsEmpty(Channel channel, User user) {
        if(channelUsers.get(channel) != null){
            if(channelUsers.get(channel.name).get(user.name.getName()) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user has OWNER role
     * @param channel
     * @param user
     * @return
     */
    public boolean isOwner(Channel channel, User user) {
        Role userRole = channelUsers.get(channel.name).get(user.name.getName());
        if(userRole == Role.OWNER) {
            return true;
        }
        return false;
    }

    /**
     * Check if user has MODERATOR role
     * @param channel
     * @param user
     * @return
     */
    private boolean isModerator(Channel channel, User user) {
        Role userRole = channelUsers.get(channel.name).get(user.name.getName());
        if(userRole == Role.MODERATOR) {
            return true;
        }
        return false;
    }

    /**
     * Check if user is sender of message
     * @param account
     * @param event
     * @return
     */
    private boolean isSender(Stored<Account> account, Stored<Channel.Event> event) {
        return account.value.user.value.name.name.equals(event.value.sender);
    }

    public User getUser(String username) {
        try {
            return userStore.lookup(username).get().value;
        }catch(NothingException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Check if user has permission to post in channel
     * @param channel
     * @param user
     * @return
     */
    private boolean canPost(Channel channel, User user) {
        Role userRole = channelUsers.get(channel.name).get(user.name.getName());
        if(userRole.equals(Role.OWNER) || userRole.equals(Role.PARTICIPANT) || userRole.equals(Role.MODERATOR)) {
            return true;
        }
        return false;
    }

    /**
     * Check if user is banned from channel
     * @param channel
     * @param user
     * @return
     */
    public boolean isBanned(Channel channel, User user) {
        Role userRole = channelUsers.get(channel.name).get(user.name.getName());
        if(userRole == Role.BANNED) {
            return true;
        }
        return false;
    }

    /**
     * Check if channel has owner
     * @param channel
     * @throws NoSuchObjectException
     */
    private void checkHasOwner(Channel channel) throws NoSuchObjectException {
        HashMap<String, Role> userRoles = channelUsers.get(channel.name);
        for (Role role : userRoles.values()) {
            if (role == Role.OWNER)
                return;
        }

        throw new NoSuchObjectException("Channel has no owner.");
    }

}


