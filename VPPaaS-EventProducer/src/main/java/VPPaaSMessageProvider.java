import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.json.JSONArray;
import org.json.JSONObject;


public class VPPaaSMessageProvider {

	static String brokerList = "localhost:9092";
	static int throughput = 10;
	static String filterprefix = "";
	static String kongUrl = "http://localhost:8000";

	static Map<String, List<PartitionInfo>> topics;
	static Map<String, List<String[]>> topicAssetCache = new HashMap<>();
	static HttpClient httpClient = HttpClient.newHttpClient();

	private static String RandomTopic()
	{
		String Topic = new String("");
		int index = (new Random()).nextInt(topics.size());
		Set<String> keys = topics.keySet();
		Iterator<String> it = keys.iterator();
		for (int idx = 0; idx < index; idx++) it.next();
		Topic = (String) it.next();
		System.out.println("Topic randomized: " + Topic);
		return Topic;
	}

	private static Long fetchProsumerId(String assetLinkId)
	{
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(kongUrl + "/AssetLink/" + assetLinkId))
				.GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				JSONObject json = new JSONObject(response.body());
				return json.getLong("prosumerId");
			}
			System.out.println("AssetLink lookup returned status " + response.statusCode() + " for id=" + assetLinkId);
		} catch (Exception e) {
			System.out.println("Failed to fetch AssetLink " + assetLinkId + ": " + e.getMessage());
		}
		return null;
	}

	private static List<String[]> fetchAssetsForProsumer(Long prosumerId)
	{
		List<String[]> assets = new ArrayList<>();
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(kongUrl + "/Prosumer/" + prosumerId + "/assets"))
				.GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				JSONArray arr = new JSONArray(response.body());
				for (int i = 0; i < arr.length(); i++) {
					JSONObject obj = arr.getJSONObject(i);
					assets.add(new String[]{obj.getString("id"), obj.getString("assetType")});
				}
			} else {
				System.out.println("Prosumer assets lookup returned status " + response.statusCode() + " for prosumerId=" + prosumerId);
			}
		} catch (Exception e) {
			System.out.println("Failed to fetch assets for prosumer " + prosumerId + ": " + e.getMessage());
		}
		return assets;
	}

	private static List<String[]> getAssetsForTopic(String topic)
	{
		if (topicAssetCache.containsKey(topic)) return topicAssetCache.get(topic);

		String assetLinkId = topic.substring(0, topic.indexOf('-'));
		System.out.println("Resolving assets for topic=" + topic + " (assetLinkId=" + assetLinkId + ")");

		Long prosumerId = fetchProsumerId(assetLinkId);
		if (prosumerId == null) {
			System.out.println("Could not resolve prosumerId for assetLinkId=" + assetLinkId);
			return Collections.emptyList();
		}
		System.out.println("Resolved prosumerId=" + prosumerId + " for assetLinkId=" + assetLinkId);

		List<String[]> assets = fetchAssetsForProsumer(prosumerId);
		if (assets.isEmpty()) {
			System.out.println("No assets found for prosumerId=" + prosumerId);
			return Collections.emptyList();
		}

		System.out.print("Assets cached for topic=" + topic + ": { ");
		for (String[] a : assets) System.out.print(a[0] + "(" + a[1] + ") ; ");
		System.out.println("}");

		topicAssetCache.put(topic, assets);
		return assets;
	}

	private static boolean isAssetLinkTopic(String topic) {
		if (!topic.contains("-")) return false;
		try { Long.parseLong(topic.substring(0, topic.indexOf('-'))); return true; }
		catch (NumberFormatException e) { return false; }
	}

	private static Message CreateMessage(String topicToSend, Timestamp ts)
	{
		if (!isAssetLinkTopic(topicToSend)) {
			System.out.println("Skipping non-AssetLink topic: " + topicToSend);
			return null;
		}

		List<String[]> assets = getAssetsForTopic(topicToSend);
		if (assets.isEmpty()) {
			System.out.println("Skipping topic=" + topicToSend + ": no assets resolved.");
			return null;
		}

		String[] asset = assets.get(new Random().nextInt(assets.size()));
		String assetId = asset[0];
		String assetType = asset[1];

		String[] GridCells = {
			"TOKYO-NW", "BERLIN-CE", "AUSTIN-DT", "LONDON-SE", "MUMBAI-WP",
			"SYDNEY-HB", "MADRID-NE", "DENVER-MT", "SEOUL-IT", "LAGOS-VI",
			"PARIS-RG", "MEXICO-SZ", "DUBAI-MR", "CAIRO-ND", "LISBON-EX",
			"TORONTO-QU", "VIENNA-BZ", "SANTOS-IN", "OSLO-FJ", "SINGA-GP"};
		String gridCell = GridCells[new Random().nextInt(GridCells.length)];

		switch (assetType) {
			case "BATTERY":
				return new BatteryEnergyStorage(
					ts.toLocalDateTime(),
					assetId,
					gridCell,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 100.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 20.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(-10.0, 10.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 5.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 100.0)),
					new BatteryEnergyStorage().randomLevel());
			case "SOLAR":
				return new SolarInverter(
					ts.toLocalDateTime(),
					assetId,
					gridCell,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 7.5)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 150)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(245, 255)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(49.5, 50.5)));
			case "EV_CHARGER":
				return new EVCharger(
					ts.toLocalDateTime(),
					assetId,
					gridCell,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 20.5)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 17.8)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0, 100)),
					new EVCharger().randomLevel());
			default:
				System.out.println("Unknown asset type: " + assetType + ", skipping.");
				return null;
		}
	}

	private static void CheckArguments()
	{
		System.out.println(
			"--broker-list=" + brokerList + "\n" +
			"--throughput=" + throughput + "\n" +
			"--filterprefix=" + filterprefix + "\n" +
			"--kong-url=" + kongUrl);
	}

	private static boolean VerifyArgs(String[] cabecalho)
	{
		for (int i = 0; i < cabecalho.length; i = i + 2)
		{
			if (cabecalho[i].compareTo("--broker-list") == 0) brokerList = cabecalho[i + 1];
			else if (cabecalho[i].compareTo("--throughput") == 0) throughput = Integer.valueOf(cabecalho[i + 1]).intValue();
			else if (cabecalho[i].compareTo("--filterprefix") == 0) filterprefix = cabecalho[i + 1];
			else if (cabecalho[i].compareTo("--kong-url") == 0) kongUrl = cabecalho[i + 1];
			else
			{
				System.out.println("Bad argument name: " + cabecalho[i]);
				return (false);
			}
		}

		if (brokerList.length() == 0) System.out.println("Broker-list argument is mandatory!");
		else return (true);

		return (false);
	}

	private static void SendMessage(Message msg, KafkaProducer<String, String> prd, String topicTarget)
	{
		System.out.println("This is the message to send = " + msg.toStringAsJSON());
		String seqkey = msg.getSeqkey().toString();
		System.out.println("Sending new message to Kafka, to the topic = " + topicTarget + ", with key=" + seqkey);
		ProducerRecord<String, String> record = new ProducerRecord<>(topicTarget, seqkey, msg.toStringAsJSON());
		prd.send(record);
		System.out.print("Sent...Fire-and-forget stopped...");
	}

	private static void CheckTopicsAvailable()
	{
		Properties props = new Properties();
		props.put("bootstrap.servers", brokerList);
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		topics = consumer.listTopics();
		consumer.close();

		topics.remove("__consumer_offsets");
		System.out.print("Topics discovered = { ");
		for (String topicName : topics.keySet()) System.out.print(topicName + " ; ");
		System.out.println(" } ");
	}

	public static void main(String[] args) {

		String usage = "The usage of the Message Producer for VPPaaS 2026, for Enterprise Integration 2026 course, is the following.\n" +
			"\n" +
			"VPPaaSSimulator --broker-list <brokers> --throughput <value> --filterprefix <value> --kong-url <url>\n" +
			"where, \n" +
			"--broker-list: is a broker list with ports (e.g.: kafka02.example.com:9092,kafka03.example.com:9092), default value is localhost:9092\n" +
			"--throughput: is the approximate maximum messages to be produced by minute, default value is 10\n" +
			"--filterprefix: is the prefix to be filtered. Only the topics starting with this prefix will be considered to sending messages.\n" +
			"--kong-url: base URL of the Kong API Gateway (e.g.: http://ec2-xx-xx-xx-xx.compute.amazonaws.com:8000), default value is http://localhost:8000\n";

		Properties kafkaProps = new Properties();
		if (args.length == 0) System.out.println(usage);
		else
		{
			if (VerifyArgs(args))
			{
				System.out.println("The following arguments are accepted:");
				CheckArguments();
				System.out.println("------- Processing starting -------");

				kafkaProps.put("bootstrap.servers", brokerList);
				kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
				kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
				KafkaProducer<String, String> producer = new KafkaProducer<String, String>(kafkaProps);

				CheckTopicsAvailable();

				Timestamp mili;

				while (true)
				{
					try {
						mili = new Timestamp(System.currentTimeMillis());

						if (!topics.isEmpty())
						{
							String topic_to_send = RandomTopic();

							if (topic_to_send.startsWith(filterprefix))
							{
								Message messageToSend = CreateMessage(topic_to_send, mili);
								if (messageToSend != null) SendMessage(messageToSend, producer, topic_to_send);
							}
							else System.out.println("Topic = " + topic_to_send + " has been filtered. Therefore, not sending message.");
						}
						else System.out.println("Empty list of Topics. Therefore, no message to send.");

						Timestamp timestamp = new Timestamp(System.currentTimeMillis());
						System.out.println("...Time spent for sending: " + (timestamp.getTime() - mili.getTime()));
						Thread.sleep(60000 / throughput);
						CheckTopicsAvailable();
					}
					catch (Exception e) { e.printStackTrace(); }
				}
			}
			else System.out.println("Application Arguments bad usage.\n\nPlease check syntax.\n\n" + usage);
		}
	}
}
