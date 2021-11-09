package inf226.inchat;

import inf226.util.Maybe.NothingException;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import inf226.storage.*;

import inf226.util.immutable.List;
import inf226.util.*;

/**
 * This class stores accounts in the database.
 */
public final class AccountStorage
        implements Storage<Account,SQLException> {

    final Connection connection;
    final Storage<User,SQLException> userStore;
    final Storage<Channel,SQLException> channelStore;

    /**
     * Create a new account storage.
     *
     * @param  connection   The connection to the SQL database.
     * @param  userStore    The storage for User data.
     * @param  channelStore The storage for channels.
     */
    public AccountStorage(Connection connection,
            Storage<User,SQLException> userStore,
            Storage<Channel,SQLException> channelStore)
            throws SQLException {
        this.connection = connection;
        this.userStore = userStore;
        this.channelStore = channelStore;

        final PreparedStatement sqlCreateAccount = connection.prepareStatement("CREATE TABLE IF NOT EXISTS Account (id TEXT PRIMARY KEY, version TEXT, user TEXT, password TEXT, salt TEXT, FOREIGN KEY(user) REFERENCES User(id) ON DELETE CASCADE)");
        final PreparedStatement sqlCreateChannel = connection.prepareStatement("CREATE TABLE IF NOT EXISTS AccountChannel (account TEXT, channel TEXT, alias TEXT, ordinal INTEGER, PRIMARY KEY(account,channel), FOREIGN KEY(account) REFERENCES Account(id) ON DELETE CASCADE, FOREIGN KEY(channel) REFERENCES Channel(id) ON DELETE CASCADE)");
        sqlCreateAccount.executeUpdate();
        sqlCreateChannel.executeUpdate();
    }

    @Override
    public Stored<Account> save(Account account)
            throws SQLException, NothingException {

        final Stored<Account> stored = new Stored<Account>(account);

        final PreparedStatement sql = connection.prepareStatement("INSERT INTO Account VALUES(?,?,?,?,?)");
        sql.setObject(1,stored.identity);
        sql.setObject(2,stored.version);
        sql.setObject(3,account.user.identity);
        sql.setObject(4,account.password.get().toString());
        sql.setString(5,account.salt);
        sql.executeUpdate();

        // Write the list of channels
        final Maybe.Builder<SQLException> exception = Maybe.builder();
        final Mutable<Integer> ordinal = new Mutable<Integer>(0);
        account.channels.forEach(element -> {
            String alias = element.first;
            Stored<Channel> channel = element.second;

            try {
                final PreparedStatement sqlChannel = connection.prepareStatement("INSERT INTO AccountChannel VALUES(?,?,?,?)");
                sql.setObject(1,stored.identity);
                sql.setObject(2,channel.identity);
                sql.setString(3,alias);
                sql.setString(4,ordinal.get().toString());
                sqlChannel.executeUpdate();
            } catch (SQLException throwable) {
                exception.accept(throwable);
            }


            /*

            final String msql
                    = "INSERT INTO AccountChannel VALUES('" + stored.identity + "','"
                    + channel.identity + "','"
                    + alias + "','"
                    + ordinal.get().toString() + "')";
            try { connection.createStatement().executeUpdate(msql); }
            catch (SQLException e) { exception.accept(e) ; }

             */

            ordinal.accept(ordinal.get() + 1);
        });

        Util.throwMaybe(exception.getMaybe());
        return stored;
    }

    @Override
    public synchronized Stored<Account> update(Stored<Account> account,
                                               Account new_account)
            throws UpdatedException,
            DeletedException,
            SQLException, GeneralSecurityException {
        final Stored<Account> current = get(account.identity);
        final Stored<Account> updated = current.newVersion(new_account);
        if(current.version.equals(account.version)) {

            final PreparedStatement sql = connection.prepareStatement("UPDATE Account SET (version, user) = (?,?) WHERE id=?");
            sql.setObject(1,updated.version);
            sql.setObject(2,new_account.user.identity);
            sql.setObject(3,updated.identity);
            sql.executeUpdate();


            // Rewrite the list of channels
            final PreparedStatement sqlRewrite = connection.prepareStatement("DELETE FROM AccountChannel WHERE account=?");
            sqlRewrite.setObject(1, account.identity);
            sqlRewrite.executeUpdate();

            final Maybe.Builder<SQLException> exception = Maybe.builder();
            final Mutable<Integer> ordinal = new Mutable<Integer>(0);
            new_account.channels.forEach(element -> {
                String alias = element.first;
                Stored<Channel> channel = element.second;

                try {
                    final PreparedStatement sqlChannel = connection.prepareStatement("INSERT INTO AccountChannel VALUES(?,?,?,?)");
                    sqlChannel.setObject(1,account.identity);
                    sqlChannel.setObject(2,channel.identity);
                    sqlChannel.setObject(3,alias);
                    sqlChannel.setObject(4,ordinal.get().toString());
                    sqlChannel.executeUpdate();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }


                ordinal.accept(ordinal.get() + 1);
            });

            Util.throwMaybe(exception.getMaybe());
        } else {
            throw new UpdatedException(current);
        }
        return updated;
    }


    @Override
    public synchronized void delete(Stored<Account> account)
            throws UpdatedException,
            DeletedException,
            SQLException, GeneralSecurityException {
        final Stored<Account> current = get(account.identity);
        if(current.version.equals(account.version)) {
            final PreparedStatement sql = connection.prepareStatement("DELETE  FROM Account WHERE account=?");
            sql.setObject(1,account.identity);
            sql.executeUpdate();

        } else {
            throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Account> get(UUID id)
            throws DeletedException,
            SQLException, GeneralSecurityException {

        final PreparedStatement accountsql = connection.prepareStatement("SELECT version,user,password,salt FROM Account WHERE id=?");
        final PreparedStatement channelsql = connection.prepareStatement("SELECT channel,alias,ordinal FROM AccountChannel WHERE account=? ORDER BY ordinal DESC");

        accountsql.setString(1,id.toString());
        channelsql.setString(1,id.toString());

        final ResultSet accountResult = accountsql.executeQuery();
        final ResultSet channelResult = channelsql.executeQuery();

        if(accountResult.next()) {
            final UUID version = UUID.fromString(accountResult.getString("version"));
            final UUID userid =
                    UUID.fromString(accountResult.getString("user"));
            final String password =
                    accountResult.getString("password");
            final Stored<User> user = userStore.get(userid);
            // Get all the channels associated with this account
            final List.Builder<Pair<String,Stored<Channel>>> channels = List.builder();
            while(channelResult.next()) {
                final UUID channelId =
                        UUID.fromString(channelResult.getString("channel"));
                final String alias = channelResult.getString("alias");
                channels.accept(
                        new Pair<String,Stored<Channel>>(
                                alias,channelStore.get(channelId)));
            }

            //Passwords are now added properly
            Maybe<Password> newPass = Password.createPass(Password.toByte(password), Password.toByte(accountResult.getString("salt")));
            return (new Stored<Account>(new Account(user,channels.getList(),newPass, accountResult.getString("salt")), id, version));

        } else {
            throw new DeletedException();
        }
    }

    /**
     * Look up an account based on their username.
     */
    public Stored<Account> lookup(String username)
            throws DeletedException,
            SQLException, GeneralSecurityException {

        final PreparedStatement sql = connection.prepareStatement("SELECT Account.id FROM Account INNER JOIN User ON user=User.id WHERE User.name=?");
        sql.setString(1,username);

        System.err.println(sql);

        final ResultSet rs = sql.executeQuery();
        if(rs.next()) {
            final UUID identity =
                    UUID.fromString(rs.getString("id"));
            return get(identity);
        }
        throw new DeletedException();
    }

}