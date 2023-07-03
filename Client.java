import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.io.InputStream;
import java.io.OutputStream;

import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    public static Client core;
    public final static int port = 2525;

    private final ClientFrame frame;
    private String name;
    private Socket sock;
    BufferedReader reader;
    PrintStream writer;

    public static void main(String[] args) {

        Client.core = new Client(new ClientFrame());
    }

    public Client(ClientFrame frame) {
        this.frame = frame;
        this.frame.setVisible(true);
        Thread readerThread = new Thread(new IncomingReader(this));
        readerThread.start();
    }

    public String getName(){
        return name;
    }

    public void connect(String ip) {
        try {
            this.sock = new Socket(ip, port);
            InputStreamReader streamReader = new InputStreamReader(this.sock.getInputStream());
            this.reader = new BufferedReader(streamReader);
            this.writer = new PrintStream(this.sock.getOutputStream());
            this.frame.connect(true);
        } catch (UnknownHostException e) {
            this.frame.addChat("[訊息] 找不到伺服器.");
            System.out.println("未知主機");
        } catch (IOException e) {
            this.frame.addChat("[訊息] 建立連線失敗.");
            System.out.println("建立連線失敗");
        }
    }

    public void sendData(PacketDataHandle send) {
        if (this.writer == null) {
            return;
        }
        try {
            this.writer.println(send);
            this.writer.flush();
        } catch (Exception ex) {
        }
    }

    public class IncomingReader implements Runnable {

        private final Client core;

        public IncomingReader(Client core) {
            this.core = core;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e1) {
                }
                if (this.core.reader != null) {
                    String message;
                    try {
                        while ((message = this.core.reader.readLine()) != null) {
                            try {
                                PacketDataHandle msg = PacketDataHandle.readData(message);
                                if (msg != null) {
                                    switch (msg.type) {
                                        case Max:
                                            this.core.frame.addChat("[訊息] 伺服器人數滿人.");
                                            this.core.sock.close();
                                            break;
                                        case Msg:
                                            // 發送聊天訊息
                                            this.core.frame.addPlayerChat(msg.getData().toString());
                                            break;
                                        case Bingo:
                                            // 點選賓果號碼
                                            Object data = msg.getData();
                                            if (data instanceof Integer)
                                                this.core.frame.bingo((Integer) data);
                                            break;
                                        case Choose:
                                            Object obj = msg.getData();
                                            if (obj instanceof int[]) {
                                                int[] num = (int[]) obj;
                                                new ClientWinFrame(num,this.core.frame.width,this.core.frame.height).setVisible(true);
                                            }
                                            break;
                                        case Connect:
                                            // 玩家連接
                                            String[] commands = msg.getData().toString().split(" ");
                                            if (commands.length >= 1) {
                                                switch (commands[0]) {
                                                    case "setname":
                                                        if (commands.length < 2)
                                                            break;
                                                        this.core.name = commands[1];
                                                        this.core.frame.setName(this.core.name);
                                                        break;
                                                    case "room":
                                                        if (commands.length == 4)
                                                            this.core.frame.enterRoom(commands[1], Integer.valueOf(commands[2]), Integer.valueOf(commands[3]));
                                                        break;
                                                    case "rooml":
                                                        this.core.frame.leaveRoom();
                                                        break;
                                                    case "roomcf":
                                                        this.core.frame.addChat("[訊息] 其他玩家取消準備, 請等待所有玩家準備.");
                                                        this.core.frame.readyC(false);
                                                        break;
                                                    case "roomct":
                                                        this.core.frame.addChat("[訊息] 所有玩家準備完成, 隨時可以開始遊戲.");
                                                        this.core.frame.readyC(true);
                                                        break;
                                                    case "rooms":
                                                        this.core.frame.gameStart(true);
                                                        break;
                                                    case "roome":
                                                        this.core.frame.gameStart(false);
                                                        break;
                                                    case "ready":
                                                        if (commands.length < 2)
                                                            break;
                                                        if (commands[1].equals("true")) {
                                                            this.core.frame.addChat("[訊息] 你準備好了.");
                                                            this.core.frame.ready(true);
                                                        } else {
                                                            this.core.frame.addChat("[訊息] 取消準備.");
                                                            this.core.frame.ready(false);
                                                        }
                                                        break;
                                                    case "readyc":
                                                        if (commands.length < 2)
                                                            break;
                                                        if (commands[1].equals("true")) {
                                                            this.core.frame.ready(true);
                                                        } else {
                                                            this.core.frame.ready(false);
                                                        }
                                                        break;
                                                    case "bingo":
                                                        if (commands.length < 2)
                                                            break;
                                                        if (commands[1].equals("true")) {
                                                            this.core.frame.trun(true);
                                                        } else {
                                                            this.core.frame.trun(false);
                                                        }
                                                        break;
                                                    case "info":
                                                        if (commands.length < 2)
                                                            break;
                                                        String str = "";
                                                        for (int i=1;i<commands.length;i++){
                                                            if (i>1)
                                                                str += " ";
                                                            str += commands[i];
                                                        }
                                                        this.core.frame.addChat("[訊息] "+str);
                                                        break;
                                                    case "say":
                                                        str = "";
                                                        for (int i=1;i<commands.length;i++){
                                                            if (i>1)
                                                                str += " ";
                                                            str += commands[i];
                                                        }
                                                        this.core.frame.addChat(str);
                                                        break;
                                                    case "join":
                                                        if (commands.length < 2)
                                                            break;
                                                        this.core.frame.addChat("[訊息] "+commands[1]+" 加入了遊戲!");
                                                        break;
                                                }
                                            }
                                            break;
                                    }
                                }
                            } catch (Exception ex) {
                                System.out.println("錯誤訊息: "+message);
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("斷開連線!");
                        try {
                            if (this.core.sock != null)
                                this.core.sock.close();
                            if (this.core.reader != null)
                                this.core.reader.close();
                            if (this.core.writer != null)
                                this.core.writer.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        this.core.sock = null;
                        this.core.reader = null;
                        this.core.writer = null;
                        this.core.frame.connect(false);
                    }
                }
            }
        }

    }

    public boolean disconnect() {
        if (this.core.sock == null)
            return true;
        try {
            this.core.sendData(new PacketDataHandle(PacketDataHandle.DataType.Connect, "leave"));
            try {
                this.core.sock.close();
            } catch (IOException e) {
                this.core.sock = null;
            }
        } catch (Exception ex) {
        }
        return false;
    }

}