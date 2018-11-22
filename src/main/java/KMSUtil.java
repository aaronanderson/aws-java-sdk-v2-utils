import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;

//Utility class used to lazy load an AWS data key from some type of text file or optionally retrieved from DynamoDB
//https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html#data-keys
public class KMSUtil {
	private static SecretKey secret;
	private static KmsClient kmsClient;
	private static DynamoDbClient dynamdDBClient;
	private static String dynamoDBTable;
	private static AttributeValue dynamoDBID;
	private static String dynamoDBAttr;

	public static void initialize(KmsClient kmsClient) {
		KMSUtil.kmsClient = kmsClient;
	}

	public static void initialize(KmsClient kmsClient, DynamoDbClient dynamdDBClient, String dynamoDBTable, AttributeValue dynamoDBID, String dynamoDBAttr) {
		initialize(kmsClient);
		KMSUtil.kmsClient = kmsClient;
		KMSUtil.dynamdDBClient = dynamdDBClient;
		KMSUtil.dynamoDBTable = dynamoDBTable;
		KMSUtil.dynamoDBID = dynamoDBID;
		KMSUtil.dynamoDBAttr = dynamoDBAttr;
	}

	private static synchronized void loadEncryptionKey() throws Exception {
		if (secret == null) {
			if (dynamdDBClient == null) {
				throw new Exception("Uninitialized");
			}
			Map<String, AttributeValue> repoQueryValues = new HashMap<>();
			repoQueryValues.put("ID", dynamoDBID);
			GetItemResponse repoResponse = dynamdDBClient.getItem(GetItemRequest.builder().tableName(dynamoDBTable).key(repoQueryValues).projectionExpression(dynamoDBAttr).build());
			Map<String, AttributeValue> repoItemValues = repoResponse.item();
			if (repoItemValues == null) {
				throw new Exception(String.format("Unable to locate %s in DynamoDB table %s", dynamoDBID, dynamoDBTable));
			}
			if (!repoItemValues.containsKey((dynamoDBAttr))) {
				throw new Exception(String.format("Unable to locate attribute %s for item ID %s in table %s", dynamoDBAttr, dynamoDBID, dynamoDBTable));
			}
			String encrypted = repoItemValues.get(dynamoDBAttr).s();
			loadEncryptionKey(encrypted);
		}
	}

	private static synchronized void loadEncryptionKey(String encrypted) throws Exception {
		if (secret == null) {
			if (kmsClient == null) {
				throw new Exception("Uninitialized");
			}
			byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
			DecryptResponse kmsResponse = kmsClient.decrypt((DecryptRequest.builder().ciphertextBlob(SdkBytes.fromByteArray(encryptedBytes))).build());
			final byte[] decBytes = kmsResponse.plaintext().asByteArray();

			secret = new SecretKeySpec(decBytes, "AES");
		}

		// String encS3Key = Base64.getEncoder().encodeToString(encBytes);
		// EncryptResponse kmsResponse = kmsClient.encrypt(EncryptRequest.builder().keyId(kmsAlias).plaintext(ByteBuffer.wrap(s3Key.getBytes())).build());
		// final byte[] encBytes = new byte[kmsResponse.ciphertextBlob().remaining()];
		// kmsResponse.ciphertextBlob().get(encBytes);
		// String encS3Key = Base64.getEncoder().encodeToString(encBytes);
	}

	public static final String encrypt(String value) throws Exception {
		loadEncryptionKey();
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, secret);
		byte[] secureReturnURL = cipher.doFinal(value.getBytes());
		return Base64.getEncoder().encodeToString(secureReturnURL);
	}

	public static final String decrypt(String value) throws Exception {
		loadEncryptionKey();
		byte[] secureReturnURL = Base64.getDecoder().decode(value);
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, secret);
		secureReturnURL = cipher.doFinal(secureReturnURL);
		return new String(secureReturnURL);
	}

}
