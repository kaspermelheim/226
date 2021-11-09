package inf226.inchat;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.TreeMap;
import java.util.Map;
import java.util.function.Consumer;

import inf226.storage.*;

import inf226.util.immutable.List;
import inf226.util.*;

/**
 * This class stores Channels in a SQL database.
 */
public final class ChannelStorage
    implements Storage<Channel,SQLException> {
    
    final Connection connection;
    /* The waiters object represent the callbacks to
     * make when the channel is updated.
     */
    private Map<UUID,List<Consumer<Stored<Channel>>>> waiters
        = new TreeMap<UUID,List<Consumer<Stored<Channel>>>>();
    public final EventStorage eventStore;
    
    public ChannelStorage(Connection connection) 
      throws SQLException {
        this.connection = connection;
        this.eventStore = new EventStorage(connection);
        final PreparedStatement sql = connection.prepareStatement("CREATE TABLE IF NOT EXISTS Channel (id TEXT PRIMARY KEY, version TEXT, name TEXT)");
        sql.executeUpdate();
    }

    
    @Override
    public Stored<Channel> save(Channel channel)
      throws SQLException {


        final Stored<Channel> stored = new Stored<Channel>(channel);
        final PreparedStatement sql = connection.prepareStatement("INSERT INTO Channel VALUES (?,?,?)");
        sql.setObject(1,stored.identity);
        sql.setObject(2,stored.version);
        sql.setObject(3,channel.name);
        sql.executeUpdate();
        return stored;
    }
    
    @Override
    public synchronized Stored<Channel> update(Stored<Channel> channel,
                                            Channel new_channel)
        throws UpdatedException,
            DeletedException,
            SQLException {
        final Stored<Channel> current = get(channel.identity);
        final Stored<Channel> updated = current.newVersion(new_channel);
        if(current.version.equals(channel.version)) {
            final PreparedStatement sql = connection.prepareStatement("UPDATE Channel SET (version,name) = (?,?) WHERE id=?");
            sql.setObject(1,updated.version);
            sql.setObject(2,new_channel.name);
            sql.setObject(3,updated.identity);
            sql.executeUpdate();
        }
        giveNextVersion(updated);
        return updated;
    }
   
    @Override
    public synchronized void delete(Stored<Channel> channel)
       throws UpdatedException,
              DeletedException,
              SQLException {
        final Stored<Channel> current = get(channel.identity);
        if(current.version.equals(channel.version)) {
            final PreparedStatement sql = connection.prepareStatement("DELETE FROM Channel WHERE id=?");
            sql.setObject(1,channel.identity);
            sql.executeUpdate();
        } else {
        throw new UpdatedException(current);
        }
    }
    @Override
    public Stored<Channel> get(UUID id)
      throws DeletedException,
             SQLException {


        final PreparedStatement channelsql = connection.prepareStatement("SELECT version,name FROM Channel WHERE id=?");
        final PreparedStatement eventsql = connection.prepareStatement("SELECT id,rowid FROM Event WHERE channel=? ORDER BY rowid ASC");

        channelsql.setString(1,id.toString());
        eventsql.setString(1,id.toString());

        final ResultSet channelResult = channelsql.executeQuery();
        final ResultSet eventResult = eventsql.executeQuery();

        if(channelResult.next()) {
            final UUID version = 
                UUID.fromString(channelResult.getString("version"));
            final String name =
                channelResult.getString("name");
            // Get all the events associated with this channel
            final List.Builder<Stored<Channel.Event>> events = List.builder();
            while(eventResult.next()) {
                final UUID eventId = UUID.fromString(eventResult.getString("id"));
                events.accept(eventStore.get(eventId));
            }
            return (new Stored<Channel>(new Channel(name,events.getList()),id,version));
        } else {
            throw new DeletedException();
        }
    }
    
    /**
     * This function creates a "dummy" update.
     * This function should be called when events are changed or
     * deleted from the channel.
     */
    public Stored<Channel> noChangeUpdate(UUID channelId)
        throws SQLException, DeletedException {
        final PreparedStatement sql = connection.prepareStatement("UPDATE Channel SET (version) = (?) WHERE id=?");
        sql.setObject(1, UUID.randomUUID());
        sql.setObject(2,channelId);
        sql.executeUpdate();

        Stored<Channel> channel = get(channelId);
        giveNextVersion(channel);
        return channel;
    }
    
    /**
     * Get the current version UUID for the specified channel.
     * @param id UUID for the channel.
     */
    public UUID getCurrentVersion(UUID id)
      throws DeletedException,
             SQLException {

        final PreparedStatement channelsql = connection.prepareStatement("SELECT version FROM Channel WHERE id=?");
        channelsql.setString(1,id.toString());

        final ResultSet channelResult = channelsql.executeQuery();
        if(channelResult.next()) {
            return UUID.fromString(
                    channelResult.getString("version"));
        }
        throw new DeletedException();
    }
    
    /**
     * Wait for a new version of a channel.
     * This is a blocking call to get the next version of a channel.
     * @param identity The identity of the channel.
     * @param version  The previous version accessed.
     * @return The newest version after the specified one.
     */
    public Stored<Channel> waitNextVersion(UUID identity, UUID version)
      throws DeletedException,
             SQLException {
        var result
            = Maybe.<Stored<Channel>>builder();
        // Insert our result consumer
        synchronized(waiters) {
            var channelWaiters 
                = Maybe.just(waiters.get(identity));
            waiters.put(identity
                       ,List.cons(result
                                 ,channelWaiters.defaultValue(List.empty())));
        }
        // Test if there already is a new version avaiable
        if(!getCurrentVersion(identity).equals(version)) {
            return get(identity);
        }
        // Wait
        synchronized(result) {
            while(true) {
                try {
                    result.wait();
                    return result.getMaybe().get();
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted.");
                } catch (Maybe.NothingException e) {
                    // Still no result, looping
                }
            }
        }
    }
    
    /**
     * Notify all waiters of a new version
     */
    private void giveNextVersion(Stored<Channel> channel) {
        synchronized(waiters) {
            Maybe<List<Consumer<Stored<Channel>>>> channelWaiters 
                = Maybe.just(waiters.get(channel.identity));
            try {
                channelWaiters.get().forEach(w -> {
                    w.accept(channel);
                    synchronized(w) {
                        w.notifyAll();
                    }
                });
            } catch (Maybe.NothingException e) {
                // No were waiting for us :'(
            }
            waiters.put(channel.identity,List.empty());
        }
    }
    
    /**
     * Get the channel belonging to a specific event.
     */
    public Stored<Channel> lookupChannelForEvent(Stored<Channel.Event> e)
      throws SQLException, DeletedException {
        final PreparedStatement sql = connection.prepareStatement("SELECT channel FROM ChannelEvent WHERE event=?");
        sql.setObject(1,e.identity);
        final ResultSet rs = sql.executeQuery();
        if(rs.next()) {
            final UUID channelId = UUID.fromString(rs.getString("channel"));
            return get(channelId);
        }
        throw new DeletedException();
    }
} 
 
 
