package cn.chase;

import java.io.*;

import static cn.chase.BitFile.*;

public class DeCompress {
    private static int min_rep_len = 11;
    private static int vec_size = 1 << 20;
    private static int MAX_CHAR_NUM = 1 << 28;

    private static String identifier;
    private static int diff_pos_loc_len;
    private static int diff_low_vec_len;
    private static int N_vec_len;
    private static int other_char_len;

    private static char[] ref_seq_code = new char[MAX_CHAR_NUM];
    private static char[] tar_seq_code = new char[MAX_CHAR_NUM];
    private static int ref_seq_len = 0;
    private static int tar_seq_len = 0;
    private static int ref_low_vec_len = 0;
    private static int line_break_len = 0;
    private static int diff_low_loc_len = 0;
    private static int tar_low_vec_len = 0;
    private static int[] ref_low_vec_begin = new int[vec_size];
    private static int[] ref_low_vec_length = new int[vec_size];
    private static int[] line_break_vec = new int[1 << 25];
    private static int[] diff_pos_loc_begin = new int[vec_size];
    private static int[] diff_pos_loc_length = new int[vec_size];
    private static int[] diff_low_vec_begin = new int[vec_size];
    private static int[] diff_low_vec_length = new int[vec_size];
    private static int[] N_vec_begin = new int[vec_size];
    private static int[] N_vec_length = new int[vec_size];
    private static int[] other_char_vec_pos = new int[vec_size];
    private static char[] other_char_vec_ch = new char[vec_size];
    private static int[] tar_low_vec_begin = new int[vec_size];
    private static int[] tar_low_vec_length = new int[vec_size];
    private static int[] diff_low_loc = new int[vec_size];
    public static BufferedInputStream bis = null;
    public static BufferedOutputStream bos = null;

    public static char readIndex(int num) {
        switch (num) {
            case 0: return 'A';
            case 1: return 'C';
            case 2: return 'G';
            case 3: return 'T';
            default : return 'Y';
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
                            ref_low_vec_begin[ref_low_vec_len] = letters_len;
                            letters_len = 0;
                        }
                    } else {
                        if (!flag) {
                            flag = true;
                            ref_low_vec_length[ref_low_vec_len++] = letters_len;
                            letters_len = 0;
                        }
                    }

                    if (ch == 'A' || ch == 'C' || ch == 'G' || ch == 'T')
                        ref_seq_code[ref_seq_len ++] = ch;

                    letters_len ++;
                }
            }

            if (!flag) {
                ref_low_vec_length[ref_low_vec_len++] = letters_len;
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

    public static void readTarPosVec() {
        //由diff_pos_loc得到diff_low_loc
        for (int i = 0; i < diff_pos_loc_len; i ++) {
            int start = diff_pos_loc_begin[i];
            int num = diff_pos_loc_length[i];
            diff_low_loc[diff_low_loc_len ++] = (start ++);
            for (int j = 1; j < num; j ++) {
                diff_low_loc[diff_low_loc_len ++] = (start ++);
            }
        }

        //由diff_low_loc和diff_low_vec得到tar_low_vec
        int num = 0;
        tar_low_vec_begin[tar_low_vec_len] = ref_low_vec_begin[diff_low_loc[0]];
        tar_low_vec_length[tar_low_vec_len ++] = ref_low_vec_length[diff_low_loc[0]];
        for (int i = 1; i < diff_low_loc_len; i ++) {
            if (diff_low_loc[i] != 0) {
                tar_low_vec_begin[tar_low_vec_len] = ref_low_vec_begin[diff_low_loc[i]];
                tar_low_vec_length[tar_low_vec_len ++] = ref_low_vec_length[diff_low_loc[i]];
            } else {
                tar_low_vec_begin[tar_low_vec_len] = diff_low_vec_begin[num];
                tar_low_vec_length[tar_low_vec_len ++] = diff_low_vec_length[num ++];
            }
        }
    }

    public static void readOtherInfo(Stream stream) {
        //读取identifier
        int identifierLength = binaryDecoding(stream);
        char[] metadata = new char[identifierLength];
        for (int i = 0; i < identifierLength; i ++) {
            metadata[i] = (char)bitFileGetChar(stream);  //temp是metadata数据对应ASCII码
        }
        identifier = String.valueOf(metadata);

        //还原line_break_vec和line_break_len
        int temp;
        int temp_len;
        int code_len = binaryDecoding(stream);
        for (int i = 0; i < code_len; i ++) {
            temp = binaryDecoding(stream);
            temp_len = binaryDecoding(stream);
            for (int j = 0; j < temp_len; j ++) {
                line_break_vec[line_break_len ++] = temp;
            }
        }

        //还原diff_pos_loc
        diff_pos_loc_len = binaryDecoding(stream);
//        System.out.println(diff_pos_loc_len);
        for (int i = 0; i < diff_pos_loc_len; i ++) {
            diff_pos_loc_begin[i] = binaryDecoding(stream);
            diff_pos_loc_length[i] = binaryDecoding(stream);
        }

        //还原diff_low_vec
        diff_low_vec_len = binaryDecoding(stream);
        for (int i = 0; i < diff_low_vec_len; i ++) {
            diff_low_vec_begin[i] = binaryDecoding(stream);
            diff_low_vec_length[i] = binaryDecoding(stream);
        }

        //还原tar_low_vec
        readTarPosVec();

        //还原N_vec
        N_vec_len = binaryDecoding(stream);
        for (int i = 0; i < N_vec_len; i ++) {
            N_vec_begin[i] = binaryDecoding(stream);
            N_vec_length[i] = binaryDecoding(stream);
        }

        //还原other_char
        other_char_len = binaryDecoding(stream);
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
                    int num = bitFileGetBitsInt(stream, 2);
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
        char[] temp_seq = new char[MAX_CHAR_NUM];

        //写入头文件
        try {
            bos.write(identifier.getBytes());
            bos.write('\n');
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        temp_seq = tar_seq_code;

        //写入other_character
        int tt = 0, n = 0;
        for (int i = 1; i < other_char_len; i++) {
            other_char_vec_pos[i] += other_char_vec_pos[i - 1];
        }
        for (int m = 0; m < other_char_len; m++) {
            while (tt < other_char_vec_pos[m] && tt < tar_seq_len) {
                tar_seq_code[n ++] = temp_seq[tt++];
            }
            tar_seq_code[n ++] = other_char_vec_ch[m];
        }
        while (tt < tar_seq_len) {
            tar_seq_code[n ++] = temp_seq[tt++];
        }
        tar_seq_len = n;

        //将N字符放入目标数组
        int str_len = 0, r = 0;
        char[] str = new char[MAX_CHAR_NUM];
        for (int i = 0; i < N_vec_len; i ++) {
            for (int j = 0; j < N_vec_begin[i]; j ++) {
                str[str_len ++] = tar_seq_code[r ++];
            }
            for (int j = 0; j < N_vec_length[i]; j ++) {
                str[str_len ++] = 'N';
            }
        }
        while (r < tar_seq_len) {
            str[str_len ++] = tar_seq_code[r ++];
        }

        //恢复小写，包括A C G T N X
        int k = 0;
        for (int i = 0; i < tar_low_vec_len; i ++) {
            k += tar_low_vec_begin[i];
            int temp = tar_low_vec_length[i];
            for (int j = 0; j < temp; j ++) {
                str[k] = Character.toLowerCase(str[k]);
                k ++;
            }
        }

        //恢复换行
        int k_lb = 0;
        int temp_seq_len = 0;
        for (int i = 1; i < line_break_len; i ++) {
            line_break_vec[i] += line_break_vec[i - 1];
        }
        for (int i = 0; i < str_len; i ++) {
            if(i == line_break_vec[k_lb]) {
                temp_seq[temp_seq_len ++] = '\n';
                k_lb++;
            }
            temp_seq[temp_seq_len ++] = str[i];
        }

        String s = String.valueOf(temp_seq, 0, temp_seq_len);
        try {
            bos.write(s.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        File refFile = new File("C:/Users/chase/OneDrive/GeneFiles/hg17_chr1.fa");
        File compressedFile = new File("E:/result.txt");   //压缩文件
        File resultFile = new File("E:/result2.txt");  //解压缩文件
        Stream stream = new Stream(compressedFile, 0, 0);
        try {
            bis = new BufferedInputStream(new FileInputStream(stream.getFile()));
            bos = new BufferedOutputStream(new FileOutputStream(resultFile, true));

            deCompressFile(refFile, stream, resultFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
