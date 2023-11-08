package com.example.toolbox;

import com.example.toolbox.task.GlobalThreadPool;
import com.example.toolbox.utile.BaiduTranslationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootTest
class ToolboxApplicationTests {
	static GlobalThreadPool taskManager = GlobalThreadPool.getInstance();


	@Test
	public void TranslateLanguageFiles(){
		GlobalThreadPool taskManager = GlobalThreadPool.getInstance();
		// 读取文件中的JSON数据
		File file = new File("F:\\k2024\\toolbox\\src\\main\\java\\com\\example\\toolbox\\file\\zh.json"); // 替换为实际文件路径
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> langMap = null;
		try {
			langMap = objectMapper.readValue(file, Map.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		ArrayList<String> langList = new ArrayList<>();
		/*语言添加*/
//		langList.add("ara");
//		langList.add("spa");
//		langList.add("de");
//		langList.add("th");
//		langList.add("vie");
//		langList.add("pt");
//		langList.add("it");
//		langList.add("hi");
//		langList.add("id");
//		langList.add("ru");
//		langList.add("pl");
//		langList.add("nl");
		langList.add("tr");
		Map<String, Object> finalLangMap = langMap;
		langList.forEach(s -> {
			String lang = s;
			String filePath = "F:\\k2024\\toolbox\\src\\main\\java\\com\\example\\toolbox\\file\\"+lang+".json";
			// 创建新的Map以存储翻译后的值
			Map<String, Object> translatedMap = ergodicMap(finalLangMap,s);
			// 创建新的 ObjectMapper 以美化 JSON 输出
			ObjectMapper prettyObjectMapper = new ObjectMapper();
			prettyObjectMapper.enable(SerializationFeature.INDENT_OUTPUT);
			// 创建名为 "en.json" 的输出文件
			File outputFile = new File(filePath);
			// 将翻译后的Map转换为 JSON 并写入输出文件
			try {
				prettyObjectMapper.writeValue(outputFile, translatedMap);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}


	private Map<String, Object> ergodicMap(Map<String, Object> data,String lang) {
		//获取key为中文的原始map
		Map<String, String> sourceMap = getSourceMap(data, lang);
		CountDownLatch latch = new CountDownLatch(sourceMap.entrySet().size()); // 初始值是线程数量减一
		//翻译原始map的key做为值
		for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			taskManager.addTask(()->{
				String translatedItem = findHitKeyWords(key,lang);
				sourceMap.put(key,translatedItem);
				latch.countDown(); // 确保无论如何都减少计数
			});
		}
		try {
			latch.await(); // 等待所有任务完成
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		//保险起见再次判断
		if (latch.getCount() == 0) {
			System.out.println("所有的翻译任务执行完毕");
		} else {
			System.out.println("部分翻译任务可能未完成");
		}
		System.out.println("资源map大小是:"+sourceMap.entrySet().size());
		for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			System.out.println("key: "+key+" "+"value: "+value);
		}
		Map<String, Object> translateMap = getStructuredMap(data,sourceMap);
		return translateMap;
	}


	public  static  Map<String, Object>  getStructuredMap (Map<String, Object> data,Map<String, String> sourceMap){
		Map<String, Object> translateMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				Map<String, Object> subMap = getStructuredMap((Map<String, Object>) value,sourceMap);
				translateMap.put(key,subMap);
			}else if (value instanceof List) {
				List<?> listValue = (List<?>) value;
				List<Object> translatedList = iterationStructureList(listValue,translateMap,sourceMap);
				translateMap.put(key,translatedList);
			}else if (value instanceof String) {
				// 如果值是String，将其添加到stringSourceMap并设置为空字符串
				String strValue = (String) value;
				String s = sourceMap.get(strValue);
				translateMap.put(key,s);
			}else {
				translateMap.put(key, value);
			}
		}
		return translateMap;
	}

	private static List<Object> iterationStructureList(List<?> list ,Map<String, Object> translateMap, Map<String, String> sourceMap){
		List<Object> translatedList = new ArrayList<>();
		for (Object value : list) {
			if (value instanceof Map) {
				Map<String, Object> subMap = getStructuredMap((Map<String, Object>) value,sourceMap);
				translatedList.add(subMap);
			}else if (value instanceof List) {
				List<?> listValue = (List<?>) value;
				List<Object> subList = iterationStructureList(listValue,translateMap,sourceMap);
				translatedList.add(subList);
			}else if (value instanceof String) {
				String strValue = (String) value;
				String translatedItem = sourceMap.get(strValue);
				translatedList.add(translatedItem);
			}else {
				translatedList.add(value);
			}
		}
		return translatedList;

	}


	private Map<String, String> getSourceMap(Map<String, Object> data,String lang) {
		Map<String, String> stringSourceMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				// 如果值是Map，递归调用iterationMap
				Map<String, Object> subMap = iterationMap((Map<String, Object>) value,stringSourceMap);
			} else if (value instanceof List) {
				// 如果值是List，调用iterationList
				List<?> listValue = (List<?>) value;
				List<Object> translatedList = iterationList(listValue,stringSourceMap);
			} else if (value instanceof String) {
				// 如果值是String，将其添加到stringSourceMap并设置为空字符串
				String strValue = (String) value;
				stringSourceMap.put(strValue, "");
			} else {
				// 其他类型的值不做处理
				String strValue = (String) value;
				stringSourceMap.put(key, strValue);
			}
		}
		return stringSourceMap;
	}


	private static Map<String, Object> iterationMap(Map<String, Object> data,Map<String, String> stringSourceMap) {
		Map<String, Object> translationMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				// 如果值是Map，递归调用iterationMap
				Map<String, Object> subMap = iterationMap((Map<String, Object>) value,stringSourceMap);
				translationMap.put(key, subMap);
			} else if (value instanceof List) {
				// 如果值是List，调用iterationList
				List<?> listValue = (List<?>) value;
				List<Object> translatedList = iterationList(listValue,stringSourceMap);
				translationMap.put(key, translatedList);
			} else if (value instanceof String) {
				// 如果值是String，将其添加到stringSourceMap并设置为空字符串
				String strValue = (String) value;
				stringSourceMap.put(strValue, "");
			} else {
				// 其他类型的值不做处理
				translationMap.put(key, value);
			}
		}
		return translationMap;
	}


	private static List<Object> iterationList(List<?> value,Map<String, String> stringSourceMap) {
		List<Object> translatedList = new ArrayList<>();
		for (Object listItem : value) {
			if (listItem instanceof Map) {
				// 如果List的元素是Map，递归处理
				Map<String, Object> subMap = iterationMap((Map<String, Object>) listItem,stringSourceMap);
				translatedList.add(subMap);
			} else if (listItem instanceof List) {
				// 如果List的元素是List，调用iterationList
				List<?> subListValue = (List<?>) listItem;
				List<Object> subList = iterationList(subListValue,stringSourceMap);
				translatedList.add(subList);
			} else if (listItem instanceof String) {
				// 如果值是String，将其添加到stringSourceMap并设置为空字符串
				String strValue = (String) listItem;
				stringSourceMap.put(strValue, "");
				translatedList.add("");
			} else {
				translatedList.add(listItem);
			}
		}
		return translatedList;
	}


	private static String findHitKeyWords(String input ,String lang) {
		if (!input.startsWith("http") && !input.startsWith("/")){
			if (input==null){
				return input;
			}else {
				if (isMathExpression(input)){
					return input;
				}else {
					Map<String, String> characterMap1 = getCharacterMap();
					Map<String, String> stringStringMap = backReplace();
					List<String> foundCharacters = charactersToCheck.stream()
							.filter(input::contains)
							.sorted((s1, s2) -> s2.length() - s1.length()) // 按长度降序排序
							.distinct()
							.collect(Collectors.toList());
					for (String character : foundCharacters) {
						if (getCharacterMap().get(character)!=null){
							input = input.replace(character, getCharacterMap().get(character));
						}
					}
					input = BaiduTranslationUtils.getTranslateResult(input, "zh", lang);
					input += "";//
					if (!input.equals("") && input.length() > 0) {
						input = input.toLowerCase();
					}
					for (String character : foundCharacters) {
						String targetString = characterMap.get(character);
						if (backReplace().get(targetString)!=null && !backReplace().get(targetString).equals("")
								&& backReplace().get(targetString).length()>0){
							String backString  = stringStringMap.get(targetString);
							input = input.replace(targetString, backString);
						}
					}
					if (input!=null && !input.equals("")){
						input = input.replace("null", "");
					}
					return input;
				}
			}
		}else {
			return input;
		}
	}

	public static boolean isMathExpression(String input) {
		String regex = "^[≥≤≠><=]?[0-9]+(\\.[0-9]+)?(%|mm|kg|Ah|°)?[^\\u4e00-\\u9fff]*$";

		// 使用正则表达式模式编译正则表达式
		Pattern pattern1 = Pattern.compile(regex);
		// 使用模式匹配输入字符串
		Matcher matcher1 = pattern1.matcher(input);
		System.out.println("的长度"+input.length());
		// 检查是否至少有一个模式匹配成功
		return matcher1.find() ;
	}


	public static final List<String> charactersToCheck = Arrays.asList(
			"%",
			"堒",
			"堒洁",
			"Kwunphi",
			"怪虫机器人",
			"堒® B37H",
			"堒® B37L",
			"堒® B63G",
			"堒® B62G",
			"堒® B33F",
			"堒® B33H",
			"堒® B32H",
			"堒® B32L",
			"堒® B22H",
			"堒® B22L",
			"堒® B11L",
			"堒® B11T",
			"堒® B12T",
			"堒® B11T",
			"堒® π1",
			"Kwun-B37H",
			"Kwun-B37L",
			"Kwun-B63G",
			"Kwun-B62G",
			"Kwun-B33F",
			"Kwun-B33H",
			"Kwun-B32H",
			"Kwun-B32L",
			"Kwun-B22H",
			"Kwun-B22L",
			"Kwun-B11L",
			"Kwun-B11T",
			"Kwun-B12T",
			"Kwun π1",
			"KwunPhi-APP",
			"粤ICP备2021034388号",
			"Kwun Care"
	);

	private static volatile Map<String, String> characterMap;
	private static volatile Map<String, String> backReplaceMap;

	public static Map<String, String> getCharacterMap() {
		if (characterMap == null) {
			synchronized (ToolboxApplicationTests.class) {
				if (characterMap == null) {
					Map<String, String> map = new HashMap<>();
					/*回替换专有名词*/
					map.put("%", "percent");
					map.put("Kwunphi", "kph");
					map.put("堒", "kwun");
					map.put("堒洁", "kwunj");
					map.put("怪虫机器人", "kph");
					map.put("堒® B37H", "kwb37h");
					map.put("堒® B37L", "kwb37l");
					map.put("堒® B63G", "kwb63g");
					map.put("堒® B62G", "kwb62g");
					map.put("堒® B33F", "kwb33f");
					map.put("堒® B33H", "kwb33h");
					map.put("堒® B32H", "kwb32h");
					map.put("堒® B32L", "kwb32l");
					map.put("堒® B22H", "kwb22h");
					map.put("堒® B22L", "kwb22l");
					map.put("堒® B11L", "kwb11l");
					map.put("堒® B11T", "kwb11t");
					map.put("堒® B12T", "kwb12t");
					map.put("堒® π1", "kwbπ1");
					map.put("Kwun-B37H", "kwb37h");
					map.put("Kwun-B37L", "kwb37l");
					map.put("Kwun-B63G", "kwb63g");
					map.put("Kwun-B62G", "kwb62g");
					map.put("Kwun-B33F", "kwb33f");
					map.put("Kwun-B33H", "kwb33h");
					map.put("Kwun-B32H", "kwb32h");
					map.put("Kwun-B32L", "kwb32l");
					map.put("Kwun-B22H", "kwb22h");
					map.put("Kwun-B22L", "kwb22l");
					map.put("Kwun-B11L", "kwb11l");
					map.put("Kwun-B11T", "kwb11t");
					map.put("Kwun-B12T", "kwb12t");
					map.put("Kwun π1", "kwbπ1");
					map.put("KwunPhi-APP", "kpapp");
					map.put("粤ICP备2021034388号", "icp2021");
					map.put("Kwun Care", "kce");
					characterMap = Collections.unmodifiableMap(map);
				}
			}
		}
		return characterMap;
	}


	public static Map<String, String> backReplace() {
		if (backReplaceMap == null) {
			synchronized (ToolboxApplicationTests.class) {
				if (backReplaceMap == null) {
					Map<String, String> map = new HashMap<>();
					/*回替换专有名词*/
					map.put("percent", "%");
					map.put("kph", "Kwunphi");
					map.put("kwun", "Kwun");
					map.put("kwunj", "KwunJ");
					map.put("kwb37h", "PhiCleaner™ E37H");
					map.put("kwb37l", "PhiCleaner™ E37L");
					map.put("kwb63g", "PhiCleaner™ E63G");
					map.put("kwb62g", "PhiCleaner™ E62G");
					map.put("kwb33f", "PhiCleaner™ E33F");
					map.put("kwb33h", "PhiCleaner™ E33H");
					map.put("kwb32h", "PhiCleaner™ E32H");
					map.put("kwb32l", "PhiCleaner™ E32L");
					map.put("kwb22h", "PhiCleaner™ E22H");
					map.put("kwb22l", "PhiCleaner™ E22L");
					map.put("kwb11l", "PhiCleaner™ E11L");
					map.put("kwb11t", "PhiCleaner™ E11T");
					map.put("kwb12t", "PhiCleaner™ E12T");
					map.put("kwbπ1", "PhiCleaner™ π1");
					map.put("kpapp", "KwunPhi-APP");
					map.put("icp2021", "粤ICP备2021034388号");
					map.put("kce", "Kwun Care");
					backReplaceMap = Collections.unmodifiableMap(map);
				}
			}
		}
		return backReplaceMap;
	}







	public static String findMostSimilarCharacter(String input, List<String> charactersToCheck) {
		String mostSimilarCharacter = null;
		double maxSimilarity = Double.MIN_VALUE;
		for (String character : charactersToCheck) {
			int distance = calculateLevenshteinDistance(input, character);
			double similarity = 1.0 - (double) distance / Math.max(input.length(), character.length());
			if (similarity > maxSimilarity) {
				maxSimilarity = similarity;
				mostSimilarCharacter = character;
			}
		}
		return mostSimilarCharacter;
	}

	public static int calculateLevenshteinDistance(String s1, String s2) {
		int m = s1.length();
		int n = s2.length();
		int[][] dp = new int[m + 1][n + 1];
		for (int i = 0; i <= m; i++) {
			for (int j = 0; j <= n; j++) {
				if (i == 0) {
					dp[i][j] = j;
				} else if (j == 0) {
					dp[i][j] = i;
				} else if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
					dp[i][j] = dp[i - 1][j - 1];
				} else {
					dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
				}
			}
		}
		return dp[m][n];
	}


}
