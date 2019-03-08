package cn.chase;

import java.io.IOException;

import static cn.chase.DeCompress.bis;

public class BitFile {

    public static int getInt(int num, int offset) {
        String numOfString = Integer.toBinaryString(num);
        String result;

        while (numOfString.length() < 32) {
            numOfString = "0".concat(numOfString);
        }
        char[] data= numOfString.toCharArray();
        result = new String(data, (8 * offset), 8);

        return (Integer.parseInt(result, 2));
    }

    public static String getString(String string, int flag) {
        int num = flag - string.length();
        for (int i = 0; i < num; i ++) {
            string = "0".concat(string);
        }

        return string;
    }

    public static int bitFileGetBit(Stream stream) {
        int returnValue;
        int bitBuffer = stream.getBitBuffer();
        int bitCount = stream.getBitCount();

        if (bitCount == 0) {
            try {
                if ((returnValue = bis.read()) != -1) {
                    bitBuffer = returnValue;
                    bitCount = 8;
                } else {
                    return -1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        bitCount --;
        stream.setBitBuffer(bitBuffer);
        stream.setBitCount(bitCount);

        returnValue = ((bitBuffer >> bitCount) & 0x01);

        return returnValue;
    }

    public static int bitFileGetChar(Stream stream) {
        int returnValue = -1;
        int tmp;
        int bitBuffer = stream.getBitBuffer();
        int bitCount = stream.getBitCount();

        try {
            returnValue = bis.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitCount == 0) {
            return returnValue;
        }

        if (returnValue != -1) {
            tmp = returnValue >> bitCount;
            tmp = tmp | getInt(bitBuffer << (8 - bitCount), 3);
            bitBuffer = returnValue;
            stream.setBitBuffer(bitBuffer);
            returnValue = tmp;
        }

        return returnValue;
    }

    public static int bitFileGetBitsInt(Stream stream, int count) {
        int returnValue;
        int remaining = count;
        int tmp = 0;
        String str = "";
        int flag = 0;

        while (remaining >= 8) {
            int num = bitFileGetChar(stream);
            if (num != -1) {
                str = getString(Integer.toBinaryString(num), 8).concat(str);
                remaining -= 8;
            } else {
                return -1;
            }
        }

        if (remaining != 0) {
            while (remaining > 0) {
                returnValue = bitFileGetBit(stream);
                if (remaining != -1) {
                    tmp <<= 1;
                    tmp |= (returnValue & 0x01);
                    remaining --;
                    flag ++;
                } else {
                    return -1;
                }
            }
            str = getString(Integer.toBinaryString(tmp), flag).concat(str);
        }

        return Integer.parseInt(str, 2);
    }
}
