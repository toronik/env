package localstack

import io.github.adven27.env.localstack.resettableLocalStack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class ResettableLocalStackRemoteTest {

    private val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
        .withServices(LocalStackContainer.Service.S3)

    private lateinit var s3: S3Client

    @Before
    fun startContainer() {
        localstack.start()

        s3 = S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
                )
            )
            .region(Region.of("us-east-1"))
            .build()

        // Create bucket and put an object to simulate existing state
        s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build())
        s3.putObject(
            PutObjectRequest.builder().bucket(TEST_BUCKET).key("junk-object").build(),
            RequestBody.fromString("junk content")
        )
    }

    @After
    fun stopContainer() {
        System.clearProperty("SPECS_SUT_START")
        s3.close()
        localstack.stop()
    }

    @Test
    fun `start with reset gate unset empties junk and provisions classpath fixtures`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true

        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to TEST_BUCKET
        )

        val system = resettableLocalStack(props)
        system.start(false)

        assertTrue(system.running())

        // Bucket must exist and contain only classpath fixtures (junk-object deleted, fixtures uploaded)
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(TEST_BUCKET).build()
        )
        val keys = objects.contents().map { it.key() }.sorted()
        assertTrue("junk-object must be removed after reset", "junk-object" !in keys)
        assertTrue("fixture.txt must be provisioned from classpath s3/", "fixture.txt" in keys)
        // Verify the bucket still exists (not deleted)
        s3.headBucket(HeadBucketRequest.builder().bucket(TEST_BUCKET).build()) // throws if missing
    }

    @Test
    fun `start with reset gate unset creates bucket and provisions fixtures when it does not exist yet`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true

        val freshBucket = "first-run-bucket"
        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to freshBucket
        )

        val system = resettableLocalStack(props)
        system.start(false)

        // Bucket must now exist and contain classpath fixtures (created on first run)
        s3.headBucket(HeadBucketRequest.builder().bucket(freshBucket).build()) // throws if missing
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(freshBucket).build()
        )
        val keys = objects.contents().map { it.key() }.sorted()
        assertTrue("fixture.txt must be provisioned from classpath s3/", "fixture.txt" in keys)
    }

    @Test
    fun `start with SPECS_SUT_START=false is connect-only and data survives`() {
        System.setProperty("SPECS_SUT_START", "false")

        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to TEST_BUCKET
        )

        val system = resettableLocalStack(props)
        system.start(false)

        // Object should survive (no reset performed)
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(TEST_BUCKET).build()
        )
        assertEquals("data should survive in connect-only mode", 1, objects.contents().size)
        assertEquals("junk-object", objects.contents()[0].key())
    }

    @Test
    fun `path-style endpoint works for namespaced bucket name`() {
        // Regression for #1: virtual-host style SDK default fails for namespaced buckets.
        // The resettable system must force path-style so `df-test`, `pk849-test`, etc. resolve.
        System.clearProperty("SPECS_SUT_START")

        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val namespacedBucket = "df-test-namespaced"
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to namespacedBucket
        )

        // Should not throw: virtual-host style would DNS-fail for "df-test-namespaced.<host>"
        val system = resettableLocalStack(props)
        system.start(false)

        // Verify bucket exists and is reachable via path-style
        s3WithPathStyle().use { cl ->
            cl.headBucket(HeadBucketRequest.builder().bucket(namespacedBucket).build())
        }
        assertTrue(system.running())
    }

    @Test
    fun `after reset classpath s3 fixtures are uploaded to the bucket`() {
        // Regression for #3: resettable must provision fixtures from classpath s3/ the same way
        // the container variant does (it mounts s3/ dir and runs awslocal s3 cp recursively).
        System.clearProperty("SPECS_SUT_START")

        val fixturedBucket = "fixture-test-bucket"
        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to fixturedBucket
        )

        val system = resettableLocalStack(props)
        system.start(false)

        // The classpath s3/ folder contains fixture.txt and subdir/nested.txt
        // After start(), both must be present in the bucket
        s3WithPathStyle().use { cl ->
            val objectKeys = cl.listObjectsV2(ListObjectsV2Request.builder().bucket(fixturedBucket).build())
                .contents().map { it.key() }.sorted()
            assertTrue(
                "fixture.txt must be uploaded from classpath s3/",
                objectKeys.contains("fixture.txt")
            )
            assertTrue(
                "subdir/nested.txt must be uploaded from classpath s3/",
                objectKeys.contains("subdir/nested.txt")
            )
            val content = cl.getObjectAsBytes(
                GetObjectRequest.builder().bucket(fixturedBucket).key("fixture.txt").build()
            ).asUtf8String().trim()
            assertEquals("hello fixture", content)
        }
    }

    private fun s3WithPathStyle(): S3Client = S3Client.builder()
        .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .region(Region.of("us-east-1"))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    companion object {
        private const val TEST_BUCKET = "pk-849-test"
    }
}
