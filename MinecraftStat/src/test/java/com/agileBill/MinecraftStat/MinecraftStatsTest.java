package com.agileBill.MinecraftStat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.canarymod.api.ConfigurationManager;
import net.canarymod.api.PlayerReference;
import net.canarymod.api.Server;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.chat.MessageReceiver;
import net.canarymod.logger.Logman;
import net.canarymod.user.Group;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class MinecraftStatsTest {
	
	@Mock Logman logger;
	@Mock Server server;
	@Mock ConfigurationManager configMgr;
	@Mock MessageReceiver caller;
	@Mock Player player;
	@Mock PlayerReference pref;


	
	List<String> message = new ArrayList<String>();
	
	JSONObject jsonObject;
	Map<String, Long> jsonMap;

	@Before
	public void setUp() throws IOException, ParseException {
		initMocks(this);
		JsonFileReader jsonFileReader = new JsonFileReader();
		assertNotNull(jsonFileReader);
		jsonObject = jsonFileReader.read("src/test/resources/worlds/stats/00000000-0000-0001-0000-000000000001.json");
		@SuppressWarnings("unchecked")
		Map<String, Long> statMap = jsonObject;
		jsonMap = statMap;
		assertNotNull(statMap);
		assertTrue("Size = " + statMap.size(), statMap.size() > 0);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testStisticStructure() throws IOException, ParseException {
		Map<String, Object> statsMap = new TreeMap<String, Object>();
		JSONObject biomData = (JSONObject) (jsonObject.get("achievement.exploreAllBiomes"));
		List<String> biomes = (JSONArray) (biomData.get("progress"));
		Map<String, Long> biomeMap = new TreeMap<String, Long>();
		for (String biome : biomes) {
			biomeMap.put(biome, 1l);
		}
		statsMap.put("biomesVisited"
		// +" (" + biomData + ")"
				, biomeMap);
		for (Entry<String, Long> jsonEntry : (Set<Entry<String, Long>>) jsonMap.entrySet()) {
			String key = jsonEntry.getKey();
			if (!"achievement.exploreAllBiomes".equals(key)) {
				Long artifactActionValue = (Long) (jsonEntry.getValue());
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.useItem.minecraft.", statsMap, "used");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.craftItem.minecraft.", statsMap, "crafted");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.breakItem.minecraft.", statsMap, "broke");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "achievement.build", statsMap, "built");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.mineBlock.minecraft.", statsMap, "block",
						"gathered");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.entityKilledBy.", statsMap, "killedMe");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.killEntity.", statsMap, "iKilled");
				setValueBasedOnStartsWithKey(key, artifactActionValue, "stat.leaveGame", statsMap, "leftGame");
				setDistanceValue(key, artifactActionValue, "OneCm", statsMap);

			}
		}
		// print(statsMap, " - ");
		Map<String, Object> flattenedMap = new TreeMap<String, Object>();
		MinecraftStats.flatten(statsMap, "", flattenedMap);
		// print((Map<String, ?>)flattenedMap, "");
	}

	@Test
	public void testGetPlayerStats() {
		Map<String, Object> playerProfile = new LinkedHashMap<String, Object>();
		Set<String> statNameSet = new TreeSet<String>();
		MinecraftStats.getPlayerStats("src/test/resources/worlds/stats/00000000-0000-0001-0000-000000000001.json", playerProfile, statNameSet);
		// print(playerProfile, "");
	}

	@Test
	public void testFlattenList() {
		List<String> list = Arrays.asList("asdf", "zxcv", "qwer");
		Map<String, List<String>> map = new HashMap<String, List<String>>();
		map.put("biome", list);
		Map<String, Object> flattenedMap = new LinkedHashMap<String, Object>();
		MinecraftStats.flatten(map, "", flattenedMap);
		Map<String, Long> expectation = new LinkedHashMap<String, Long>();
		expectation.put(".biome.asdf", 1l);
		expectation.put(".biome.qwer", 1l);
		expectation.put(".biome.zxcv", 1l);
		assertEquals(expectation, flattenedMap);
		assertEquals(expectation.toString(), flattenedMap.toString());
		// print(flattenedMap,"");
	}

	@SuppressWarnings({ "unchecked", "unused" })
	private void print(Map<String, ?> flattenedMap, String indent) {
		for (Entry<String, ?> entry : flattenedMap.entrySet()) {
			System.out.print(indent + entry.getKey());
			Object value = entry.getValue();
			if (value instanceof Map) {
				System.out.println();
				print((Map<String, Object>) value, "   " + indent);
			} else {
				System.out.println(": " + value);
			}
		}
	}

	private void setDistanceValue(String key, Long value, String endsWith, Map<String, Object> statsMap) {
		String units = "centimeters";
		Long reducedValue = value;
		// if (reducedValue > 200000) {
		// units = "kilometers";
		// reducedValue /= 100000;
		// } else if (reducedValue > 200) {
		// units = "meters";
		// reducedValue /= 100;
		// }
		if (key.endsWith(endsWith)) {
			String artifact = key.replace("stat.", "").replace(endsWith, "").toLowerCase();
			setValue(reducedValue, statsMap, Arrays.asList("distance", artifact, units
			// + "(" + key + " " + value + ")"
					));
		}
	}

	private void setValueBasedOnStartsWithKey(String key, Long value, String startsWith, Map<String, Object> statsMap,
			String... qualifiers) {
		if (key.startsWith(startsWith)) {
			String artifact = key.replace(startsWith, "").toLowerCase();
			String descriptor = null;
			int underScorePosition = artifact.lastIndexOf('_');
			if (underScorePosition > 0) {
				descriptor = artifact.substring(underScorePosition + 1);
				artifact = artifact.substring(0, underScorePosition);
			}
			List<String> keys = new ArrayList<String>(Arrays.asList(qualifiers));
			int insertionIndex = "block".equals(keys.get(0)) ? 1 : 0;
			keys.addAll(insertionIndex, Arrays.asList(descriptor, artifact));
			removeDuplicatesAndNulls(keys);
			// String lastKey = keys.remove(keys.size() - 1);
			// keys.add(lastKey + "(" + key + ")");
			setValue(value, statsMap, keys);
		}
	}

	@Test
	public void testRemoveDuplicates() {
		List<String> list = new ArrayList<String>(Arrays.asList("asdf", "qwer", "qwer", "asdf", null, "asdf", "qwer"));
		removeDuplicatesAndNulls(list);
		assertEquals(Arrays.asList("asdf", "qwer", "asdf", "qwer"), list);
	}

	void removeDuplicatesAndNulls(List<String> keys) {
		List<Integer> listOfDuplicates = new ArrayList<Integer>();
		String lastKey = "";
		for (int index = keys.size() - 1; index >= 0; index--) {
			String key = keys.get(index);
			if (lastKey.equals(key)) {
				listOfDuplicates.add(index);
			}
			if (key == null) {
				listOfDuplicates.add(index);
			} else {
				lastKey = key;
			}
		}
		for (int index : listOfDuplicates) {
			keys.remove(index);
		}
	}

	@SuppressWarnings("unchecked")
	private void setValue(Long value, Map<String, Object> map, List<String> keys) {
		assert map != null;
		Object child = map;
		String lastKey = keys.get(keys.size() - 1);
		for (String key : keys) {
			if (key != null && key.trim().length() > 0) {
				if (lastKey.equals(key)) {
					((Map<String, Long>) child).put(key, value);
				} else {
					Map<String, Object> parent = (Map<String, Object>) child;
					child = ((Map<String, Long>) child).get(key);
					if (child == null) {
						child = new TreeMap<String, Long>();
						parent.put(key, child);
					}
				}
			}
		}
	}

	@Test
	public void testJsonStructure() throws IOException, ParseException {
		for (Entry<String, Long> jsonEntry : (Set<Entry<String, Long>>) jsonMap.entrySet()) {
			assertEquals("java.lang.String", jsonEntry.getKey().getClass().getName());
			String key = jsonEntry.getKey();
			Object value = jsonEntry.getValue();
			if ("org.json.simple.JSONObject".equals(value.getClass().getName())) {
				assertEquals("Key = " + key, key, "achievement.exploreAllBiomes");
			} else {
				assertEquals("Key = " + key, "java.lang.Long", value.getClass().getName());
				if (!(key.startsWith("stat.useItem.minecraft.") //
						|| key.startsWith("stat.craftItem.minecraft.") //
						|| key.startsWith("stat.breakItem.minecraft.") //
						|| key.startsWith("achievement.build") //
						|| key.startsWith("stat.mineBlock.minecraft.") //
						|| key.startsWith("stat.entityKilledBy.") //
						|| key.startsWith("stat.killEntity.") //
						|| key.endsWith("OneCm") //
				|| "stat.leaveGame".equals(key))) {
					if (key.indexOf('.', 1 + key.indexOf('.')) != -1) {
						assertEquals(key, "");
					}
				}
			}
		}
	}

	@Test
	public void testReadListOfDesiredStats() throws IOException {
		List<String> readListOfDesiredStats = MinecraftStats.readListOfDesiredStats("src/test/resources/statList.txt");
		for (String stat : readListOfDesiredStats) {
			System.out.println(stat);
		}
	}
	
	@Test
	public void testSavePlayerStatsHelp() {
		MinecraftStats minecraftStats = new MinecraftStats(server, logger, configMgr);
		minecraftStats.savePlayerStats(caller, new String[]{"command", "hElP"});
		
		ArgumentCaptor<String> serverCaptor = ArgumentCaptor.forClass(String.class);
		verify(server, times(4)).broadcastMessage(serverCaptor.capture());
		List<String> capturedMessages = serverCaptor.getAllValues();

		int i = 0;
		assertEquals("/savePlayerStats [inputFilePath]", capturedMessages.get(i++));
		assertEquals("inputFilePath: optionally specify an input file path relative to server directory.", capturedMessages.get(i++));
		assertEquals("The input file contains an ordered list of statistics to be collected.", capturedMessages.get(i++));
		assertEquals("The input file contains one statistic name per line.", capturedMessages.get(i++));
	}

	@Test
	public void testSavePlayerStatsTooManyParameters() {
		MinecraftStats minecraftStats = new MinecraftStats(server, logger, configMgr);
		minecraftStats.savePlayerStats(caller, new String[]{"command", "hElP", "asdf"});
		
		ArgumentCaptor<String> serverCaptor = ArgumentCaptor.forClass(String.class);
		verify(server, times(5)).broadcastMessage(serverCaptor.capture());
		List<String> capturedMessages = serverCaptor.getAllValues();

		int i = 0;
		assertEquals("Error: Too many parameters.", capturedMessages.get(i++));
		assertEquals("/savePlayerStats [inputFilePath]", capturedMessages.get(i++));
		assertEquals("inputFilePath: optionally specify an input file path relative to server directory.", capturedMessages.get(i++));
		assertEquals("The input file contains an ordered list of statistics to be collected.", capturedMessages.get(i++));
		assertEquals("The input file contains one statistic name per line.", capturedMessages.get(i++));
	}

	@Test
	public void testSavePlayerStatsMissingInputFile() {
		MinecraftStats minecraftStats = new MinecraftStats(server, logger, configMgr, "missingInputFileResults.csv");
		minecraftStats.savePlayerStats(caller, new String[]{"command", "Missing.Input.FileName"});
		
		ArgumentCaptor<String> serverCaptor = ArgumentCaptor.forClass(String.class);
		verify(server, times(5)).broadcastMessage(serverCaptor.capture());
		List<String> capturedMessages = serverCaptor.getAllValues();

		int i = 0;
		assertEquals("Error: Unable to read input file Missing.Input.FileName.", capturedMessages.get(i++));
		assertEquals("/savePlayerStats [inputFilePath]", capturedMessages.get(i++));
		assertEquals("inputFilePath: optionally specify an input file path relative to server directory.", capturedMessages.get(i++));
		assertEquals("The input file contains an ordered list of statistics to be collected.", capturedMessages.get(i++));
		assertEquals("The input file contains one statistic name per line.", capturedMessages.get(i++));

		ArgumentCaptor<String> loggerCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
		verify(logger, times(1)).error(loggerCaptor.capture(), exceptionCaptor.capture());
		List<String> capturedLogs = serverCaptor.getAllValues();
		List<Exception> capturedExceptions = exceptionCaptor.getAllValues();

		assertEquals("Error: Unable to read input file Missing.Input.FileName.", capturedLogs.get(0));
		assertEquals("FileNotFoundException", capturedExceptions.get(0).getClass().getSimpleName());
		assertEquals("Missing.Input.FileName (The system cannot find the file specified)", capturedExceptions.get(0).getMessage());
	}

	@Test
	public void testGetPlayerIdentity() {
		// Given:
		PlayerReference pref = mock(PlayerReference.class);
		String testName = "testName";
		when(pref.getName()).thenReturn(testName);
		int testLevel = 7;
		when(pref.getLevel()).thenReturn(testLevel);
		int testExperience = 11;
		when(pref.getExperience()).thenReturn(testExperience);
		Group group = new Group();
		String testGroup = "testGroup";
		group.setName(testGroup);
		when(pref.getGroup()).thenReturn(group);
		
		// When:
		Map<String, Object> playerProfile = new HashMap<String, Object>();
		MinecraftStats.getPlayerIdentity(pref, playerProfile);
		
		// Then:
		Map<String, Object> expectation = new HashMap<String, Object>();
		expectation.put(MinecraftStats.PLAYERNAME, testName);
		expectation.put(MinecraftStats.LEVEL, Integer.toString(testLevel));
		expectation.put(MinecraftStats.EXPERIENCE, Integer.toString(testExperience));
		expectation.put(MinecraftStats.GROUP, testGroup);
		assertEquals(expectation, playerProfile);
	}
	
	@Test
	public void testMergeSets () {
		Set<String> mergedSet = new TreeSet<String>();
		mergedSet.addAll(Arrays.asList(new String[]{"c","a","d","e"}));
		mergedSet.addAll(Arrays.asList(new String[]{"a","b","d","f"}));
		assertEquals("[a, b, c, d, e, f]", mergedSet.toString());
	}
}