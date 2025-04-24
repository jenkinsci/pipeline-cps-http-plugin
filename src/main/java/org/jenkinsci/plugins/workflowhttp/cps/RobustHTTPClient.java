package org.jenkinsci.plugins.workflowhttp.cps;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.MasterToSlaveFileCallable;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.JenkinsJVM;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.message.StatusLine;

/** Utility to make HTTP connections with protection against transient failures. */
public final class RobustHTTPClient implements Serializable {

    private static final long serialVersionUID = 1;

    private static final ExecutorService executors =
            JenkinsJVM.isJenkinsJVM() ? Computer.threadPoolForRemoting : Executors.newCachedThreadPool();

    private int stopAfterAttemptNumber;
    // all times are measured in milliseconds
    private long waitMultiplier;
    private long waitMaximum;
    private long timeout;

    /**
     * Creates a client configured with reasonable defaults from system properties. THIS IS A ADAPTED
     * COPY OF https://github.com/jenkinsci/apache-httpcomponents-client-4-api-plugin but uses
     * httpclient5 and retries on all errors
     *
     * <p>This constructor should be run in the Jenkins master. To make requests from an agent JVM,
     * create a {@code final} field of this type in your {@link MasterToSlaveCallable} or similar; set
     * it with a field initializer (run in the callable’s constructor on the master), letting the
     * agent deserialize the configuration.
     */
    public RobustHTTPClient() {
        JenkinsJVM.checkJenkinsJVM();
        this.stopAfterAttemptNumber =
                Integer.getInteger(RobustHTTPClient.class.getName() + ".STOP_AFTER_ATTEMPT_NUMBER", 10);
        this.waitMultiplier = Long.getLong(RobustHTTPClient.class.getName() + ".WAIT_MULTIPLIER", 100);
        this.waitMaximum =
                Long.getLong(RobustHTTPClient.class.getName() + ".WAIT_MAXIMUM", TimeUnit.MINUTES.toMillis(5));
        this.timeout = Long.getLong(RobustHTTPClient.class.getName() + ".TIMEOUT", TimeUnit.MINUTES.toMillis(15));
    }

    /**
     * Mask out query string or user info details in a URL. Useful in conjunction with {@code
     * VirtualFile#toExternalURL}.
     *
     * @param url any URL
     * @return the same, but with any {@link URL#getQuery} and/or {@link URL#getUserInfo} concealed
     */
    public static String sanitize(URL url) {
        try {
            URI orig = url.toURI();
            return new URI(
                            orig.getScheme(),
                            orig.getUserInfo() != null ? "…" : null,
                            orig.getHost(),
                            orig.getPort(),
                            orig.getPath(),
                            orig.getQuery() != null ? "…" : null,
                            orig.getFragment())
                    .toString();
        } catch (URISyntaxException x) {
            assert false : x;
            return url.toString();
        }
    }

    /** Number of upload/download attempts of nonfatal errors before giving up. */
    public void setStopAfterAttemptNumber(int stopAfterAttemptNumber) {
        this.stopAfterAttemptNumber = stopAfterAttemptNumber;
    }

    /**
     * Initial time between first and second upload/download attempts. Subsequent ones increase
     * exponentially. Note that this is not a <em>randomized</em> exponential backoff; and the base of
     * the exponent is currently hard-coded to 2.
     */
    public void setWaitMultiplier(long waitMultiplier, TimeUnit unit) {
        this.waitMultiplier = unit.toMillis(waitMultiplier);
    }

    /** Maximum time between upload/download attempts. */
    public void setWaitMaximum(long waitMaximum, TimeUnit unit) {
        this.waitMaximum = unit.toMillis(waitMaximum);
    }

    /** Time to permit a single upload/download attempt to take. */
    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
    }

    /**
     * Perform an HTTP network operation with appropriate timeouts and retries. 2xx status codes are
     * considered successful. Low-level network errors (for example, DNS failures) and 5xx server
     * responses are considered retryable, as are timeouts on individual attempts; other response
     * codes (normally 3xx or 4xx) are treated as immediate failures.
     *
     * @param whatConcise a short description of the operation, like {@code upload}, used when
     *     retrying
     * @param whatVerbose a longer description of the operation, like {@code uploading … to …}, used
     *     when retrying (see {@link #sanitize})
     * @param connectionCreator how to establish a connection prior to getting the server’s response
     * @param connectionUser what to do, if anything, after a successful (2xx) server response
     * @param listener a place to print messages
     * @throws IOException if there is an unrecoverable error; {@link AbortException} will be used
     *     where appropriate
     * @throws InterruptedException if an operation, or a sleep between retries, is interrupted
     */
    public void connect(
            String whatConcise,
            String whatVerbose,
            @NonNull ConnectionCreator connectionCreator,
            @NonNull ConnectionUser connectionUser,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        AtomicInteger responseCode = new AtomicInteger();
        int attempt = 1;
        while (true) {
            try {
                try {
                    executors
                            .submit(() -> {
                                responseCode.set(0);
                                try (CloseableHttpClient client = HttpClients.createSystem()) {
                                    try (CloseableHttpResponse response = connectionCreator.connect(client)) {
                                        StatusLine statusLine = new StatusLine(response);
                                        responseCode.set(statusLine.getStatusCode());
                                        if (responseCode.get() < 200 || responseCode.get() >= 300) {
                                            String diag;
                                            HttpEntity entity = response.getEntity();
                                            if (entity != null) {
                                                try (InputStream err = entity.getContent()) {
                                                    String contentEncoding = entity.getContentEncoding();
                                                    diag = IOUtils.toString(err, contentEncoding);
                                                }
                                            } else {
                                                diag = null;
                                            }
                                            throw new AbortException(String.format(
                                                    "Failed to %s, response: %d %s, body: %s",
                                                    whatVerbose,
                                                    responseCode.get(),
                                                    statusLine.getReasonPhrase(),
                                                    diag));
                                        }
                                        connectionUser.use(response);
                                    }
                                }
                                return null; // success
                            })
                            .get(timeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException x) {
                    throw new ExecutionException(
                            new IOException(x)); // ExecutionException unwrapped & treated as retryable below
                }
                listener.getLogger().flush(); // seems we can get interleaved output with master otherwise
                return; // success
            } catch (ExecutionException wrapped) {
                Throwable x = wrapped.getCause();
                if (x instanceof IOException) {
                    if (attempt == stopAfterAttemptNumber) {
                        throw (IOException) x; // last chance
                    }
                    // TODO exponent base could be made into a configurable parameter
                    Thread.sleep(Math.min(((long) Math.pow(2d, attempt)) * waitMultiplier, waitMaximum));
                    listener.getLogger()
                            .printf(
                                    "Retrying %s after: %s%n",
                                    whatConcise, x instanceof AbortException ? x.getMessage() : x.toString());
                    attempt++; // and continue
                } else if (x instanceof InterruptedException) { // all other exceptions considered fatal
                    throw (InterruptedException) x;
                } else if (x instanceof RuntimeException) {
                    throw (RuntimeException) x;
                } else if (x != null) {
                    throw new RuntimeException(x);
                } else {
                    throw new IllegalStateException();
                }
            }
        }
    }

    /** Upload a file to a URL. */
    public void uploadFile(File f, URL url, TaskListener listener) throws IOException, InterruptedException {
        uploadFile(f, null, url, listener);
    }

    /**
     * Upload a file to a URL with a specific content type.
     *
     * @param f the file to upload
     * @param contentType the content type for the specified file
     */
    public void uploadFile(File f, String contentType, URL url, TaskListener listener)
            throws IOException, InterruptedException {
        connect(
                "upload",
                "upload " + f + " to " + sanitize(url),
                client -> {
                    HttpPut put = new HttpPut(url.toString());
                    put.setEntity(new FileEntity(f, ContentType.parse(contentType)));
                    if (contentType != null) {
                        put.setHeader("Content-Type", contentType);
                    }
                    return client.execute(put);
                },
                response -> {},
                listener);
    }

    /** Download a file from a URL. */
    public void downloadFile(File f, URL url, TaskListener listener) throws IOException, InterruptedException {
        connect(
                "download",
                "download " + sanitize(url) + " to " + f,
                client -> client.execute(new HttpGet(url.toString())),
                response -> {
                    try (InputStream is = response.getEntity().getContent()) {
                        FileUtils.copyInputStreamToFile(is, f);
                    }
                },
                listener);
    }

    /**
     * Like {@link FilePath#copyFrom(URL)} but using {@link #downloadFile} and running remotely on the
     * agent.
     */
    public void copyFromRemotely(FilePath f, URL url, TaskListener listener) throws IOException, InterruptedException {
        f.act(new CopyFromRemotely(this, url, listener));
    }

    /**
     * How to initiate a connection. For example, call {@link
     * CloseableHttpClient#execute(ClassicHttpRequest)} on {@link HttpGet#HttpGet(String)}.
     *
     * @see #connect
     */
    @FunctionalInterface
    public interface ConnectionCreator {
        CloseableHttpResponse connect(CloseableHttpClient client) throws IOException, InterruptedException;
    }

    /**
     * What to do with a successful (2xx) connection. For example, call {@link
     * CloseableHttpResponse#getEntity} and {@link HttpEntity#getContent}.
     *
     * @see #connect
     */
    @FunctionalInterface
    public interface ConnectionUser {
        void use(CloseableHttpResponse response) throws IOException, InterruptedException;
    }

    private static final class CopyFromRemotely extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1;
        private final RobustHTTPClient client;
        private final URL u;
        private final TaskListener listener;

        CopyFromRemotely(RobustHTTPClient client, URL u, TaskListener listener) {
            this.client = client;
            this.u = u;
            this.listener = listener;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                client.downloadFile(f, u, listener);
            } finally {
                listener.getLogger().flush();
            }
            return null;
        }
    }
}
