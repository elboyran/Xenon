package nl.esciencecenter.xenon.adaptors.generic;

import java.net.URI;
import java.net.URISyntaxException;

import nl.esciencecenter.xenon.InvalidLocationException;

public abstract class Location {

    private final String user;
    private final String host;
    private final int port;
    private String scheme;
    protected final String path;
    private static final String SCHEME_SEPARATOR = "://";
    private static final String DUMMY_SCHEME = "dummy";

    public Location(String user, String host, int port) {
        this.user = user;
        this.host = host;
        this.port = port;
        scheme = null;
        path = "";
    }

    protected Location(String location) throws InvalidLocationException {
        // Augment location with dummy scheme, if needed, to allow processing by URI
        String augmentedLocation = null;
        boolean hasScheme = location.contains(SCHEME_SEPARATOR);
        if (!hasScheme) {
            augmentedLocation = DUMMY_SCHEME + SCHEME_SEPARATOR + location;
        }

        try {
            URI url = new URI(augmentedLocation == null ? location : augmentedLocation);
            user = url.getUserInfo();
            host = url.getHost();
            port = url.getPort();
            scheme = hasScheme ? url.getScheme() : null;
            path = url.getPath();
            validate();
        } catch (URISyntaxException e) {
            throw new InvalidLocationException(getAdaptorName(), "Could not parse location " + location, e);
        }
        if (port <= 0 && port != -1) {
            throw new InvalidLocationException(getAdaptorName(), "Port number of " + location + " must be positive or omited");
        }
    }

    private void validate() throws URISyntaxException {
        if (host == null) {
            throw new URISyntaxException(getAdaptorName(), "Could not extract host from URI");
        }
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
    	if (port == -1) {
    		return getDefaultPort();
    	}
    
        return port;
    }

    public String getSCheme() {
        return scheme;
    }

    protected void setScheme(String scheme) {
        this.scheme = scheme;
    }

    protected abstract String getAdaptorName();

    protected abstract int getDefaultPort();

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(100);
        appendScheme(result);
        appendUser(result);
        appendHostAndPort(result);
        return result.toString();
    }

    private void appendHostAndPort(StringBuilder result) {
        result.append(host);
        result.append(":");
        result.append(port);
    }

    private void appendUser(StringBuilder result) {
        if (user != null) {
            result.append(user);
            result.append("@");
        }
    }

    private void appendScheme(StringBuilder result) {
        if (scheme != null) {
            result.append(scheme);
            result.append(SCHEME_SEPARATOR);
        }
    }
    
    /** Whether the default port was used because none was provided. */
    public boolean usesDefaultPort() {
        return port == -1;
    }
}