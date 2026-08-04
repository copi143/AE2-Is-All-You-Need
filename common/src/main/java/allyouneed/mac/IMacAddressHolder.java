package allyouneed.mac;

/**
 * Mixed into {@link appeng.me.GridNode} to expose the 48-bit MAC address.
 */
public interface IMacAddressHolder {

    long getMacAddress();

    void setMacAddress(long mac);
}
