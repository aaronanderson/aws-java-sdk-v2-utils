import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import javax.xml.bind.DatatypeConverter;

//This class is inspired from the following sources:
//https://github.com/javaquery/Examples/blob/master/src/com/javaquery/aws/AWSV4Auth.java
//https://stackoverflow.com/a/36677808
//https://github.com/inreachventures/aws-signing-request-interceptor/blob/master/src/main/java/vc/inreach/aws/request/AWSSigner.java

public class AWSSignerClientRequestFilter implements ClientRequestFilter, WriterInterceptor {

	private static final String AWS_SIGNER_DEFERRED = "AWSSigner.deferredSigning";
	private static final String ALGORITHM = "AWS4-HMAC-SHA256";

	private static final DateTimeFormatter BASIC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
	private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	private String accessKeyID;
	private String secretAccessKey;
	private Supplier<String> sessionToken;
	private String regionName;
	private String serviceName;

	private AWSSignerClientRequestFilter() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final AWSSignerClientRequestFilter filter = new AWSSignerClientRequestFilter();

		private Builder() {

		}

		public Builder accessKeyID(String accessKeyID) {
			filter.accessKeyID = accessKeyID;
			return this;
		}

		public Builder secretAccessKey(String secretAccessKey) {
			filter.secretAccessKey = secretAccessKey;
			return this;
		}

		public Builder sessionToken(Supplier<String> sessionToken) {
			filter.sessionToken = sessionToken;
			return this;
		}

		public Builder regionName(String regionName) {
			filter.regionName = regionName;
			return this;
		}

		public Builder serviceName(String serviceName) {
			filter.serviceName = serviceName;
			return this;
		}

		public AWSSignerClientRequestFilter build() {
			return filter;
		}

	}

	public void filter(ClientRequestContext requestContext) throws IOException {
		// System.out.format("Base Request: \n%s\n", requestContext.getUri());
		// capture request information in filter and perform signing if no entity is present.
		// If there is an entity defer signing until raw payload is available.
		LocalDateTime requestTime = requestContext.getDate() != null ? requestContext.getDate().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime() : LocalDateTime.now(ZoneOffset.UTC);
		String method = requestContext.getMethod();
		URI requestURI = requestContext.getUri();

		if (requestContext.hasEntity()) {
			requestContext.setProperty(AWS_SIGNER_DEFERRED, new DeferredSigning(method, requestURI, requestTime));
		} else {
			signRequest(requestContext.getHeaders(), method, requestURI, requestTime, "".getBytes("UTF-8"));
		}
		// requestContext.abortWith(Response.ok("{}").build());
	}

	public void aroundWriteTo(WriterInterceptorContext writerContext) throws IOException, WebApplicationException {
		DeferredSigning deferredSigning = (DeferredSigning) writerContext.getProperty(AWS_SIGNER_DEFERRED);
		if (deferredSigning != null) {
			AWSSigningStream stream = new AWSSigningStream(writerContext.getOutputStream());
			writerContext.setOutputStream(stream);
			writerContext.proceed();
			byte[] payload = stream.baos.toByteArray();
			signRequest(writerContext.getHeaders(), deferredSigning.method, deferredSigning.requestURI, deferredSigning.requestTime, payload);
		}
	}

	private static class DeferredSigning {
		String method;
		URI requestURI;
		LocalDateTime requestTime;

		public DeferredSigning(String method, URI requestURI, LocalDateTime requestTime) {
			this.method = method;
			this.requestURI = requestURI;
			this.requestTime = requestTime;
		}

	}

	// This class is used to capture the raw payload after JAX-RS marshalling
	private class AWSSigningStream extends FilterOutputStream {

		private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

		AWSSigningStream(OutputStream out) {
			super(out);
		}

		@Override
		public void write(final int i) throws IOException {
			baos.write(i);
			super.write(i);
		}
	}

	private void signRequest(MultivaluedMap<String, Object> headers, String method, URI requestURI, LocalDateTime requestTime, byte[] payload) throws IOException {
		String formattedTime = requestTime.format(BASIC_TIME_FORMAT);
		String formattedDate = requestTime.format(BASIC_DATE_FORMAT);
		// RuntimeDelegate.getInstance().createHeaderDelegate(type)

		List<String> signedHeaders = prepareSignedHeaders(headers, requestURI.getHost(), formattedTime);
		String canonicalRequest = generateCanonicalRequest(method, requestURI, signedHeaders, headers, payload);
		String stringToSign = createStringToSign(canonicalRequest, formattedDate, formattedTime);
		String signature = calculateSignature(stringToSign.getBytes("UTF-8"), formattedDate);
		addAuthorizationHeader(headers, formattedDate, signedHeaders, signature);

	}

	private List<String> prepareSignedHeaders(MultivaluedMap<String, Object> headers, String host, String formattedTime) {
		List<String> signedHeaders = new LinkedList<>();
		signedHeaders.add("host");
		headers.add("host", host);
		signedHeaders.add("x-amz-date");
		headers.add("x-amz-date", formattedTime);
		if (sessionToken != null) {
			signedHeaders.add("x-amz-security-token");
			headers.add("x-amz-security-token", sessionToken.get());
		}
		return signedHeaders;
	}

	private String generateCanonicalRequest(String methodName, URI requestURI, List<String> signedHeaders, MultivaluedMap<String, Object> headers, byte[] payload) throws IOException {
		URL requestURL = requestURI.toURL();
		String path = requestURL.getPath();
		MultivaluedMap<String, String> params = params(requestURL.getQuery());
		StringBuilder canonicalRequest = new StringBuilder("");

		/* Step 1.1 Start with the HTTP request method (GET, PUT, POST, etc.), followed by a newline character. */
		canonicalRequest.append(methodName).append("\n");

		/* Step 1.2 Add the canonical URI parameter, followed by a newline character. */
		if (path != null && !path.trim().isEmpty()) {
			StringBuilder pathSegments = new StringBuilder();
			for (String segment : path.split("/", -1)) {
				//System.out.format("Path %s Segment %s\n", path, segment);
				// URL encoding creates valid path segments but the segments need to be additionally URLEncoded
				if (pathSegments.length() == 0 || pathSegments.charAt(pathSegments.length() - 1) != '/') {
					pathSegments.append("/");
				}
				pathSegments.append(URLEncoder.encode(segment, "UTF-8"));
			}
			path = pathSegments.toString();
		} else {
			path = "/";
		}
		canonicalRequest.append(path).append("\n");

		/* Step 1.3 Add the canonical query string, followed by a newline character. */
		if (!params.isEmpty()) {
			StringBuilder queryString = new StringBuilder();
			List<String> sortedParamNames = params.keySet().stream().sorted().collect(Collectors.toList());
			for (String paramName : sortedParamNames) {
				List<String> sortedParamValues = params.get(paramName).stream().sorted().collect(Collectors.toList());
				for (String paramValue : sortedParamValues) {
					queryString.append(paramName).append("=").append(paramValue).append("&");
				}
			}
			queryString.deleteCharAt(queryString.length() - 1);
			canonicalRequest.append(queryString).append("\n");
		} else {
			canonicalRequest.append("\n");
		}

		/* Step 1.4 Add the canonical headers, followed by a newline character. */
		StringBuilder signedHeaderLine = new StringBuilder("");
		if (signedHeaders != null && !signedHeaders.isEmpty()) {
			for (String signedHeader : signedHeaders) {
				String name = signedHeader.toLowerCase();
				signedHeaderLine.append(name).append(";");
				for (Object value : headers.get(signedHeader)) {
					canonicalRequest.append(name).append(":").append(((String) value).trim()).append("\n");
				}
			}
			canonicalRequest.append("\n");
			signedHeaderLine.deleteCharAt(signedHeaderLine.length() - 1);
		} else {
			canonicalRequest.append("\n");
		}

		/* Step 1.5 Add the signed headers, followed by a newline character. */
		canonicalRequest.append(signedHeaderLine).append("\n");

		/* Step 1.6 Use a hash (digest) function like SHA256 to create a hashed value from the payload in the body of the HTTP or HTTPS. */
		if (payload != null) {
			canonicalRequest.append(hexHash(payload));
		} else {
			canonicalRequest.append("UNSIGNED-PAYLOAD");
		}

		return canonicalRequest.toString();
	}

	// unfortunately JAX-RS has no way to retrieve the query parameters from the ClientRequestContext parameter and
	// the JDK does not provide any utility methods for parsing query parameters.
	// https://github.com/jax-rs/api/issues/580
	private MultivaluedMap<String, String> params(String queryString) {
		MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
		if (queryString != null) {
			for (String param : queryString.split("&")) {
				String[] nameValue = param.split("=");
				if (nameValue.length > 0) {
					params.add(nameValue[0], nameValue[1]);
				} else {
					params.add(nameValue[0], null);
				}
			}
		}
		return params;
	}

	private String createStringToSign(String canonicalRequest, String formattedDate, String formattedTime) throws UnsupportedEncodingException, IOException {
		//System.out.format("Canonical Request: %s\n", canonicalRequest);
		StringBuilder stringToSign = new StringBuilder();

		/* Step 2.1 Start with the algorithm designation, followed by a newline character. */
		stringToSign.append(ALGORITHM).append("\n");

		/* Step 2.2 Append the request date value, followed by a newline character. */
		stringToSign.append(formattedTime).append("\n");

		/* Step 2.3 Append the credential scope value, followed by a newline character. */
		stringToSign.append(formattedDate).append("/").append(regionName).append("/").append(serviceName).append("/aws4_request\n");

		/* Step 2.4 Append the hash of the canonical request that you created in Task 1: Create a Canonical Request for Signature Version 4. */
		stringToSign.append(hexHash(canonicalRequest.getBytes("UTF-8")));

		//System.out.format("String to sign: %s\n", stringToSign);

		return stringToSign.toString();
	}

	private String calculateSignature(byte[] requestToSign, String date) {
		try {
			/* Step 3.1 Derive your signing key */
			byte[] signatureKey = getSignatureKey(date);

			/* Step 3.2 Calculate the signature. */
			byte[] signature = HmacSHA256(signatureKey, requestToSign);

			/* Step 3.2.1 Encode signature (byte[]) to Hex */
			return DatatypeConverter.printHexBinary(signature).toLowerCase();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	private byte[] getSignatureKey(String date) throws Exception {
		byte[] kSecret = ("AWS4" + secretAccessKey).getBytes("UTF8");
		byte[] kDate = HmacSHA256(kSecret, date.getBytes("UTF-8"));
		byte[] kRegion = HmacSHA256(kDate, regionName.getBytes("UTF8"));
		byte[] kService = HmacSHA256(kRegion, serviceName.getBytes("UTF8"));
		byte[] kSigning = HmacSHA256(kService, "aws4_request".getBytes("UTF8"));
		return kSigning;
	}

	private byte[] HmacSHA256(byte[] key, byte[] data) throws Exception {
		String algorithm = "HmacSHA256";
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(data);
	}

	private String hexHash(byte[] payload) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(payload);
			return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private void addAuthorizationHeader(MultivaluedMap<String, Object> headers, String date, List<String> signedHeaders, String signature) {
		String header = generateAuthorizationHeader(headers, date, signedHeaders, signature);
		// System.out.format("Authorization: %s\n", header);
		headers.add("Authorization", header);
	}

	private String generateAuthorizationHeader(MultivaluedMap<String, Object> headers, String date, List<String> signedHeaders, String signature) {
		String signedHeadersLine = signedHeaders.stream().collect(Collectors.joining(";"));
		return String.format("%s Credential=%s/%s, SignedHeaders=%s, Signature=%s", ALGORITHM, accessKeyID, getCredentialScope(date), signedHeadersLine, signature);
	}

	private String getCredentialScope(String date) {
		return String.format("%s/%s/%s/aws4_request", date, regionName, serviceName);
	}

	public URI presign(String method, URI originalURI, Duration expiration) throws IOException {
		LocalDateTime requestTime = LocalDateTime.now(ZoneOffset.UTC);

		String formattedTime = requestTime.format(BASIC_TIME_FORMAT);
		String formattedDate = requestTime.format(BASIC_DATE_FORMAT);

		UriBuilder builder = UriBuilder.fromUri(originalURI);
		builder.queryParam("X-Amz-Algorithm", ALGORITHM);
		builder.queryParam("X-Amz-Credential", URLEncoder.encode(String.format("%s/%s", accessKeyID, getCredentialScope(formattedDate)), "UTF-8"));
		builder.queryParam("X-Amz-Date", formattedTime);
		builder.queryParam("X-Amz-Expires", expiration.toMillis()/1000);
		builder.queryParam("X-Amz-SignedHeaders", "host");

		URI modifiedURI = builder.build();
		MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
		headers.add("host", originalURI.getHost());
		String canonicalRequest = generateCanonicalRequest(method, modifiedURI, Arrays.asList("host"), headers, null);
		String stringToSign = createStringToSign(canonicalRequest, formattedDate, formattedTime);
		String signature = calculateSignature(stringToSign.getBytes("UTF-8"), formattedDate);

		builder = UriBuilder.fromUri(modifiedURI);
		builder.queryParam("X-Amz-Signature", signature);
		return builder.build();
	}

}
