package cn.chase;

import java.io.*;

import static cn.chase.BitFile.*;

public class DeCompress {
    private static int min_rep_len = 11;
    private static int min_size = 1 << 20;
    private static int MAX_CHAR_NUM = 1 << 26;

    private static int diff_pos_loc_len;
    private static int diff_low_vec_len;
    private static int N_vec_len;
    private static int other_char_len;

    private static char[] ref_seq_code = new char[MAX_CHAR_NUM];
    private static char[] tar_seq_code = new char[MAX_CHAR_NUM];
    private static int ref_seq_len = 0;
    private static int tar_seq_len = 0;
    private static int ref_pos_vec_len = 0;
    private static int line_break_len = 0;
    private static int[] ref_pos_vec_begin = new int[min_size];
    private static int[] ref_pos_vec_length = new int[min_size];
    private static int[] line_break_vec = new int[1 << 25];
    private static int[] diff_pos_loc_begin = new int[min_size];
    private static int[] diff_pos_loc_length = new int[min_size];
    private static int[] diff_low_vec_begin = new int[min_size];
    private static int[] diff_low_vec_length = new int[min_size];
    private static int[] N_vec_begin = new int[min_size];
    private static int[] N_vec_length = new int[min_size];
    private static int[] other_char_vec_pos = new int[min_size];
    private static char[] other_char_vec_ch = new char[min_size];
    private static char[] dismatched_str = new char[min_size];

    public static BufferedInputStream bis = null;

    public static char readIndex(int num) {
        switch (num) {
            case 0: return 'A';
            case 1: return 'C';
            case 2: return 'G';
            case 3: return 'T';
            default : return 'Y';
        }
    }

    public static void extractRefInfo(File refFile) {  //得到ref_seq_code  ref_seq_len  ref_pos_vec(begin,length)
        BufferedReader br = null;
        String str;
        int str_length;
        char ch;
        Boolean flag = true;
        int letters_len = 0;

        try {
            br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(refFile))));
            br.readLine();
            while ((str = br.readLine()) != null) {
                str_length = str.length();
                for (int i = 0; i < str_length; i++) {
                    ch = str.charAt(i);

                    if (Character.isLowerCase(ch)) {
                        ch = Character.toUpperCase(ch);

                        if (flag) {
                            flag = false;
                            ref_pos_vec_begin[ref_pos_vec_len] = letters_len;
                            letters_len = 0;
                        }
                    } else {
                        if (!flag) {
                            flag = true;
                            ref_pos_vec_length[ref_pos_vec_len ++] = letters_len;
                            letters_len = 0;
                        }
                    }

                    if (ch == 'A' || ch == 'C' || ch == 'G' || ch == 'T')
                        ref_seq_code[ref_seq_len ++] = ch;

                    letters_len ++;
                }
            }

            if (!flag) {
                ref_pos_vec_length[ref_pos_vec_len ++] = letters_len;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static int binaryDecoding(Stream stream) {
        int type = bitFileGetBit(stream);
        int num;

        if (type == -1) {
            return -1;
        } else if (type == 1) {	//1     (2 <= num < 262146)
            if ((num = bitFileGetBitsInt(stream, 18)) == -1) {
                return -1;
            } else {
                return (num + 2);
            }
        } else {
            type = bitFileGetBit(stream);
            if (type == -1) {
                return -1;
            }else if (type == 1) {	//01    (num < 2)
                if ((num = bitFileGetBit(stream)) == -1) {
                    return -1;
                } else {
                    return num;
                }
            } else {	//00    (num >= 262146)
                if ((num = bitFileGetBitsInt(stream, 28)) == -1) {
                    return -1;
                } else {
                    return (num + 262146);
                }
            }
        }
    }

    public static void readOtherInfo(Stream stream) {
        //读取metadata
        int temp1;
        String str = "";
        int identifierLength = binaryDecoding(stream);
//        System.out.println(identifierLength);
        for (int i = 0; i < identifierLength; i ++) {
            temp1 = bitFileGetChar(stream);  //temp是metadata数据对应ASCII码
//            System.out.println(temp1);
        }

        //还原line_break_vec和line_break_len
        int temp2;
        int temp22;
        int code_len = binaryDecoding(stream);
        for (int i = 0; i < code_len; i ++) {
            temp2 = binaryDecoding(stream);
            temp22 = binaryDecoding(stream);
            for (int j = 0; j < temp22; j ++) {
                line_break_vec[line_break_len ++] = temp2;
            }
        }
//        System.out.println(line_break_len);

        //还原diff_pos_loc
        diff_pos_loc_len = binaryDecoding(stream);
//        System.out.println(diff_pos_loc_len);
        for (int i = 0; i < diff_pos_loc_len; i ++) {
            diff_pos_loc_begin[i] = binaryDecoding(stream);
            diff_pos_loc_length[i] = binaryDecoding(stream);
        }

        //还原diff_low_vec
        diff_low_vec_len = binaryDecoding(stream);
//        System.out.println(diff_low_vec_len);
        for (int i = 0; i < diff_low_vec_len; i ++) {
            diff_low_vec_begin[i] = binaryDecoding(stream);
            diff_low_vec_length[i] = binaryDecoding(stream);
        }

        //还原N_vec
        N_vec_len = binaryDecoding(stream);
//        System.out.println(N_vec_len);
        for (int i = 0; i < N_vec_len; i ++) {
            N_vec_begin[i] = binaryDecoding(stream);
            N_vec_length[i] = binaryDecoding(stream);
//            System.out.println(N_vec_begin[i]);
//            System.out.println(N_vec_length[i]);
        }

        //还原other_char
        other_char_len = binaryDecoding(stream);
//        System.out.println(other_char_len);
        if (other_char_len > 0) {
            for(int i = 0; i < other_char_len; i ++){
                other_char_vec_pos[i] = binaryDecoding(stream);
                other_char_vec_ch[i] = (char)(bitFileGetChar(stream) + 65);
            }
        }
    }

    public static void readCompressedFile(Stream stream) throws IOException {
        readOtherInfo(stream);

        int pre_pos = 0, misLen, cur_pos, length, temp_len, temp_pos;

        while ((temp_len = binaryDecoding(stream)) != -1) {
            misLen = temp_len;
            if (misLen > 0) {
                for(int i = 0; i < misLen; i ++) {
                    int num;
                    num = bitFileGetBitsInt(stream, 2);
                    if (num != -1) {
                        tar_seq_code[tar_seq_len ++] = readIndex(num);
                    } else {
                        return;
                    }
                }
            }

            int type = bitFileGetBit(stream);
            if (type == -1) {
                break;
            } else {
                if ((temp_pos = binaryDecoding(stream)) == -1) {
                    break;
                } else {
                    if (type == 1) {
                        cur_pos = pre_pos - temp_pos;
                    } else {
                        cur_pos = pre_pos + temp_pos;
                    }
                }
            }

            length = binaryDecoding(stream) + min_rep_len;

            pre_pos = cur_pos + length;
            for (int i = cur_pos, j = 0; j < length; j++, i++) {
                tar_seq_code[tar_seq_len ++] = ref_seq_code[i];
            }
        }
    }

    public static void saveDecompressedData(File tarFile) {

    }

    public static void deCompressFile(File refFile, Stream stream, File resultFile) {
        extractRefInfo(refFile);
        try {
            readCompressedFile(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveDecompressedData(resultFile);
    }

    public static void main(String[] args) {
        File refFile = new File("C:/Users/chase/OneDrive/GeneFiles/hg17_chr21.fa");
        File resultFile1 = new File("E:/result.txt");   //压缩文件
        File resultFile2 = new File("E:/result2.txt");  //解压缩文件
        Stream stream = new Stream(resultFile1, 0, 0);
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(stream.getFile()));
            bos = new BufferedOutputStream(new FileOutputStream(new File("E:/bbbResult.txt"), true));
            deCompressFile(refFile, stream, resultFile2);
            for (int m = 0; m < tar_seq_len; m ++) {
                bos.write(tar_seq_code[m]);
                bos.flush();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
