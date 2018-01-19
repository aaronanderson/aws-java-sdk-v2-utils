
import java.net.URI;
import java.time.Duration;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.johnzon.jaxrs.JsrProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import software.amazon.awssdk.core.auth.AwsCredentials;
import software.amazon.awssdk.core.auth.AwsSessionCredentials;
import software.amazon.awssdk.core.auth.DefaultCredentialsProvider;

public class ElasticSearchTest {

	private static AwsCredentials credentials;

	//@BeforeClass
	public static void setup() {
		// AWS classes are not required by this utility but they will probably be available in the classpath
		// and make it easy to lookup credentials
		DefaultCredentialsProvider provider = DefaultCredentialsProvider.create();
		credentials = provider.getCredentials();

	}

	//@Test
	public void elasticsearchTest() throws Exception {
		System.out.format("Accessing Elastic Search\n");
		Client client = ClientBuilder.newClient();
		client.register(JsrProvider.class);

		AWSSignerClientRequestFilter.Builder builder = AWSSignerClientRequestFilter.builder().accessKeyID(credentials.accessKeyId()).secretAccessKey(credentials.secretAccessKey()).regionName("us-west-1").serviceName("es");
		if (AwsSessionCredentials.class.isAssignableFrom(credentials.getClass())) {
			builder.sessionToken(() -> (((AwsSessionCredentials) credentials).sessionToken()));
		}
		AWSSignerClientRequestFilter signer = builder.build();
		client.register(signer);
		WebTarget target = client.target("https://search-some-aws-elasticsearch-domain.us-west-1.es.amazonaws.com");
		Response response = target.request().get();
		//This is an invalid post but it shows the signature process works for complex paths and parameters.
		//JsonObject obj = Json.createObjectBuilder().add("settings", Json.createObjectBuilder().add("index", Json.createObjectBuilder().add("number_of_shards", 1).add("number_of_replicas", 1))).build();
		//Response response = target.path("artifact test + 2/test 3/").queryParam("a", "test").queryParam("Version", "2010-05-08", "2010-04-08").queryParam("Action", "ListUsers").request().post(Entity.entity(obj, MediaType.APPLICATION_JSON));
		System.out.format("%d %s", response.getStatus(), response.readEntity(String.class));
	}

	//@Test
	public void presignTest() throws Exception {
		AWSSignerClientRequestFilter signer = AWSSignerClientRequestFilter.builder().accessKeyID(credentials.accessKeyId()).secretAccessKey(credentials.secretAccessKey()).regionName("us-west-1").serviceName("s3").build();

		Client client = ClientBuilder.newClient();
		client.register(JsrProvider.class);
		URI presigned = signer.presign("GET", new URI("https://some-s3-bucket.s3-us-west-1.amazonaws.com/intworkspace.json"), Duration.ofDays(1));
		WebTarget target = client.target(presigned);
		System.out.format("Download URL: %s\n", presigned);
		Response response = target.request().get();
		System.out.format("Download Response: %d %s\n", response.getStatus(), response.readEntity(String.class));

		presigned = signer.presign("PUT", new URI("https://some-s3-bucket.s3-us-west-1.amazonaws.com/intworkspace-test.json"), Duration.ofDays(1));
		System.out.format("Upload URL: %s\n", presigned);
		target = client.target(presigned);
		JsonObject obj = Json.createObjectBuilder().add("test", "test").build();
		response = target.request().put(Entity.entity(obj, MediaType.APPLICATION_JSON));
		System.out.format("Upload Response: %d %s\n", response.getStatus(), response.readEntity(String.class));

	}

}
