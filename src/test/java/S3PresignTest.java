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
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

public class S3PresignTest {
		
	
	public static final String S3_BUCKET = "XXXXXXXXXXXXXXXXXX";
	public static final String S3_FILENAME = "/test/test.txt";

	//@Test
	public void presignTest() throws Exception {
		S3Presigner presigner = S3Presigner.create();
		Client client = ClientBuilder.newClient();
		client.register(JsrProvider.class);
		PresignedGetObjectRequest gpresigned = presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5)).getObjectRequest(gor -> gor.bucket(S3_BUCKET).key(S3_FILENAME).build()));

		WebTarget target = client.target(gpresigned.url().toURI());
		System.out.format("Download URL: %s\n", gpresigned.url());
		Response response = target.request().get();
		System.out.format("Download Response: %d %s\n", response.getStatus(), response.readEntity(String.class));

		PresignedPutObjectRequest ppresigned = presigner.presignPutObject(r -> r.signatureDuration(Duration.ofMinutes(5)).putObjectRequest(por -> por.bucket(S3_BUCKET).key(S3_FILENAME).build()));

		System.out.format("Upload URL: %s\n", ppresigned.url());
		target = client.target(ppresigned.url().toURI());
		JsonObject obj = Json.createObjectBuilder().add("test", "test").build();
		response = target.request().put(Entity.entity(obj, MediaType.APPLICATION_JSON));
		System.out.format("Upload Response: %d %s\n", response.getStatus(), response.readEntity(String.class));

	}

}
