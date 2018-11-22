import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.Test;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DynamoDBTest {

	@Test
	public void attributeValueToJSON() throws Exception {
		Map<String, AttributeValue> map = new HashMap<>();
		map.put("string", AttributeValue.builder().s("test").build());
		map.put("boolean", AttributeValue.builder().bool(true).build());
		map.put("number", AttributeValue.builder().n("123").build());
		Map<String, AttributeValue> subMap = new HashMap<>();
		subMap.put("key", AttributeValue.builder().s("value").build());
		map.put("map", AttributeValue.builder().m(subMap).build());
		List<AttributeValue> list = new LinkedList<>();
		list.add(AttributeValue.builder().s("entryOne").build());
		list.add(AttributeValue.builder().s("entryTwo").build());
		map.put("list", AttributeValue.builder().l(list).build());

		AttributeValue test = AttributeValue.builder().m(map).build();
		JsonObject json = (JsonObject) DynamoDBUtil.toJson(test);

		System.out.format("toJson: %s\n", json);
		assertEquals("test", json.getString("string"));
		assertEquals(true, json.getBoolean("boolean"));
		assertEquals(new BigInteger("123"), json.getJsonNumber("number").bigIntegerValue());
		JsonObject mapJson = json.getJsonObject("map");
		assertNotNull(mapJson);
		assertEquals("value", mapJson.getString("key"));
		JsonArray arrJson = json.getJsonArray("list");
		assertNotNull(arrJson);
		assertEquals("entryOne", arrJson.getString(0));
		assertEquals("entryTwo", arrJson.getString(1));
	}

	@Test
	public void jsonToAttributeValue() throws Exception {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("string", "test");
		builder.add("boolean", true);
		builder.add("number", 123);
		JsonObjectBuilder subBuilder = Json.createObjectBuilder();
		subBuilder.add("key", "value");
		builder.add("map", subBuilder);
		JsonArrayBuilder list = Json.createArrayBuilder();
		list.add("entryOne");
		list.add("entryTwo");
		builder.add("list", list);
		Map<String, AttributeValue> attrValue = DynamoDBUtil.toAttribute(builder.build());

		System.out.format("toAttributeValue: %s\n", attrValue);
		assertEquals("test", attrValue.get("string").s());
		assertEquals(true, attrValue.get("boolean").bool());
		assertEquals("123", attrValue.get("number").n());
		Map<String, AttributeValue> subAttrValue = attrValue.get("map").m();
		assertNotNull(subAttrValue);
		assertEquals("value", subAttrValue.get("key").s());
		List<AttributeValue> listAttrValue = attrValue.get("list").l();
		assertNotNull(listAttrValue);
		assertEquals("entryOne", listAttrValue.get(0).s());
		assertEquals("entryTwo", listAttrValue.get(1).s());
	}

	/**
	 * At times it may be necessary to store part of the data in a compressed format to avoid exceeding the 400k entry limit.
	 */
	@Test
	public void compression() throws Exception {
		JsonObject entry = Json.createObjectBuilder().add("test", "value").build();
		AttributeValue compressed = DynamoDBUtil.compress(entry);
		entry = (JsonObject) DynamoDBUtil.decompress(compressed);
		assertEquals("value", entry.getString("test"));
	}

}
