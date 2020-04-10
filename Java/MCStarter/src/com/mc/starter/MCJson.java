package com.mc.starter;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mc.starter.Util.Jar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MCJson {
    private static Gson gson = new Gson();

    private JsonObject mMCJson = null;
    private static final char mDelimiter = File.separatorChar;

    public enum SystemType {
        Windows, MacOS, Linux
    }

    public enum SystemBit {
        B32, B64
    }

    private SystemType mSystemType;
    private SystemBit mSystemBit;

    private String mGameDir;
    private String mMcRoot;
    private String mMcVersion;

    private String mNativesDir;

    /**
     * 隐藏的构造函数
     */
    private MCJson(String version, String gameDir, JsonObject MCJsonObject, SystemType systemType, SystemBit systemBit) {
        mMCJson = MCJsonObject;
        mSystemType = systemType;
        mSystemBit = systemBit;
        mGameDir = gameDir;

        mMcRoot = gameDir + mDelimiter + ".minecraft";
        mNativesDir = ".minecraft" + mDelimiter + "versions" + mDelimiter + version + mDelimiter + "Natives";

        File natives = new File(mGameDir + mDelimiter + mNativesDir);
        natives.mkdir();
        System.out.println(mGameDir + mDelimiter + mNativesDir);

        mMcVersion = version;

    }

    /**
     * 加载MC的Json数据
     *
     * @return 成功返回MCJson类，失败返回null
     */
    public static MCJson LoadJson(String version, String gameDir, SystemType systemType, SystemBit systemBit) {
        File json_file = new File(gameDir + mDelimiter + ".minecraft" + mDelimiter + "versions" + mDelimiter + version + mDelimiter + version + ".json");
        if (!json_file.exists()) {
            System.out.println("json文件不存在：" + json_file.getPath());
            return null;
        }

        String json_data = LoadJsonData(json_file.getPath());

        if (json_data != null) {
            return new MCJson(version, gameDir, gson.fromJson(json_data, JsonObject.class), systemType, systemBit);
        }

        return null;
    }

    /**
     * 加载MC的JSON数据文本
     */
    private static String LoadJsonData(String jsonFilePath) {
        String result = null;

        if (!new File(jsonFilePath).exists()) return null;

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(jsonFilePath);
            byte[] json_data = fileInputStream.readAllBytes();
            result = new String(json_data, 0, json_data.length, StandardCharsets.UTF_8);
        } catch (IOException e) {
            result = null;
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    /**
     * 制取MC libraries数据
     *
     * @return 返回一个HashMap
     * key：launchParameters
     * 所有启动jar文件路径
     * key：decompressionNatives
     * Natives数据，启动前需要解压到natives目录
     */
    public Map<String, List<String>> getLibraries() {
        String libraries_path = mMcRoot + mDelimiter + "libraries";
        Map<String, List<String>> result = new HashMap<>();

        List<String> launchParameters = new ArrayList<>();
        List<String> decompressionNatives = new ArrayList<>();

        String localOsName = getLocalOsName();

        JsonArray libraries = mMCJson.getAsJsonArray("libraries");
        int lib_count = libraries.size();
        for (int i = 0; i < lib_count; ++i) {
            JsonObject lib_obj = libraries.get(i).getAsJsonObject();

            boolean lose_flag = false;

            //判断是否存在规则配置，如果存在，则判断规则
            if (lib_obj.get("rules") != null) {
                JsonArray rules = lib_obj.get("rules").getAsJsonArray();
                for (int j = 0; j < rules.size(); ++j) {
                    JsonObject rules_item = rules.get(j).getAsJsonObject();

                    //判断本机系统名称是否存在规则内
                    boolean os_flag = false;
                    if (rules_item.get("os") != null) {
                        if (rules_item.get("os").getAsJsonObject().get("name").getAsString().equals(localOsName))
                            os_flag = true;
                    }

                    String action = (rules_item.get("action") == null) ? "allow" : rules_item.get("action").getAsString();
                    lose_flag = ((action.equals("disallow") && os_flag) || (action.equals("allow") && !os_flag));
                }
            }
            if (lose_flag) continue;

            String jar_path = NameToDirPath(libraries_path, lib_obj.get("name").getAsString());

            if (lib_obj.get("natives") != null) {
                JsonObject natives = lib_obj.get("natives").getAsJsonObject();
                jar_path += "-" + natives.get(localOsName).getAsString();

                switch (mSystemBit) {
                    case B32:
                        jar_path = jar_path.replace("${arch}", "32");
                        break;
                    case B64:
                        jar_path = jar_path.replace("${arch}", "64");
                        break;
                }

                decompressionNatives.add(jar_path + ".jar");

            } else {
                launchParameters.add(jar_path + ".jar");
            }

        }

        launchParameters.add(mMcRoot + mDelimiter + "versions" + mDelimiter + mMcVersion + mDelimiter + mMcVersion + ".jar");

        result.put("launchParameters", launchParameters);
        result.put("decompressionNatives", decompressionNatives);

        return result;
    }

    private String getLocalOsName() {
        switch (mSystemType) {
            case MacOS:
                return "osx";
            case Linux:
                return "linux";
            default:
                return "windows";
        }
    }

    /**
     * 解析包
     */
    private String NameToDirPath(String libraries_path, String libraries_name) {
        StringBuilder lib_path = new StringBuilder(libraries_path);

        String last_1 = null, last_2 = null;

        String[] name_partition = libraries_name.split("\\.");

        int last_index = 0;
        for (String name : name_partition) {
            if (name.indexOf(':') != -1) break;//遇到带:的就退出
            lib_path.append(mDelimiter).append(name);
            ++last_index;

            if (last_1 != null) last_2 = last_1;
            last_1 = name;
        }

        //归并剩余数据
        StringBuilder merger_data = null;
        for (int j = last_index; j < name_partition.length; ++j) {
            if ((merger_data == null)) merger_data = new StringBuilder(name_partition[j]);
            else merger_data.append(".").append(name_partition[j]);
        }

        //处理剩余归并数据
        if (merger_data != null) {
            name_partition = merger_data.toString().split(":");
            for (String name : name_partition) {
                lib_path.append(mDelimiter).append(name);
                if (last_1 != null) last_2 = last_1;
                last_1 = name;
            }
        }

        return lib_path.toString() + mDelimiter + last_2 + "-" + last_1;
    }

    /**
     * 制取启动参数
     *
     * @param maxMemory  最大内存 单位M
     * @param additional 附加数据
     * @param userName   玩家名称
     * @param uuid       玩家uuid
     * @param token      玩家Token
     * @param twitch     twitch
     */
    public String makeStartArg(String javaw, int maxMemory, String additional, String userName, String uuid, String token, String twitch) {

        if (uuid.equals("")) uuid = "{}";
        if (token.equals("")) token = "{}";
        if (twitch.equals("")) twitch = "{}";

        Map<String, List<String>> libraries = getLibraries();
        List<String> launchParameters = libraries.get("launchParameters");
        List<String> decompressionNatives = libraries.get("decompressionNatives");

        for (String jar_file : decompressionNatives) {
            try {
                Jar.decompress(jar_file, mGameDir + mDelimiter + mNativesDir);
            } catch (IOException e) {

            }

        }

        StringBuffer arg_libraries = null;
        for (String path : launchParameters) {
            if (arg_libraries == null) {
                arg_libraries = new StringBuffer("\"" + path + "\"");
            } else {
                arg_libraries.append(";").append("\"").append(path).append("\"");
            }
        }

        String minecraftArguments = mMCJson.get("minecraftArguments").getAsString();

        minecraftArguments = minecraftArguments.replace("${auth_player_name}", userName);
        minecraftArguments = minecraftArguments.replace("${version_name}", mMCJson.get("id").getAsString());
        minecraftArguments = minecraftArguments.replace("${game_directory}", ".minecraft");//数据目录
        minecraftArguments = minecraftArguments.replace("${game_assets}", "\"" + mMcRoot + mDelimiter + "assets" + "\"");
        minecraftArguments = minecraftArguments.replace("${assets_root}", "\"" + mMcRoot + mDelimiter + "assets" + "\"");
        minecraftArguments = minecraftArguments.replace("${assets_index_name}", mMCJson.get("assets").getAsString());
        minecraftArguments = minecraftArguments.replace("${auth_uuid}", uuid);
        minecraftArguments = minecraftArguments.replace("${auth_session}", token);
        minecraftArguments = minecraftArguments.replace("${auth_access_token}", token);
        minecraftArguments = minecraftArguments.replace("${user_properties}", twitch);
        minecraftArguments = minecraftArguments.replace("${user_type}", "Legacy");


        String final_arg = "\"" + javaw + "\" -Xmx" + maxMemory + "M " + additional + " -Djava.library.path=\"" + mNativesDir + "\" ";
        assert arg_libraries != null;
        final_arg += "-cp \"" + arg_libraries.toString() + "\" ";
        final_arg += mMCJson.get("mainClass").getAsString();
        final_arg += " " + minecraftArguments;


        return final_arg;
    }


    public String makeStartArg(String javaw, int maxMemory, String userName) {
        return makeStartArg(javaw, maxMemory, "-Dfml.ignoreInvalidMinecraftCertificates=true -Dfml.ignorePatchDiscrepancies=true", userName, "", "", "");
    }
}
