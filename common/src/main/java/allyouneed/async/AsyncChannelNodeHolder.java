package allyouneed.async;

/**
 * Mixed into {@link appeng.me.GridNode} to track how many channels the grid node of an
 * async processing connector swallowed during the most recent channel assignment.
 */
public interface AsyncChannelNodeHolder {

    void setAsyncSwallowedChannels(int channels);

    int getAsyncSwallowedChannels();
}
