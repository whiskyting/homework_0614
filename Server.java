import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;

import javax.swing.event.ListSelectionEvent;

public class Server {

    public static Server core;
    public final static int port = 2525;
    public final static int maxRoom = 1;

    private final GameServer server;
    private final ServerConsole console;
    private Random rnd = new Random();

    public static void main(String[] args) {
        core = new Server();
    }

    private Map<String, PrintStream> outputs = new HashMap<String, PrintStream>();
    private Map<String, Process> clients = new HashMap<String, Process>();
    private Map<String, GameRoom> rooms = new HashMap<String, GameRoom>();

    public Server() {
        new Thread(server = new GameServer(this)).start();
        new Thread(console = new ServerConsole(this, new Scanner(System.in))).start();
        try {
            ServerSocket svs = new ServerSocket(port);
            System.out.println("等待客戶端的請求中...");
            while (true) {
                Socket s = svs.accept();
                PrintStream output = new PrintStream(s.getOutputStream());
                String playerName;
                if (clients.size() >= 2) {
                    this.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Max, ""));
                    s.shutdownInput();
                    s.shutdownOutput();
                    s.close();
                    continue;
                }
                do {
                    playerName = "Player" + rnd.nextInt(2);//0~1
                } while (clients.containsKey(playerName));
                Process client = new Process(this, playerName, s);
                new Thread(client).start();
                this.clients.put(playerName, client);
                this.rooms.put(playerName, null);
                System.out.println("客戶端連接: " + playerName + s.getLocalSocketAddress());
                this.sendJoin(playerName);
                this.outputs.put(playerName, output);
                this.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect, "setname " + playerName));
                this.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect, "say " + "你加入了遊戲!"));
            }
        } catch (Exception ex) {
            System.out.println("連接失敗");
            System.exit(0);
        }
    }

    private void sendMessage(String msg) {
        try {
            PacketDataHandle send = new PacketDataHandle(PacketDataHandle.DataType.Msg, msg);
            for (PrintStream output : this.outputs.values()) {
                this.sendData(output, send);
            }
        } catch (Exception ex) {
        }
    }

    private void sendJoin(String playerName) {
        try {
            PacketDataHandle send = new PacketDataHandle(PacketDataHandle.DataType.Connect, "join " + playerName);
            for (PrintStream output : this.outputs.values()) {
                this.sendData(output, send);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendData(PrintStream output, PacketDataHandle send) {
        try {
            output.println(send);
            output.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private GameRoom getPlayerRoom(String playerName) {
        if (this.clients.containsKey(playerName)) {
            if (!this.rooms.containsKey(playerName))
                this.rooms.put(playerName, null);
            return this.rooms.get(playerName);
        }
        return null;
    }

    public static class ServerConsole implements Runnable {

        final Server core;
        final Scanner scn;

        public ServerConsole(Server core, Scanner scn) {
            this.core = core;
            this.scn = scn;
        }

        @Override
        public void run() {
            while (true) {
                String msg = this.scn.nextLine();
                try {
                    PacketDataHandle send = new PacketDataHandle(PacketDataHandle.DataType.Connect, "say " + msg);
                    for (PrintStream output : this.core.outputs.values()) {
                        this.core.sendData(output, send);
                    }
                } catch (Exception ex) {
                }
            }
        }

    }

    public static class GameServer implements Runnable {

        final Server core;
        final Map<String, GameRoom> rooms;
        long last = 0;

        public GameServer(Server core) {
            this.core = core;
            this.rooms = new HashMap<String, GameRoom>();
        }

        public boolean canCreate() {
            return this.rooms.size() < Server.maxRoom;
        }

        public GameRoom create() {
            return this.create("房間" + this.core.rnd.nextInt(2), 2, 5, 5, 3);
        }

        public GameRoom create(String roomName, int maxplayer, int width, int height, int line) {
            if (this.canCreate() && !this.rooms.containsKey(roomName)) {
                try {
                    GameRoom room = new GameRoom(this.core, roomName, maxplayer, width, height, line);
                    this.rooms.put(roomName, room);
                    return room;
                } catch (Exception e) {
                }
            }
            return null;
        }

        public GameRoom getRoom(String roomID) {
            return this.rooms.containsKey(roomID) ? this.rooms.get(roomID) : null;
        }

        public GameRoom joinRoom(String roomID) {
            GameRoom room = this.getRoom(roomID);
            if (room != null && room.canJoin())
                return room;
            return null;
        }

        public GameRoom joinVacancyRoom() {
            for (GameRoom room : this.rooms.values()) {
                if (room.canJoin())
                    return room;
            }
            if (this.canCreate())
                return this.create();
            return null;
        }

        @Override
        public void run() {
            while (true) {
                List<String> close = new ArrayList<String>();
                Iterator<Entry<String, GameRoom>> iterator = this.rooms.entrySet().iterator();
                Entry<String, GameRoom> entry;
                while (iterator.hasNext()) {
                    entry = iterator.next();
                    if (entry.getValue().tick())
                        close.add(entry.getKey());
                }
                for (String s : close) {
                    this.rooms.get(s).kickAll();
                    this.rooms.remove(s);
                }
                if (System.currentTimeMillis() < last + 20) {
                    try {
                        Thread.sleep(last + 20 - System.currentTimeMillis());
                    } catch (InterruptedException e) {
                    }
                }
                this.last = System.currentTimeMillis();
            }
        }

    }

    public static class GameRoom {

        private final static int idleTime = 1000 * 30;

        boolean close = false;

        private long idle = 0;

        final Server core;
        final String ID;
        String RoomChief;
        final int maxPlayer;
        final Map<String, Boolean> playerlist;
        final int width;
        final int height;
        final int line;

        boolean allready = false;
        boolean start = false;

        String[] order = null;
        int order_index = 0;
        Map<String, int[]> bingos = new HashMap<String, int[]>();
        List<Integer> bingoN = new ArrayList<Integer>();

        public GameRoom(Server core, String ID, int maxPlayer, int width, int height, int line) throws Exception {
            this(core, ID, maxPlayer, null, width, height, line);
        }

        public GameRoom(Server core, String ID, int maxPlayer, Map<String, Boolean> playerlist, int width, int height, int line)
                throws Exception {
            if (playerlist == null)
                playerlist = new HashMap<String, Boolean>();
            for (String player : playerlist.keySet()) {
                if (!core.clients.containsKey(player))
                    throw new Exception("Cannot create room.");
                else
                    this.bingos.put(player, null);
            }
            this.core = core;
            this.ID = ID;
            this.maxPlayer = maxPlayer;
            this.playerlist = playerlist;
            this.width = width;
            this.height = height;
            this.line = line;
            this.idle = System.currentTimeMillis() + idleTime;
        }

        public String getID() {
            return this.ID;
        }

        public boolean canJoin() {
            return this.playerlist.size() < this.maxPlayer;
        }

        // 加入房間
        public boolean joinRoom(String playerName) {
            if (this.close)
                return false;
            if (!this.core.clients.containsKey(playerName) || start)
                return false;
            if (!this.playerlist.containsKey(playerName))
                this.playerlist.put(playerName, false);
            this.core.rooms.put(playerName, this);
            return true;
        }

        // 離開房間
        public void leaveRoom(String playerName) {
            if (this.playerlist.containsKey(playerName)) {
                this.playerlist.remove(playerName);
                this.bingos.remove(playerName);
                this.core.rooms.put(playerName, null);
            }
            if (this.start && this.order[this.order_index].equals(playerName)) {
                do {
                    if (this.playerlist.size()==1)
                        break;
                    this.order_index++;
                    while (this.order_index >= this.order.length) {
                        this.order_index -= this.order.length;
                    }
                } while (!this.playerlist.containsKey(this.order[this.order_index]));
            }
        }

        public void kickAll() {
            String[] playerlist = new String[this.playerlist.size()];
            playerlist = this.playerlist.keySet().toArray(playerlist);
            for (String player : playerlist) {
                PrintStream output = this.core.outputs.get(player);
                if (output != null) {
                    try {
                        this.core.sendData(output,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "很抱歉. 您被強制踢出房間."));
                    } catch (Exception e) {
                    }
                }
                this.leaveRoom(player);
            }
        }

        public void start() {
            this.start = true;
            this.order = null;
            this.order_index = 0;
            for (String player : this.playerlist.keySet()) {
                PrintStream output = this.core.outputs.get(player);
                if (output != null) {
                    try {
                        this.core.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect, "rooms"));
                        this.core.sendData(output,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "遊戲開始."));
                    } catch (Exception e) {
                    }
                }
            }
        }

        public void win(String playerName) {
            PrintStream[] o2 = new PrintStream[2];
            int[][] nums2 = new int[2][];
            int c2 = 0;
            for (String player : this.playerlist.keySet()) {
                PrintStream output = this.core.outputs.get(player);
                if (c2 < 2) {
                    o2[c2] = output;
                    nums2[c2] = this.bingos.get(player);
                    c2++;
                }
                this.bingoNum(player, null);
                if (output != null) {
                    try {
                        this.core.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect, "roome"));
                        this.core.sendData(output,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "遊戲結束, "+playerName+" 勝利."));
                    } catch (Exception e) {
                    }
                }
            }
            if (c2==2) {
                Tsend(o2[0],nums2[1]);
                Tsend(o2[1],nums2[0]);
            }
            this.start = false;
            this.allready = false;
            this.bingoN.clear();
            this.idle = System.currentTimeMillis() + idleTime;
        }

        public void Tsend(PrintStream output, int[] nums){
            try {
                String data = "";
                for (int i = 0; i < nums.length; i++) {
                    if (i > 0)
                        data += ",";
                    data += String.valueOf(nums[i]);
                }
                this.core.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Choose, data));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public boolean bingoNum(String playerName, int[] num) {
            if (num == null || num.length != this.width * this.height) {
                this.bingos.put(playerName, null);
                this.playerlist.put(playerName, false);
                return false;
            }
            if (this.playerlist.containsKey(playerName)) {
                this.bingos.put(playerName, num);
                this.playerlist.put(playerName, true);
                return true;
            }
            return false;
        }

        public void choose(String playerName, int num) {
            PrintStream output = this.core.outputs.get(playerName);
            if (output == null)
                return;
            if (this.order[this.order_index].equals(playerName)) {
                if (this.bingoN.contains(num)) {
                    try {
                        this.core.sendData(output,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "這個號碼選過囉."));
                    } catch (Exception e) {
                    }
                    return;
                }
                this.bingoN.add(num);
                try {
                    this.core.sendData(output,
                            new PacketDataHandle(PacketDataHandle.DataType.Connect, "bingo false"));
                } catch (Exception e) {
                }
                for (String player : this.playerlist.keySet()) {
                    PrintStream output2 = this.core.outputs.get(player);
                    if (output2 != null) {
                        try {
                            this.core.sendData(output2,
                                    new PacketDataHandle(PacketDataHandle.DataType.Bingo, String.valueOf(num)));
                            this.core.sendData(output2, new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                    "info " + "玩家 " + playerName + " 選擇" + num + "號."));
                        } catch (Exception e) {
                        }
                    }
                }
                do {
                    if (this.playerlist.size()==1)
                        break;
                    this.order_index++;
                    while (this.order_index >= this.order.length) {
                        this.order_index -= this.order.length;
                    }
                } while (!this.playerlist.containsKey(this.order[this.order_index]));
                PrintStream output2 = this.core.outputs.get(this.order[this.order_index]);
                if (output2 != null) {
                    try {
                        this.core.sendData(output2,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "輪到您選號."));
                        this.core.sendData(output2,
                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "bingo true"));
                    } catch (Exception e) {
                    }
                }
            } else {
                try {
                    this.core.sendData(output,
                            new PacketDataHandle(PacketDataHandle.DataType.Connect, "bingo false"));
                    this.core.sendData(output,
                            new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "未輪到您選號."));
                } catch (Exception e) {
                }
            }
        }

        private int checkLine(int[] nums,int width,int height) {
            if (nums.length != width*height)
                return 0;
            int c = 0;
            for(int y=0;y<height;y++){
                Integer[] poses = new Integer[width];
                for(int x=0;x<width;x++){
                    int pos = y*width+x;
                    poses[x] = pos;
                }
                if(this.checkLine(nums, poses))
                    c++;
            }
            for(int x=0;x<width;x++){
                Integer[] poses = new Integer[height];
                for(int y=0;y<height;y++){
                    int pos = y*width+x;
                    poses[y] = pos;
                }
                if(this.checkLine(nums, poses))
                    c++;
            }
            if (width==height) {
                Integer[] posesA = new Integer[width];
                Integer[] posesB = new Integer[width];
                for(int z=0;z<width;z++){
                    posesA[z] = z*width+z;
                    posesB[z] = (width-1-z)*width+z;
                }
                if(this.checkLine(nums, posesA))
                    c++;
                if(this.checkLine(nums, posesB))
                    c++;
            }
            return c;
        }

        private boolean checkLine(int[] nums,Integer[] args){
            for(int i=0;i<args.length;i++){
                int n = nums[args[i]];
                if(!this.bingoN.contains(n))
                    return false;
            }
            return true;
        }

        // 房間狀態更新
        public boolean tick() {
            try {
                if (this.close){
                    return true;
                }
                if (this.playerlist.size() < 1)
                    this.RoomChief = null;
                else if (!this.playerlist.containsKey(this.RoomChief)) {
                    Iterator<String> iterator = this.playerlist.keySet().iterator();
                    if (iterator.hasNext()) {
                        this.RoomChief = iterator.next();
                        PrintStream output = this.core.outputs.get(RoomChief);
                        if (output != null) {
                            try {
                                this.core.sendData(output,
                                        new PacketDataHandle(PacketDataHandle.DataType.Connect, "info 您成為了室長"));
                            } catch (Exception e) {
                            }
                        }
                    } else {
                        this.RoomChief = null;
                    }
                }
                if (this.start || this.RoomChief != null) {
                    this.idle = System.currentTimeMillis() + idleTime;
                } else if (System.currentTimeMillis() >= this.idle) {
                    this.close = true;
                    return true;
                }
                if (this.RoomChief == null)
                    return false;
                PrintStream output = this.core.outputs.get(RoomChief);
                if (output == null)
                    this.close = true;
                if (this.start) {
                    if (this.order == null) {
                        // 遊戲初始排序
                        this.order = new String[this.playerlist.size()];
                        this.order = this.playerlist.keySet().toArray(this.order);
                        for (int i = order.length; i > 0; i--) {
                            int pos = this.core.rnd.nextInt(i);
                            String temp = this.order[pos];
                            this.order[pos] = this.order[i - 1];
                            this.order[i - 1] = temp;
                        }
                        PrintStream output2 = this.core.outputs.get(this.order[this.order_index]);
                        if (output2 != null) {
                            try {
                                this.core.sendData(output2,
                                        new PacketDataHandle(PacketDataHandle.DataType.Connect, "info " + "輪到您選號."));
                                this.core.sendData(output2,
                                        new PacketDataHandle(PacketDataHandle.DataType.Connect, "bingo true"));
                            } catch (Exception e) {
                            }
                        }
                    } else {
                        if (this.playerlist.size() == 1) {
                            for (String player : this.playerlist.keySet()) {
                                this.win(player);
                                break;
                            }
                        } else {
                            //檢查連線
                            String winner = "";
                            for (String player : this.playerlist.keySet()) {
                                if (this.checkLine(this.bingos.get(player), this.width, this.height)>=this.line) {
                                    if (winner.length() > 0)
                                        winner += ", ";
                                    winner += player;
                                }
                            }
                            if (winner.length() > 0) {
                                this.win(winner);
                            }
                        }
                    }
                } else {
                    Collection<Boolean> collection = this.playerlist.values();
                    Iterator<Boolean> iterator = collection.iterator();
                    while (iterator.hasNext()) {
                        if (!iterator.next()) {
                            try {
                                if (this.allready) {
                                    this.allready = false;
                                    this.core.sendData(output,
                                            new PacketDataHandle(PacketDataHandle.DataType.Connect, "roomcf"));
                                }
                            } catch (Exception e) {
                            }
                            return false;
                        }
                    }
                    try {
                        if (!this.allready && this.playerlist.size() >= 2) {
                            this.allready = true;
                            this.core.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect, "roomct"));
                        }
                    } catch (Exception e) {
                    }
                }
                return false;
            } catch (Exception ex) {
                ex.printStackTrace();
                return true;
            }
        }
    }

    public static class Process implements Runnable {

        final Server core;
        final String playerName;
        final Socket sock;

        private BufferedReader reader;

        public Process(Server core, String playerName, Socket cSocket) {
            this.core = core;
            this.playerName = playerName;
            this.sock = cSocket;
            try {
                // 取得Socket的輸入資料流
                InputStreamReader isReader = new InputStreamReader(this.sock.getInputStream());

                this.reader = new BufferedReader(isReader);
            } catch (Exception ex) {
                System.out.println("連接失敗Process");
            }
        }

        private String PlayerChatDecode(String data) {
            String[] datas = data.split("@");
            if (datas.length > 1) {
                String msg = "";
                for (int i = 1; i < datas.length; i++) {
                    if (i > 1)
                        msg += "@";
                    msg += datas[i];
                }
                return "<" + datas[0] + "> " + msg;
            }
            return null;
        }

        public void run() {
            String message;
            try {
                while ((message = this.reader.readLine()) != null) {
                    try {
                        PacketDataHandle msg = PacketDataHandle.readData(message);
                        if (msg != null) {
                            switch (msg.type) {
                                case Msg:
                                    // 發送聊天訊息
                                    this.core.sendMessage(msg.getData().toString());
                                    String chat = PlayerChatDecode(msg.getData().toString());
                                    if (chat != null)
                                        System.out.println("[Chat] " + chat);
                                    break;
                                case Connect:
                                    if (msg.getData().toString().equals("leave")) {
                                        Process client = this.core.clients.get(this.playerName);
                                        client.disconnect();
                                    }
                                    break;
                                case Start:
                                    // 客戶端加入房間
                                    PrintStream output = this.core.outputs.get(this.playerName);
                                    if (output == null)
                                        break;
                                    GameRoom room = null;
                                    if (msg.getData().toString().equals("")) {
                                        room = this.core.server.joinVacancyRoom();
                                        if (room == null) {
                                            this.core.sendData(output, new PacketDataHandle(
                                                    PacketDataHandle.DataType.Connect, "info " + "找不到空房"));
                                        }
                                    } else {
                                        String command = msg.getData().toString();
                                        String[] commands = command.split("@");
                                        if (commands.length >= 2 && commands[0].equals("create")) {
                                            String roomName = "";
                                            int maxplayer = 2;
                                            int width = 5;
                                            int height = 5;
                                            int line = 6;
                                            if (commands.length == 2) {
                                                roomName = commands[1];
                                            }
                                            if (commands.length == 3) {
                                                try {
                                                    maxplayer = Integer.valueOf(commands[2]);
                                                } catch (NumberFormatException e) {
                                                }
                                            }
                                            if (commands.length == 5) {
                                                // 暫無功能
                                            }
                                            if (commands.length == 6) {
                                                // 暫無功能
                                            }
                                            if (roomName.length() > 4 && maxplayer >= 2) {
                                                room = this.core.server.create(roomName, maxplayer, width, height, line);
                                                if (room == null) {
                                                    this.core.sendData(output,
                                                            new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                    "info " + "創立房間(" + roomName + ")失敗"));
                                                } else {
                                                    if (room.joinRoom(this.playerName)) {
                                                        this.core.sendData(output,
                                                                new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                        "room " + room.getID() + " " + room.width + " "
                                                                                + room.height));
                                                        this.core.sendData(output,
                                                                new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                        "info " + "創立房間(" + roomName + ")成功"));
                                                    } else {
                                                        room.close = true;
                                                        this.core.sendData(output,
                                                                new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                        "info " + "創立房間(" + roomName + ")失敗"));
                                                    }
                                                }
                                            } else {
                                                this.core.sendData(output,
                                                        new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                "info " + "創立房間(" + roomName + ")失敗"));
                                            }
                                            room = null;
                                        } else if (commands.length == 1 && commands[0].equals("room")) {
                                            room = this.core.getPlayerRoom(this.playerName);
                                            if (room != null && room.RoomChief.equals(this.playerName)) {
                                                room.start();
                                            }
                                            break;
                                        } else if (commands.length == 2 && commands[0].equals("join")) {
                                            room = this.core.server.joinRoom(commands[1]);
                                        } else if (commands.length == 1 && commands[0].equals("leave")) {
                                            room = this.core.getPlayerRoom(this.playerName);
                                            if (room != null) {
                                                room.leaveRoom(this.playerName);
                                                if (this.core.getPlayerRoom(this.playerName) == null) {
                                                    this.core.sendData(output, new PacketDataHandle(
                                                            PacketDataHandle.DataType.Connect, "rooml"));
                                                    this.core.sendData(output,
                                                            new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                                    "info " + "您已離開房間(" + room.getID() + ")"));
                                                }
                                                room = null;
                                            }
                                        }
                                    }
                                    if (room != null) {
                                        if (room.joinRoom(this.playerName)) {
                                            this.core.sendData(output,
                                                    new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                            "room " + room.getID() + " " + room.width + " " + room.height));
                                            this.core.sendData(output,
                                                    new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                            "info " + "成功加入房間(" + room.getID() + ")"));
                                        } else {
                                            this.core.sendData(output,
                                                    new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                            "info " + "無法加入房間(" + room.getID() + ")"));
                                        }
                                    }
                                    break;
                                case Choose:
                                    // 客戶端發送號碼牌
                                    output = this.core.outputs.get(this.playerName);
                                    if (output == null)
                                        break;
                                    room = this.core.getPlayerRoom(this.playerName);
                                    if (room != null) {
                                        int n = room.width * room.height;
                                        Object obj = msg.getData();
                                        if (obj instanceof int[]) {
                                            int[] num = (int[]) obj;
                                            if (room.bingoNum(this.playerName, num)) {
                                                this.core.sendData(output, new PacketDataHandle(
                                                        PacketDataHandle.DataType.Connect, "ready true"));
                                                break;
                                            }
                                        }
                                        this.core.sendData(output, new PacketDataHandle(PacketDataHandle.DataType.Connect,
                                                "info " + "填選號碼牌失敗,請嘗試重新填選"));
                                    }
                                    break;
                                case Cancel:
                                    output = this.core.outputs.get(this.playerName);
                                    if (output == null)
                                        break;
                                    room = this.core.getPlayerRoom(this.playerName);
                                    if (room != null) {
                                        room.bingoNum(this.playerName, null);
                                        this.core.sendData(output,
                                                new PacketDataHandle(PacketDataHandle.DataType.Connect, "ready false"));
                                    }
                                    break;
                                case Bingo:
                                    // 點選賓果號碼
                                    Object data = msg.getData();
                                    if (data instanceof Integer) {
                                        room = this.core.getPlayerRoom(this.playerName);
                                        if (room != null)
                                            room.choose(this.playerName, (Integer) data);
                                    }
                                    break;
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("錯誤訊息(來ˋ自:" + this.playerName + "): " + message);
                        ex.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                System.out.println("客戶端離開: " + this.playerName);
                GameRoom room = this.core.getPlayerRoom(this.playerName);
                if (room != null)
                    room.leaveRoom(this.playerName);
                this.core.clients.remove(this.playerName);
                this.core.rooms.remove(this.playerName);
                this.core.outputs.remove(this.playerName);
                if (this.sock != null) {
                    try {
                        this.sock.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        public void disconnect(){
            try {
                this.sock.close();
            } catch (IOException e) {
            }
        }

    }

}
