package sonar.core.api.blocks;

/** Shared connection IDs allow compatible blocks to join their textures. */
public interface IConnectedBlock {
    int[] getConnections();
}
