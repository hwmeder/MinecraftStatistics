package com.agileBill.MinecraftStat;

import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JsonFileReader {
	private JSONParser parser = new JSONParser();
	
	public JSONObject read(String filePath) throws IOException, ParseException {
		FileReader fileReader = new FileReader(filePath);
		return (JSONObject) parser.parse(fileReader);
	}

}
