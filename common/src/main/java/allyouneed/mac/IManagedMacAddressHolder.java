package allyouneed.mac;

/**
 * Mixed into {@link appeng.me.ManagedGridNode} as the authoritative MAC store
 * across node destroy/recreate cycles.
 */
public interface IManagedMacAddressHolder {

    long getMacAddress();

    void setMacAddress(long mac);

    String getMacTagName();
}
