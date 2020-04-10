package com.mc.starter;

import java.io.File;
import java.util.Scanner;

public class Main {


    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.print("Javaw：");
        String javaw = scanner.next();
        System.out.print("游戏目录：");
        String gameDir = scanner.next();
        System.out.print("最大内存：");
        int maxMemory = scanner.nextInt();
        System.out.print("启动版本：");
        String version = scanner.next();
        System.out.print("玩家名称：");
        String username = scanner.next();

        MCJson mcjson = MCJson.LoadJson(version, gameDir, MCJson.SystemType.Windows, MCJson.SystemBit.B64);

        String cmd = mcjson.makeStartArg(javaw, maxMemory, username);

        System.out.println(cmd);

        try {
            Runtime.getRuntime().exec(cmd, null, new File(gameDir));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
