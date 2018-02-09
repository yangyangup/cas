package org.apereo.cas.couchbase.core;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.bucket.BucketManager;
import com.couchbase.client.java.error.DesignDocumentDoesNotExistException;
import com.couchbase.client.java.view.DesignDocument;
import com.couchbase.client.java.view.View;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apereo.cas.util.HttpUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A factory class which produces a client for a particular Couchbase getBucket.
 * A design consideration was that we want the server to start even if Couchbase
 * is unavailable, picking up the connection when Couchbase comes online. Hence
 * the creation of the client is made using a scheduled task which is repeated
 * until successful connection is made.
 *
 * @author Fredrik Jönsson "fjo@kth.se"
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
public class CouchbaseClientFactory {
    private static final int DEFAULT_TIMEOUT = 5;
    private static final int BUCKET_QUOTA = 120;

    private Cluster cluster;

    private Bucket bucket;
    private final Collection<View> views;
    private final Set<String> nodes;

    /* The name of the bucket, will use the default getBucket unless otherwise specified. */
    private String bucketName = "default";

    /* Password for the bucket if any. */
    private String bucketPassword = StringUtils.EMPTY;

    /* Design document and views to create in the bucket, if any. */
    private final String designDocument;

    private long timeout = DEFAULT_TIMEOUT;

    /**
     * Instantiates a new Couchbase client factory.
     *
     * @param nodes          cluster nodes
     * @param bucketName     getBucket name
     * @param bucketPassword the bucket password
     * @param timeout        connection timeout
     * @param documentName   the document name
     * @param views          the views
     */
    public CouchbaseClientFactory(final Set<String> nodes, final String bucketName,
                                  final String bucketPassword, final long timeout,
                                  final String documentName, final Collection<View> views) {
        this.nodes = nodes;
        this.bucketName = bucketName;
        this.bucketPassword = bucketPassword;
        this.timeout = timeout;
        this.designDocument = documentName;
        this.views = views;
        initializeCluster();
    }

    /**
     * Instantiates a new Couchbase client factory.
     *
     * @param nodes          the nodes
     * @param bucketName     the bucket name
     * @param bucketPassword the bucket password
     */
    public CouchbaseClientFactory(final Set<String> nodes, final String bucketName, final String bucketPassword) {
        this(nodes, bucketName, bucketPassword, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Inverse of connectBucket, shuts down the client, cancelling connection
     * task if not completed.
     */
    @SneakyThrows
    public void shutdown() {
        if (this.cluster != null) {
            this.cluster.disconnect();
        }
    }

    private void initializeCluster() {
        if (this.cluster != null) {
            shutdown();
        }
        this.cluster = CouchbaseCluster.create(new ArrayList<>(this.nodes));
    }

    /**
     * Retrieve the Couchbase getBucket.
     *
     * @return the getBucket.
     */
    public Bucket getBucket() {
        if (this.bucket != null) {
            return this.bucket;
        }
        initializeBucket();
        return this.bucket;
    }

    private void initializeBucket() {
        createBucketIfNeeded();
        createDesignDocumentAndViewIfNeeded();
    }

    private void createDesignDocumentAndViewIfNeeded() {
        if (this.views != null && this.designDocument != null) {
            LOGGER.debug("Ensure that indexes exist in bucket [{}]", this.bucket.name());
            final BucketManager bucketManager = this.bucket.bucketManager();
            final DesignDocument newDocument = DesignDocument.create(this.designDocument, new ArrayList<>(views));
            try {
                if (!newDocument.equals(bucketManager.getDesignDocument(this.designDocument))) {
                    LOGGER.warn("Missing indexes in bucket [{}] for document [{}]", this.bucket.name(), this.designDocument);
                    bucketManager.upsertDesignDocument(newDocument);
                }
            } catch (final DesignDocumentDoesNotExistException e) {
                LOGGER.debug("Design document in bucket [{}] for document [{}] should be created", this.bucket.name(), this.designDocument);
                bucketManager.upsertDesignDocument(newDocument);
            } catch (final Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    private void createBucketIfNeeded() {
        try {
            LOGGER.debug("Trying to connect to couchbase bucket [{}]", this.bucketName);
            this.bucket = this.cluster.openBucket(this.bucketName, this.bucketPassword, this.timeout, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to connect to Couchbase bucket " + this.bucketName, e);
        }
        LOGGER.info("Connected to Couchbase bucket [{}]", this.bucketName);
    }

    /**
     * Remove default bucket.
     */
    public static void removeDefaultBucket() {
        HttpUtils.execute("http://localhost:8091/pools/default/buckets/default", "DELETE");
    }

    /**
     * Create default bucket http servlet response.
     *
     * @return the http servlet response
     */
    @SneakyThrows
    public static HttpResponse createDefaultBucket() {
        final List postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("authType", "none"));
        postParameters.add(new BasicNameValuePair("name", "default"));
        postParameters.add(new BasicNameValuePair("bucketType", "couchbase"));
        postParameters.add(new BasicNameValuePair("proxyPort", "11216"));
        postParameters.add(new BasicNameValuePair("ramQuotaMB", "120"));
        final UrlEncodedFormEntity entity = new UrlEncodedFormEntity(postParameters, "UTF-8");
        return HttpUtils.executePost("http://localhost:8091/pools/default/buckets", entity);
    }
}

