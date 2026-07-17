package io.github.adven27.env.localstack

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.shouldReset
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val S3_CLASSPATH_DIR = "s3"

@Suppress("unused")
fun resettableLocalStack(props: Map<String, String>): ExternalSystem = ResettableLocalStackRemote(props)

private class ResettableLocalStackRemote(private val props: Map<String, String>) : ExternalSystem {
    override val config: ExternalSystemConfig = ExternalSystemConfig(props)

    override fun start(fixedEnv: Boolean) {
        props.forEach(System::setProperty)
        if (!shouldReset()) return

        val endpoint = props.getValue("env.aws.s3.endpoint")
        val region = props.getValue("env.aws.region")
        val accessKey = props.getValue("env.aws.access-key")
        val secretKey = props.getValue("env.aws.secret-key")
        val bucket = props.getValue("env.aws.s3.bucket")

        // #1: force path-style — SDK v2 defaults to virtual-host which DNS-fails for namespaced
        // bucket names (e.g. "df-test", "pk849-test") when there is no wildcard DNS alias.
        val s3 = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            )
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

        s3.use { client ->
            ensureBucketEmpty(client, bucket)
            // #3: provision fixtures from classpath s3/ — mirrors LocalStackContainerSystem which
            // mounts the same directory and runs `awslocal s3 cp /home/test/s3 s3://<bucket> --recursive`.
            uploadClasspathFixtures(client, bucket)
        }
    }

    override fun stop() = Unit

    override fun running() = true

    private fun ensureBucketEmpty(s3: S3Client, bucket: String) {
        // Ensure the bucket exists — create only if missing (idempotent on first run)
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        } catch (_: BucketAlreadyOwnedByYouException) {
            // Already exists — proceed to empty
        } catch (_: BucketAlreadyExistsException) {
            // Already exists — proceed to empty
        }

        // Bulk-empty: one deleteObjects call per page
        var continuationToken: String? = null
        do {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .also { b -> continuationToken?.let { b.continuationToken(it) } }
                .build()
            val response = s3.listObjectsV2(request)
            val objects = response.contents()
            if (objects.isNotEmpty()) {
                val identifiers = objects.map {
                    ObjectIdentifier.builder().key(it.key()).build()
                }
                s3.deleteObjects(
                    DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(identifiers).build())
                        .build()
                )
            }
            continuationToken = if (response.isTruncated == true) response.nextContinuationToken() else null
        } while (continuationToken != null)
    }

    /**
     * Uploads all files from the classpath `s3/` directory into [bucket], preserving relative paths as S3 keys.
     * Mirrors the container variant: `awslocal s3 cp /home/test/s3 s3://<bucket> --recursive`.
     *
     * Supports both unpacked classpath directories and JAR-packaged resources.
     * If no `s3/` classpath resource exists, this is a no-op (fixtures are optional).
     */
    private fun uploadClasspathFixtures(s3: S3Client, bucket: String) {
        val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        loader.getResources(S3_CLASSPATH_DIR).toList().forEach { uploadResource(s3, bucket, it) }
    }

    private fun uploadResource(s3: S3Client, bucket: String, resource: URL) {
        val uri = resource.toURI()
        if (uri.scheme == "jar") {
            uploadJarResource(s3, bucket, uri)
        } else {
            val root = Paths.get(uri)
            if (Files.exists(root)) uploadDirectory(s3, bucket, root)
        }
    }

    private fun uploadJarResource(s3: S3Client, bucket: String, uri: URI) {
        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fs ->
            val root = fs.getPath("/$S3_CLASSPATH_DIR")
            if (Files.exists(root)) uploadDirectory(s3, bucket, root)
        }
    }

    private fun uploadDirectory(s3: S3Client, bucket: String, root: Path) {
        Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .forEach { file ->
                val key = root.relativize(file).toString().replace("\\", "/")
                s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(Files.readAllBytes(file))
                )
            }
    }
}
