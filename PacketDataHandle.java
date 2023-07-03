import java.util.ArrayList;
import java.util.List;

public class PacketDataHandle {

    public final static String splitChar = "€";

    public static PacketDataHandle readData(String data) {
        String[] datas = data.split(PacketDataHandle.splitChar);
        try {
            return new PacketDataHandle(DataType.getType(datas[0]), datas.length == 2 ? datas[1] : "");
        } catch (Exception e) {
        }
        return null;
    }

    public final DataType type;
    private final String data;

    public PacketDataHandle(DataType type, String data) throws Exception {
        if (type == null)
            throw new Exception("DataType is null.");
        switch (type) {
            case Max:
            case Connect:
                break;
            case Start:
                break;
            case Choose:
                try {
                    List<Integer> check = new ArrayList<Integer>();
                    String[] nums = data.split(",");
                    for (String num : nums) {
                        int n = Integer.valueOf(num);
                        if (n == -1 || check.contains(n)) {
                            throw new Exception("Integer has been repeat.");
                        } else
                            check.add(n);
                    }
                } catch (NumberFormatException e) {
                    throw new Exception("Data isn't integer array.");
                }
                break;
            case Bingo:
                try {
                    Integer.valueOf(data);
                } catch (NumberFormatException e) {
                    throw new Exception("Data isn't integer.");
                }
                break;
            case Cancel:
            case Msg:
                break;
            default:
                throw new Exception("DataType is unknow.");
        }
        this.type = type;
        this.data = data;
    }

    public Object getData() {
        switch (type) {
            case Connect:
                return this.data;
            case Start:
                return this.data;
            case Choose:
                String[] nums = data.split(",");
                int[] result = new int[nums.length];
                for (int i = 0; i < nums.length; i++) {
                    result[i] = Integer.valueOf(nums[i]);
                }
                return result;
            case Bingo:
                return Integer.valueOf(this.data);
            case Msg:
                return this.data;
        }
        return null;
    }

    public String toString() {
        return this.type.get() + PacketDataHandle.splitChar + this.data;
    }

    public static enum DataType {

        // 連接, 開始, 選號, 叫號, 訊息
        Max("max"), Connect("connect"), Start("start"), Choose("choose"), Cancel("cancel"), Bingo("bingo"), Msg("msg");

        public static DataType getType(String prefix) {
            prefix = prefix.toLowerCase();
            for (DataType type : DataType.values()) {
                if (prefix.equals(type.get()))
                    return type;
            }
            return null;
        }

        private final String prefix;

        private DataType(String prefix) {
            this.prefix = prefix;
        }

        private String get() {
            return prefix;
        }

    }

}
