package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.events.CoasterAfterChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterAfterChangeTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterDeleteConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterDeleteNodeEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterDeleteTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Base class that makes it easier to create and add new changes
 */
public abstract class HistoryChangeCollection {

    /**
     * Adds a change to be performed after this change is performed ({@link #redo()}).
     * When undoing, the child changes are performed in reverse order beforehand.
     * 
     * @param change to add
     * @return change added
     */
    public abstract HistoryChange addChange(HistoryChange change);

    /**
     * Removes a change from this collection of changes, making it as if the change
     * never happened.
     * 
     * @param change to remove
     */
    public abstract void removeChange(HistoryChange change);

    /**
     * Gets whether changes were added using {@link #addChange(HistoryChange)}.
     * 
     * @return True if there are changes
     */
    public abstract boolean hasChanges();

    public final HistoryChange addChangeGroup() {
        return addChange(new HistoryChangeGroup());
    }

    /**
     * Returns a history change that adds itself to this collection when the first
     * element is added. If no elements are ever added, then this change is not added either.
     * 
     * @return change added
     */
    public final HistoryChange addLazyChangeGroup() {
        return new HistoryChangeLazyGroup(this);
    }

    private <T extends CoasterEvent> T handleEvent(T event) throws ChangeCancelledException {
        CommonUtil.callEvent(event);
        if (event.isCancelled()) {
            throw new ChangeCancelledException();
        }
        return event;
    }

    public final HistoryChange addChangeConnect(Player who, TrackNode nodeA, TrackNode nodeB) throws ChangeCancelledException {
        for (TrackConnection connection : nodeA.getConnections()) {
            if (connection.getOtherNode(nodeA) == nodeB) {
                return addChangeConnect(who, connection);
            }
        }
        throw new IllegalStateException("Trying to handle a connect change for a connection that does not exist");
    }

    public final HistoryChange addChangeConnect(Player who, TrackConnection connection) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterCreateConnectionEvent(who, connection));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            connection.remove();
            throw ex;
        }
        return addChange(new HistoryChangeConnect(connection.getNodeA(), connection.getNodeB(), connection.getObjects()));
    }

    public final HistoryChange addChangeDisconnect(Player who, TrackConnection connection) throws ChangeCancelledException {
        handleEvent(new CoasterDeleteConnectionEvent(who, connection));
        return addChange(new HistoryChangeDisconnect(connection.getNodeA(), connection.getNodeB(), connection.getObjects()));
    }

    public final HistoryChange addChangeAfterChangingNode(Player who, TrackNode node, TrackNodeState startState) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterAfterChangeNodeEvent(who, node, startState));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            node.setState(startState);
            throw ex;
        }

        return addChange(new HistoryChangeNode(node.getWorld(), startState, node.getState()));
    }

    public final HistoryChange addChangeCreateNode(Player who, TrackNode node) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterCreateNodeEvent(who, node));
        } catch (ChangeCancelledException ex) {
            node.remove();
            throw ex;
        }
        return addChange(new HistoryChangeCreateNode(node));
    }

    public final HistoryChange addChangeBeforeSetRail(Player who, TrackNode node, IntVector3 new_rail) throws ChangeCancelledException {
        handleEvent(new CoasterBeforeChangeNodeEvent(who, node));
        TrackNodeState old_state = node.getState();
        TrackNodeState new_state = old_state.changeRail(new_rail);
        return addChange(new HistoryChangeNode(node.getWorld(), old_state, new_state));
    }

    public final HistoryChange addChangeBeforeCreateTrackObject(Player who, TrackConnection connection, TrackObject object) throws ChangeCancelledException {
        handleEvent(new CoasterCreateTrackObjectEvent(who, connection, object));
        return addChange(new HistoryChangeCreateTrackObject(connection, object));
    }

    public final HistoryChange addChangeBeforeDeleteTrackObject(Player who, TrackConnection connection, TrackObject object) throws ChangeCancelledException {
        handleEvent(new CoasterDeleteTrackObjectEvent(who, connection, object));
        return addChange(new HistoryChangeDeleteTrackObject(connection, object));
    }

    public final HistoryChange addChangeAfterChangingTrackObjectType(Player who, TrackConnection connection, TrackObject object, TrackObject old_object) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterAfterChangeTrackObjectEvent(who, connection, object, connection, old_object));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            object.setType(connection, old_object.getType());
            throw ex;
        }

        return addChange(new HistoryChangeTrackObject(connection, connection, old_object, object));
    }

    public final HistoryChange addChangeAfterMovingTrackObject(Player who, TrackConnection connection, TrackObject object, TrackConnection old_connection, TrackObject old_object) throws ChangeCancelledException {
        try {
            handleEvent(new CoasterAfterChangeTrackObjectEvent(who, connection, object, old_connection, old_object));
        } catch (ChangeCancelledException ex) {
            // Revert the changes
            if (connection == old_connection) {
                object.setDistanceFlipped(connection, old_object.getDistance(), old_object.isFlipped());
            } else {
                connection.removeObject(object);
                object.setDistanceFlippedSilently(old_object.getDistance(), old_object.isFlipped());
                old_connection.addObject(object);
            }
            throw ex;
        }

        return addChange(new HistoryChangeTrackObject(old_connection, connection, old_object, object));
    }

    /**
     * Adds a change for after a player (already) created a full coaster. No events or permissions are checked.
     * 
     * @param coaster
     * @return change
     */
    public final HistoryChange addChangeAfterCreatingCoaster(TrackCoaster coaster) {
        HistoryChange group = this.addChangeGroup();
        Set<TrackConnection> connections = new LinkedHashSet<TrackConnection>();
        for (TrackNode node : coaster.getNodes()) {
            group.addChange(new HistoryChangeCreateNode(node));
            connections.addAll(node.getConnections());
        }
        for (TrackConnection connection : connections) {
            group.addChange(new HistoryChangeConnect(connection.getNodeA(), connection.getNodeB(), connection.getObjects()));
        }
        return group;
    }

    public void handleChangeBefore(Player who, TrackNode node) throws ChangeCancelledException {
        handleEvent(new CoasterBeforeChangeNodeEvent(who, node));
    }

    public void handleChangeAfterSetRail(Player who, TrackNode node, IntVector3 old_rail) throws ChangeCancelledException {
        TrackNodeState old_state = node.getState().changeRail(old_rail);
        handleEvent(new CoasterAfterChangeNodeEvent(who, node, old_state));
    }

    /**
     * Adds the changes required when deleting a node. Also adds all changes to do
     * with disconnecting (and re-connecting) neighbour connections
     * 
     * @param who   The player that wants to delete a node
     * @param node  The node being deleted
     * @return change
     */
    public final HistoryChange addChangeDeleteNode(Player who, TrackNode node) throws ChangeCancelledException {
        handleEvent(new CoasterDeleteNodeEvent(who, node));

        List<TrackConnection> connections = node.getConnections();
        if (connections.isEmpty()) {
            return addChange(new HistoryChangeDeleteNode(node));
        } else {
            HistoryChange changes = this.addChangeGroup();
            for (TrackConnection connection : connections) {
                changes.addChangeDisconnect(who, connection);
            }
            changes.addChange(new HistoryChangeDeleteNode(node));
            return changes;
        }
    }
}
