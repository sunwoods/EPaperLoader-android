package com.example.epaperuploader;

import static com.example.epaperuploader.MainActivity.busy_pin;
import static com.example.epaperuploader.MainActivity.cs_pin;
import static com.example.epaperuploader.MainActivity.dc_pin;
import static com.example.epaperuploader.MainActivity.din_pin;
import static com.example.epaperuploader.MainActivity.rst_pin;
import static com.example.epaperuploader.MainActivity.sck_pin;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;

import com.example.epaperuploader.image_processing.EPaperDisplay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

//---------------------------------------------------------
//  Socket Handler
//---------------------------------------------------------
class SocketHandlerBt extends Handler
{

    // Uploaded data buffer
    //---------------------------------------------------------
    private static final int BUFF_SIZE = 512;
    private static final int BUFF_SIZE_WIFI = 496;
    private static byte[]    buffArr = new byte[BUFF_SIZE];
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

    private final static String TAG = SocketHandlerBt.class.getSimpleName();

    public SocketHandlerBt()
    {
        super();
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
            // 打印压缩后的数据
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
            byteArr = new byte[array.length / 2];
            while (pixelId < array.length) {

                int v = 0;
                // 从发送数据的角度看，每个字节高4bit和低4bit可以分别存储一个像素颜色值(0~7)
                // 所以4个pixel占用2个存储单元
                for (i = 0; i < 16; i += 4) {
                    if (pixelId < array.length) v |= (array[pixelId] << i);
                    pixelId++;
                }

                byteArr[index++] = (byte) (v);
                byteArr[index++] = (byte) (v >> 8);
            }
        } else if(epdInd == 45) {
            byteArr = new byte[array.length/4];
            while (pixelId < array.length)
            {
                int v = 0;

                for (i = 0; i < 8; i += 2)
                {
                    if (pixelId < array.length) v |= (array[pixelId] << (6-i));
                    pixelId++;
                }

                byteArr[index++] = (byte)v;
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
    public boolean init_image(Bitmap bmp, boolean is_compress, int[] saved_image_data)
    {
        int w, h;
        int epdInd = EPaperDisplay.epdInd;

        // Loading pixels into array
        //-------------------------------------------------
        System.out.println("=========init_image is calling ==========");

        if (saved_image_data == null) {
            w = bmp.getWidth();
            h = bmp.getHeight();
        } else {
            w = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
            h = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;
        }

        array = new int[w*h]; // Array of pixels
        int i = 0;            // Index of pixel in the array of pixels

        if (saved_image_data == null) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++, i++)
                    if(epdInd == 25 || epdInd ==37 || epdInd == 43 || epdInd == 47)
                        array[i] = getVal_7color(bmp.getPixel(x, y));
                    else
                        array[i] = getVal(bmp.getPixel(x, y));
        } else {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++, i++)
                    if(epdInd == 25 || epdInd ==37 || epdInd == 43 || epdInd == 47)
                        array[i] = getVal_7color(saved_image_data[i]);
                    else
                        array[i] = getVal(saved_image_data[i]);
        }

        byte[] byteArr;
//        byte[] decompressed_data;

        System.out.println("image array size: " + array.length);

        mIsCompress = is_compress;

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
            } else if (epdInd>15 && epdInd < 22) {
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

        pxInd = 0; // pixel id
        xLine = 0;  //2.13inch
        stInd = 0;  // step id
        dSize = 0;  // nof data size

        sendSize = 0; // nof send size
        load_stage = false;
        next_stage = false; // if has next stage

//        buffInd = 9;                             // Size of command in bytes
        buffInd = 13;                             // Size of command in bytes
        buffArr[0] = (byte)'I';                  // Name of command (Initialize)
        buffArr[1] = (byte)EPaperDisplay.epdInd; // Index of display

        // 数据是否压缩
        if (mIsCompress) {
            buffArr[2] = (byte)1;
        } else {
            buffArr[2] = (byte)0;
        }
        // SPI管脚信息
        buffArr[3] = (byte)din_pin;
        buffArr[4] = (byte)sck_pin;
        buffArr[5] = (byte)cs_pin;
        buffArr[6] = (byte)dc_pin;
        buffArr[7] = (byte)rst_pin;
        buffArr[8] = (byte)busy_pin;

        //图像尺寸
        buffArr[9] = (byte)(EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width >> 8);
        buffArr[10] = (byte)EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
        buffArr[11] = (byte)(EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height >> 8);
        buffArr[12] = (byte)EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;

        return u_send(false);
//        return true;
    }

    // The function is executed after every "Ok!" response
    // obtained from esp32, which means a previous command
    // is complete and esp32 is ready to get the new one.
    //-----------------------------------------------------
    // 这个接口每调用一次，就批量发送一批数据，循环往复直到发送完毕
    public boolean handleUploadingStage()
    {
        int epdInd = EPaperDisplay.epdInd;

        // 2.13 e-Paper display
        if ((epdInd == 3) || (epdInd == 39))
        {
            if(stInd == 0) return u_line(0, 0, 100);
            //-------------------------------------------------
            if(stInd == 1) return u_show();
        }

        // 2.13 b V4
        else if ((epdInd == 40))
        {
            if(stInd == 0) return u_line(0, 0, 50);
            if(stInd == 1) return u_next();
            if(stInd == 2) return u_line(3, 50, 50);
            if(stInd == 3) return u_show();
        }

        // White-black e-Paper displays
        //-------------------------------------------------
        else if ((epdInd==0)||(epdInd==6)||(epdInd==7)||(epdInd==9)||(epdInd==12)||
                (epdInd==16)||(epdInd==19)||(epdInd==22)||(epdInd==26)||(epdInd==27)||(epdInd==28))
        {
            if(stInd == 0) return u_data(0,0,100);
            if(stInd == 1) return u_show();
        }

        // 7.5 colored e-Paper displays
        //-------------------------------------------------
        else if ((epdInd>15 && epdInd < 22) || (epdInd == 45))
        {
            if(stInd == 0) return u_data(-1,0,100);
            if(stInd == 1) return u_show();
        }

        // 5.65f colored e-Paper displays
        //-------------------------------------------------
        else if (epdInd == 25 || epdInd == 37 || epdInd == 43 || epdInd == 47)
        {
            if(stInd == 0) return u_data(-2,0,100);
            if(stInd == 1) return u_show();
        }

        // Other colored e-Paper displays
        //-------------------------------------------------
        else
        {
            if(stInd == 0)return u_data((epdInd == 1)? -1 : 0,0,50);
            if(stInd == 1)return u_next();
            if(stInd == 2)return u_data(3,50,50);
            if(stInd == 3)return u_show();
        }

        return true;
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

    // Sends command cmd
    //-----------------------------------------------------
    private boolean u_send(boolean next)
    {
        byte[] sendArr = new byte[buffInd];
        System.arraycopy(buffArr, 0, sendArr, 0, buffInd);
        MainActivity.sendData(sendArr);

        if(next) stInd++; // Go to next stage if it is needed
        return true;      // Command is sent successful
    }

    // Next stage command
    //-----------------------------------------------------
    private boolean u_next()
    {
        Log.e(TAG, "u_next: " + stInd);
        buffInd = 1;           // Size of command in bytes
        buffArr[0] = (byte)'N';// Name of command (Next)

        pxInd = 0;
        load_stage = false;
        next_stage = true;
        return u_send(true);
    }

    // The finishing command
    //-----------------------------------------------------
    private boolean u_show()
    {
        buffInd = 1;           // Size of command in bytes
        buffArr[0] = (byte)'S';// Name of command (Show picture)

        // Return false if the SHOW command is not sent
        //-------------------------------------------------
        if (!u_send(true)) return false;

        // Otherwise exit the uploading activity.
        //-------------------------------------------------
        return true;
    }

    // Sends pixels of picture and shows uploading progress
    //-----------------------------------------------------
    private boolean u_load(int k1, int k2)
    {
        // Uploading progress message
        //-------------------------------------------------
        // 计算出已发送的数据占全部数据的百分比
        // pxInd=1830, x=0+100*1830/122*250=6%
        int progress = 0;
        if (mIsCompress) {
            // 两批压缩的数据长度可能会不一样，但是进度暂时按各50%统计
            if (!next_stage) {
                progress = (k1 + k2*pxInd/compress_data.length);
            } else {
                progress = (k1 + k2*pxInd/compress_data2.length);
            }
        } else {
            progress = (k1 + k2*pxInd/array.length);
        }
        String x = "" + progress;

        if (x.length() > 5) x = x.substring(0, 5);
        MainActivity.updateProgress(progress);

        // Size of uploaded data
        //-------------------------------------------------
        dSize += buffInd;
        sendSize += buffInd;

        String send_info;
        if (mIsCompress) {
            if (next_stage) {
                send_info = "像素:" + array.length + ",发送字节:" + sendSize + "/" + (compress_data.length + compress_data2.length);
            } else {
                send_info = "像素:" + array.length + ",发送字节:" + sendSize + "/" + compress_data.length;
            }
        } else {
            send_info = "像素:" + array.length + ",发送字节:" + sendSize;
        }
        MainActivity.updateSendInfo(send_info);

        if (mIsCompress) {
            if (!next_stage) {
                return u_send(pxInd >= compress_data.length);
            } else {
                return u_send(pxInd >= compress_data2.length);
            }
        } else {
            return u_send(pxInd >= array.length);
        }
    }

    // Pixel format converting
    //-----------------------------------------------------
    private boolean u_data(int c, int k1, int k2)
    {
        if (!load_stage) {
            buffArr[0] = (byte)'L';
            buffInd = 1;
            load_stage = true;
//                System.out.println("u_data: LOAD");
            return u_send(false);
        }

        buffInd = 0;

        if(c == -1)
        {
            if (mIsCompress) {
                for (; (pxInd < compress_data.length) && (buffInd < BUFF_SIZE); pxInd++) {
//                        buffArr[buffInd++] = compress_data[pxInd++];
                    buffArr[buffInd++] = compress_data[pxInd];
                }
            } else {
                while ((pxInd < array.length) && (buffInd + 1 < BUFF_SIZE)) {
                    int v = 0;

                    for (int i = 0; i < 16; i += 2) {
                        if (pxInd < array.length) v |= (array[pxInd] << i);
                        pxInd++;
                    }

                    buffArr[buffInd++] = (byte) (v);
                    buffArr[buffInd++] = (byte) (v >> 8);
                }
            }
        }
        else if(c == -2)
        {
            if (mIsCompress) {
                for (; (pxInd < compress_data.length) && (buffInd < BUFF_SIZE); pxInd++) {
//                        buffArr[buffInd++] = compress_data[pxInd++];
                    buffArr[buffInd++] = compress_data[pxInd];
                }
            } else {
                // 遍历pixel数据，按照缓冲区大小来进行批量填充
                while ((pxInd < array.length) && (buffInd + 1 < BUFF_SIZE))
                {
                    int v = 0;

                    // 从发送数据的角度看，每个字节高4bit和低4bit可以分别存储一个像素颜色值(0~7)
                    // 所以4个pixel占用2个存储单元
                    for(int i = 0; i < 16; i += 4)
                    {
                        if (pxInd < array.length) v |= (array[pxInd] << i);
                        pxInd++;
                    }

                    buffArr[buffInd++] = (byte)(v     );
                    buffArr[buffInd++] = (byte)(v >> 8);
                }
            }

        }
        else
        {
            if (mIsCompress) {
                if (!next_stage) {
                    for (; (pxInd < compress_data.length) && (buffInd < BUFF_SIZE); pxInd++) {
                        buffArr[buffInd++] = compress_data[pxInd];
                    }
                } else {
                    // c值为3，代表读取红色像素数据
                    for (; (pxInd < compress_data2.length) && (buffInd < BUFF_SIZE); pxInd++)
                    {
                        buffArr[buffInd++] = compress_data2[pxInd];
                    }
                }
            } else {
                while ((pxInd < array.length) && (buffInd < BUFF_SIZE))
                {
                    int v = 0;

                    for (int i = 0; i < 8; i++)
                    {
                        if ((pxInd < array.length) && (array[pxInd] != c)) v |= (128 >> i);
                        pxInd++;
                    }

                    buffArr[buffInd++] = (byte)v;
                }
            }
        }

        return u_load(k1, k2);
    }

    // Pixel format converting (2.13 e-Paper display)
    //-----------------------------------------------------
    private boolean u_line(int c, int k1, int k2)
    {
        if (!load_stage) {
            buffArr[0] = (byte)'L';
            buffInd = 1;
            load_stage = true;
            System.out.println("u_line: LOAD");
            return u_send(false);
        }

        buffInd = 0;
        // 分辨率 122 * 250
        // 遍历像素数组
        // 每行122个像素，需要用122/8=15.25≈16个字节； 246-6=240个字节； 可以存储240/16=15行数据
        // 换句话讲，老程序默认缓冲区大小为256个字节，抛去头部的6个字节，就剩下250个字节，也只够存储15行数据
        // 当把缓冲区扩大为512字节之后，最多可以存储31行，即有效数据字节 6 + 16 * 31 = 502
        while ((pxInd < array.length) && (buffInd < BUFF_SIZE))     // 15*16+6 ，16*8 = 128
        {
            int v = 0;

            // 逐行扫描，将每8个像素的0或1，压缩成一个0~255的字节，存储在bufferArr[]里
            for (int i = 0; (i < 8) && (xLine < 122); i++, xLine++){
                // [0,1,0,1,0,1...]
                // i=0; array[0]=0; v=0
                // i=1; array[1]=1; v=0 | 0x80>>1 = 0x40
                // i=2; array[2]=0; v=0x40
                // i=3; array[3]=1; v=0x40 | 0x80>>3 = 0x40 | 0x10 = 0x50
                // i=4; array[4]=0; v=0x50
                // i=5; array[5]=1; v=0x50 | 0x80>>5 = 0x50 | 0x4 = 0x54
                // i=6; array[6]=0; v=0x54
                // i=7; array[7]=1; v=0x54 | 0x80>>7 = 0x54 | 0x1 = 0x55
                if (array[pxInd++] != c) v |= (128 >> i);
            }
            if(xLine >= 122 )xLine = 0;
            buffArr[buffInd++] = (byte)v;
        }

        return u_load(k1, k2);
    }
}
