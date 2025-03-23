package com.example.epaperuploader;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;

import com.example.epaperuploader.image_processing.EPaperDisplay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

class SocketHandlerWifi extends Handler
{
    private static final int BUFF_SIZE_WIFI = 10000;
    private static int       buffInd;
    private static int       xLine;
    private static String rqMsg;

    private int   pxInd; // Pixel index in picture
    private int   stInd; // Stage index of uploading
    private int   dSize; // Size of uploaded data by LOAD command
    private int[] array; // Values of picture pixels

    private int sendSize;
    private boolean next_stage;  // 是否分两批发送: 双色或七色fasle，三色true
    private boolean load_stage = false;
    private boolean mIsCompress = false;
    private byte[] compress_data;
    private byte[] compress_data2;

    private String mEsp32IpAddress = null;
    private String mEsp32HostName = null;

    private final static String TAG = SocketHandlerWifi.class.getSimpleName();

    public SocketHandlerWifi(String ip_address, String host_hame)
    {
        super();
        mEsp32IpAddress = ip_address;
        mEsp32HostName = host_hame;
    }

    // 图像数据压缩
    byte[] compress_image_data(byte[] image_array) {
        // 输出字节流
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // GZip输出流
        GZIPOutputStream gzipOutputStream;
        try {
            // 将图像数据导入GZip
            gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gzipOutputStream.write(image_array);
            // 关闭GZip输出流
            gzipOutputStream.close();
            // 记录压缩后的字节流
            byte[] encode_array = byteArrayOutputStream.toByteArray();
            // 释放输出字节流
            byteArrayOutputStream.flush();
            byteArrayOutputStream.close();
            System.out.println("gzip compress len=" + encode_array.length);
            return encode_array;
        } catch (IOException e) {
            System.out.println("gzip compress error."+e);
            return null;
        }
    }

    // 解压缩图像数据
    byte[] decompress_image_data(byte[] encode_array) {
        try {
            // 定义临时缓冲区
            byte[] buffer = new byte[1024];
            byte[] image_data;

            // 输出字节流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            // 输入字节流
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(encode_array);
            // GZip输入流
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            // 批量写入输出字节流
            int n = 0;
            while ((n = gzipInputStream.read(buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, n);
            }

            // 打印解压后的数据
            System.out.println("gzip decompress len=" + outputStream.toByteArray().length);

            image_data = outputStream.toByteArray();
            // 释放流资源
            outputStream.close();
            byteArrayInputStream.close();
            gzipInputStream.close();
            return image_data;
        } catch (IOException e) {
            System.out.println("gzip decompress error."+e);
            return null;
        }
    }

    private byte[] get_compress_data(int epdInd, int c) {
        byte[] byteArr;
        int i;
        int index = 0;
        int pixelId = 0;

        if(epdInd == 25 || epdInd ==37 || epdInd == 43 || epdInd == 47) {
            byteArr = new byte[array.length/2];
            while (pixelId < array.length) {

                int v = 0;
                // 从发送数据的角度看，每个字节高4bit和低4bit可以分别存储一个像素颜色值(0~7)
                // 所以4个pixel占用2个存储单元
                for(i = 0; i < 16; i += 4)
                {
                    if (pixelId < array.length) v |= (array[pixelId] << i);
                    pixelId++;
                }

                byteArr[index++] = (byte)(v     );
                byteArr[index++] = (byte)(v >> 8);
            }

        } else {
            byteArr = new byte[array.length/8];
            while (pixelId < array.length)
            {
                int v = 0;

                for (i = 0; i < 8; i++)
                {
                    if ((pixelId < array.length) && (array[pixelId] != c)) v |= (128 >> i);
                    pixelId++;
                }

                byteArr[index++] = (byte)v;
            }
        }

        return byteArr;
    }

    // Converts picture pixels into selected pixel format
    // and sends EPDx command
    //-----------------------------------------------------
    public boolean init_image(Bitmap bmp, boolean is_compress)
    {
        int w = bmp.getWidth(); // Picture with
        int h = bmp.getHeight();// Picture height
        int epdInd = EPaperDisplay.epdInd;
        array = new int[w*h]; // Array of pixels
        int i = 0;            // Index of pixel in the array of pixels

        Log.e(TAG, "init_image start");

        // Loading pixels into array
        //-------------------------------------------------
        // 从处理过的图像里提取像素信息
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++, i++)
                if(epdInd == 25 || epdInd ==37 || epdInd == 43) {
                    // 7-color: value 0~7
                    array[i] = getVal_7color(bmp.getPixel(x, y));
                }
                else if (epdInd == 47) {
                    // New 7-color: value 0~7
                    array[i] = getVal_new7color(bmp.getPixel(x, y));
                } else {
                    // B/W/R: value 0~3
                    array[i] = getVal(bmp.getPixel(x, y));
                }

        byte[] byteArr;
//        byte[] decompressed_data;

        System.out.println("image array size: " + array.length);

        mIsCompress = is_compress;
        // wifi模式不压缩
        mIsCompress = false;
        if (mIsCompress)
        {
            // re-init
            compress_data = null;
            compress_data2 = null;

            // 数据压缩
            int c = 0;
            // 先根据不同屏幕，获取编码数据
            byteArr = get_compress_data(epdInd, c);
            // 再对编码结果进行压缩
            compress_data = compress_image_data(byteArr);
//            decompressed_data = decompress_image_data(compress_data);

            // 如果是三色屏的话，还需要再准备第二批代表红色的数据
            if ((epdInd == 3) || (epdInd == 39) || (epdInd==0)||(epdInd==6)||(epdInd==7)||(epdInd==9)||(epdInd==12)||
                    (epdInd==16)||(epdInd==19)||(epdInd==22)||(epdInd==26)||(epdInd==27)||(epdInd==28)){
                // do nothing
            } else if ((epdInd>15 && epdInd < 22) || (epdInd == 45)) {
                // do nothing
            } else if (epdInd == 25 || epdInd == 37 || epdInd == 43 || epdInd == 47) {
                // do nothing
            } else {
                c = 3;
                byteArr = get_compress_data(epdInd, c);
                compress_data2 = compress_image_data(byteArr);
//                decompressed_data = decompress_image_data(compress_data2);
            }
        }

        String send_info = "像素:" + array.length + ",发送字节:0";
        MainActivity.updateSendInfo(send_info);
        MainActivity.updateProgress(0);

        sendSize = 0; // nof send size
        next_stage = false; // if has next stage

        pxInd = 0;
        xLine = 0;  //2.13inch
        stInd = 0;
        dSize = 0;

        Log.e(TAG, "init_image end");

        return true;
    }

    void send_data(int k1, int k2, int total_data_size, int loaded_size, String uri, String post_data) throws IOException {
        int progress = (k1 + k2*loaded_size/total_data_size);
        String x = "" + progress;

        if (x.length() > 5) x = x.substring(0, 5);

        MainActivity.updateProgress(progress);

        // Size of uploaded data
        //-------------------------------------------------
        String send_info = "像素:" + array.length + ",发送字节:" + loaded_size/2;

        MainActivity.updateSendInfo(send_info);

        httpWrite(uri, post_data);
    }

    // The function is executed after every "Ok!" response
    // obtained from esp32, which means a previous command
    // is complete and esp32 is ready to get the new one.
    //-----------------------------------------------------
    public boolean handle_uploading_12in48b(String imgBWStr, String imgRStr) throws IOException {
        StringBuilder imgBWStr_m1s1m2s2 = new StringBuilder();
        StringBuilder imgRStr_m1s1m2s2 = new StringBuilder();

        int i = 0;
        int start_index = 0;
        int end_index = 0;
        // 打包第一个区域
        for (i = 0; i < 492; i++) {
            start_index = i * 326;
            end_index = start_index + 162;
            imgBWStr_m1s1m2s2.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1m2s2.append(imgRStr.substring(start_index, end_index));
        }
        // 打包第二个区域
        for (i = 0; i < 492; i++) {
            start_index = i * 326 + 162;
            end_index = start_index + 164;
            imgBWStr_m1s1m2s2.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1m2s2.append(imgRStr.substring(start_index, end_index));
        }
        // 打包第三个区域
        for (i = 492; i < 984; i++) {
            start_index = i * 326;
            end_index = start_index + 162;
            imgBWStr_m1s1m2s2.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1m2s2.append(imgRStr.substring(start_index, end_index));
        }
        // 打包第四个区域
        for (i = 492; i < 984; i++) {
            start_index = i * 326 + 162;
            end_index = start_index + 164;
            imgBWStr_m1s1m2s2.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1m2s2.append(imgRStr.substring(start_index, end_index));
        }

        Log.e(TAG, "package for 12in48b");

        int bw_data_size = imgBWStr_m1s1m2s2.length();
        int r_data_size = imgRStr_m1s1m2s2.length();

        Log.e(TAG, "bw_data_size=" + bw_data_size + ", r_data_size=" + r_data_size);

        int batch_size = 30000;
        String uri = "";
        String post_data = "";
        int k1 = 0;
        int k2 = 50;
        start_index = 0;
        // 将第一阶段数据进行分批发送
        while (start_index < bw_data_size) {
            uri = "LOADA";
            end_index = Math.min((start_index + batch_size), bw_data_size);
            post_data = imgBWStr_m1s1m2s2.substring(start_index, end_index);
            Log.e(TAG, "LOADA: " + start_index + "->" + end_index);
            send_data(k1, k2, bw_data_size, end_index,uri, post_data);

            start_index += batch_size;
        }

        Log.e(TAG, "LoadA finish");

        k1 = 50;
        k2 = 50;
        start_index = 0;
        // 再将第二阶段数据也分批发送
        while (start_index < r_data_size) {
            uri = "LOADB";
            end_index = Math.min((start_index + batch_size), r_data_size);
            post_data = imgRStr_m1s1m2s2.substring(start_index, end_index);
            Log.e(TAG, "LOADB: " + start_index + "->" + end_index);
            send_data(k1, k2, r_data_size, end_index,uri, post_data);

            start_index += batch_size;
        }
        Log.e(TAG, "LoadB finish");

        // 最后刷新显示
        uri = "SHOW";
        post_data = "";
        httpWrite(uri, post_data);

        Log.e(TAG, "SHOW");

        return false;
    }

    // The function is executed after every "Ok!" response
    // obtained from esp32, which means a previous command
    // is complete and esp32 is ready to get the new one.
    //-----------------------------------------------------
    public boolean handle_uploading_9in69b(String imgBWStr, String imgRStr) throws IOException {
        StringBuilder imgBWStr_m1s1 = new StringBuilder();
        StringBuilder imgRStr_m1s1 = new StringBuilder();

        int i = 0;
        int start_index = 0;
        int end_index = 0;

        int epd_width = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
        int epd_height = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;
        int bytes_per_line = epd_width * 2 / 8;
        // 打包第一个区域
        for (i = 0; i < epd_height; i++) {
            start_index = i * bytes_per_line;
            end_index = start_index + bytes_per_line / 2;
            imgBWStr_m1s1.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1.append(imgRStr.substring(start_index, end_index));
        }
        // 打包第二个区域
        for (i = 0; i < epd_height; i++) {
            start_index = i * bytes_per_line + bytes_per_line / 2;
            end_index = start_index + bytes_per_line / 2;
            imgBWStr_m1s1.append(imgBWStr.substring(start_index, end_index));
            imgRStr_m1s1.append(imgRStr.substring(start_index, end_index));
        }

        Log.e(TAG, "package for 9in69b");

        int bw_data_size = imgBWStr_m1s1.length();
        int r_data_size = imgRStr_m1s1.length();

        Log.e(TAG, "bw_data_size=" + bw_data_size + ", r_data_size=" + r_data_size);

        int batch_size = 30000;
        String uri = "";
        String post_data = "";
        int k1 = 0;
        int k2 = 50;
        start_index = 0;
        // 将第一阶段数据进行分批发送
        while (start_index < bw_data_size) {
            uri = "LOADA";
            end_index = Math.min((start_index + batch_size), bw_data_size);
            post_data = imgBWStr_m1s1.substring(start_index, end_index);
            Log.e(TAG, "LOADA: " + start_index + "->" + end_index);
            send_data(k1, k2, bw_data_size, end_index,uri, post_data);

            start_index += batch_size;
        }

        Log.e(TAG, "LoadA finish");

        k1 = 50;
        k2 = 50;
        start_index = 0;
        // 再将第二阶段数据也分批发送
        while (start_index < r_data_size) {
            uri = "LOADB";
            end_index = Math.min((start_index + batch_size), r_data_size);
            post_data = imgRStr_m1s1.substring(start_index, end_index);
            Log.e(TAG, "LOADB: " + start_index + "->" + end_index);
            send_data(k1, k2, r_data_size, end_index,uri, post_data);

            start_index += batch_size;
        }
        Log.e(TAG, "LoadB finish");

        // 最后刷新显示
        uri = "SHOW";
        post_data = "";
        httpWrite(uri, post_data);

        Log.e(TAG, "SHOW");

        return false;
    }

    public boolean handleUploadingStage() throws IOException {
        StringBuilder imgBWStr = new StringBuilder();
        StringBuilder imgRStr = new StringBuilder();

        Log.e(TAG, "handleUploadingStage start");

        boolean redIsEnabled = (EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].index & 1) != 0;
        if (EPaperDisplay.epdInd == 45) {
            redIsEnabled = false;
        } else if (EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].index > 5) {
            redIsEnabled = false;
        }
        // 根据像素信息，转换成待发送的字符串
        while ((pxInd < array.length))
        {
            int v_bw = 0;
            int v_r = 0;
            int c_bw = 0;
            int c_r = 3;

            int epdInd = EPaperDisplay.epdInd;

            // 根据像素值占用bit划分
            if ((epdInd>15 && epdInd < 22) || (epdInd == 45)) {
                // 像素值0~3,占用2个bit
                for (int i = 0; i < 8; i += 2) {
                    if (pxInd < array.length) v_bw |= (array[pxInd] << (6-i));
                    pxInd++;
                }
                imgBWStr.append(byteToStr(v_bw));
            } else if (epdInd == 25 || epdInd == 37 || epdInd == 43 || epdInd == 47) {
                // 像素值0~6,占用3个bit,实际占用4bit
                for (int i = 0; i < 8; i += 4) {
                    if (pxInd < array.length) v_bw |= (array[pxInd] << (4-i));
                    pxInd++;
                }
                imgBWStr.append(byteToStr(v_bw));
            } else {
                // 像素值0~1,占用1个bit
                // 这里的编码采用顺序方式，即bit_[x]代表像素[x]
                // 先用8->1的方式，将连续的8个像素压缩到一个数据字节里
                for (int i = 0; i < 8; i++)
                {
                    // 0x80 >> i:  0~7-> 0x80, 0x40, 0x20, 0x10, 0x8, 0x4, 0x2, 0
                    if ((pxInd < array.length) && (array[pxInd] != c_bw)) v_bw |= (128 >> i);
                    if (redIsEnabled) {
                        if ((pxInd < array.length) && (array[pxInd] != c_r)) v_r |= (128 >> i);
                    }

                    pxInd++;
                }

                // 然后再将字节转换成字符串，压缩比为1->2
                // 例如0xfe -> 'f' + 'e'
                imgBWStr.append(byteToStr(v_bw));
                if (redIsEnabled) {
                    imgRStr.append(byteToStr(v_r));
                }
            }
        }

        Log.e(TAG, "-> imgBWStr:" + imgBWStr.length() + ", imgRStr:" + imgRStr.length());

        Log.e(TAG, String.format("%s", imgBWStr.toString().substring(0, 10)));

        // 对12.48屏进行特殊处理
        if (EPaperDisplay.epdInd == 44) {
            return handle_uploading_12in48b(imgBWStr.toString(), imgRStr.toString());
        } else if (EPaperDisplay.epdInd == 46) {
            return handle_uploading_9in69b(imgBWStr.toString(), imgRStr.toString());
        }

        Log.e(TAG, "array size=" + array.length);
        Log.e(TAG, "imgStr len=" + imgBWStr.length());

        int start_index = 0;
        int end_index = 0;
        int bw_data_size = imgBWStr.length();
        int r_data_size = imgRStr.length();

        String uri = "";
        String post_data = "";

        int k1, k2;
        if (redIsEnabled) {
            k1 = 0;
            k2 = 50;
        } else {
            k1 = 0;
            k2 = 100;
        }

        // 一次性发送的话，如果数据量太大，反而效率有所降低
        int batch_size = 30000;

        start_index = 0;
        while (start_index < bw_data_size) {
            uri = "LOADA";
            end_index = Math.min((start_index + batch_size), bw_data_size);
            post_data = imgBWStr.substring(start_index, end_index);
            send_data(k1, k2, bw_data_size, end_index,uri, post_data);

            start_index += batch_size;
        }

        // 如果还有Red数据
        if (redIsEnabled) {
            k1 = 50;
            k2 = 50;

            // 再批量发送
            uri = "LOADB";
            start_index = 0;
            while (start_index < r_data_size) {
                end_index = Math.min((start_index + batch_size), r_data_size);
                post_data = imgRStr.substring(start_index, end_index);
                send_data(k1, k2, r_data_size, end_index, uri, post_data);
                start_index += batch_size;
            }
        }

        // 最后刷新显示
        uri = "SHOW";
        post_data = "";
        httpWrite(uri, post_data);

        Log.e(TAG, "handleUploadingStage end");

        return false;
    }

    // Returns the index of color in palette
    //-----------------------------------------------------
    public int getVal(int color)
    {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

//  黑色：对应00b
//  白色：对应01b
//  黄色：对应10b
//  红色：对应11b
//  @ColorInt public static final int BLACK       = 0xFF000000;
//  @ColorInt public static final int WHITE       = 0xFFFFFFFF;
//  @ColorInt public static final int RED         = 0xFFFF0000;
//  @ColorInt public static final int YELLOW      = 0xFFFFFF00;
        if((r == 0xFF) && (b == 0xFF)) return 1;
        if((r == 0x7F) && (b == 0x7F)) return 2;
        if((r == 0xFF) && (g == 0xFF)) return 2;
        if((r == 0xFF) && (b == 0x00)) return 3;

        return 0;
    }

    // Returns the index of color in palette just for 5.65f e-Paper
    //-----------------------------------------------------
    public int getVal_7color(int color)
    {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        if((r == 0x00) && (g == 0x00) && (b == 0x00)) return 0;
        if((r == 0xFF) && (g == 0xFF) && (b == 0xFF)) return 1;
        if((r == 0x00) && (g == 0xFF) && (b == 0x00)) return 2;
        if((r == 0x00) && (g == 0x00) && (b == 0xFF)) return 3;
        if((r == 0xFF) && (g == 0x00) && (b == 0x00)) return 4;
        if((r == 0xFF) && (g == 0xFF) && (b == 0x00)) return 5;
        if((r == 0xFF) && (g == 0x80) && (b == 0x00)) return 6;

        return 7;
    }

    public int getVal_new7color(int color)
    {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        //#define EPD_7IN3E_BLACK   0x0   /// 000   0xFF000000
        //#define EPD_7IN3E_WHITE   0x1   /// 001   0xFFFFFFFF
        //#define EPD_7IN3E_YELLOW  0x2   /// 010   0xFFFFFF00
        //#define EPD_7IN3E_RED     0x3   /// 011   0xFFFF0000
        //#define EPD_7IN3E_ORANGE  0x4   /// 100
        //#define EPD_7IN3E_BLUE    0x5   /// 101   0xFF0000FF
        //#define EPD_7IN3E_GREEN   0x6   /// 110   0xFF00FF00
        if((r == 0x00) && (g == 0x00) && (b == 0x00)) return 0;
        if((r == 0xFF) && (g == 0xFF) && (b == 0xFF)) return 1;
        if((r == 0xFF) && (g == 0xFF) && (b == 0x00)) return 2;
        if((r == 0xFF) && (g == 0x00) && (b == 0x00)) return 3;
        if((r == 0xFF) && (g == 0x80) && (b == 0x00)) return 4;
        if((r == 0x00) && (g == 0x00) && (b == 0xFF)) return 5;
        if((r == 0x00) && (g == 0xFF) && (b == 0x00)) return 6;

        return 7;
    }

    // 高4位 + 低4位
    private String byteToStr(int v) {
        return String.valueOf((char)(((v >> 4) & 0xF) + 97)) + String.valueOf((char)((v & 0xF) + 97));
    }

    public void httpWrite(String uri, String post_data) throws IOException {
        Log.e(TAG, "ip_address: " + mEsp32IpAddress + ", uri: " + uri);

        URL url = new URL("http://" + mEsp32IpAddress + "/" + uri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        OutputStream os = conn.getOutputStream();

        byte[] input = post_data.getBytes("utf-8");
        os.write(input, 0, input.length);

        try {
            InputStream is = conn.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "get input instream failed.");
        }

        os.close();
        conn.disconnect();
    }
}
