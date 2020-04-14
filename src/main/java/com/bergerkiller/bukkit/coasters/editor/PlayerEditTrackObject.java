package com.bergerkiller.bukkit.coasters.editor;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Metadata about a single track object being edited
 */
public class PlayerEditTrackObject {
    public TrackConnection connection;
    public final TrackObject object;
    public double dragDistance;
    public boolean dragDirection;

    public PlayerEditTrackObject(TrackConnection connection, TrackObject object) {
        this.connection = connection;
        this.object = object;
        this.dragDistance = Double.NaN;
        this.dragDirection = false;
    }
}
